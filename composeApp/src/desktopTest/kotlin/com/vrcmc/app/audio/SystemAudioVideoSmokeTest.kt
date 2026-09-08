package com.vrcmc.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue

/** Explicitly opted-in acceptance test; normal test runs never use stored API credentials. */
class SystemAudioVideoSmokeTest {
    @Test
    fun listenToNinetySecondsOfVideoUsingConfiguredServices() = runBlocking(Dispatchers.Default) {
        val directory = System.getenv("VRCMC_VIDEO_SMOKE_DIR")?.let(::File)
        assumeTrue("Opt in with VRCMC_VIDEO_SMOKE_DIR", directory != null)
        val output = checkNotNull(directory).apply { mkdirs() }
        val replay = System.getenv("VRCMC_VIDEO_SMOKE_REPLAY") == "1"
        val offline = System.getenv("VRCMC_VIDEO_SMOKE_OFFLINE") == "1"
        val state = AppState()
        check(state.voiceInputConfig.apiKey.isNotBlank() && state.isTranslationApiConfigured)
        val settings = SystemAudioListeningSettings(state.voiceInputConfig, state.provider, state.providerConfig, state.languages.toList())
            .withLanguages(state.systemAudioLanguages)
        val requests = AtomicInteger()
        val translations = AtomicInteger()
        val backlog = AtomicInteger()
        val captions = Collections.synchronizedList(mutableListOf<SystemAudioCaption>())
        val report = File(output, "report.txt").apply { writeText("") }
        val started = System.nanoTime()
        fun log(message: String) = synchronized(report) {
            report.appendText("%.2fs %s\n".format((System.nanoTime() - started) / 1e9, message))
        }
        fun key(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val recording = File(output, "recording.pcm")
        val capture = if (replay) SystemAudioCapture { _, emit ->
            val pcm = recording.readBytes()
            for (offset in pcm.indices step 1_024) {
                emit(pcm.copyOfRange(offset, minOf(offset + 1_024, pcm.size)))
                // Match real playback pacing when contacting services; cached regression runs are fast.
                if (offline) yield() else delay(32)
            }
        } else SystemAudioCapture { rate, emit ->
            File(output, "ready").writeText("Ready; create start file after starting playback")
            withTimeout(120_000) { while (!File(output, "start").exists()) delay(100) }
            val pcm = ByteArrayOutputStream()
            try {
                withTimeoutOrNull(90_000) {
                    WindowsLoopbackCapture().capture(rate) {
                        pcm.write(it)
                        emit(it)
                    }
                }
            } finally {
                recording.writeBytes(pcm.toByteArray())
                File(output, "capture-finished").writeText("Capture stopped automatically")
            }
        }
        withTimeout(240_000) {
            SileroSpeechDetector().use { detector ->
                SystemAudioListeningSession(
                    transcribe = { config, wav ->
                        val duration = (wav.size - 44).toDouble() / 32_000
                        assertTrue(duration <= 6.0)
                        val cacheKey = key((config.baseUrl + "\n" + config.model + "\n" + config.language + "\n").toByteArray() + wav)
                        val cache = File(output, "$cacheKey.asr.txt")
                        val result = if (cache.exists()) VoiceTranscriptionResult.Success(cache.readText()) else {
                            check(!offline && !replay) { "Replay cache miss; refusing another ASR call: $cacheKey" }
                            check(requests.incrementAndGet() <= 30) { "Video smoke API budget exceeded" }
                            File(output, "$cacheKey.wav").writeBytes(wav)
                            transcribeQwenAudio(config, wav).also {
                                if (it is VoiceTranscriptionResult.Success) cache.writeText(it.text)
                            }
                        }
                        log("ASR duration=%.3f %s".format(duration, result))
                        result
                    },
                    translate = { provider, config, language, text ->
                        val cache = File(output, "${key((language + text).toByteArray())}.translation.txt")
                        if (cache.exists()) TranslationResult.Success(cache.readText()) else if (offline) {
                            TranslationResult.Success("[offline: $text]")
                        } else {
                            check(translations.incrementAndGet() <= 30 * settings.languages.size)
                            translateText(provider, config, language, text).also {
                                if (it is TranslationResult.Success) cache.writeText(it.text)
                            }
                        }
                    },
                ).run(settings, capture, LocaleStringsZhHans, {
                    captions.add(it)
                    log("CAPTION $it")
                }, { log("SPEECH $it") }, detector::isSpeech, { backlog.incrementAndGet(); log("BACKLOG") })
            }
        }
        log("SUMMARY asrRequests=${requests.get()} translationRequests=${translations.get()} captions=${captions.size} backlog=${backlog.get()}")
        assertTrue(captions.isNotEmpty(), "Expected video speech")
        assertEquals(0, backlog.get(), "Services must keep up with playback")
        assertTrue(captions.all { it.error == null && it.original.isNotBlank() && it.translations.size == settings.languages.size })
    }
}
