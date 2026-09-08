package com.vrcmc.app

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class SystemAudioListeningTest {
    private val voice = VoiceInputConfig(
        apiKey = "test", language = "auto", maxSegmentSeconds = 6,
        tailSilenceMillis = 160, vadActivationMillis = 160,
        partialMinSpeechMillis = 256, partialIntervalMillis = 150,
    )
    private val provider = providerById("microsoft_edge_web")
    private val settings = SystemAudioListeningSettings(
        voice, provider, defaultProviderConfig(provider), listOf("English", "日本語"),
    )

    private suspend fun SystemAudioListeningSession.listen(
        capture: SystemAudioCapture,
        captions: (SystemAudioCaption) -> Unit = {},
        detector: (ByteArray) -> Boolean = { true },
        config: SystemAudioListeningSettings = settings,
    ) = run(config, capture, LocaleStringsEn, captions, {}, detector)

    @Test
    fun reusesServiceSettingsAndPublishesEachSentenceOnce() = runBlocking {
        val captions = mutableListOf<SystemAudioCaption>()
        var recognized = 0
        SystemAudioListeningSession(
            transcribe = { config, wav ->
                assertEquals(voice, config)
                assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
                VoiceTranscriptionResult.Success("sentence ${++recognized}")
            },
            translate = { usedProvider, config, language, text ->
                assertEquals(provider, usedProvider)
                assertEquals(settings.providerConfig, config)
                TranslationResult.Success("$language: $text")
            },
        ).listen(SystemAudioCapture { _, emit -> emit(utterance() + utterance()) }, captions::add)
        assertEquals(2, recognized)
        assertEquals(2, captions.size)
        assertTrue(captions.all { it.translations.map { pair -> pair.first } == settings.languages })
    }

    @Test
    fun waitsForPauseAndIgnoresShortGapsWithoutSendingPartials() = runBlocking {
        val captions = mutableListOf<SystemAudioCaption>()
        var requests = 0
        SystemAudioListeningSession(
            transcribe = { _, wav ->
                requests++
                assertTrue(wav.size > 44 + voice.sampleRate * 2 * 3)
                VoiceTranscriptionResult.Success("hello world")
            },
            translate = { _, _, language, text -> TranslationResult.Success("$language: $text") },
        ).listen(SystemAudioCapture { _, emit ->
            emit(tone(16_000, 3_000))
            yield()
            assertEquals(0, requests)
            assertTrue(captions.isEmpty())
            emit(silence(96))
            yield()
            assertEquals(0, requests)
            emit(tone(16_000, 300) + silence(320))
        }, captions::add)
        assertEquals(1, requests)
        assertEquals(2, captions.single().translations.size)
    }

    @Test
    fun continuousSpeechNeverExceedsSixSecondsAndKeepsListening() = runBlocking {
        val durations = mutableListOf<Double>()
        val captions = mutableListOf<SystemAudioCaption>()
        val translated = mutableListOf<String>()
        val recognized = listOf("我想用完整的句子", "完整的句子来测试语音", "测试语音识别的效果。")
        SystemAudioListeningSession(
            transcribe = { config, wav ->
                assertEquals(6, config.maxSegmentSeconds)
                durations += (wav.size - 44).toDouble() / (16_000 * 2)
                VoiceTranscriptionResult.Success(recognized[durations.lastIndex])
            },
            translate = { _, _, _, text -> translated += text; TranslationResult.Success(text) },
        ).listen(SystemAudioCapture { _, emit -> emit(tone(16_000, 14_000) + silence(320)) },
            captions::add,
            config = settings.copy(voice = voice.copy(maxSegmentSeconds = 60)))
        assertEquals(3, durations.size)
        assertTrue(durations.all { it <= 6.0 }, "Audio durations: $durations")
        assertTrue(durations.sum() >= 14.0, "Speech must not be dropped between segments")
        assertEquals("我想用完整的句子来测试语音识别的效果。", captions.single().original)
        assertEquals(List(2) { captions.single().original }, translated)
    }

    @Test
    fun overlappingAudioPreservesEverySampleAndSpeechStateAcrossBoundaries() {
        val chunks = mutableListOf<VoiceAudioChunk>()
        val states = mutableListOf<Boolean>()
        val random = Random(121)
        val spoken = pcmSamples(16_000 * 14_016 / 1_000) { random.nextDouble(-0.2, 0.2) }
        VoiceCaptureProcessor(
            voice, states::add, { fail("No partial requests") }, { fail("Use chunk metadata") }, {}, {},
            continuous = true, emitPartials = false, speechDetector = { true }, onChunk = chunks::add,
        ).accept(spoken + silence(320))
        val reconstructed = chunks.fold(ByteArray(0)) { pcm, chunk ->
            val wav = assertNotNull(chunk.wav)
            assertTrue(wav.size <= 44 + 6 * 32_000)
            pcm + wav.copyOfRange(44 + chunk.overlapSamples * 2, wav.size)
        }
        assertContentEquals(spoken + silence(160), reconstructed)
        assertEquals(listOf(true, false), states)
        assertEquals(listOf(false, false, true), chunks.map { it.isFinal })
        assertTrue(chunks.drop(1).all { it.overlapSamples > 0 })
    }

    @Test
    fun durationBoundaryDoesNotPublishOrTranslateUntilActualPause() = runBlocking {
        val chunkRecognized = CompletableDeferred<Unit>()
        val captions = mutableListOf<SystemAudioCaption>()
        var translations = 0
        var requests = 0
        SystemAudioListeningSession(
            transcribe = { _, _ ->
                requests++
                chunkRecognized.complete(Unit)
                VoiceTranscriptionResult.Success(if (requests == 1) "This is a long" else "a long sentence.")
            },
            translate = { _, _, _, text -> translations++; TranslationResult.Success(text) },
        ).listen(SystemAudioCapture { _, emit ->
            emit(tone(16_000, 6_016))
            withTimeout(2_000) { chunkRecognized.await() }
            assertTrue(captions.isEmpty())
            assertEquals(0, translations)
            emit(tone(16_000, 1_000) + silence(320))
        }, captions::add)
        assertEquals(2, requests)
        assertEquals("This is a long sentence.", captions.single().original)
    }

    @Test
    fun pauseExactlyAtDurationBoundaryDoesNotSendOverlapOnlyToAsr() = runBlocking {
        var requests = 0
        val captions = mutableListOf<SystemAudioCaption>()
        SystemAudioListeningSession(
            transcribe = { _, _ -> requests++; VoiceTranscriptionResult.Success("A complete sentence.") },
            translate = { _, _, _, text -> TranslationResult.Success(text) },
        ).listen(SystemAudioCapture { _, emit -> emit(tone(16_000, 5_984) + silence(320)) }, captions::add)
        assertEquals(1, requests)
        assertEquals("A complete sentence.", captions.single().original)
    }

    @Test
    fun stoppingCaptureWithoutPauseDiscardsUnfinishedSpeech() = runBlocking {
        SystemAudioListeningSession(
            transcribe = { _, _ -> fail("Stopping is not a sentence boundary") },
        ).listen(SystemAudioCapture { _, emit -> emit(tone(16_000, 1_000)) }, { fail("No incomplete caption on stop") })
    }

    @Test
    fun continuousNarrationPublishesCompleteSentencesBeforeSilence() = runBlocking {
        val published = CompletableDeferred<Unit>()
        val captions = mutableListOf<SystemAudioCaption>()
        var requests = 0
        SystemAudioListeningSession(
            transcribe = { _, _ -> VoiceTranscriptionResult.Success(if (++requests == 1)
                "第一句话已经完整说完了。下一句话仍然需要继续。" else "需要继续说完才可以显示。") },
            translate = { _, _, _, text -> TranslationResult.Success(text) },
        ).listen(SystemAudioCapture { _, emit ->
            emit(tone(16_000, 6_016))
            withTimeout(2_000) { published.await() }
            assertEquals("第一句话已经完整说完了。", captions.single().original)
            emit(tone(16_000, 1_000) + silence(320))
        }, { captions.add(it); published.complete(Unit) })
        assertEquals(listOf("第一句话已经完整说完了。", "下一句话仍然需要继续说完才可以显示。"), captions.map { it.original })
        assertEquals(2, captions.map { it.id }.distinct().size)
    }

    @Test
    fun keepsShortFinalWordAfterDurationBoundary() = runBlocking {
        var requests = 0
        val captions = mutableListOf<SystemAudioCaption>()
        SystemAudioListeningSession(
            transcribe = { _, _ -> VoiceTranscriptionResult.Success(if (++requests == 1) "We can do" else "can do it.") },
            translate = { _, _, _, text -> TranslationResult.Success(text) },
        ).listen(SystemAudioCapture { _, emit -> emit(tone(16_000, 6_080) + silence(320)) }, captions::add)
        assertEquals(2, requests)
        assertEquals("We can do it.", captions.single().original)
    }

    @Test
    fun failedChunkDiscardsWholeSentenceAndNextSentenceStillWorks() = runBlocking {
        var requests = 0
        val captions = mutableListOf<SystemAudioCaption>()
        SystemAudioListeningSession(
            transcribe = { _, _ ->
                if (++requests == 2) VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED)
                else VoiceTranscriptionResult.Success(if (requests == 1) "An unfinished" else "Next sentence.")
            },
            translate = { _, _, _, text ->
                assertEquals("Next sentence.", text)
                TranslationResult.Success(text)
            },
        ).listen(SystemAudioCapture { _, emit ->
            emit(tone(16_000, 7_000) + silence(320))
            yield()
            emit(utterance())
        }, captions::add)
        assertTrue(captions.any { it.error != null && it.original.isBlank() })
        assertEquals(listOf("Next sentence."), captions.filter { it.error == null }.map { it.original })
    }

    @Test
    fun missingChunkFromSlowServiceNeverPublishesHalfSentence() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val dropped = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val captions = mutableListOf<SystemAudioCaption>()
        var requests = 0
        val job = launch {
            SystemAudioListeningSession(
                transcribe = { _, _ ->
                    requests++
                    started.complete(Unit)
                    resume.await()
                    VoiceTranscriptionResult.Success("incomplete")
                },
                translate = { _, _, _, _ -> fail("Dropped utterance must not be translated") },
            ).run(settings, SystemAudioCapture { _, emit ->
                emit(tone(16_000, 6_016))
                started.await()
                repeat(5) { emit(tone(16_000, 6_016)); yield() }
                emit(silence(320))
            }, LocaleStringsEn, captions::add, {}, { true }, { dropped.complete(Unit) })
        }
        withTimeout(2_000) { dropped.await() }
        resume.complete(Unit)
        withTimeout(2_000) { job.join() }
        assertTrue(captions.isEmpty())
        assertEquals(1, requests)
    }

    @Test
    fun silenceDcOffsetLowNoiseAndClicksNeverCallRecognitionEvenIfClassifierMisfires() = runBlocking {
        val random = Random(19)
        val noise = pcmSamples(16_000 * 8) { random.nextDouble(-0.001, 0.001) }
        val dc = pcmSamples(16_000 * 8) { 0.25 }
        val clicks = pcmSamples(16_000 * 8) { if (it % 8_000 == 0) 0.8 else 0.0 }
        SystemAudioListeningSession(
            transcribe = { _, _ -> fail("No request is allowed for inaudible audio or clicks") },
        ).listen(SystemAudioCapture { _, emit ->
            emit(silence(90_000))
            emit(noise + silence(1_000))
            emit(dc + silence(1_000))
            emit(clicks + silence(1_000))
        }, { fail("No subtitles for silence") })
    }

    @Test
    fun loudNonSpeechRejectedByLocalDetectorNeverCallsRecognition() = runBlocking {
        SystemAudioListeningSession(
            transcribe = { _, _ -> fail("A loud signal alone is not speech") },
        ).listen(SystemAudioCapture { _, emit -> emit(tone(16_000, 20_000) + silence(1_000)) }, detector = { false })
    }

    @Test
    fun publishesOnlyAfterBothTranslationsAreReady() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val captions = mutableListOf<SystemAudioCaption>()
        val job = launch {
            SystemAudioListeningSession(
                transcribe = { _, _ -> VoiceTranscriptionResult.Success("hello world") },
                translate = { _, _, language, text ->
                    started.complete(Unit)
                    finish.await()
                    TranslationResult.Success("$language: $text")
                },
            ).listen(SystemAudioCapture { _, emit -> emit(utterance()) }, captions::add)
        }
        withTimeout(2_000) { started.await() }
        assertTrue(captions.isEmpty())
        finish.complete(Unit)
        withTimeout(2_000) { job.join() }
        assertEquals(2, captions.single().translations.size)
    }

    @Test
    fun closeCancelsCaptureAndInFlightRequests() = runBlocking {
        var released = false
        var cancelled = false
        val started = CompletableDeferred<Unit>()
        val job = launch {
            SystemAudioListeningSession(transcribe = { _, _ ->
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }).listen(SystemAudioCapture { _, emit ->
                try { emit(utterance()); awaitCancellation() } finally { released = true }
            }, { fail("Cancelled request must not publish subtitles") })
        }
        withTimeout(2_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(released)
        assertTrue(cancelled)
    }

    @Test
    fun slowServicesBoundAudioBacklogAndResumeWithoutStoppingCapture() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        var requests = 0
        var dropped = 0
        val job = launch {
            SystemAudioListeningSession(
                transcribe = { _, _ ->
                    requests++
                    resume.await()
                    VoiceTranscriptionResult.Success("sentence")
                },
                translate = { _, _, _, text -> TranslationResult.Success(text) },
            ).run(settings, SystemAudioCapture { _, emit ->
                repeat(10) { emit(utterance()); yield() }
                ready.complete(Unit)
                resume.await()
            }, LocaleStringsEn, {}, {}, { true }, { dropped++ })
        }
        withTimeout(2_000) { ready.await() }
        assertEquals(2, requests)
        assertTrue(dropped > 0)
        resume.complete(Unit)
        withTimeout(2_000) { job.join() }
        assertEquals(5, requests, "Two in flight and the newest three queued sentences")
    }

    @Test
    fun translationFailureRetainsOriginalAndOtherSuccessfulLanguage() = runBlocking<Unit> {
        var caption: SystemAudioCaption? = null
        SystemAudioListeningSession(
            transcribe = { _, _ -> VoiceTranscriptionResult.Success("hello") },
            translate = { _, _, language, _ ->
                if (language == "English") TranslationResult.Success("hello")
                else TranslationResult.Failure(reason = TranslationFailureReason.NETWORK_REQUEST_FAILED)
            },
        ).listen(SystemAudioCapture { _, emit -> emit(utterance()) }, { caption = it })
        assertEquals("hello", caption?.original)
        assertEquals(listOf("English" to "hello"), caption?.translations)
        assertNotNull(caption?.error)
    }

    private fun utterance(): ByteArray = tone(16_000, 640) + silence(320)
}

internal fun silence(millis: Int): ByteArray = ByteArray((16_000L * 2 * millis / 1_000).toInt())

internal fun pcmSamples(count: Int, sample: (Int) -> Double): ByteArray = ByteArray(count * 2).also { bytes ->
    repeat(count) { index ->
        val value = (sample(index).coerceIn(-1.0, 1.0) * 32767).toInt()
        bytes[index * 2] = value.toByte()
        bytes[index * 2 + 1] = (value shr 8).toByte()
    }
}

internal fun tone(sampleRate: Int, millis: Int, amplitude: Double = 0.2): ByteArray =
    pcmSamples(sampleRate * millis / 1_000) { sin(2 * PI * 440 * it / sampleRate) * amplitude }
