package com.vrcmc.app

import javax.sound.sampled.AudioSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assume.assumeTrue

class SileroSpeechDetectorTest {
    @Test
    fun configuredServicesRecognizeAndTranslateSpeechWithoutRequestsForSilence() = runBlocking {
        assumeTrue("Opt in with VRCMC_LISTENING_API_SMOKE=1", System.getenv("VRCMC_LISTENING_API_SMOKE") == "1")
        val state = AppState()
        assumeTrue(state.voiceInputConfig.apiKey.isNotBlank() && state.isTranslationApiConfigured)
        val settings = SystemAudioListeningSettings(state.voiceInputConfig, state.provider, state.providerConfig, state.languages.toList())
            .withLanguages(state.systemAudioLanguages)
        assumeTrue(settings.voice.language in listOf("zh", "auto"))
        val captions = mutableListOf<SystemAudioCaption>()
        var requests = 0
        SileroSpeechDetector().use { detector ->
            SystemAudioListeningSession(transcribe = { config, wav ->
                requests++
                assertTrue(wav.size <= 44 + 16_000 * 2 * 6)
                transcribeQwenAudio(config, wav)
            }).run(settings, SystemAudioCapture { _, emit ->
                emit(silence(10_000))
                yield()
                assertEquals(0, requests)
                emit(speechFixture("speech-zh.wav") + silence(1_000))
            }, LocaleStringsEn, captions::add, {}, detector::isSpeech)
        }
        assertTrue(requests > 0)
        assertTrue(captions.isNotEmpty())
        assertTrue(captions.all { it.error == null }, "Configured speech recognition and translation must succeed")
        assertTrue(captions.any { "语音" in it.original }, "Recognition must reflect the known speech fixture")
        assertTrue(captions.all { it.translations.size == settings.languages.size && it.translations.all { pair -> pair.second.isNotBlank() } })
    }

    @Test
    fun actualModelRecognizesSpeechButRejectsSilenceNoiseAndTones() = runBlocking {
        val provider = providerById("microsoft_edge_web")
        val settings = SystemAudioListeningSettings(VoiceInputConfig(), provider, defaultProviderConfig(provider), listOf("English"))
        val speech = speechFixture()
        var requests = 0
        val durations = mutableListOf<Double>()
        SileroSpeechDetector().use { detector ->
            val random = Random(77)
            SystemAudioListeningSession(
                transcribe = { _, wav ->
                    requests++
                    durations += (wav.size - 44).toDouble() / 32_000
                    VoiceTranscriptionResult.Success("test sentence")
                },
                translate = { _, _, _, text -> TranslationResult.Success(text) },
            ).run(settings, SystemAudioCapture { _, emit ->
                suspend fun feed(pcm: ByteArray) {
                    var offset = 0
                    while (offset < pcm.size) {
                        emit(pcm.copyOfRange(offset, minOf(offset + 1_024, pcm.size)))
                        offset += 1_024
                        yield()
                    }
                }
                feed(silence(8_000))
                feed(pcmSamples(16_000 * 3) { random.nextDouble(-0.06, 0.06) } + silence(1_000))
                feed(tone(16_000, 3_000) + silence(1_000))
                feed(pcmSamples(16_000 * 3) { 0.2 } + silence(1_000))
                assertEquals(0, requests, "Actual VAD must reject non-speech before contacting ASR")
                feed(speech + silence(1_500))
                assertTrue(requests > 0, "Actual VAD must retain spoken words")
                val afterSpeech = requests
                feed(silence(10_000))
                assertEquals(afterSpeech, requests, "Silence after speech must not generate ghost requests")
            }, LocaleStringsEn, {}, {}, detector::isSpeech)
        }
        assertTrue(durations.all { it <= 6.0 }, "All real speech requests must be capped at six seconds: $durations")
    }
}

internal fun speechFixture(file: String = "speech.wav"): ByteArray =
    AudioSystem.getAudioInputStream(checkNotNull(SileroSpeechDetectorTest::class.java.getResource("/audio/$file"))).use {
        check(it.format.sampleRate == 16_000f && it.format.channels == 1 && it.format.sampleSizeInBits == 16)
        it.readBytes()
    }
