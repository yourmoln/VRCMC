package com.vrcmc.app

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
    maxInputCharacters: Int,
    strings: LocaleStrings,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleAlwaysInterpretation: () -> Unit,
    onToggleVoiceInput: () -> Unit,
    animationTimeNanos: State<Long>? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val animationTime = animationTimeNanos ?: rememberChatAnimationTime(
        active = !alwaysInterpretationActive && (sending || (voiceInputEnabled && voiceTranscribing))
    )

    fun sendAndKeepFocus() {
        onSend()
        scope.launch {
            yield()
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (voiceTranscribing) {
            Text(
                strings.recognizing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
                modifier = Modifier.align(Alignment.End).padding(end = 18.dp, bottom = 2.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color =
                if (interpreting)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .28f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
            border =
                BorderStroke(
                    if (interpreting) 2.dp else 1.dp,
                    if (interpreting) primary.copy(alpha = .34f) else outline,
                ),
        ) {
            Box {
                Column {
                    if (sending && !alwaysInterpretationActive) {
                        ChatLinearProgressIndicator(animationTime, Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(start = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (voiceInputEnabled) {
                            FilledTonalIconButton(
                                enabled =
                                    enabled &&
                                        !sending &&
                                        (!voiceTranscribing || (alwaysInterpretationActive && voiceRecording)),
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
                                if (voiceRecording) {
                                    Icon(Icons.Default.Stop, strings.stopVoiceInput)
                                } else if (voiceTranscribing) {
                                    if (alwaysInterpretationActive) {
                                        Icon(Icons.Default.Mic, strings.startVoiceInput, tint = primary)
                                    } else {
                                        ChatCircularProgressIndicator(
                                            animationTime,
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.Mic,
                                        strings.startVoiceInput,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        ChatComposerTextInput(
                            input = input,
                            onInputChange = { onInputChange(it.take(maxInputCharacters)) },
                            enabled = enabled,
                            strings = strings,
                            onSend = ::sendAndKeepFocus,
                            modifier =
                                Modifier.weight(1f)
                                    .focusRequester(focusRequester)
                                    .padding(vertical = 14.dp),
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
                }
            }
        }
    }
}

@Composable
private fun ChatComposerTextInput(
    input: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean,
    strings: LocaleStrings,
    onSend: () -> Unit,
    modifier: Modifier,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
    val decoration: @Composable (@Composable () -> Unit) -> Unit = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
            if (input.isEmpty()) {
                Text(
                    if (enabled) strings.typeMessage else strings.addIp,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                )
            }
            innerTextField()
        }
    }
    if (isDesktopAudioPlatform()) {
        var editingValue by remember { mutableStateOf(TextFieldValue(input)) }
        val value = editingValue.copy(text = input)
        val keyHandler = remember { ChatComposerKeyHandler() }
        SideEffect {
            if (value.selection != editingValue.selection || value.composition != editingValue.composition) {
                editingValue = value
            }
        }
        BasicTextField(
            value = value,
            onValueChange = {
                editingValue = it
                if (it.text != input) onInputChange(it.text)
            },
            modifier = modifier
                .onFocusChanged { if (!it.isFocused) keyHandler.reset() }
                .onPreviewKeyEvent {
                    keyHandler.onKeyEvent(
                        it, desktop = true, canSend = enabled && input.isNotBlank(),
                        composing = value.composition != null,
                        onNewline = {
                            val updated = insertChatComposerNewline(value)
                            editingValue = updated
                            onInputChange(updated.text)
                        },
                        onSend = onSend,
                    )
                },
            textStyle = textStyle,
            cursorBrush = cursorBrush,
            maxLines = 5,
            decorationBox = decoration,
        )
    } else {
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = modifier,
            textStyle = textStyle,
            cursorBrush = cursorBrush,
            maxLines = 5,
            decorationBox = decoration,
        )
    }
}
