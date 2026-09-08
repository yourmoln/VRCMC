package com.vrcmc.app

data class SystemAudioLanguageConfig(
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "简体中文",
    val opacityPercent: Int = 80,
    val steamVrOverlayEnabled: Boolean = false,
    val steamVrOverlayPosition: SteamVrOverlayPosition = SteamVrOverlayPosition.LEFT_HAND,
    val steamVrOverlayScalePercent: Int = 100,
)

enum class SteamVrOverlayPosition { LEFT_HAND, RIGHT_HAND, SCREEN_CENTER }

internal val steamVrOverlayScalePercents = listOf(50, 75, 100, 125, 150, 175, 200)

internal fun normalizedSteamVrOverlayScalePercent(value: Int): Int =
    value.takeIf { it in steamVrOverlayScalePercents } ?: 100

internal val systemAudioSourceLanguages = linkedMapOf(
    "zh" to "中文", "ja" to "日本語", "en" to "English", "ko" to "한국어",
    "de" to "Deutsch", "fr" to "Français", "ru" to "Русский",
)

internal fun SystemAudioLanguageConfig.normalized(): SystemAudioLanguageConfig = copy(
    sourceLanguage = sourceLanguage.takeIf { it == "auto" || it in systemAudioSourceLanguages } ?: "auto",
    targetLanguage = targetLanguage.takeIf { it in availableTargetLanguages } ?: "简体中文",
    opacityPercent = opacityPercent.coerceIn(30, 100),
    steamVrOverlayScalePercent = normalizedSteamVrOverlayScalePercent(steamVrOverlayScalePercent),
)
