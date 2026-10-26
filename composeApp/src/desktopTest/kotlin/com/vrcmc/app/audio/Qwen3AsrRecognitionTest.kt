package com.vrcmc.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class Qwen3AsrRecognitionTest {
    @Test
    fun recognizesCachedChineseFixture() = runBlocking {
        assumeTrue(System.getenv("VRCMC_QWEN3_ASR_INTEGRATION_TEST") == "1")
        val model = LocalSpeechModel.QWEN3_ASR_0_6B_INT8
        val root = Path.of("..", "build", "qwen3-asr-test")
        val store = model.createStore(root.resolve(model.modelFileName()))
        assertTrue(store.isComplete())
        val recognizer = JvmLocalSpeechRecognizer(
            mapOf(model to store),
            supported = true,
            createEngine = ::createDesktopLocalSpeechEngine,
        )
        try {
            assertTrue(recognizer.prepare(model), recognizer.status(model).value.toString())
            val wav = Files.readAllBytes(root.resolve("test_wavs/raokouling.wav"))
            val result = assertIs<VoiceTranscriptionResult.Success>(
                recognizer.transcribe(model, wav, "zh"),
            )
            println("Qwen3-ASR zh: ${result.text}")
            assertTrue(result.text.isNotBlank())
        } finally {
            recognizer.release()
        }
    }

    @Test
    fun qwenAndExistingOnnxModelsCanAlternateInOneProcess() {
        runBlocking {
            assumeTrue(System.getenv("VRCMC_QWEN3_ASR_INTEGRATION_TEST") == "1")
            val qwen = LocalSpeechModel.QWEN3_ASR_0_6B_INT8
            val sense = LocalSpeechModel.SENSE_VOICE_SMALL_INT8
            val funAsr = LocalSpeechModel.FUN_ASR_NANO_2512_INT8
            val qwenRoot = Path.of("..", "build", "qwen3-asr-test")
            val senseRoot = Path.of("..", "build", "sensevoice-test")
            val funAsrRoot = Path.of("..", "build", "funasr-test")
            val stores = mapOf(
                qwen to qwen.createStore(qwenRoot.resolve(qwen.modelFileName())),
                sense to sense.createStore(senseRoot.resolve(sense.modelFileName())),
                funAsr to funAsr.createStore(funAsrRoot.resolve(funAsr.modelFileName())),
            )
            assumeTrue(stores.values.all { it.isComplete() })
            val recognizer = JvmLocalSpeechRecognizer(
                stores,
                supported = true,
                createEngine = ::createDesktopLocalSpeechEngine,
            )
            val qwenWav = Files.readAllBytes(qwenRoot.resolve("test_wavs/raokouling.wav"))
            val senseWav = checkNotNull(javaClass.getResourceAsStream("/audio/speech-zh.wav")).use {
                it.readBytes()
            }
            val funAsrWav = Files.readAllBytes(funAsrRoot.resolve("zh.wav"))
            try {
                assertTrue(recognizer.prepare(qwen), recognizer.status(qwen).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(qwen, qwenWav, "zh"))
                assertTrue(recognizer.prepare(sense), recognizer.status(sense).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(sense, senseWav, "zh"))
                assertTrue(recognizer.prepare(funAsr), recognizer.status(funAsr).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(funAsr, funAsrWav, "zh"))
                assertTrue(recognizer.prepare(qwen), recognizer.status(qwen).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(qwen, qwenWav, "zh"))
            } finally {
                recognizer.release()
            }
        }
    }

    @Test
    fun existingOnnxModelCanLoadBeforeQwenInOneProcess() {
        runBlocking {
            assumeTrue(System.getenv("VRCMC_QWEN3_ASR_INTEGRATION_TEST") == "1")
            val qwen = LocalSpeechModel.QWEN3_ASR_0_6B_INT8
            val sense = LocalSpeechModel.SENSE_VOICE_SMALL_INT8
            val funAsr = LocalSpeechModel.FUN_ASR_NANO_2512_INT8
            val qwenRoot = Path.of("..", "build", "qwen3-asr-test")
            val senseRoot = Path.of("..", "build", "sensevoice-test")
            val funAsrRoot = Path.of("..", "build", "funasr-test")
            val stores = mapOf(
                qwen to qwen.createStore(qwenRoot.resolve(qwen.modelFileName())),
                sense to sense.createStore(senseRoot.resolve(sense.modelFileName())),
                funAsr to funAsr.createStore(funAsrRoot.resolve(funAsr.modelFileName())),
            )
            assumeTrue(stores.values.all { it.isComplete() })
            val recognizer = JvmLocalSpeechRecognizer(
                stores,
                supported = true,
                createEngine = ::createDesktopLocalSpeechEngine,
            )
            val qwenWav = Files.readAllBytes(qwenRoot.resolve("test_wavs/raokouling.wav"))
            val senseWav = checkNotNull(javaClass.getResourceAsStream("/audio/speech-zh.wav")).use {
                it.readBytes()
            }
            val funAsrWav = Files.readAllBytes(funAsrRoot.resolve("zh.wav"))
            try {
                assertTrue(recognizer.prepare(sense), recognizer.status(sense).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(sense, senseWav, "zh"))
                assertTrue(recognizer.prepare(funAsr), recognizer.status(funAsr).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(funAsr, funAsrWav, "zh"))
                assertTrue(recognizer.prepare(qwen), recognizer.status(qwen).value.toString())
                assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(qwen, qwenWav, "zh"))
            } finally {
                recognizer.release()
            }
        }
    }
}
