package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationProviderTest {
    @Test
    fun providerIdsAreUniqueAndDefaultsAreComplete() {
        assertEquals(translationProviders.size, translationProviders.map { it.id }.distinct().size)
        translationProviders.forEach { provider ->
            assertTrue(provider.defaultBaseUrl.startsWith("http"), provider.id)
            assertTrue(provider.defaultModel.isNotBlank(), provider.id)
        }
    }

    @Test
    fun publicSettingsNeverPersistProviderSecrets() {
        val original = StoredTranslationSettings(
            providerId = "openai_compatible",
            translate = true,
            targetLanguages = listOf("日本語", "English"),
            configs = mapOf("openai_compatible" to ProviderConfig("secret", "https://relay.example/v1", "custom/model", "custom", 47, "X-Test: yes", true, 6)),
        )
        val json = original.toJson()
        val restored = storedTranslationSettingsFromJson(json)
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("X-Test"))
        assertEquals(
            original.copy(configs = original.configs.mapValues { (_, value) -> value.copy(apiKey = "", customHeaders = "") }),
            restored,
        )
    }

    @Test
    fun protectedSecretsRoundTripSeparately() {
        val original = mapOf("openai" to ProviderSecrets("secret", "X-Test: yes"))
        assertEquals(original, storedProviderSecretsFromJson(original.toSecretsJson()))
    }

    @Test
    fun legacyPlaintextSettingsCanStillBeMigrated() {
        val restored = storedTranslationSettingsFromJson("""{"provider":"openai","configs":{"openai":{"apiKey":"legacy","headers":"Authorization: old"}}}""")
        assertEquals("legacy", restored.configs.getValue("openai").apiKey)
        assertEquals("Authorization: old", restored.configs.getValue("openai").customHeaders)
    }

    @Test
    fun invalidStoredDataFallsBackSafely() {
        val restored = storedTranslationSettingsFromJson("not-json")
        assertEquals("deepseek", restored.providerId)
        assertFalse(restored.translate)
    }

    @Test
    fun legacySingleLanguageMigratesToLanguageList() {
        val restored = storedTranslationSettingsFromJson("""{"provider":"deepseek","targetLanguage":"简体中文","configs":{}}""")
        assertEquals(listOf("简体中文"), restored.targetLanguages)
        assertEquals(listOf("简体中文", originalOutputKey), restored.outputOrder)
    }

    @Test
    fun persistedLanguagesAreLimitedToTwo() {
        val value = StoredTranslationSettings(targetLanguages = listOf("English", "日本語", "Deutsch"))
        assertEquals(listOf("English", "日本語"), storedTranslationSettingsFromJson(value.toJson()).targetLanguages)
    }

    @Test
    fun outputOrderRoundTripsAndDropsUnknownEntries() {
        val value = StoredTranslationSettings(
            targetLanguages = listOf("English", "日本語"),
            outputOrder = listOf(originalOutputKey, "日本語", "unknown", "English"),
        )
        assertEquals(listOf(originalOutputKey, "日本語", "English"), storedTranslationSettingsFromJson(value.toJson()).outputOrder)
    }

    @Test
    fun legacyConfigsUseFiveRetries() {
        val restored = storedTranslationSettingsFromJson("""{"provider":"deepseek","configs":{"deepseek":{"timeout":20}}}""")
        assertEquals(5, restored.configs.getValue("deepseek").retryCount)
    }
}
