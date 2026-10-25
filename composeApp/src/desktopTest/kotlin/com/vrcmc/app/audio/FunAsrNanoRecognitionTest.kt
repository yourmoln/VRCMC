package com.vrcmc.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FunAsrNanoRecognitionTest {
    @Test
    fun recognizesOfficialChineseFixture() = runBlocking {
        org.junit.Assume.assumeTrue(System.getenv("VRCMC_FUN_ASR_INTEGRATION_TEST") == "1")
        val model = LocalSpeechModel.FUN_ASR_NANO_2512_INT8
        val root = Path.of("..", "build", "funasr-test")
        val store = model.createStore(root.resolve(model.modelFileName()))
        assertTrue(store.isComplete())
        val recognizer = JvmLocalSpeechRecognizer(mapOf(model to store), supported = true)
        try {
            assertTrue(recognizer.prepare(model), recognizer.status(model).value.toString())
            val wav = Files.readAllBytes(root.resolve("zh.wav"))
            val result = assertIs<VoiceTranscriptionResult.Success>(
                recognizer.transcribe(model, wav, "auto"),
            )
            println("Fun-ASR-Nano zh: ${result.text}")
            assertTrue(result.text.isNotBlank())
        } finally {
            recognizer.release()
        }
    }
}
