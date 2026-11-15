package com.example.ui.viewmodel

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiMathService
import com.example.data.model.InkStrokeDoc
import com.example.data.model.MathEntry
import com.example.data.model.MathNotebook
import com.example.data.repository.NotebookRepository
import com.example.engine.AuraMathEngine
import com.example.engine.GraphSpec
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuraMathVM"

data class GraphCurve(
    val id: String,
    val expression: String,
    val label: String,
    val derivativeExpr: String? = null,
    val showDerivative: Boolean = false,
    val roots: List<Double> = emptyList(),
    val yIntercept: Double? = null,
    val colorIndex: Int = 0
)

data class TutorMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

enum class LiveVoiceStatus {
    IDLE, CONNECTING, ACTIVE, ERROR
}

data class AuraWorkspaceState(
    val notebooks: List<MathNotebook> = emptyList(),
    val activeNotebook: MathNotebook? = null,
    val entries: List<MathEntry> = emptyList(),
    val strokes: List<InkStrokeDoc> = emptyList(),
    val variables: Map<String, Double> = emptyMap(),
    val graphCurves: List<GraphCurve> = emptyList(),
    val active3DSurface: String? = null,
    val showGraphPanel: Boolean = false,
    val showTutorPanel: Boolean = false,
    val isOcrSolving: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    val isDarkTheme: Boolean = true,
    val tutorMessages: List<TutorMessage> = listOf(
        TutorMessage(
            id = "welcome",
            isUser = false,
            text = "Welcome to AuraMath Studio. Write an equation ending in '=' on the canvas or expression bar, plot 2D/3D curves, or ask me any engineering math question."
        )
    ),
    val useDeepReasoning: Boolean = false,
    val isTutorStreaming: Boolean = false,
    val liveVoiceStatus: LiveVoiceStatus = LiveVoiceStatus.IDLE,
    val liveVoiceError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AuraMathViewModel(
    private val repository: NotebookRepository,
    private val aiService: GeminiMathService,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedNotebookId = MutableStateFlow<String?>(null)
    private val _localState = MutableStateFlow(AuraWorkspaceState())
    private var autoCreatedDefault = false

    private var expressionDebounceJob: Job? = null
    private var tutorJob: Job? = null

    @OptIn(PublicPreviewAPI::class)
    private var activeLiveSession: LiveSession? = null

    private val notebooksFlow: StateFlow<List<MathNotebook>> =
        repository.observeNotebooks(currentUserId)
            .catch { err ->
                val fsErr = unwrapFirestore(err)
                if (fsErr == null && err is CancellationException) throw err
                Log.w(TAG, "Failed observing notebooks", fsErr ?: err)
                _localState.update { it.copy(errorMessage = (fsErr ?: err).localizedMessage) }
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val entriesFlow: StateFlow<List<MathEntry>> =
        combine(notebooksFlow, _selectedNotebookId) { list, selected ->
            selected?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { nbId ->
            if (nbId == null) flowOf(emptyList())
            else repository.observeEntries(currentUserId, nbId)
                .catch { err ->
                    val fsErr = unwrapFirestore(err)
                    if (fsErr == null && err is CancellationException) throw err
                    Log.w(TAG, "Failed observing entries", fsErr ?: err)
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val strokesFlow: StateFlow<List<InkStrokeDoc>> =
        combine(notebooksFlow, _selectedNotebookId) { list, selected ->
            selected?.takeIf { id -> list.any { it.id == id } } ?: list.firstOrNull()?.id
        }.flatMapLatest { nbId ->
            if (nbId == null) flowOf(emptyList())
            else repository.observeStrokes(currentUserId, nbId)
                .catch { err ->
                    val fsErr = unwrapFirestore(err)
                    if (fsErr == null && err is CancellationException) throw err
                    Log.w(TAG, "Failed observing strokes", fsErr ?: err)
                    emit(emptyList())
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val uiState: StateFlow<AuraWorkspaceState> = combine(
        notebooksFlow,
        entriesFlow,
        strokesFlow,
        _selectedNotebookId,
        _localState
    ) { notebooks, entries, strokes, selectedId, local ->
        val activeNb = notebooks.firstOrNull { it.id == selectedId } ?: notebooks.firstOrNull()
        val vars = linkedMapOf<String, Double>()
        entries.sortedBy { it.orderIndex }.forEach { entry ->
            runCatching {
                val res = AuraMathEngine.evaluate(entry.expression, vars)
                if (res.assignedVarName != null && res.assignedVarValue != null) {
                    vars[res.assignedVarName] = res.assignedVarValue
                }
            }
        }
        local.copy(
            notebooks = notebooks,
            activeNotebook = activeNb,
            entries = entries,
            strokes = strokes,
            variables = vars
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AuraWorkspaceState())

    init {
        viewModelScope.launch {
            notebooksFlow.collect { list ->
                if (list.isEmpty() && !autoCreatedDefault) {
                    autoCreatedDefault = true
                    val created = repository.createNotebook("Engineering & Calculus")
                    created.getOrNull()?.let { _selectedNotebookId.value = it }
                } else if (_selectedNotebookId.value == null && list.isNotEmpty()) {
                    _selectedNotebookId.value = list.first().id
                }
            }
        }
    }

    fun selectNotebook(notebookId: String) {
        _selectedNotebookId.value = notebookId
    }

    fun createNotebook(title: String) {
        viewModelScope.launch {
            repository.createNotebook(title).onSuccess { newId ->
                _selectedNotebookId.value = newId
            }.onFailure { err ->
                _localState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun deleteNotebook(notebookId: String) {
        viewModelScope.launch {
            repository.deleteNotebook(notebookId).onFailure { err ->
                _localState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun toggleTheme() {
        _localState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun toggleGraphPanel(show: Boolean? = null) {
        _localState.update { it.copy(showGraphPanel = show ?: !it.showGraphPanel) }
    }

    fun toggleTutorPanel(show: Boolean? = null) {
        _localState.update { it.copy(showTutorPanel = show ?: !it.showTutorPanel) }
    }

    fun setDeepReasoning(enabled: Boolean) {
        _localState.update { it.copy(useDeepReasoning = enabled) }
    }

    fun clearBanner() {
        _localState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    fun onExpressionTyped(raw: String, onSolvedAndCleared: () -> Unit) {
        expressionDebounceJob?.cancel()
        if (raw.trimEnd().endsWith("=")) {
            expressionDebounceJob = viewModelScope.launch {
                delay(700L)
                evaluateAndSaveExpression(raw)
                onSolvedAndCleared()
            }
        }
    }

    fun evaluateAndSaveExpression(raw: String, anchorX: Double = 80.0, anchorY: Double = 120.0) {
        val state = uiState.value
        val nbId = state.activeNotebook?.id ?: return
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            try {
                val res = AuraMathEngine.evaluate(trimmed, state.variables)
                val entry = MathEntry(
                    expression = res.expression,
                    result = res.result,
                    steps = res.steps,
                    domainTag = res.domainTag,
                    graphType = when {
                        res.graphSpec == null -> "NONE"
                        res.graphSpec.is3D -> "3D"
                        else -> "2D"
                    },
                    anchorX = anchorX,
                    anchorY = anchorY,
                    orderIndex = (state.entries.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
                )
                repository.saveEntry(nbId, entry).onFailure { err ->
                    _localState.update { it.copy(errorMessage = err.localizedMessage) }
                }
                res.graphSpec?.let { applyGraphSpec(it) }

                // Cascade re-evaluate downstream entries if a variable was assigned
                if (res.assignedVarName != null) {
                    reEvaluateCascade(nbId, state.entries, res.assignedVarName to (res.assignedVarValue ?: 0.0))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _localState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "Unable to evaluate '$trimmed'")
                }
            }
        }
    }

    private suspend fun reEvaluateCascade(
        nbId: String,
        existingEntries: List<MathEntry>,
        updatedVar: Pair<String, Double>
    ) {
        val env = uiState.value.variables.toMutableMap()
        env[updatedVar.first] = updatedVar.second
        for (entry in existingEntries.sortedBy { it.orderIndex }) {
            runCatching {
                val reEval = AuraMathEngine.evaluate(entry.expression, env)
                if (reEval.assignedVarName != null && reEval.assignedVarValue != null) {
                    env[reEval.assignedVarName] = reEval.assignedVarValue
                }
                if (reEval.result != entry.result && entry.id.isNotBlank()) {
                    repository.saveEntry(
                        nbId,
                        entry.copy(result = reEval.result, steps = reEval.steps)
                    )
                }
            }
        }
    }

    fun plotEntry(entry: MathEntry) {
        viewModelScope.launch {
            runCatching {
                val res = AuraMathEngine.evaluate(entry.expression, uiState.value.variables)
                if (res.graphSpec != null) {
                    applyGraphSpec(res.graphSpec)
                } else {
                    // Attempt direct expression plot
                    val spec = GraphSpec(is3D = false, expression = entry.expression, label = entry.expression)
                    applyGraphSpec(spec)
                }
            }.onFailure { err ->
                _localState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    private fun applyGraphSpec(spec: GraphSpec) {
        _localState.update { state ->
            if (spec.is3D) {
                state.copy(active3DSurface = spec.expression, showGraphPanel = true)
            } else {
                val existing = state.graphCurves.filterNot { it.expression == spec.expression }
                val newCurve = GraphCurve(
                    id = "${spec.expression}_${existing.size}",
                    expression = spec.expression,
                    label = spec.label,
                    derivativeExpr = spec.derivativeExpr,
                    showDerivative = spec.derivativeExpr != null,
                    roots = spec.roots,
                    yIntercept = spec.yIntercept,
                    colorIndex = existing.size
                )
                state.copy(
                    graphCurves = (existing + newCurve).takeLast(5),
                    active3DSurface = null,
                    showGraphPanel = true
                )
            }
        }
    }

    fun addGraphCurve(expr: String) {
        viewModelScope.launch {
            runCatching {
                val res = AuraMathEngine.evaluate(expr, uiState.value.variables)
                val spec = res.graphSpec ?: GraphSpec(is3D = false, expression = expr, label = "f(x) = $expr")
                applyGraphSpec(spec)
            }.onFailure { err ->
                _localState.update { it.copy(errorMessage = err.localizedMessage) }
            }
        }
    }

    fun toggleCurveDerivative(curveId: String) {
        _localState.update { state ->
            state.copy(
                graphCurves = state.graphCurves.map {
                    if (it.id == curveId) it.copy(showDerivative = !it.showDerivative) else it
                }
            )
        }
    }

    fun removeGraphCurve(curveId: String) {
        _localState.update { state ->
            state.copy(graphCurves = state.graphCurves.filterNot { it.id == curveId })
        }
    }

    fun saveInkStroke(
        colorArgb: Long,
        strokeWidth: Double,
        toolType: String,
        points: List<Map<String, Double>>
    ) {
        val nbId = uiState.value.activeNotebook?.id ?: return
        if (points.isEmpty()) return
        viewModelScope.launch {
            repository.saveStroke(
                nbId,
                InkStrokeDoc(
                    colorArgb = colorArgb,
                    strokeWidth = strokeWidth,
                    toolType = toolType,
                    points = points
                )
            )
        }
    }

    fun undoLastStroke() {
        val state = uiState.value
        val nbId = state.activeNotebook?.id ?: return
        val last = state.strokes.lastOrNull() ?: return
        viewModelScope.launch {
            repository.deleteStroke(nbId, last.id)
        }
    }

    fun clearCanvasStrokes() {
        val nbId = uiState.value.activeNotebook?.id ?: return
        viewModelScope.launch {
            repository.clearStrokes(nbId)
        }
    }

    fun deleteEntry(entryId: String) {
        val nbId = uiState.value.activeNotebook?.id ?: return
        viewModelScope.launch {
            repository.deleteEntry(nbId, entryId)
        }
    }

    fun solveCanvasBitmapWithGemini(bitmap: Bitmap, anchorX: Double, anchorY: Double) {
        val state = uiState.value
        val nbId = state.activeNotebook?.id ?: return
        viewModelScope.launch {
            _localState.update { it.copy(isOcrSolving = true, statusBanner = "Analyzing handwriting with Gemini Vision...") }
            val result = aiService.recognizeAndSolveCanvas(bitmap, state.variables)
            result.onSuccess { ocr ->
                val expr = ocr.recognizedExpression.ifBlank { ocr.latex }
                // Cross-verify with local deterministic engine when possible
                val localCheck = runCatching { AuraMathEngine.evaluate(expr, state.variables) }.getOrNull()
                val finalResult = localCheck?.result ?: ocr.answer
                val finalSteps = if (ocr.steps.isNotEmpty()) ocr.steps else localCheck?.steps.orEmpty()

                val entry = MathEntry(
                    expression = expr.ifBlank { "Handwritten Expression" },
                    result = finalResult,
                    steps = finalSteps,
                    domainTag = ocr.domainTag.ifBlank { localCheck?.domainTag ?: "Gemini OCR" },
                    graphType = when {
                        ocr.is3DGraph -> "3D"
                        !ocr.graphExpression.isNullOrBlank() || localCheck?.graphSpec != null -> "2D"
                        else -> "NONE"
                    },
                    anchorX = anchorX,
                    anchorY = anchorY,
                    orderIndex = (state.entries.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
                )
                repository.saveEntry(nbId, entry)

                if (!ocr.graphExpression.isNullOrBlank()) {
                    applyGraphSpec(
                        GraphSpec(
                            is3D = ocr.is3DGraph,
                            expression = ocr.graphExpression,
                            label = expr
                        )
                    )
                } else if (localCheck?.graphSpec != null) {
                    applyGraphSpec(localCheck.graphSpec)
                }

                _localState.update {
                    it.copy(
                        isOcrSolving = false,
                        statusBanner = "Solved: $expr = $finalResult"
                    )
                }
            }.onFailure { err ->
                _localState.update {
                    it.copy(
                        isOcrSolving = false,
                        statusBanner = null,
                        errorMessage = err.localizedMessage ?: "Gemini OCR failed"
                    )
                }
            }
        }
    }

    fun sendTutorMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isBlank()) return
        val state = uiState.value
        val userMsg = TutorMessage(id = "u_${System.currentTimeMillis()}", isUser = true, text = trimmed)
        val botId = "m_${System.currentTimeMillis() + 1}"
        val placeholder = TutorMessage(id = botId, isUser = false, text = "", isStreaming = true)

        _localState.update {
            it.copy(
                tutorMessages = it.tutorMessages + userMsg + placeholder,
                isTutorStreaming = true
            )
        }

        val sheetContext = buildSheetSummary(state)
        tutorJob?.cancel()
        tutorJob = viewModelScope.launch {
            try {
                aiService.streamTutorResponse(trimmed, sheetContext, state.useDeepReasoning)
                    .collect { partial ->
                        _localState.update { s ->
                            s.copy(
                                tutorMessages = s.tutorMessages.map { m ->
                                    if (m.id == botId) m.copy(text = partial, isStreaming = true) else m
                                }
                            )
                        }
                    }
                _localState.update { s ->
                    s.copy(
                        isTutorStreaming = false,
                        tutorMessages = s.tutorMessages.map { m ->
                            if (m.id == botId) m.copy(isStreaming = false) else m
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tutor stream failed", e)
                _localState.update { s ->
                    s.copy(
                        isTutorStreaming = false,
                        tutorMessages = s.tutorMessages.map { m ->
                            if (m.id == botId) {
                                m.copy(
                                    text = "Error: ${e.localizedMessage ?: "Failed to reach Gemini AI."}",
                                    isStreaming = false
                                )
                            } else m
                        }
                    )
                }
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun toggleLiveVoiceSession() {
        val current = uiState.value.liveVoiceStatus
        if (current == LiveVoiceStatus.ACTIVE || current == LiveVoiceStatus.CONNECTING) {
            stopLiveVoiceSession()
            return
        }
        viewModelScope.launch {
            _localState.update { it.copy(liveVoiceStatus = LiveVoiceStatus.CONNECTING, liveVoiceError = null) }
            try {
                val contextSummary = buildSheetSummary(uiState.value)
                val session = aiService.startLiveVoiceSession(contextSummary)
                activeLiveSession = session
                _localState.update { it.copy(liveVoiceStatus = LiveVoiceStatus.ACTIVE) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live API session failed", e)
                _localState.update {
                    it.copy(
                        liveVoiceStatus = LiveVoiceStatus.ERROR,
                        liveVoiceError = e.localizedMessage ?: "Live Voice API connection failed."
                    )
                }
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    fun stopLiveVoiceSession() {
        viewModelScope.launch {
            runCatching { activeLiveSession?.close() }
            activeLiveSession = null
            _localState.update { it.copy(liveVoiceStatus = LiveVoiceStatus.IDLE, liveVoiceError = null) }
        }
    }

    private fun buildSheetSummary(state: AuraWorkspaceState): String {
        return buildString {
            appendLine("Notebook: ${state.activeNotebook?.title ?: "Untitled"}")
            if (state.variables.isNotEmpty()) {
                appendLine("Variables: ${state.variables.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
            }
            state.entries.takeLast(12).forEach { e ->
                appendLine("- [${e.domainTag}] ${e.expression} = ${e.result}")
            }
        }
    }

    private fun unwrapFirestore(t: Throwable): FirebaseFirestoreException? {
        return generateSequence(t) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    @OptIn(PublicPreviewAPI::class)
    override fun onCleared() {
        super.onCleared()
        val session = activeLiveSession
        activeLiveSession = null
        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { session.close() }
            }
        }
    }
}
