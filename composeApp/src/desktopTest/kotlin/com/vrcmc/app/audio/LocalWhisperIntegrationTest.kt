package com.vrcmc.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class LocalWhisperIntegrationTest {
    @Test
    fun downloadsVerifiesLoadsAndRecognizesEnglishAndChineseOffline() = runBlocking {
        assumeTrue(System.getenv("VRCMC_LOCAL_ASR_INTEGRATION_TEST") == "1")
        assumeTrue(localWhisperSupported())
        val cache = Path.of("build", "local-asr-test", "ggml-small-q5_1.bin").toAbsolutePath()
        val recognizer = DesktopLocalSpeechRecognizer(LocalWhisperModelStore(cache))
        try {
            assertTrue(recognizer.downloadModel(), recognizer.status.value.toString())
            assertTrue(recognizer.prepare(), recognizer.status.value.toString())
            for ((fixture, language, expected) in listOf(
                Triple("speech.wav", "en", "speech"),
                Triple("speech-zh.wav", "zh", "语音"),
                Triple("speech.wav", "auto", "speech"),
            )) {
                val wav = checkNotNull(javaClass.getResourceAsStream("/audio/$fixture")).use { it.readBytes() }
                val result = assertIs<VoiceTranscriptionResult.Success>(recognizer.transcribe(wav, language))
                println("Local Whisper ($language): ${result.text}")
                assertTrue(result.text.contains(expected, ignoreCase = true), result.text)
            }
        } finally {
            recognizer.release()
        }
    }
}
