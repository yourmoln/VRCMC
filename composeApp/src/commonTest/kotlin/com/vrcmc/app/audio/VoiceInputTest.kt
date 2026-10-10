package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.sin

class VoiceInputTest {
    @Test
    fun localProviderRoundTripsAndOldSettingsKeepQwen() {
        val config = VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_WHISPER,
            localWhisperModel = LocalWhisperModel.MEDIUM_Q5_0,
            apiKey = "retained-secret", model = "my-qwen-model", region = "custom")
        val json = StoredTranslationSettings(voiceInput = config).toJson()
        assertFalse(json.contains("retained-secret"))
        assertTrue(json.contains("medium-q5_0"))
        assertEquals(config.copy(apiKey = ""), storedTranslationSettingsFromJson(json).voiceInput)
        assertEquals(VoiceInputProvider.QWEN, storedTranslationSettingsFromJson("""{"voiceInput":{"enabled":true}}""").voiceInput.provider)
        assertEquals(VoiceInputProvider.QWEN, storedTranslationSettingsFromJson("""{"voiceInput":{"provider":"unknown"}}""").voiceInput.provider)
        assertEquals(0.008, VoiceInputConfig().vadMinRms)
        assertEquals(
            0.008,
            storedTranslationSettingsFromJson("""{"voiceInput":{"enabled":true}}""").voiceInput.vadMinRms,
        )
        assertEquals(
            LocalWhisperModel.SMALL_Q5_1,
            storedTranslationSettingsFromJson("""{"voiceInput":{"provider":"LOCAL_WHISPER"}}""")
                .voiceInput.localWhisperModel,
        )
        assertEquals(
            LocalWhisperModel.SMALL_Q5_1,
            storedTranslationSettingsFromJson(
                """{"voiceInput":{"provider":"LOCAL_WHISPER","localWhisperModel":"unknown"}}""",
            ).voiceInput.localWhisperModel,
        )
    }

    @Test
    fun voiceInputSettingsRoundTripWithoutApiKey() {
        val stored = StoredTranslationSettings(
            voiceInput = VoiceInputConfig(
                enabled = true,
                apiKey = "secret",
                region = "china_mainland",
                baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
                model = "fun-asr",
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
        assertEquals("fun-asr", restored.model)
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
    fun funAsrRequestAndResponseParsing() {
        assertTrue(buildFunAsrRequest("oss://audio.wav", "ja").contains("\"model\":\"fun-asr\""))
        assertEquals("task-123", parseFunAsrTaskId("""{"output":{"task_id":"task-123"}}"""))
        assertEquals(
            "こんにちは",
            parseFunAsrResponse(
                """{"output":{"text":"こんにちは"}}"""
            ),
        )
        assertEquals("こんにちは", parseFunAsrResponse("""{"transcripts":[{"text":"こんにちは"}]}"""))
        assertEquals("https://host/api/v1", funAsrApiBaseUrl("https://host/compatible-mode/v1"))
        assertEquals("https://host/api/v1", funAsrApiBaseUrl("https://host/api/v1"))
    }

    @Test
    fun funAsrUploadPolicySupportsDashScopeRootResponse() {
        val policy = """
            {
              "request_id": "request-1",
              "upload_host": "https://oss.example.com",
              "upload_dir": "uploads/session",
              "oss_access_key_id": "access-key",
              "signature": "signature",
              "policy": "policy"
            }
        """.trimIndent()

        assertTrue(parseUploadPolicy(policy) != null)
    }

    @Test
    fun oldQwenModelsMigrateToFunAsr() {
        assertEquals(
            "fun-asr",
            storedTranslationSettingsFromJson(
                """{"voiceInput":{"model":"qwen3-asr-flash-2026-02-10"}}""",
            ).voiceInput.model,
        )
        assertEquals(
            "fun-asr",
            storedTranslationSettingsFromJson(
                """{"voiceInput":{"model":"qwen3-asr-flash"}}""",
            ).voiceInput.model,
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
    fun acceptsShortUtterancesAfterVadActivation() {
        val states = mutableListOf<Boolean>()
        val finals = mutableListOf<ByteArray>()
        var noSpeech = false
        val config = VoiceInputConfig(
            tailSilenceMillis = 300,
            vadActivationMillis = 200,
            vadMinRms = 0.008,
            vadSpeechRatio = 0.6,
            partialMinSpeechMillis = 450,
        )
        val processor = VoiceCaptureProcessor(
            config,
            states::add,
            {},
            finals::add,
            { noSpeech = true },
            {},
        )

        repeat(4) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.25)) }
        repeat(12) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.0)) }

        assertEquals(listOf(true, false), states)
        assertEquals(1, finals.size)
        assertFalse(noSpeech)
    }

    @Test
    fun continuousCaptureEmitsEachUtteranceWithoutStopping() {
        val chunks = mutableListOf<VoiceAudioChunk>()
        val states = mutableListOf<Boolean>()
        var autoStops = 0
        val config = VoiceInputConfig(
            tailSilenceMillis = 300,
            vadActivationMillis = 200,
            vadMinRms = 0.008,
            vadSpeechRatio = 0.6,
            partialMinSpeechMillis = 450,
        )
        val processor = VoiceCaptureProcessor(
            config,
            states::add,
            {},
            {},
            {},
            { autoStops++ },
            continuous = true,
            emitPartials = false,
            onChunk = chunks::add,
        )

        repeat(4) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.25)) }
        repeat(12) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.0)) }
        repeat(4) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.25)) }
        repeat(12) { processor.accept(pcmFrame(config.sampleRate, amplitude = 0.0)) }

        assertEquals(listOf(true, false, true, false), states)
        assertEquals(2, chunks.count { it.isFinal })
        assertTrue(chunks.all { it.wav != null })
        assertEquals(0, autoStops)
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
