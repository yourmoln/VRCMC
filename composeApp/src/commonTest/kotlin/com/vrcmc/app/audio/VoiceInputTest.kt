package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.sin

class VoiceInputTest {
    @Test
    fun voiceInputSettingsRoundTripWithoutApiKey() {
        val stored = StoredTranslationSettings(
            voiceInput = VoiceInputConfig(
                enabled = true,
                apiKey = "secret",
                region = "china_mainland",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "qwen3-asr-flash",
                language = "zh",
                maxSegmentSeconds = 9,
                tailSilenceMillis = 800,
                vadMinRms = 0.02,
                vadSpeechRatio = 0.7,
                partialIntervalMillis = 600,
                timeoutSeconds = 18,
            )
        )

        val json = stored.toJson()
        val restored = storedTranslationSettingsFromJson(json).voiceInput

        assertFalse(json.contains("secret"))
        assertTrue(restored.enabled)
        assertEquals("china_mainland", restored.region)
        assertEquals("qwen3-asr-flash", restored.model)
        assertEquals("zh", restored.language)
        assertEquals(9, restored.maxSegmentSeconds)
        assertEquals(800, restored.tailSilenceMillis)
        assertEquals(0.02, restored.vadMinRms)
        assertEquals(0.7, restored.vadSpeechRatio)
        assertEquals(600, restored.partialIntervalMillis)
        assertEquals(18, restored.timeoutSeconds)
    }

    @Test
    fun wavHeaderContainsPcmPayload() {
        val wav = pcm16ToWav(byteArrayOf(1, 2, 3, 4), 16_000)

        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals("data", wav.copyOfRange(36, 40).decodeToString())
        assertEquals(listOf<Byte>(1, 2, 3, 4), wav.copyOfRange(44, 48).toList())
    }

    @Test
    fun base64AndQwenResponseParsing() {
        assertEquals("AQIDBA==", encodeBase64(byteArrayOf(1, 2, 3, 4)))
        assertEquals(
            "こんにちは",
            parseQwenAsrResponse(
                """{"choices":[{"message":{"content":"こんにちは"}}]}"""
            ),
        )
    }

    @Test
    fun detectsSpeechEmitsPartialsAndStopsAfterSilence() {
        val states = mutableListOf<Boolean>()
        val partials = mutableListOf<ByteArray>()
        val finals = mutableListOf<ByteArray>()
        var autoStopped = false
        var noSpeech = false
        val config = VoiceInputConfig(
            sampleRate = 16_000,
            tailSilenceMillis = 300,
            vadActivationMillis = 90,
            vadMinRms = 0.01,
            vadSpeechRatio = 0.6,
            partialIntervalMillis = 250,
            partialMinSpeechMillis = 200,
        )
        val processor = VoiceCaptureProcessor(
            config,
            states::add,
            partials::add,
            finals::add,
            { noSpeech = true },
            { autoStopped = true },
        )

        repeat(5) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.0)) }
        repeat(40) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.25)) }
        repeat(12) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.0)) }

        assertEquals(listOf(true, false), states)
        assertTrue(partials.isNotEmpty())
        assertEquals(1, finals.size)
        assertTrue(autoStopped)
        assertFalse(noSpeech)
    }

    @Test
    fun ignoresLowLevelNoise() {
        var noSpeech = false
        var finalCount = 0
        val config = VoiceInputConfig(vadMinRms = 0.02)
        val processor = VoiceCaptureProcessor(
            config,
            {},
            {},
            { finalCount++ },
            { noSpeech = true },
            {},
        )
        repeat(30) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.003)) }
        processor.finish()

        assertTrue(noSpeech)
        assertEquals(0, finalCount)
    }

    @Test
    fun streamingMergerKeepsStableText() {
        val merger = StreamingTextMerger(stableRepeats = 2)
        assertEquals("hello world one", merger.ingestPartial("hello world one"))
        assertEquals("hello world two", merger.ingestPartial("hello world two"))
        assertEquals("hello world alpha", merger.ingestPartial("hello world alpha"))
        assertEquals("hello world", merger.ingestPartial("hello w"))
        assertEquals("hello world!", merger.ingestFinal("hello world!"))
    }

    private fun pcmFrame(sampleRate: Int, amplitude: Double): ByteArray {
        val samples = sampleRate * 30 / 1_000
        val bytes = ByteArray(samples * 2)
        repeat(samples) { index ->
            val sample = (sin(2.0 * PI * 220.0 * index / sampleRate) * amplitude * 32767.0).toInt().toShort()
            bytes[index * 2] = sample.toByte()
            bytes[index * 2 + 1] = (sample.toInt() shr 8).toByte()
        }
        return bytes
    }
}
