package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface LiveOscAction

private data class LiveOriginalUpdate(val device: Device, val text: String) : LiveOscAction

private data class LiveOscBarrier(val completed: CompletableDeferred<Unit>) : LiveOscAction

@Composable
fun ChatPage(state: AppState, strings: LocaleStrings) {
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var retryAttempt by remember { mutableIntStateOf(0) }
    var retryLimit by remember { mutableIntStateOf(0) }
    var activeTranslationJob by remember { mutableStateOf<Job?>(null) }
    var activeLoadingMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var translationGeneration by remember { mutableIntStateOf(0) }
    var voiceRecording by remember { mutableStateOf(false) }
    var voiceSpeaking by remember { mutableStateOf(false) }
    var voiceTranscribing by remember { mutableStateOf(false) }
    var voiceGeneration by remember { mutableIntStateOf(0) }
    var voiceBaseDraft by remember { mutableStateOf("") }
    var voiceRequestConfig by remember { mutableStateOf<VoiceInputConfig?>(null) }
    var activeVoiceRequestJob by remember { mutableStateOf<Job?>(null) }
    var pendingPartialAudio by remember { mutableStateOf<ByteArray?>(null) }
    var managedVoiceCapture by remember { mutableStateOf(false) }
    var pendingSimultaneousVoiceSend by remember { mutableStateOf(false) }
    var pendingManagedSendText by remember { mutableStateOf<String?>(null) }
    var managedVoiceRestartToken by remember { mutableIntStateOf(0) }
    val streamingMerger = remember { StreamingTextMerger() }
    val audioRecorder = remember { createAudioRecorder() }
    val scope = rememberCoroutineScope()
    val messages = state.messages.toList()
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0))
    val active = state.activeDevice()
    val maxInputCharacters =
        if (state.disableDynamicInputLimit) maxChatboxCharacters
        else chatboxInputCharacterLimit(state.translate, state.languages.size)
    val now = currentTimeMillis()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val clipboard = LocalClipboard.current
    val liveOriginalUpdates = remember { Channel<LiveOscAction>(Channel.UNLIMITED) }
    val timestampVisibility = chatTimestampVisibility(messages.map(ChatMessage::timestamp))
    LaunchedEffect(maxInputCharacters) {
        if (state.chatDraft.length > maxInputCharacters) {
            state.chatDraft = state.chatDraft.take(maxInputCharacters)
        }
    }
    KeepScreenAwake(
        state.interpretationKeepScreenOn &&
            (state.isSimultaneousInterpretationActive || state.isAlwaysInterpretationActive ||
                voiceRecording || voiceTranscribing)
    )

    DisposableEffect(audioRecorder) {
        onDispose {
            activeVoiceRequestJob?.cancel()
            audioRecorder.release()
        }
    }
    LaunchedEffect(state.voiceInputConfig.enabled) {
        if (!state.voiceInputConfig.enabled && voiceRecording) {
            audioRecorder.stop()
            voiceRecording = false
        }
    }

    fun applyVoiceText(text: String) {
        state.chatDraft =
            listOf(voiceBaseDraft, text).filter(String::isNotBlank).joinToString(" ")
                .take(maxInputCharacters)
    }

    fun submitPartialRecognition(wav: ByteArray) {
        pendingPartialAudio = wav
        if (activeVoiceRequestJob?.isActive == true) return
        val audio = pendingPartialAudio ?: return
        val config = voiceRequestConfig ?: return
        val generation = voiceGeneration
        pendingPartialAudio = null
        activeVoiceRequestJob = scope.launch {
            when (val result = transcribeQwenAudio(config, audio, state::addErrorLog)) {
                is VoiceTranscriptionResult.Success ->
                    if (generation == voiceGeneration) {
                        if (!managedVoiceCapture) {
                            applyVoiceText(streamingMerger.ingestPartial(result.text))
                        }
                        error = null
                    }
                is VoiceTranscriptionResult.Failure -> Unit
            }
            activeVoiceRequestJob = null
            pendingPartialAudio?.let(::submitPartialRecognition)
        }
    }

    fun submitFinalRecognition(wav: ByteArray) {
        val config = voiceRequestConfig ?: return
        val generation = voiceGeneration
        activeVoiceRequestJob?.cancel()
        pendingPartialAudio = null
        voiceTranscribing = true
        activeVoiceRequestJob = scope.launch {
            when (val result = transcribeQwenAudio(config, wav, state::addErrorLog)) {
                is VoiceTranscriptionResult.Success ->
                    if (generation == voiceGeneration) {
                        val finalText = streamingMerger.ingestFinal(result.text)
                        applyVoiceText(finalText)
                        error = null
                        if (pendingSimultaneousVoiceSend) {
                            pendingSimultaneousVoiceSend = false
                            pendingManagedSendText = finalText
                        } else if (managedVoiceCapture && state.isAlwaysInterpretationActive) {
                            pendingManagedSendText = finalText
                        }
                    }
                is VoiceTranscriptionResult.Failure ->
                    if (generation == voiceGeneration) {
                        error = result.message
                        if (
                            managedVoiceCapture &&
                                (state.isAlwaysInterpretationActive ||
                                    state.isSimultaneousInterpretationActive)
                        ) {
                            managedVoiceRestartToken++
                        }
                    }
            }
            if (generation == voiceGeneration) voiceTranscribing = false
            activeVoiceRequestJob = null
        }
    }

    fun startVoiceInput(stopOnSilence: Boolean = true, managed: Boolean = false) {
        val config = state.voiceInputConfig
        if (config.apiKey.isBlank()) {
            error = strings.apiNotConfiguredVoiceInput
            return
        }
        val generation = ++voiceGeneration
        activeVoiceRequestJob?.cancel()
        pendingPartialAudio = null
        voiceBaseDraft = state.chatDraft.trimEnd()
        voiceRequestConfig = config
        managedVoiceCapture = managed
        streamingMerger.reset()
        error = null
        voiceRecording = true
        voiceSpeaking = false
        voiceTranscribing = false
        lateinit var processor: VoiceCaptureProcessor
        processor =
            VoiceCaptureProcessor(
                config = config,
                onSpeechState = { speaking ->
                    scope.launch {
                        if (generation == voiceGeneration) voiceSpeaking = speaking
                    }
                },
                onPartial = { wav ->
                    scope.launch {
                        if (generation == voiceGeneration) submitPartialRecognition(wav)
                    }
                },
                onFinal = { wav ->
                    scope.launch {
                        if (generation == voiceGeneration) {
                            voiceRecording = false
                            voiceSpeaking = false
                            submitFinalRecognition(wav)
                        }
                    }
                },
                onNoSpeech = {
                    scope.launch {
                        if (generation == voiceGeneration) {
                            voiceRecording = false
                            voiceSpeaking = false
                            voiceTranscribing = false
                            if (
                                managedVoiceCapture &&
                                    (state.isAlwaysInterpretationActive ||
                                        state.isSimultaneousInterpretationActive)
                            ) {
                                error = null
                                managedVoiceRestartToken++
                            }
                            error = "未检测到有效语音"
                        }
                    }
                },
                onAutoStop = audioRecorder::stop,
                stopOnSilence = stopOnSilence,
            )
        audioRecorder.start(
            sampleRate = config.sampleRate,
            maxDurationSeconds = (config.maxSegmentSeconds + 30).coerceAtMost(60),
            microphoneId = config.microphoneId,
            onPcmData = processor::accept,
            onStopped = {
                processor.finish()
                scope.launch {
                    if (generation == voiceGeneration) voiceRecording = false
                }
            },
            onError = { message ->
                scope.launch {
                    voiceRecording = false
                    voiceTranscribing = false
                    error = message
                }
            },
        )
    }

    fun toggleVoiceInput() {
        if (voiceRecording) {
            audioRecorder.stop()
            return
        }
        if (requestAudioPermissionIfNeeded(::startVoiceInput)) startVoiceInput()
    }

    fun stopManagedAlwaysCapture() {
        if (!managedVoiceCapture) return
        voiceGeneration++
        activeVoiceRequestJob?.cancel()
        activeVoiceRequestJob = null
        pendingPartialAudio = null
        pendingManagedSendText = null
        audioRecorder.stop()
        voiceRecording = false
        voiceSpeaking = false
        voiceTranscribing = false
        managedVoiceCapture = false
    }

    LaunchedEffect(managedVoiceRestartToken) {
        if (managedVoiceRestartToken == 0) return@LaunchedEffect
        while (voiceRecording || voiceTranscribing) delay(50)
        delay(120)
        if (
            (state.isAlwaysInterpretationActive || state.isSimultaneousInterpretationActive) &&
                state.interpretationVoiceInputEnabled &&
                managedVoiceCapture &&
                !voiceRecording &&
                !voiceTranscribing
        ) {
            startVoiceInput(
                stopOnSilence = !state.isSimultaneousInterpretationActive,
                managed = true,
            )
        }
    }

    LaunchedEffect(state.isSimultaneousInterpretationActive, state.interpretationVoiceInputEnabled) {
        if (state.isSimultaneousInterpretationActive && state.interpretationVoiceInputEnabled) {
            if (!voiceRecording && !voiceTranscribing) {
                if (requestAudioPermissionIfNeeded { startVoiceInput(stopOnSilence = false, managed = true) }) {
                    startVoiceInput(stopOnSilence = false, managed = true)
                }
            }
        } else if (managedVoiceCapture && voiceRecording && !state.isAlwaysInterpretationActive) {
            audioRecorder.stop()
        }
    }

    LaunchedEffect(state.isAlwaysInterpretationActive, state.interpretationVoiceInputEnabled) {
        if (
            state.isAlwaysInterpretationActive &&
                state.interpretationVoiceInputEnabled &&
                !voiceRecording &&
                !voiceTranscribing
        ) {
            if (requestAudioPermissionIfNeeded { startVoiceInput(stopOnSilence = true, managed = true) }) {
                startVoiceInput(stopOnSilence = true, managed = true)
            }
        } else if (managedVoiceCapture && voiceRecording && !state.isSimultaneousInterpretationActive) {
            audioRecorder.stop()
        }
    }

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
        if (!isValidChatboxText(original, maxInputCharacters)) {
            error = strings.messageTooLong
            return
        }
        val shouldTranslate = state.translate && !shouldSkipTranslation(original)
        if (shouldTranslate && !state.isTranslationApiConfigured) {
            error = strings.apiNotConfiguredTranslation
            return
        }
        val targetLanguages = state.languages.toList()
        val outputOrder = state.outputOrder.toList()
        val lineBreakOutput = state.lineBreakOutput
        val sendOriginalBeforeTranslation = state.sendOriginalBeforeTranslation
        val displayLanguages = outputOrder.filter { it in targetLanguages }
        val translatingText = "$original\n(Translating...)"
        if (
            shouldTranslate && sendOriginalBeforeTranslation && !isValidChatboxText(translatingText)
        ) {
            error = strings.messageTooLong
            return
        }
        sending = true
        retryAttempt = 0
        retryLimit = state.providerConfig.totalRetryCount()
        error = null
        if (clearDraft) state.chatDraft = ""
        state.addMessage(ChatMessage(original, MessageRole.USER))
        val loadingMessages =
            if (shouldTranslate) {
                displayLanguages
                    .map { language ->
                        ChatMessage(
                                "",
                                MessageRole.ASSISTANT,
                                isLoading = true,
                                language = language,
                            )
                            .also(state::addMessage)
                    }
                    .also { activeLoadingMessages = it }
            } else emptyList()
        val provider = state.provider
        val providerConfig = state.providerConfig
        val showTypingStatus = state.showTypingStatus
        val requestGeneration = ++translationGeneration
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    if (showTypingStatus) {
                        sendChatboxTypingOsc(target.address, false, target.receivePort)
                    }
                    if (
                        shouldTranslate &&
                            sendOriginalBeforeTranslation &&
                            !sendChatboxOsc(target.address, translatingText, target.receivePort)
                    ) {
                        error = strings.sendFailed
                    }
                    val translations =
                        if (shouldTranslate)
                            coroutineScope {
                                targetLanguages
                                    .map { language ->
                                        async {
                                            language to
                                                translateText(
                                                    provider = provider,
                                                    config = providerConfig,
                                                    targetLanguage = language,
                                                    text = original,
                                                    onRetry = { attempt ->
                                                        retryAttempt = maxOf(retryAttempt, attempt)
                                                    },
                                                    onApiFailure = state::addErrorLog,
                                                )
                                        }
                                    }
                                    .awaitAll()
                            }
                        else emptyList()
                    val failure =
                        translations.firstNotNullOfOrNull { (_, result) ->
                            result as? TranslationResult.Failure
                        }
                    if (failure != null) {
                        removeLoadingMessages(loadingMessages)
                        error = failure.message
                        return@launch
                    }
                    val successful =
                        translations
                            .mapNotNull { (language, result) ->
                                (result as? TranslationResult.Success)
                                    ?.text
                                    ?.let { language to it }
                            }
                            .toMap()
                    loadingMessages.forEach { loadingMessage ->
                        val loadingIndex = state.messages.indexOfFirst { it === loadingMessage }
                        if (loadingIndex >= 0) {
                            val language = loadingMessage.language ?: return@forEach
                            val translatedText = successful[language]
                            if (translatedText.isNullOrBlank()) state.removeMessageAt(loadingIndex)
                            else
                                state.replaceMessage(
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
                    val outgoing =
                        buildTranslationOutput(
                            original,
                            successful,
                            outputOrder,
                            lineBreakOutput,
                        )
                    if (!isValidChatboxText(outgoing)) error = strings.messageTooLong
                    else if (!sendChatboxOsc(target.address, outgoing, target.receivePort)) {
                        error = strings.sendFailed
                    }
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

    LaunchedEffect(pendingManagedSendText) {
        val text = pendingManagedSendText ?: return@LaunchedEffect
        if (text.isNotBlank()) sendMessage(text, clearDraft = true)
        if (state.isAlwaysInterpretationActive && managedVoiceCapture) {
            while (voiceRecording || voiceTranscribing) delay(50)
            delay(120)
            if (state.isAlwaysInterpretationActive && managedVoiceCapture && !voiceRecording) {
                startVoiceInput(stopOnSilence = true, managed = true)
            }
        }
        pendingManagedSendText = null
    }

    LaunchedEffect(liveOriginalUpdates) {
        for (action in liveOriginalUpdates) {
            when (action) {
                is LiveOriginalUpdate ->
                    if (
                        !sendChatboxOsc(
                            action.device.address,
                            action.text,
                            action.device.receivePort,
                        )
                    ) {
                        error = strings.sendFailed
                    }
                is LiveOscBarrier -> action.completed.complete(Unit)
            }
        }
    }

    DisposableEffect(liveOriginalUpdates) { onDispose { liveOriginalUpdates.close() } }

    LaunchedEffect(state.simultaneousFinalPending) {
        if (state.simultaneousFinalPending) {
            state.consumeSimultaneousFinalRequest()
            cancelActiveTranslation()
            if (managedVoiceCapture) {
                pendingSimultaneousVoiceSend = true
                audioRecorder.stop()
                return@LaunchedEffect
            }
            val finalText = state.chatDraft
            val barrier = CompletableDeferred<Unit>()
            liveOriginalUpdates.send(LiveOscBarrier(barrier))
            barrier.await()
            if (finalText.isNotBlank()) sendMessage(finalText, clearDraft = true)
        }
    }

    LaunchedEffect(
        state.isAlwaysInterpretationActive,
        state.chatDraft,
        state.alwaysInterpretationDelayMillis,
    ) {
        val pendingText = state.chatDraft
        if (
            state.isAlwaysInterpretationActive &&
                !managedVoiceCapture &&
                pendingText.isNotBlank()
        ) {
            delay(state.alwaysInterpretationDelayMillis.toLong())
            if (
                state.isAlwaysInterpretationActive &&
                    !managedVoiceCapture &&
                    state.chatDraft == pendingText
            ) {
                sendMessage(pendingText, clearDraft = true)
            }
        }
    }

    LaunchedEffect(messages.lastIndex) {
        if (messages.isNotEmpty()) listState.requestScrollToItem(messages.lastIndex)
    }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            listState.requestScrollToItem(messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (messages.isEmpty()) {
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
                itemsIndexed(messages) { index, message ->
                    Column(Modifier.fillMaxWidth()) {
                        if (timestampVisibility[index]) {
                            Text(
                                formatChatTime(
                                    timestamp = message.timestamp,
                                    now = now,
                                    yesterdayLabel = strings.yesterday,
                                    dayBeforeYesterdayLabel = strings.dayBeforeYesterday,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
                                modifier =
                                    Modifier.align(Alignment.CenterHorizontally)
                                        .padding(bottom = 8.dp),
                            )
                        }
                        MessageBubble(
                            message = message,
                            strings = strings,
                            retryAttempt = retryAttempt,
                            retryLimit = retryLimit,
                            resendEnabled = active != null && !sending,
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(textClipEntry(message.text))
                                }
                            },
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
            interpreting =
                state.isSimultaneousInterpretationActive || state.isAlwaysInterpretationActive,
            alwaysInterpretationEnabled = state.alwaysInterpretationEnabled,
            alwaysInterpretationActive = state.isAlwaysInterpretationActive,
            voiceInputEnabled = state.voiceInputConfig.enabled,
            voiceRecording = voiceRecording,
            voiceSpeaking = voiceSpeaking,
            voiceTranscribing = voiceTranscribing,
            maxInputCharacters = maxInputCharacters,
            strings = strings,
            onInputChange = {
                state.chatDraft = it.take(maxInputCharacters)
                error = null
                if (state.showTypingStatus && active != null) {
                    scope.launch { sendChatboxTypingOsc(active.address, true, active.receivePort) }
                }
                val original = it.trim()
                if (
                    state.isSimultaneousInterpretationActive &&
                        active != null &&
                        isValidChatboxText(original, maxInputCharacters)
                ) {
                    liveOriginalUpdates.trySend(LiveOriginalUpdate(active, original))
                }
            },
            onSend = {
                val wasInterpreting = state.isSimultaneousInterpretationActive
                cancelActiveTranslation()
                state.finishSimultaneousInterpretation()
                val finalText = state.chatDraft
                if (wasInterpreting)
                    scope.launch {
                        val barrier = CompletableDeferred<Unit>()
                        liveOriginalUpdates.send(LiveOscBarrier(barrier))
                        barrier.await()
                        sendMessage(finalText, clearDraft = true)
                    }
                else {
                    sendMessage(finalText, clearDraft = true)
                }
            },
            onToggleAlwaysInterpretation = {
                val stopping = state.isAlwaysInterpretationActive
                state.toggleAlwaysInterpretationActive()
                if (stopping) stopManagedAlwaysCapture()
            },
            onToggleVoiceInput = ::toggleVoiceInput,
        )
    }
}
