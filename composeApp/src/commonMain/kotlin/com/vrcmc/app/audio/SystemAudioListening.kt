package com.vrcmc.app

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun interface SystemAudioCapture {
    // Emits mono, little-endian PCM16 until cancelled. Implementations release all native resources.
    suspend fun capture(sampleRate: Int, onPcm: suspend (ByteArray) -> Unit)
}

internal data class SystemAudioListeningSettings(
    val voice: VoiceInputConfig,
    val provider: TranslationProvider,
    val providerConfig: ProviderConfig,
    val languages: List<String>,
) {
    fun withLanguages(config: SystemAudioLanguageConfig): SystemAudioListeningSettings = copy(
        voice = voice.copy(language = config.sourceLanguage),
        languages = listOf(config.targetLanguage),
    )
}

internal data class SystemAudioCaption(
    val id: Long,
    val original: String = "",
    val translations: List<Pair<String, String>> = emptyList(),
    val error: String? = null,
)

internal class SystemAudioListeningSession(
    private val transcribe: suspend (VoiceInputConfig, ByteArray) -> VoiceTranscriptionResult =
        { config, wav -> transcribeQwenAudio(config, wav) },
    private val translate: suspend (TranslationProvider, ProviderConfig, String, String) -> TranslationResult =
        { provider, config, language, text -> translateText(provider, config, language, text) },
) {
    private class Sentence(val id: Int) {
        val chunks = Channel<VoiceAudioChunk>(3)
        val discarded = CompletableDeferred<Unit>()

        fun discard(): Boolean {
            if (!discarded.complete(Unit)) return false
            chunks.close()
            while (chunks.tryReceive().isSuccess) { /* Release queued audio. */ }
            return true
        }
    }

    suspend fun run(
        settings: SystemAudioListeningSettings,
        capture: SystemAudioCapture,
        strings: LocaleStrings,
        onCaption: (SystemAudioCaption) -> Unit,
        onSpeechState: (Boolean) -> Unit,
        speechDetector: (ByteArray) -> Boolean,
        onBacklog: () -> Unit = {},
    ) = coroutineScope {
        val voice = settings.voice.copy(
            sampleRate = 16_000,
            maxSegmentSeconds = settings.voice.maxSegmentSeconds.coerceIn(1, 6),
            vadMinRms = settings.voice.vadMinRms.takeIf { it.isFinite() }?.coerceAtLeast(0.004) ?: 0.012,
            vadActivationMillis = settings.voice.vadActivationMillis.coerceAtLeast(160),
            vadSpeechRatio = settings.voice.vadSpeechRatio.takeIf { it.isFinite() }?.coerceAtLeast(0.6) ?: 0.6,
            partialMinSpeechMillis = settings.voice.partialMinSpeechMillis.coerceAtLeast(256),
        )
        val pcm = Channel<ByteArray>(32)
        launch {
            try {
                capture.capture(voice.sampleRate) { pcm.send(it) }
            } finally {
                pcm.close()
            }
        }
        var nextId = 0
        val sentences = Channel<Sentence>(3)
        var currentSentence: Sentence? = null

        fun acceptChunk(chunk: VoiceAudioChunk) {
            val sentence = currentSentence ?: Sentence(nextId++).also {
                currentSentence = it
                if (sentences.trySend(it).isFailure) {
                    // Drop an entire utterance: a missing chunk must never become a half subtitle.
                    sentences.tryReceive().getOrNull()?.discard()
                    if (sentences.trySend(it).isFailure) it.discard()
                    onBacklog()
                }
            }
            if (!sentence.discarded.isCompleted && chunk.wav != null && sentence.chunks.trySend(chunk).isFailure) {
                if (sentence.discard()) onBacklog()
            }
            if (chunk.isFinal) {
                sentence.chunks.close()
                currentSentence = null
            }
        }

        repeat(2) {
            launch {
                for (sentence in sentences) {
                    val assembler = SentenceTextAssembler()
                    var captionIndex = 0
                    fun nextCaptionId(): Long = (sentence.id.toLong() shl 32) or captionIndex++.toLong()
                    suspend fun publish(text: String) {
                        if (sentence.discarded.isCompleted) return
                        val results = settings.languages.map { language ->
                            async { language to translate(settings.provider, settings.providerConfig, language, text) }
                        }.awaitAll()
                        if (sentence.discarded.isCompleted) return
                        onCaption(SystemAudioCaption(
                            id = nextCaptionId(),
                            original = text,
                            translations = results.mapNotNull { (language, translation) ->
                                (translation as? TranslationResult.Success)?.let { language to it.text }
                            },
                            error = results.mapNotNull { (_, translation) ->
                                (translation as? TranslationResult.Failure)?.let(strings::translationFailureMessage)
                            }.firstOrNull(),
                        ))
                    }
                    for (chunk in sentence.chunks) {
                        if (sentence.discarded.isCompleted) break
                        val wav = chunk.wav ?: continue
                        when (val result = transcribe(voice, wav)) {
                            is VoiceTranscriptionResult.Failure -> {
                                if (sentence.discard()) onCaption(SystemAudioCaption(
                                    id = nextCaptionId(),
                                    error = strings.voiceTranscriptionFailureMessage(result),
                                ))
                                break
                            }
                            is VoiceTranscriptionResult.Success -> {
                                assembler.append(result.text, chunk.overlapSamples > 0).forEach { publish(it) }
                            }
                        }
                    }
                    if (sentence.discarded.isCompleted) continue
                    // A real pause releases the final sentence. Continuous narration can also
                    // release confirmed internal sentence endings without displaying its unfinished tail.
                    assembler.finish().forEach { publish(it) }
                }
            }
        }

        val processor = VoiceCaptureProcessor(
            config = voice,
            onSpeechState = onSpeechState,
            onPartial = {},
            onFinal = {},
            onNoSpeech = {},
            onAutoStop = {},
            continuous = true,
            emitPartials = false,
            speechDetector = speechDetector,
            onChunk = ::acceptChunk,
        )
        try {
            for (chunk in pcm) processor.accept(chunk)
            // Capture stopping is not a sentence boundary; discard its unfinished tail.
        } finally {
            pcm.cancel()
            currentSentence?.discard()
            sentences.close()
        }
    }
}
