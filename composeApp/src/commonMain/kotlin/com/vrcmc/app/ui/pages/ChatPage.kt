package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
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

private data class LiveOriginalUpdate(
    val device: Device,
    val text: String,
    val requiresTranslationIdle: Boolean = false,
) : LiveOscAction

private data class LiveOscBarrier(val completed: CompletableDeferred<Unit>) : LiveOscAction

private data class TypingOscUpdate(val device: Device, val typing: Boolean)

private const val alwaysInterpretationMergeWindowMillis = 2_000L

private data class AlwaysInterpretationBatch(
    val userMessage: ChatMessage,
    val sentAtMillis: Long,
    val deviceAddress: String,
)

private fun mergeAlwaysInterpretationMessages(previous: String, next: String): String =
    listOf(previous.trim(), next.trim()).filter(String::isNotBlank).joinToString(" ")

private data class ManagedVoiceChunk(
    val generation: Int,
    val config: VoiceInputConfig,
    val chunk: VoiceAudioChunk,
)

@Composable
fun ChatPage(
    state: AppState,
    strings: LocaleStrings,
    onReadAloud: (String, ReadAloudConfig) -> Unit = { _, _ -> },
    visible: Boolean = true,
    animationsEnabled: Boolean = true,
) {
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
    var livePreviewReady by remember { mutableStateOf(false) }
    var pendingSimultaneousVoiceSend by remember { mutableStateOf(false) }
    var pendingManagedSendText by remember { mutableStateOf<String?>(null) }
    var managedVoiceRestartToken by remember { mutableIntStateOf(0) }
    var lastAlwaysInterpretationBatch by remember {
        mutableStateOf<AlwaysInterpretationBatch?>(null)
    }
    var lastVoiceTypingStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val streamingMerger = remember { StreamingTextMerger() }
    val audioRecorder = remember { createAudioRecorder() }
    val localSpeechStatusFlow = remember(state.voiceInputConfig.localWhisperModel) {
        localSpeechRecognizer.status(state.voiceInputConfig.localWhisperModel)
    }
    val localSpeechStatus by localSpeechStatusFlow.collectAsState()
    val voiceServiceReady = state.voiceInputConfig.enabled && state.voiceInputConfig.hasServiceConfiguration() &&
        (state.voiceInputConfig.provider != VoiceInputProvider.LOCAL_WHISPER || localSpeechStatus == LocalSpeechModelStatus.Ready)
    val scope = rememberCoroutineScope()
    val blockedSnackbar = remember { SnackbarHostState() }
    var blockedNotificationJob by remember { mutableStateOf<Job?>(null) }
    val messages = state.messages.toList()
    val active = state.activeDevice()
    val maxInputCharacters =
        if (state.disableDynamicInputLimit) maxChatboxCharacters
        else chatboxInputCharacterLimit(state.translate, state.languages.size)
    val now = currentTimeMillis()
    val clipboard = LocalClipboard.current
    val liveOriginalUpdates = remember { Channel<LiveOscAction>(Channel.UNLIMITED) }
    val typingUpdates = remember { Channel<TypingOscUpdate>(Channel.CONFLATED) }
    val managedVoiceChunks = remember { Channel<ManagedVoiceChunk>(Channel.UNLIMITED) }
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
            managedVoiceChunks.close()
            audioRecorder.release()
        }
    }
    LaunchedEffect(state.voiceInputConfig.enabled, state.voiceInputConfig.provider) {
        if (!state.voiceInputConfig.enabled ||
            voiceRequestConfig?.let { it.provider != state.voiceInputConfig.provider } == true) {
            voiceGeneration++
            activeVoiceRequestJob?.cancel()
            pendingPartialAudio = null
            audioRecorder.stop()
            voiceRecording = false
            voiceTranscribing = false
            voiceSpeaking = false
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
            when (val result = transcribeVoiceAudio(config, audio, state::addErrorLog)) {
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
            when (val result = transcribeVoiceAudio(config, wav, state::addErrorLog)) {
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
                        error = strings.voiceTranscriptionFailureMessage(result)
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
        val continuous = managed && state.isAlwaysInterpretationActive
        val readinessFailure = voiceInputReadinessFailure(config)
        if (readinessFailure != null) {
            error = strings.voiceTranscriptionFailureMessage(readinessFailure)
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
                        if (generation == voiceGeneration && config.provider != VoiceInputProvider.LOCAL_WHISPER) {
                            submitPartialRecognition(wav)
                        }
                    }
                },
                onFinal = { wav ->
                    if (!continuous) scope.launch {
                        if (generation == voiceGeneration) {
                            voiceRecording = false
                            voiceSpeaking = false
                            submitFinalRecognition(wav)
                        }
                    }
                },
                onNoSpeech = {
                    if (!continuous) scope.launch {
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
                onAutoStop = { if (!continuous) audioRecorder.stop() },
                stopOnSilence = stopOnSilence,
                continuous = continuous,
                // Fun-ASR is an asynchronous file-transcription model; it has no partial
                // response API, so submit one request only after the utterance is complete.
                emitPartials = false,
                onChunk = if (continuous) {
                    { chunk -> managedVoiceChunks.trySend(ManagedVoiceChunk(generation, config, chunk)) }
                } else null,
            )
        audioRecorder.start(
            sampleRate = config.sampleRate,
            maxDurationSeconds = if (continuous) 0 else (config.maxSegmentSeconds + 30).coerceAtMost(60),
            microphoneId = config.microphoneId,
            onPcmData = processor::accept,
            onStopped = {
                processor.finish()
                scope.launch {
                    if (generation == voiceGeneration) {
                        voiceRecording = false
                        if (continuous && managedVoiceCapture && state.isAlwaysInterpretationActive) {
                            managedVoiceRestartToken++
                        }
                    }
                }
            },
            onError = { message ->
                scope.launch {
                    voiceRecording = false
                    voiceTranscribing = false
                    error = message
                    if (generation == voiceGeneration && continuous && managedVoiceCapture) {
                        managedVoiceRestartToken++
                    }
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
        while (managedVoiceChunks.tryReceive().isSuccess) { }
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

    LaunchedEffect(state.isSimultaneousInterpretationActive, state.interpretationVoiceInputEnabled, voiceServiceReady, state.voiceInputConfig.provider) {
        if (state.isSimultaneousInterpretationActive && state.interpretationVoiceInputEnabled && voiceServiceReady) {
            if (!voiceRecording && !voiceTranscribing) {
                if (requestAudioPermissionIfNeeded { startVoiceInput(stopOnSilence = false, managed = true) }) {
                    startVoiceInput(stopOnSilence = false, managed = true)
                }
            }
        } else if (managedVoiceCapture && voiceRecording && !state.isAlwaysInterpretationActive) {
            audioRecorder.stop()
        }
    }

    LaunchedEffect(state.isAlwaysInterpretationActive, state.interpretationVoiceInputEnabled, voiceServiceReady, state.voiceInputConfig.provider) {
        if (
            state.isAlwaysInterpretationActive &&
                state.interpretationVoiceInputEnabled &&
                voiceServiceReady &&
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

    LaunchedEffect(state.isAlwaysInterpretationActive) {
        if (!state.isAlwaysInterpretationActive) lastAlwaysInterpretationBatch = null
    }

    LaunchedEffect(state.showTypingStatus, voiceRecording, voiceSpeaking, active) {
        val target = active ?: return@LaunchedEffect
        val typing = state.showTypingStatus && voiceRecording && voiceSpeaking
        val status = target.address to typing
        if (lastVoiceTypingStatus == status) return@LaunchedEffect
        val hadPreviousStatus = lastVoiceTypingStatus != null
        lastVoiceTypingStatus = status
        if (hadPreviousStatus) typingUpdates.trySend(TypingOscUpdate(target, typing))
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

    suspend fun sendOutput(target: Device, text: String, live: Boolean = false): Boolean {
        if (!live && text.isNotBlank()) state.recordNonLiveChatboxSend()
        val result = sendChatboxOutput(target, text)
        when (result) {
            ChatboxSendResult.SENT_OVER_LIMIT -> error = strings.messageTooLong
            ChatboxSendResult.FAILED -> error = strings.sendFailed
            ChatboxSendResult.SENT, ChatboxSendResult.EMPTY ->
                if (error == strings.messageTooLong) error = null
        }
        return result == ChatboxSendResult.SENT || result == ChatboxSendResult.SENT_OVER_LIMIT
    }

    fun sendMessage(
        rawText: String,
        clearDraft: Boolean,
        automaticAlwaysInterpretation: Boolean = false,
    ): Job? {
        val incomingOriginal = state.hotwordDictionary.process(rawText)
        if (incomingOriginal == null) {
            if (clearDraft) state.chatDraft = ""
            error = null
            if (blockedNotificationJob?.isActive != true) {
                blockedNotificationJob = scope.launch { blockedSnackbar.showSnackbar(strings.sentenceBlocked) }
            }
            return null
        }
        val target = state.activeDevice() ?: return null
        cancelActiveTranslation()
        if (incomingOriginal.isBlank()) {
            if (clearDraft) state.chatDraft = ""
            error = null
            return null
        }
        if (!automaticAlwaysInterpretation) lastAlwaysInterpretationBatch = null
        val sendTimestamp = currentTimeMillis()
        val previousBatch = lastAlwaysInterpretationBatch
        val previousIndex =
            previousBatch?.let { batch ->
                state.messages.indexOfFirst { it === batch.userMessage }
            } ?: -1
        val mergePrevious =
            automaticAlwaysInterpretation &&
                state.isAlwaysInterpretationActive &&
                state.alwaysInterpretationMergeMessages &&
                previousBatch != null &&
                previousBatch.deviceAddress == target.address &&
                sendTimestamp - previousBatch.sentAtMillis in 0 until alwaysInterpretationMergeWindowMillis &&
                previousIndex >= 0
        val original =
            if (mergePrevious) {
                mergeAlwaysInterpretationMessages(previousBatch!!.userMessage.text, incomingOriginal)
            } else incomingOriginal
        val shouldTranslate = state.translate && !shouldSkipTranslation(original)
        if (shouldTranslate && !state.isTranslationApiConfigured) {
            error = strings.apiNotConfiguredTranslation
            return null
        }
        val userMessage = ChatMessage(original, MessageRole.USER, timestamp = sendTimestamp)
        if (mergePrevious) {
            while (
                previousIndex + 1 < state.messages.size &&
                    state.messages[previousIndex + 1].role == MessageRole.ASSISTANT
            ) {
                state.removeMessageAt(previousIndex + 1)
            }
            state.replaceMessage(previousIndex, userMessage)
        } else {
            state.addMessage(userMessage)
        }
        if (automaticAlwaysInterpretation && state.isAlwaysInterpretationActive) {
            lastAlwaysInterpretationBatch =
                AlwaysInterpretationBatch(userMessage, sendTimestamp, target.address)
        }
        val targetLanguages = state.languages.toList()
        val outputOrder = state.outputOrder.toList()
        val lineBreakOutput = state.lineBreakOutput
        val showOriginalText = state.showOriginalText
        val sendOriginalBeforeTranslation = state.sendOriginalBeforeTranslation
        val readAloudConfig = state.readAloudConfig
        val displayLanguages = outputOrder.filter { it in targetLanguages }
        val translatingText = "$original\n(Translating...)"
        sending = true
        retryAttempt = 0
        retryLimit = state.providerConfig.totalRetryCount(state.provider)
        error = null
        if (clearDraft) state.chatDraft = ""
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
                    if (shouldTranslate && sendOriginalBeforeTranslation) {
                        sendOutput(target, translatingText)
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
                        error = strings.translationFailureMessage(failure)
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
                            showOriginalText,
                        )
                    if (sendOutput(target, outgoing) && readAloudConfig.enabled) {
                        onReadAloud(
                            readAloudText(readAloudConfig.source, original, successful, outputOrder, outgoing),
                            readAloudConfig,
                        )
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
        activeTranslationJob = job
        job.start()
        return job
    }

    LaunchedEffect(managedVoiceChunks) {
        var generation = -1
        var assembler = SentenceTextAssembler()
        for (managedChunk in managedVoiceChunks) {
            if (managedChunk.generation != generation) {
                generation = managedChunk.generation
                assembler = SentenceTextAssembler()
            }
            if (
                managedChunk.generation != voiceGeneration ||
                    !managedVoiceCapture ||
                    !state.isAlwaysInterpretationActive
            ) continue

            val chunk = managedChunk.chunk
            val completedTexts = mutableListOf<String>()
            val wav = chunk.wav
            if (wav != null) {
                voiceTranscribing = true
                when (val result = transcribeVoiceAudio(managedChunk.config, wav, state::addErrorLog)) {
                    is VoiceTranscriptionResult.Success ->
                        completedTexts += assembler.append(result.text, chunk.overlapSamples > 0)
                    is VoiceTranscriptionResult.Failure -> {
                        error = strings.voiceTranscriptionFailureMessage(result)
                        assembler = SentenceTextAssembler()
                    }
                }
            }
            if (chunk.isFinal) completedTexts += assembler.finish()

            for (text in completedTexts) {
                if (
                    text.isNotBlank() &&
                        managedChunk.generation == voiceGeneration &&
                        managedVoiceCapture &&
                        state.isAlwaysInterpretationActive
                ) {
                    sendMessage(
                        text,
                        clearDraft = true,
                        automaticAlwaysInterpretation = true,
                    )?.join()
                }
            }
            if (managedChunk.generation == voiceGeneration) voiceTranscribing = false
        }
    }

    LaunchedEffect(pendingManagedSendText) {
        val text = pendingManagedSendText ?: return@LaunchedEffect
        if (text.isNotBlank()) {
            sendMessage(
                text,
                clearDraft = true,
                automaticAlwaysInterpretation = true,
            )
        }
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
        var lastLiveUpdateMillis: Long? = null
        for (action in liveOriginalUpdates) {
            when (action) {
                is LiveOriginalUpdate -> {
                    val cooldown =
                        oscCooldownRemainingMillis(currentTimeMillis(), lastLiveUpdateMillis)
                    if (cooldown > 0) delay(cooldown)
                    var latest = action
                    var barrier: LiveOscBarrier? = null
                    while (true) {
                        when (val next = liveOriginalUpdates.tryReceive().getOrNull()) {
                            null -> break
                            is LiveOriginalUpdate -> latest = next
                            is LiveOscBarrier -> {
                                barrier = next
                                break
                            }
                        }
                    }
                    if (!latest.requiresTranslationIdle || !sending) {
                        val processed = state.hotwordDictionary.replaceKeywords(latest.text)
                        val sent = sendOutput(latest.device, processed, live = true)
                        if (sent) lastLiveUpdateMillis = currentTimeMillis()
                    }
                    barrier?.completed?.complete(Unit)
                }
                is LiveOscBarrier -> action.completed.complete(Unit)
            }
        }
    }

    LaunchedEffect(typingUpdates) {
        var lastTypingUpdateMillis: Long? = null
        for (update in typingUpdates) {
            val cooldown =
                oscCooldownRemainingMillis(currentTimeMillis(), lastTypingUpdateMillis)
            if (cooldown > 0) delay(cooldown)
            var latest = update
            while (true) {
                latest = typingUpdates.tryReceive().getOrNull() ?: break
            }
            if (sendChatboxTypingOsc(latest.device.address, latest.typing, latest.device.receivePort)) {
                lastTypingUpdateMillis = currentTimeMillis()
            } else {
                error = strings.sendFailed
            }
        }
    }

    DisposableEffect(liveOriginalUpdates, typingUpdates) {
        onDispose {
            liveOriginalUpdates.close()
            typingUpdates.close()
        }
    }

    LaunchedEffect(
        state.liveInputPreview,
        state.liveInputPreviewDelaySeconds,
        state.lastNonLiveChatboxSendMillis,
        state.isSimultaneousInterpretationActive,
        sending,
        active,
    ) {
        livePreviewReady = false
        val target = active ?: return@LaunchedEffect
        if (
            !state.liveInputPreview ||
                sending ||
                state.isSimultaneousInterpretationActive
        ) return@LaunchedEffect
        delay(
            liveInputPreviewDelayRemainingMillis(
                currentTimeMillis(),
                state.lastNonLiveChatboxSendMillis,
                state.liveInputPreviewDelaySeconds,
            )
        )
        if (!sending && !state.isSimultaneousInterpretationActive) {
            livePreviewReady = true
            val previewText = state.chatDraft.trim()
            if (previewText.isNotBlank()) {
            liveOriginalUpdates.send(
                LiveOriginalUpdate(target, previewText, requiresTranslationIdle = true)
            )
            }
        }
    }

    LaunchedEffect(
        state.simultaneousFinalPending,
        state.interpretationVoiceInputEnabled,
        state.simultaneousInterpretationSendDelayMillis,
    ) {
        if (state.simultaneousFinalPending) {
            cancelActiveTranslation()
            if (state.interpretationVoiceInputEnabled && managedVoiceCapture) {
                state.consumeSimultaneousFinalRequest()
                pendingSimultaneousVoiceSend = true
                audioRecorder.stop()
                return@LaunchedEffect
            }
            delay(state.simultaneousInterpretationSendDelayMillis.toLong())
            if (
                !state.simultaneousFinalPending ||
                    state.isSimultaneousInterpretationActive ||
                    state.interpretationVoiceInputEnabled
            ) return@LaunchedEffect
            val finalText = state.chatDraft
            val barrier = CompletableDeferred<Unit>()
            liveOriginalUpdates.send(LiveOscBarrier(barrier))
            barrier.await()
            if (finalText.isNotBlank()) sendMessage(finalText, clearDraft = true)
            state.consumeSimultaneousFinalRequest()
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
                sendMessage(
                    pendingText,
                    clearDraft = true,
                    automaticAlwaysInterpretation = true,
                )
            }
        }
    }

    val historyListState = rememberChatHistoryListState(messages)
    if (!visible) return

    val interpreting = state.isSimultaneousInterpretationActive || state.isAlwaysInterpretationActive
    val animationTimeNanos = rememberChatAnimationTime(
        active = animationsEnabled && !state.isAlwaysInterpretationActive &&
            (sending || (state.voiceInputConfig.enabled && voiceTranscribing) ||
                messages.any { it.isLoading })
    )

    Column(Modifier.fillMaxSize().imePadding()) {
        ChatHistoryList(
            messages = messages,
            listState = historyListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            emptyContent = {
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
            },
        ) { index, message ->
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
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
                    showJapaneseRomaji = state.showJapaneseRomaji,
                    onCopy = {
                        scope.launch {
                            clipboard.setClipEntry(textClipEntry(message.text))
                        }
                    },
                    onResend = { sendMessage(message.text, clearDraft = false) },
                    animationTimeNanos = animationTimeNanos,
                )
            }
        }

        SnackbarHost(blockedSnackbar)
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
            interpreting = interpreting,
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
                    typingUpdates.trySend(TypingOscUpdate(active, state.chatDraft.isNotEmpty()))
                }
                val original = it.trim()
                if (
                    state.isSimultaneousInterpretationActive &&
                        active != null &&
                        original.isNotBlank()
                ) {
                    liveOriginalUpdates.trySend(LiveOriginalUpdate(active, original))
                }
                if (
                    livePreviewReady &&
                        state.liveInputPreview &&
                        !sending &&
                        !state.isSimultaneousInterpretationActive &&
                        active != null
                ) {
                    liveOriginalUpdates.trySend(
                        LiveOriginalUpdate(active, state.chatDraft.trim(), true)
                    )
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
            animationTimeNanos = animationTimeNanos,
        )
    }
}
