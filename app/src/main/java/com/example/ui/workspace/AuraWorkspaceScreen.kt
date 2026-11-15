package com.example.ui.workspace

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.auth.signOut
import com.example.ui.canvas.InkCanvasView
import com.example.ui.graph.GraphStudioSheet
import com.example.ui.theme.AmberMath
import com.example.ui.theme.CyanAura
import com.example.ui.theme.EmeraldSolved
import com.example.ui.theme.InkWhite
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.RoseCurve
import com.example.ui.theme.VioletMatrix
import com.example.ui.tutor.AiTutorDrawer
import com.example.ui.viewmodel.AuraMathViewModel
import com.example.ui.viewmodel.AuraWorkspaceState

private val InkSwatches = listOf(CyanAura, AmberMath, InkWhite, RoseCurve, EmeraldSolved, VioletMatrix)

@Composable
fun AuraWorkspaceScreen(
    viewModel: AuraMathViewModel,
    onThemeChanged: (Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var activeColor by remember { mutableStateOf(CyanAura) }
    var strokeWidth by remember { mutableFloatStateOf(5f) }
    var isHighlighter by remember { mutableStateOf(false) }
    var palmRejection by remember { mutableStateOf(false) }
    var autoSolveOnPause by remember { mutableStateOf(false) }
    var panMode by remember { mutableStateOf(false) }

    var expressionInput by remember { mutableStateOf("") }
    var showVariablesDialog by remember { mutableStateOf(false) }
    var showNewNotebookDialog by remember { mutableStateOf(false) }
    var showPresetsBar by remember { mutableStateOf(true) }
    var bitmapProvider by remember { mutableStateOf<(() -> Bitmap)?>(null) }

    BackHandler(enabled = state.showGraphPanel || state.showTutorPanel) {
        if (state.showTutorPanel) viewModel.toggleTutorPanel(false)
        else if (state.showGraphPanel) viewModel.toggleGraphPanel(false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top Glassmorphic Studio Bar
            StudioTopBar(
                state = state,
                onSelectNotebook = viewModel::selectNotebook,
                onOpenNewNotebook = { showNewNotebookDialog = true },
                onShowVariables = { showVariablesDialog = true },
                onToggleTheme = {
                    viewModel.toggleTheme()
                    onThemeChanged(!state.isDarkTheme)
                },
                onToggleGraph = {
                    viewModel.toggleTutorPanel(false)
                    viewModel.toggleGraphPanel()
                },
                onToggleTutor = {
                    viewModel.toggleGraphPanel(false)
                    viewModel.toggleTutorPanel()
                },
                onSignOut = {
                    signOut(credentialManager, scope)
                }
            )

            // Status / Error Banner
            AnimatedVisibility(visible = state.statusBanner != null || state.errorMessage != null) {
                val isError = state.errorMessage != null
                Surface(
                    color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isOcrSolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                text = state.errorMessage ?: state.statusBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        TextButton(onClick = viewModel::clearBanner) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Adaptive Main Area (Split-pane on Tablet/Foldable, Overlay on Phone)
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val isWide = maxWidth >= 720.dp
                val sidePanelOpen = state.showGraphPanel || state.showTutorPanel

                Row(modifier = Modifier.fillMaxSize()) {
                    // Infinite Canvas + Floating Ink Toolbar
                    Box(
                        modifier = Modifier
                            .weight(if (isWide && sidePanelOpen) 0.56f else 1f)
                            .fillMaxHeight()
                    ) {
                        InkCanvasView(
                            strokes = state.strokes,
                            entries = state.entries,
                            activeColor = activeColor,
                            strokeWidth = strokeWidth,
                            isHighlighter = isHighlighter,
                            palmRejectionEnabled = palmRejection,
                            autoSolveOnPause = autoSolveOnPause,
                            panMode = panMode,
                            onStrokeCompleted = viewModel::saveInkStroke,
                            onAutoSolveInkSnapshot = { bmp, ax, ay ->
                                viewModel.solveCanvasBitmapWithGemini(bmp, ax, ay)
                            },
                            onPlotEntry = viewModel::plotEntry,
                            onDeleteEntry = viewModel::deleteEntry,
                            onRequestManualOcrReady = { provider -> bitmapProvider = provider },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Glassmorphic Ink Toolbar
                        InkToolbarOverlay(
                            activeColor = activeColor,
                            isHighlighter = isHighlighter,
                            panMode = panMode,
                            palmRejection = palmRejection,
                            autoSolveOnPause = autoSolveOnPause,
                            isOcrSolving = state.isOcrSolving,
                            onSelectColor = {
                                activeColor = it
                                panMode = false
                            },
                            onToggleHighlighter = {
                                isHighlighter = !isHighlighter
                                panMode = false
                            },
                            onTogglePan = { panMode = !panMode },
                            onTogglePalmRejection = { palmRejection = !palmRejection },
                            onToggleAutoSolve = { autoSolveOnPause = !autoSolveOnPause },
                            onUndo = viewModel::undoLastStroke,
                            onClear = viewModel::clearCanvasStrokes,
                            onSolveInk = {
                                val bmp = bitmapProvider?.invoke()
                                if (bmp != null) {
                                    viewModel.solveCanvasBitmapWithGemini(bmp, 100.0, 140.0)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp, start = 12.dp, end = 12.dp)
                        )
                    }

                    // Side Pane on Wide Screens
                    if (isWide && sidePanelOpen) {
                        Box(
                            modifier = Modifier
                                .weight(0.44f)
                                .fillMaxHeight()
                                .padding(8.dp)
                        ) {
                            if (state.showGraphPanel) {
                                GraphStudioSheet(
                                    curves = state.graphCurves,
                                    active3DSurface = state.active3DSurface,
                                    variables = state.variables,
                                    onAddCurve = viewModel::addGraphCurve,
                                    onToggleDerivative = viewModel::toggleCurveDerivative,
                                    onRemoveCurve = viewModel::removeGraphCurve,
                                    onClose = { viewModel.toggleGraphPanel(false) }
                                )
                            } else if (state.showTutorPanel) {
                                AiTutorDrawer(
                                    messages = state.tutorMessages,
                                    isStreaming = state.isTutorStreaming,
                                    useDeepReasoning = state.useDeepReasoning,
                                    liveVoiceStatus = state.liveVoiceStatus,
                                    liveVoiceError = state.liveVoiceError,
                                    onToggleDeepReasoning = viewModel::setDeepReasoning,
                                    onSendMessage = viewModel::sendTutorMessage,
                                    onToggleLiveVoice = viewModel::toggleLiveVoiceSession,
                                    onClose = { viewModel.toggleTutorPanel(false) }
                                )
                            }
                        }
                    }
                }

                // Compact Screen Modal Overlay for Graph Studio or AI Tutor
                if (!isWide && sidePanelOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(10.dp)
                    ) {
                        if (state.showGraphPanel) {
                            GraphStudioSheet(
                                curves = state.graphCurves,
                                active3DSurface = state.active3DSurface,
                                variables = state.variables,
                                onAddCurve = viewModel::addGraphCurve,
                                onToggleDerivative = viewModel::toggleCurveDerivative,
                                onRemoveCurve = viewModel::removeGraphCurve,
                                onClose = { viewModel.toggleGraphPanel(false) }
                            )
                        } else if (state.showTutorPanel) {
                            AiTutorDrawer(
                                messages = state.tutorMessages,
                                isStreaming = state.isTutorStreaming,
                                useDeepReasoning = state.useDeepReasoning,
                                liveVoiceStatus = state.liveVoiceStatus,
                                liveVoiceError = state.liveVoiceError,
                                onToggleDeepReasoning = viewModel::setDeepReasoning,
                                onSendMessage = viewModel::sendTutorMessage,
                                onToggleLiveVoice = viewModel::toggleLiveVoiceSession,
                                onClose = { viewModel.toggleTutorPanel(false) }
                            )
                        }
                    }
                }
            }

            // Engineering Presets + Smart Expression Bar (`=` 700ms Auto-Solve)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (showPresetsBar) {
                        EngineeringPresetRibbon(
                            onInsertExpression = { expr ->
                                expressionInput = expr
                                viewModel.evaluateAndSaveExpression(
                                    expr,
                                    anchorX = 40.0 + (state.entries.size % 2) * 30.0,
                                    anchorY = 85.0 + (state.entries.size * 110.0)
                                )
                                expressionInput = ""
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showPresetsBar = !showPresetsBar },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Functions,
                                contentDescription = "Toggle engineering presets",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = expressionInput,
                            onValueChange = { updated ->
                                expressionInput = updated
                                viewModel.onExpressionTyped(updated) {
                                    expressionInput = ""
                                }
                            },
                            placeholder = {
                                Text(
                                    "Type math ending with '=' (e.g., d/dx(x^3) =, det([2,3;1,4]) =)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    maxLines = 1
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (expressionInput.isNotBlank()) {
                                        viewModel.evaluateAndSaveExpression(
                                            expressionInput,
                                            anchorX = 40.0,
                                            anchorY = 85.0 + (state.entries.size * 110.0)
                                        )
                                        expressionInput = ""
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f).testTag("smart_expression_input")
                        )

                        Button(
                            onClick = {
                                if (expressionInput.isNotBlank()) {
                                    viewModel.evaluateAndSaveExpression(
                                        expressionInput,
                                        anchorX = 40.0,
                                        anchorY = 85.0 + (state.entries.size * 110.0)
                                    )
                                    expressionInput = ""
                                }
                            },
                            enabled = expressionInput.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(50.dp).testTag("solve_expression_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Evaluate expression",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Solve")
                        }
                    }
                }
            }
        }
    }

    if (showVariablesDialog) {
        AlertDialog(
            onDismissRequest = { showVariablesDialog = false },
            title = { Text("Cascading Variable Memory") },
            text = {
                if (state.variables.isEmpty()) {
                    Text("No variables defined yet. Try entering 'a = 10' then 'b = 25' then 'Hypotenuse = sqrt(a^2 + b^2) ='.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.variables.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(k, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold)
                                Text(
                                    com.example.engine.MathAstParser.formatNumber(v),
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVariablesDialog = false }) { Text("Close") }
            }
        )
    }

    if (showNewNotebookDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewNotebookDialog = false },
            title = { Text("New AuraMath Sheet") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Sheet Title") },
                    placeholder = { Text("e.g., Digital Logic & Matrices") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNotebook(newTitle.ifBlank { "Untitled Sheet" })
                        showNewNotebookDialog = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewNotebookDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StudioTopBar(
    state: AuraWorkspaceState,
    onSelectNotebook: (String) -> Unit,
    onOpenNewNotebook: () -> Unit,
    onShowVariables: () -> Unit,
    onToggleTheme: () -> Unit,
    onToggleGraph: () -> Unit,
    onToggleTutor: () -> Unit,
    onSignOut: () -> Unit
) {
    var nbMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { nbMenuExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = state.activeNotebook?.title ?: "AuraMath",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Switch notebook",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = nbMenuExpanded,
                    onDismissRequest = { nbMenuExpanded = false }
                ) {
                    state.notebooks.forEach { nb ->
                        DropdownMenuItem(
                            text = { Text(nb.title) },
                            onClick = {
                                onSelectNotebook(nb.id)
                                nbMenuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ New Sheet") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = {
                            nbMenuExpanded = false
                            onOpenNewNotebook()
                        }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clickable(onClick = onShowVariables)
                        .testTag("variables_chip")
                ) {
                    Text(
                        text = "Vars (${state.variables.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onToggleGraph,
                    modifier = Modifier.testTag("toggle_graph_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShowChart,
                        contentDescription = "Toggle 2D/3D Graph Studio",
                        tint = if (state.showGraphPanel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onToggleTutor,
                    modifier = Modifier.testTag("toggle_tutor_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Toggle Gemini AI Tutor",
                        tint = if (state.showTutorPanel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (state.isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle Dark/Light Theme"
                    )
                }

                IconButton(onClick = onSignOut) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign out"
                    )
                }
            }
        }
    }
}

@Composable
private fun InkToolbarOverlay(
    activeColor: Color,
    isHighlighter: Boolean,
    panMode: Boolean,
    palmRejection: Boolean,
    autoSolveOnPause: Boolean,
    isOcrSolving: Boolean,
    onSelectColor: (Color) -> Unit,
    onToggleHighlighter: () -> Unit,
    onTogglePan: () -> Unit,
    onTogglePalmRejection: () -> Unit,
    onToggleAutoSolve: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSolveInk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .widthIn(max = 720.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InkSwatches.forEach { swatch ->
                val selected = activeColor == swatch && !panMode
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (selected) 2.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                        .clickable { onSelectColor(swatch) }
                )
            }

            FilterChip(
                selected = isHighlighter && !panMode,
                onClick = onToggleHighlighter,
                label = { Text("Highlight") },
                leadingIcon = {
                    Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            FilterChip(
                selected = panMode,
                onClick = onTogglePan,
                label = { Text("Pan/Zoom") },
                leadingIcon = {
                    Icon(Icons.Rounded.PanTool, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            FilterChip(
                selected = palmRejection,
                onClick = onTogglePalmRejection,
                label = { Text("Stylus Only") },
                leadingIcon = {
                    Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            FilterChip(
                selected = autoSolveOnPause,
                onClick = onToggleAutoSolve,
                label = { Text("Auto =") },
                leadingIcon = {
                    Icon(Icons.Rounded.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )

            Button(
                onClick = onSolveInk,
                enabled = !isOcrSolving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(36.dp).testTag("solve_ink_ocr_button")
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Solve Ink", style = MaterialTheme.typography.labelLarge)
            }

            IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo last stroke")
            }

            IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear ink strokes")
            }
        }
    }
}

@Composable
private fun EngineeringPresetRibbon(
    onInsertExpression: (String) -> Unit
) {
    val presets = listOf(
        "a = 10" to "a = 10",
        "b = 25" to "b = 25",
        "Hypotenuse" to "Hypotenuse = sqrt(a^2 + b^2)",
        "d/dx(x³)" to "d/dx(x^3) =",
        "d²/dx²" to "d^2/dx^2(sin(x)*x^2) =",
        "∫(x²,0,3)" to "integrate(x^2, 0, 3) =",
        "det(A)" to "det([2, 3; 1, 4]) =",
        "inv(A)" to "inv([4, 7; 2, 6]) =",
        "eigen(A)" to "eigen([2, 1; 1, 2]) =",
        "Bin+Hex" to "(1011)_2 + (F)_16 =",
        "Truth Table" to "truth(A && B || !C) =",
        "Polar" to "polar(3 + 4i) =",
        "2D Curve" to "y = x^2 - 4x + 3",
        "3D Surface" to "z = sin(x) * cos(y)"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presets.forEach { (label, expr) ->
            AssistChip(
                onClick = { onInsertExpression(expr) },
                label = {
                    Text(
                        text = label,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}
