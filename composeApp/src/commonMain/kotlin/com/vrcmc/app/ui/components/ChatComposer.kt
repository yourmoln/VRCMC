package com.vrcmc.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
internal fun ChatComposer(
    input: String,
    sending: Boolean,
    enabled: Boolean,
    interpreting: Boolean,
    alwaysInterpretationEnabled: Boolean,
    alwaysInterpretationActive: Boolean,
    voiceInputEnabled: Boolean,
    voiceRecording: Boolean,
    voiceSpeaking: Boolean,
    voiceTranscribing: Boolean,
    strings: LocaleStrings,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleAlwaysInterpretation: () -> Unit,
    onToggleVoiceInput: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val transition = rememberInfiniteTransition()
    val borderAngle by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        )
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val animatedBorder =
        if (interpreting) {
            Modifier.drawBehind {
                val radians = borderAngle * (kotlin.math.PI.toFloat() / 180f)
                val radius = size.maxDimension
                val directionX = cos(radians) * radius
                val directionY = sin(radians) * radius
                drawRoundRect(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    primary.copy(alpha = .18f),
                                    primary,
                                    primary.copy(alpha = .18f),
                                ),
                            start = Offset(center.x - directionX, center.y - directionY),
                            end = Offset(center.x + directionX, center.y + directionY),
                        ),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        } else Modifier

    fun sendAndKeepFocus() {
        onSend()
        scope.launch {
            yield()
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .then(animatedBorder),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        border = if (interpreting) null else BorderStroke(1.dp, outline),
    ) {
        Column {
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (voiceInputEnabled) {
                    FilledTonalIconButton(
                        enabled = enabled && !sending && !voiceTranscribing,
                        onClick = onToggleVoiceInput,
                        modifier = Modifier.size(44.dp),
                        colors =
                            if (voiceSpeaking)
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            else IconButtonDefaults.filledTonalIconButtonColors(),
                    ) {
                        if (voiceTranscribing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (voiceRecording) Icons.Default.Stop else Icons.Default.Mic,
                                if (voiceRecording) strings.stopVoiceInput else strings.startVoiceInput,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier =
                        Modifier.weight(1f)
                            .focusRequester(focusRequester)
                            .padding(vertical = 14.dp),
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                Text(
                                    if (enabled) strings.typeMessage else strings.addIp,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                if (alwaysInterpretationEnabled) {
                    FilledIconButton(
                        enabled = enabled && (!sending || alwaysInterpretationActive),
                        onClick = {
                            onToggleAlwaysInterpretation()
                            scope.launch {
                                yield()
                                focusRequester.requestFocus()
                            }
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            if (alwaysInterpretationActive) Icons.Default.Stop
                            else Icons.Default.PlayArrow,
                            if (alwaysInterpretationActive) strings.stopAlwaysInterpretation
                            else strings.startAlwaysInterpretation,
                        )
                    }
                } else {
                    FilledIconButton(
                        enabled = input.isNotBlank() && enabled,
                        onClick = ::sendAndKeepFocus,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, strings.send)
                    }
                }
            }
            if (voiceRecording) {
                Text(
                    if (voiceSpeaking) strings.voiceSpeechDetected else strings.voiceWaitingForSpeech,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (voiceSpeaking) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
