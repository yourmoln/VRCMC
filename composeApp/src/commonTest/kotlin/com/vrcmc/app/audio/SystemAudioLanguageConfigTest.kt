package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemAudioLanguageConfigTest {
    @Test
    fun steamVrSettingsRoundTripWithSharedOpacityAndSafeDefaults() {
        for (position in SteamVrOverlayPosition.entries) for (scale in steamVrOverlayScalePercents) {
            val config = SystemAudioLanguageConfig(
                "en", "日本語", 65, steamVrOverlayEnabled = true, steamVrOverlayPosition = position,
                steamVrOverlayScalePercent = scale,
            )
            val restored = storedTranslationSettingsFromJson(StoredTranslationSettings(systemAudioLanguages = config).toJson())
            assertEquals(config, restored.systemAudioLanguages)
        }
        val old = storedTranslationSettingsFromJson("""{"systemAudioLanguages":{"opacityPercent":45}}""")
        assertEquals(SystemAudioLanguageConfig(opacityPercent = 45), old.systemAudioLanguages)
        for (invalid in listOf("null", "{}", "42", "\"unknown\"")) {
            val restored = storedTranslationSettingsFromJson("""{"systemAudioLanguages":{"sourceLanguage":"en","opacityPercent":70,"steamVrOverlayEnabled":$invalid,"steamVrOverlayPosition":$invalid}}""")
            assertEquals(SystemAudioLanguageConfig(sourceLanguage = "en", opacityPercent = 70), restored.systemAudioLanguages)
        }
    }

    @Test
    fun missingOrInvalidOverlaySizesKeepTheCurrentDefaultAndOtherSettings() {
        for (value in listOf("null", "{}", "\"bad\"", "0", "65", "201", "-2147483648")) {
            val restored = storedTranslationSettingsFromJson("""{"systemAudioLanguages":{"sourceLanguage":"en","steamVrOverlayEnabled":true,"steamVrOverlayScalePercent":$value}}""")
            assertEquals(SystemAudioLanguageConfig(sourceLanguage = "en", steamVrOverlayEnabled = true), restored.systemAudioLanguages)
        }
        val old = storedTranslationSettingsFromJson("""{"systemAudioLanguages":{"steamVrOverlayEnabled":true}}""")
        assertEquals(100, old.systemAudioLanguages.steamVrOverlayScalePercent)
    }

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
