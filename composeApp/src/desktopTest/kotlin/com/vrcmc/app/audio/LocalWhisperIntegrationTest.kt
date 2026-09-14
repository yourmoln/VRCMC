package com.vrcmc.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class LocalWhisperIntegrationTest {
    @Test
    fun downloadsVerifiesLoadsAndRecognizesAllModelsOffline() = runBlocking {
        assumeTrue(System.getenv("VRCMC_LOCAL_ASR_INTEGRATION_TEST") == "1")
        assumeTrue(localWhisperSupported())
        val root = Path.of("build", "local-asr-test").toAbsolutePath()
        for (model in LocalWhisperModel.entries) {
            val store = LocalWhisperModelStore(
                path = root.resolve(model.modelFileName()),
                expectedSize = model.modelSize(),
                expectedHash = model.modelSha256(),
            )
            val recognizer = DesktopLocalSpeechRecognizer(
                stores = mapOf(model to store),
            )
            try {
                assertTrue(recognizer.downloadModel(model), recognizer.status(model).value.toString())
                assertTrue(recognizer.prepare(model), recognizer.status(model).value.toString())
                for ((fixture, language, expected) in listOf(
                    Triple("speech.wav", "en", "speech"),
                    Triple("speech-zh.wav", "zh", "语音"),
                    Triple("speech.wav", "auto", "speech"),
                )) {
                    val wav = checkNotNull(javaClass.getResourceAsStream("/audio/$fixture")).use { it.readBytes() }
                    val result = assertIs<VoiceTranscriptionResult.Success>(
                        recognizer.transcribe(model, wav, language),
                    )
                    println("${model.displayName} ($language): ${result.text}")
                    assertTrue(result.text.contains(expected, ignoreCase = true), result.text)
                }
            } finally {
                recognizer.release()
            }
        }
    }
}
