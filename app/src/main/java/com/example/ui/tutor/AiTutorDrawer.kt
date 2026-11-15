package com.example.ui.tutor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.viewmodel.LiveVoiceStatus
import com.example.ui.viewmodel.TutorMessage

@Composable
fun AiTutorDrawer(
    messages: List<TutorMessage>,
    isStreaming: Boolean,
    useDeepReasoning: Boolean,
    liveVoiceStatus: LiveVoiceStatus,
    liveVoiceError: String?,
    onToggleDeepReasoning: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onToggleLiveVoice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var micPermissionDenied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionDenied = false
            onToggleLiveVoice()
        } else {
            micPermissionDenied = true
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxSize()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Gemini AI Tutor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (useDeepReasoning) "Deep Reasoning (Argon)" else "Fast Multimodal (Flash)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = useDeepReasoning,
                        onClick = { onToggleDeepReasoning(!useDeepReasoning) },
                        label = { Text("Deep") }
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close AI Tutor")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Gemini 3.8 Live Real-Time Voice Conversation Card
            LiveVoiceTutorCard(
                status = liveVoiceStatus,
                error = liveVoiceError ?: if (micPermissionDenied) "Microphone permission is required for Live Voice." else null,
                onClick = {
                    val hasMic = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasMic || liveVoiceStatus == LiveVoiceStatus.ACTIVE) {
                        onToggleLiveVoice()
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Student Prompts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Explain my sheet step-by-step",
                    "Explain Fourier transform intuition",
                    "How do I find eigenvalues?",
                    "Simplify this Boolean logic"
                ).forEach { quick ->
                    AssistChip(
                        onClick = { onSendMessage(quick) },
                        label = { Text(quick, style = MaterialTheme.typography.bodyMedium) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Streaming Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (msg.isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = msg.text.ifBlank { if (msg.isStreaming) "Thinking..." else "" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = { Text("Ask about derivatives, circuits, proofs...") },
                    maxLines = 3,
                    modifier = Modifier.weight(1f).testTag("tutor_chat_input")
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    IconButton(
                        enabled = !isStreaming && promptText.isNotBlank(),
                        onClick = {
                            val q = promptText
                            promptText = ""
                            onSendMessage(q)
                        },
                        modifier = Modifier.testTag("tutor_send_button")
                    ) {
                        if (isStreaming) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send question",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveVoiceTutorCard(
    status: LiveVoiceStatus,
    error: String?,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "voice_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (status == LiveVoiceStatus.ACTIVE) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        color = when (status) {
            LiveVoiceStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
            LiveVoiceStatus.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(pulse)
                            .background(
                                if (status == LiveVoiceStatus.ACTIVE) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status == LiveVoiceStatus.ACTIVE) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Live voice status",
                            tint = if (status == LiveVoiceStatus.ACTIVE) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = when (status) {
                                LiveVoiceStatus.IDLE -> "Live Voice Math Tutor"
                                LiveVoiceStatus.CONNECTING -> "Connecting to Gemini Live..."
                                LiveVoiceStatus.ACTIVE -> "Live Voice Active — Speak Now"
                                LiveVoiceStatus.ERROR -> "Live Voice Unavailable"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Real-time bidirectional voice via Gemini Live API",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (status == LiveVoiceStatus.ACTIVE) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    IconButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("live_voice_toggle_button")
                    ) {
                        when (status) {
                            LiveVoiceStatus.CONNECTING -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            LiveVoiceStatus.ACTIVE -> Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "End live voice conversation",
                                tint = MaterialTheme.colorScheme.onError
                            )
                            else -> Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Start live voice conversation",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
