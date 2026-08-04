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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Composable
fun ChatPage(state: AppState, strings: LocaleStrings) {
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryLimit by remember { mutableIntStateOf(0) }
    var activeTranslationJob by remember { mutableStateOf<Job?>(null) }
    var activeLoadingMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var translationGeneration by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val active = state.activeDevice()
    val now = currentTimeMillis()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val clipboard = LocalClipboardManager.current

    fun removeLoadingMessages(messages: List<ChatMessage>) {
        messages.forEach { message ->
            val index = state.messages.indexOfFirst { it === message }
            if (index >= 0) state.removeMessageAt(index)
        }
    }

    fun cancelActiveTranslation() {
        if (activeTranslationJob == null) return
        translationGeneration++
        activeTranslationJob?.cancel()
        activeTranslationJob = null
        removeLoadingMessages(activeLoadingMessages)
        activeLoadingMessages = emptyList()
        retryAttempt = 0
        retryLimit = 0
        sending = false
    }

    fun sendMessage(rawText: String, clearDraft: Boolean) {
        val original = rawText.trim()
        val target = state.activeDevice() ?: return
        cancelActiveTranslation()
        if (!isValidChatboxText(original)) {
            error = strings.messageTooLong
            return
        }
        val shouldTranslate = state.translate && !isArabicDigitsOnly(original)
        val targetLanguages = state.languages.toList()
        val outputOrder = state.outputOrder.toList()
        val displayLanguages = outputOrder.filter { it in targetLanguages }
        val translatingText = "$original\n(Translating...)"
        if (shouldTranslate && !isValidChatboxText(translatingText)) {
            error = strings.messageTooLong
            return
        }
        sending = true
        retryAttempt = 0
        retryLimit = state.providerConfig.retryCount.coerceIn(0, 10)
        error = null
        if (clearDraft) state.chatDraft = ""
        state.addMessage(ChatMessage(original, MessageRole.USER))
        val loadingMessages = if (shouldTranslate) {
            displayLanguages.map { language ->
                ChatMessage("", MessageRole.ASSISTANT, isLoading = true, language = language).also(state::addMessage)
            }.also { activeLoadingMessages = it }
        } else emptyList()
        val provider = state.provider
        val providerConfig = state.providerConfig
        val requestGeneration = ++translationGeneration
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                if (shouldTranslate && !sendChatboxOsc(target.address, translatingText, target.receivePort)) {
                    error = strings.sendFailed
                }
                val translations = if (shouldTranslate) coroutineScope {
                    targetLanguages.map { language ->
                        async {
                            language to translateText(provider, providerConfig, language, original) { attempt ->
                                retryAttempt = maxOf(retryAttempt, attempt)
                            }
                        }
                    }.awaitAll()
                } else emptyList()
                val failure = translations.firstNotNullOfOrNull { (_, result) -> result as? TranslationResult.Failure }
                if (failure != null) {
                    removeLoadingMessages(loadingMessages)
                    error = failure.message
                    return@launch
                }
                val successful = translations.mapNotNull { (language, result) ->
                    (result as? TranslationResult.Success)?.text?.takeIf { it != original }?.let { language to it }
                }.toMap()
                loadingMessages.forEach { loadingMessage ->
                    val loadingIndex = state.messages.indexOfFirst { it === loadingMessage }
                    if (loadingIndex >= 0) {
                        val language = loadingMessage.language ?: return@forEach
                        val translatedText = successful[language]
                        if (translatedText.isNullOrBlank()) state.removeMessageAt(loadingIndex)
                        else state.replaceMessage(
                            loadingIndex,
                            ChatMessage(
                                translatedText,
                                MessageRole.ASSISTANT,
                                timestamp = loadingMessage.timestamp,
                                language = language,
                            ),
                        )
                    }
                }
                val outgoing = buildTranslationOutput(original, successful, outputOrder)
                if (!isValidChatboxText(outgoing)) error = strings.messageTooLong
                else if (!sendChatboxOsc(target.address, outgoing, target.receivePort)) error = strings.sendFailed
            } finally {
                if (translationGeneration == requestGeneration) {
                    activeTranslationJob = null
                    activeLoadingMessages = emptyList()
                    retryAttempt = 0
                    retryLimit = 0
                    sending = false
                }
            }
        }
        if (shouldTranslate) activeTranslationJob = job
        job.start()
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
                                active?.displayEndpoint() ?: strings.addIp,
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
                            retryAttempt = retryAttempt,
                            retryLimit = retryLimit,
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
            onInputChange = {
                state.chatDraft = it
                error = null
            },
            onSend = { sendMessage(state.chatDraft, clearDraft = true) },
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    strings: LocaleStrings,
    retryAttempt: Int,
    retryLimit: Int,
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
                        if (!user && message.language != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(message.language, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        if (message.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    if (retryAttempt > 0) strings.translatingRetry(retryAttempt, retryLimit) else strings.translating,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    fun sendAndKeepFocus() {
        onSend()
        scope.launch {
            yield()
            focusRequester.requestFocus()
        }
    }

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
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).padding(vertical = 14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
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
                    enabled = input.isNotBlank() && enabled,
                    onClick = ::sendAndKeepFocus,
                    modifier = Modifier.size(44.dp),
                ) { Icon(Icons.AutoMirrored.Filled.Send, strings.send) }
            }
        }
    }
}
