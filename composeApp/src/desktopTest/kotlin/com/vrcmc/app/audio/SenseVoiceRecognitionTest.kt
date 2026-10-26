package com.vrcmc.app

import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class SenseVoiceRecognitionTest {
    @Test
    fun fbankAndLfrProduceTheModelFeatureShape() {
        val samples = FloatArray(16_000) { index ->
            (sin(2.0 * PI * 220.0 * index / 16_000.0) * 0.2).toFloat()
        }
        val fbank = localSpeechFbank(samples)
        assertEquals(98 * 80, fbank.size)
        assertTrue(fbank.all(Float::isFinite))

        val lfr = localSpeechApplyLfr(fbank, 80, 7, 6)
        assertEquals(17 * 560, lfr.size)
    }

    @Test
    fun ctcDecodeCollapsesRepeatsAndSkipsMetadataTokens() {
        val tokens = listOf(
            "<unk>",
            "<|zh|>",
            "<|NEUTRAL|>",
            "<|Speech|>",
            "<|withitn|>",
            "你",
            "好",
        )
        val ids = intArrayOf(1, 2, 3, 4, 5, 5, 0, 6, 6)
        val logits = FloatArray(ids.size * tokens.size)
        ids.forEachIndexed { frame, id ->
            logits[frame * tokens.size + id] = 1f
        }

        assertEquals(
            "你好",
            decodeCtcLogits(logits, ids.size, tokens.size, tokens, blankId = 0),
        )
    }

    @Test
    fun cachedInt8ModelRecognizesChineseFixture() = runBlocking {
        assumeTrue(System.getenv("VRCMC_SENSE_VOICE_INTEGRATION_TEST") == "1")
        val model = LocalSpeechModel.SENSE_VOICE_SMALL_INT8
        val path = Path.of("..", "build", "sensevoice-test", model.modelFileName())
        val store = model.createStore(path)
        val modelValid = store.isValid()
        val tokensValid = store.isAuxiliaryValid()
        assertTrue(
            modelValid && tokensValid,
            "Missing cached SenseVoice model or tokens: model=${path.toAbsolutePath()} ($modelValid), " +
                "tokens=${store.auxiliaryPath?.toAbsolutePath()} ($tokensValid)",
        )
        val recognizer = JvmLocalSpeechRecognizer(
            mapOf(model to store),
            supported = true,
            createEngine = ::createDesktopLocalSpeechEngine,
        )
        try {
            assertTrue(recognizer.prepare(model), recognizer.status(model).value.toString())
            val wav = checkNotNull(javaClass.getResourceAsStream("/audio/speech-zh.wav")).use {
                it.readBytes()
            }
            val result = assertIs<VoiceTranscriptionResult.Success>(
                recognizer.transcribe(model, wav, "zh"),
            )
            println("SenseVoice zh: ${result.text}")
            assertTrue(result.text.isNotBlank())
        } finally {
            recognizer.release()
        }
    }
}
