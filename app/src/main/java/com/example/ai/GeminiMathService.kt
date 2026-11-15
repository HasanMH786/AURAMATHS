package com.example.ai

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream

private const val TAG = "GeminiMathService"

data class MathOcrResponse(
    val recognizedExpression: String,
    val latex: String,
    val answer: String,
    val domainTag: String,
    val steps: List<String>,
    val graphExpression: String? = null,
    val is3DGraph: Boolean = false
)

class GeminiMathService {

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "recognizedExpression" to Schema.string(description = "Clean math expression parsed from handwriting"),
            "latex" to Schema.string(description = "Clean LaTeX representation of the handwritten math"),
            "answer" to Schema.string(description = "Exact solved answer"),
            "domainTag" to Schema.string(description = "Category: Calculus, Linear Algebra, Discrete Math, Boolean Algebra, Complex, Algebra, or Graph 2D / Graph 3D"),
            "steps" to Schema.array(
                items = Schema.string(),
                description = "Concise step-by-step derivation"
            ),
            "graphExpression" to Schema.string(
                description = "Plottable expression in x (or x,y for 3D) if the expression defines a function, otherwise empty",
                nullable = true
            ),
            "is3DGraph" to Schema.boolean(description = "True if the graphExpression is a 3D surface z(x,y)")
        ),
        optionalProperties = listOf("graphExpression")
    )

    private val ocrModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ocrSchema
            }
        )
    }

    private val tutorFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are AuraMath Studio's Principal Engineering & Mathematics Tutor. " +
                        "Explain calculus, linear algebra, discrete math, Boolean logic, complex numbers, " +
                        "and signal/systems problems with crisp mathematical precision and clear steps."
                )
            }
        )
    }

    private val tutorDeepModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text(
                    "You are AuraMath Studio's Principal Engineering & Mathematics Tutor in Deep Reasoning Mode. " +
                        "Provide rigorous step-by-step proofs, edge-case checks, and engineering insights."
                )
            }
        )
    }

    suspend fun recognizeAndSolveCanvas(
        bitmap: Bitmap,
        variables: Map<String, Double>
    ): Result<MathOcrResponse> {
        return try {
            val scaled = scaleBitmapIfNeeded(bitmap, maxDim = 1280)
            val varContext = if (variables.isEmpty()) {
                "None"
            } else {
                variables.entries.joinToString(", ") { "${it.key} = ${it.value}" }
            }

            val promptText = """
                Examine the handwritten mathematical expression or engineering diagram in this canvas snapshot.
                Active workspace variables: $varContext.
                1. Transcribe the handwritten math cleanly into `recognizedExpression` (plain ASCII math syntax like d/dx(x^3), det([2,3;1,4]), (1011)_2 + (F)_16, or y = x^2 - 4*x + 3) and `latex`.
                2. Solve the expression accurately using the active variables if referenced.
                3. Provide clear, concise step-by-step reasoning in `steps`.
                4. If it defines a 2D function of x or 3D surface z(x,y), populate `graphExpression` and `is3DGraph`.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                ocrModel.generateContent(
                    content {
                        image(scaled)
                        text(promptText)
                    }
                )
            }

            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty OCR response."))

            val json = JSONObject(rawJson)
            val stepsArr = json.optJSONArray("steps")
            val steps = if (stepsArr != null) {
                (0 until stepsArr.length()).map { stepsArr.getString(it) }
            } else {
                emptyList()
            }
            val graphExpr = json.optString("graphExpression", "").takeIf { it.isNotBlank() }

            Result.success(
                MathOcrResponse(
                    recognizedExpression = json.optString("recognizedExpression", ""),
                    latex = json.optString("latex", ""),
                    answer = json.optString("answer", ""),
                    domainTag = json.optString("domainTag", "Algebra"),
                    steps = steps,
                    graphExpression = graphExpr,
                    is3DGraph = json.optBoolean("is3DGraph", false)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "OCR timed out", e)
            Result.failure(Exception("Handwriting recognition timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            Result.failure(e)
        }
    }

    fun streamTutorResponse(
        question: String,
        sheetContext: String,
        useDeepReasoning: Boolean = false
    ): Flow<String> = flow {
        val model = if (useDeepReasoning) tutorDeepModel else tutorFlashModel
        val fullPrompt = buildString {
            if (sheetContext.isNotBlank()) {
                appendLine("Current AuraMath Sheet Context:")
                appendLine(sheetContext)
                appendLine()
            }
            append("Student Question: ")
            append(question)
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI Tutor returned an empty response.")
        }
    }

    @OptIn(PublicPreviewAPI::class)
    private val liveModel by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = "gemini-3.8-live",
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                speechConfig = SpeechConfig(voice = Voice("FENRIR"))
            },
            systemInstruction = content {
                text(
                    "You are AuraMath's live voice math & engineering tutor. " +
                        "Keep explanations crisp, intuitive, and conversational, guiding students step-by-step."
                )
            }
        )
    }

    @OptIn(PublicPreviewAPI::class)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startLiveVoiceSession(sheetContext: String): LiveSession {
        val session = withTimeout(30_000L) {
            liveModel.connect()
        }
        if (sheetContext.isNotBlank()) {
            session.send("Context of student's current AuraMath sheet:\n$sheetContext")
        }
        session.startAudioConversation()
        return session
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
        // Ensure payload stays well under 20MB
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 90, baos)
        return scaled
    }
}
