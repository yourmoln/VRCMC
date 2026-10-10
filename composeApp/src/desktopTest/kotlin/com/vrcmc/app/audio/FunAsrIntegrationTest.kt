package com.vrcmc.app

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Uses saved credentials only when explicitly opted in; uploads public test fixtures. */
class FunAsrIntegrationTest {
    @Test
    fun savedConfigurationTranscribesChineseAndEnglishFixtures() = runBlocking {
        assumeTrue("Opt in with VRCMC_FUN_ASR_SMOKE=1", System.getenv("VRCMC_FUN_ASR_SMOKE") == "1")
        val stored = storedTranslationSettingsFromJson(loadStoredTranslationSettings())
        val secrets = storedProviderSecretsFromJson(loadStoredTranslationSecrets())
        val config = stored.voiceInput.copy(apiKey = secrets["qwen3_asr"]?.apiKey.orEmpty())
        check(config.apiKey.isNotBlank()) { "Saved DashScope voice API key is required for the live test" }
        val failures = mutableListOf<String>()
        for ((file, language, expected) in listOf(
            Triple("speech-zh.wav", "zh", "语音"),
            Triple("speech.wav", "auto", "speech"),
        )) {
            val originalWav = checkNotNull(javaClass.getResourceAsStream("/audio/$file")).use { it.readBytes() }
            val wav = pcm16ToWav(originalWav.copyOfRange(44, originalWav.size), 16_000)
            val started = System.nanoTime()
            val result = transcribeQwenAudio(config.copy(language = language), wav) {
                failures += it
                println("Fun-ASR API failure: $it")
            }
            val success = assertIs<VoiceTranscriptionResult.Success>(result, "Live Fun-ASR failed for $file: $result; API responses: $failures")
            assertTrue(success.text.contains(expected, ignoreCase = true), "Expected '$expected' in '${success.text}'")
            println("Fun-ASR $file ($language), %.2fs: ${success.text}".format((System.nanoTime() - started) / 1e9))
        }
    }
}
