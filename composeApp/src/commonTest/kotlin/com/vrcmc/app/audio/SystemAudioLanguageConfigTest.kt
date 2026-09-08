package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemAudioLanguageConfigTest {
    @Test
    fun existingChatAndMicrophoneLanguagesDoNotOverrideListeningDefaults() {
        val restored = storedTranslationSettingsFromJson("""{"targetLanguages":["English"],"voiceInput":{"language":"ja"}}""")
        assertEquals(SystemAudioLanguageConfig("auto", "简体中文"), restored.systemAudioLanguages)
        assertEquals(listOf("English"), restored.targetLanguages)
        assertEquals("ja", restored.voiceInput.language)
    }

    @Test
    fun listeningLanguagesPersistIndependentlyAndInvalidValuesUseDefaults() {
        val settings = StoredTranslationSettings(
            systemAudioLanguages = SystemAudioLanguageConfig("en", "日本語", opacityPercent = 65),
            voiceInput = VoiceInputConfig(language = "ko"), targetLanguages = listOf("繁體中文"),
        )
        val restored = storedTranslationSettingsFromJson(settings.toJson())
        assertEquals(settings.systemAudioLanguages, restored.systemAudioLanguages)
        assertEquals(settings.voiceInput, restored.voiceInput)
        assertEquals(settings.targetLanguages, restored.targetLanguages)
        val invalid = storedTranslationSettingsFromJson("""{"translate":true,"systemAudioLanguages":{"sourceLanguage":"unknown","targetLanguage":42}}""")
        assertEquals(SystemAudioLanguageConfig(), invalid.systemAudioLanguages)
        assertEquals(true, invalid.translate)
    }

    @Test
    fun opacityDefaultsForOldSettingsAndStaysVisibleForInvalidValues() {
        val old = storedTranslationSettingsFromJson("""{"systemAudioLanguages":{"sourceLanguage":"en","targetLanguage":"日本語"}}""")
        assertEquals(SystemAudioLanguageConfig("en", "日本語", 80), old.systemAudioLanguages)
        for ((jsonValue, expected) in listOf("0" to 30, "-20" to 30, "100" to 100, "200" to 100, "null" to 80, "{}" to 80, "\"bad\"" to 80)) {
            val restored = storedTranslationSettingsFromJson("""{"translate":true,"systemAudioLanguages":{"sourceLanguage":"en","opacityPercent":$jsonValue}}""")
            assertEquals(expected, restored.systemAudioLanguages.opacityPercent)
            assertEquals("en", restored.systemAudioLanguages.sourceLanguage)
            assertEquals(true, restored.translate)
        }
    }
}
