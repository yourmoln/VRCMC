package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChatPage(state: AppState, strings: LocaleStrings) {
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val active = state.activeDevice()
    val now = currentTimeMillis()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val clipboard = LocalClipboardManager.current

    fun sendMessage(rawText: String, clearDraft: Boolean) {
        val original = rawText.trim()
        val target = state.activeDevice() ?: return
        if (!isValidChatboxText(original)) {
            error = strings.messageTooLong
            return
        }
        val shouldTranslate = state.translate && !isArabicDigitsOnly(original)
        val targetLanguages = state.languages.toList()
        val outputOrder = state.outputOrder.toList()
        val translatingText = "$original\n(Translating...)"
        if (shouldTranslate && !isValidChatboxText(translatingText)) {
            error = strings.messageTooLong
            return
        }
        sending = true
        error = null
        if (clearDraft) state.chatDraft = ""
        state.addMessage(ChatMessage(original, MessageRole.USER))
        val loadingIndex = if (shouldTranslate) {
            state.addMessage(ChatMessage("", MessageRole.ASSISTANT, isLoading = true))
        } else null
        scope.launch {
            if (shouldTranslate && !sendChatboxOsc(target.address, translatingText, target.port)) {
                error = strings.sendFailed
            }
            val translations = if (shouldTranslate) coroutineScope {
                targetLanguages.map { language ->
                    async { language to translateText(state.provider, state.providerConfig, language, original) }
                }.awaitAll()
            } else emptyList()
            val failure = translations.firstNotNullOfOrNull { (_, result) -> result as? TranslationResult.Failure }
            if (failure != null) {
                loadingIndex?.let(state::removeMessageAt)
                error = failure.message
                sending = false
                return@launch
            }
            val successful = translations.mapNotNull { (language, result) ->
                (result as? TranslationResult.Success)?.text?.takeIf { it != original }?.let { language to it }
            }.toMap()
            val translatedText = buildTranslationOutput("", successful, outputOrder)
            loadingIndex?.let { index ->
                if (index in state.messages.indices) {
                    if (translatedText.isBlank()) state.removeMessageAt(index)
                    else state.replaceMessage(index, ChatMessage(translatedText, MessageRole.ASSISTANT))
                }
            }
            val outgoing = buildTranslationOutput(original, successful, outputOrder)
            if (!isValidChatboxText(outgoing)) error = strings.messageTooLong
            else if (!sendChatboxOsc(target.address, outgoing, target.port)) error = strings.sendFailed
            sending = false
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    null,
                                    Modifier.padding(14.dp).size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                active?.let { "${it.address}:${it.port}" } ?: strings.addIp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.messages) { index, message ->
                    Column(Modifier.fillMaxWidth()) {
                        if (shouldShowChatTimestamp(message.timestamp, state.messages.getOrNull(index - 1)?.timestamp)) {
                            Text(
                                formatChatTime(
                                    timestamp = message.timestamp,
                                    now = now,
                                    yesterdayLabel = strings.yesterday,
                                    dayBeforeYesterdayLabel = strings.dayBeforeYesterday,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                            )
                        }
                        MessageBubble(
                            message = message,
                            strings = strings,
                            resendEnabled = active != null && !sending,
                            onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                            onResend = { sendMessage(message.text, clearDraft = false) },
                        )
                    }
                }
            }
        }

        error?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        ChatComposer(
            input = state.chatDraft,
            sending = sending,
            enabled = active != null,
            strings = strings,
            onInputChange = { state.chatDraft = it; error = null },
            onSend = { sendMessage(state.chatDraft, clearDraft = true) },
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    strings: LocaleStrings,
    resendEnabled: Boolean,
    onCopy: () -> Unit,
    onResend: () -> Unit,
) {
    val user = message.role == MessageRole.USER
    val bubbleColor = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (user) Spacer(Modifier.weight(1f))
            Box {
                Surface(
                    modifier = Modifier.widthIn(max = 320.dp).wrapContentWidth().combinedClickable(
                        enabled = !message.isLoading,
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ),
                    shape = RoundedCornerShape(8.dp),
                    color = bubbleColor,
                    contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        if (!user) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(strings.translationAssistant, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        if (message.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(9.dp))
                                Text(strings.translating, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text(message.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.copyMessage) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = { menuExpanded = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text(strings.resendMessage) },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        enabled = resendEnabled,
                        onClick = { menuExpanded = false; onResend() },
                    )
                }
            }
            if (!user) Spacer(Modifier.weight(1f))
        }
    }
}

internal fun shouldShowChatTimestamp(timestamp: Long, previousTimestamp: Long?): Boolean {
    if (previousTimestamp == null) return true
    val elapsed = timestamp - previousTimestamp
    return elapsed < 0 || elapsed >= 10 * 60 * 1_000L
}

@Composable
private fun ChatComposer(
    input: String,
    sending: Boolean,
    enabled: Boolean,
    strings: LocaleStrings,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (sending) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                    enabled = !sending,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (sending) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    decorationBox = { innerTextField ->
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
                    },
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    enabled = input.isNotBlank() && !sending && enabled,
                    onClick = onSend,
                    modifier = Modifier.size(44.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, strings.send) }
            }
        }
    }
}
