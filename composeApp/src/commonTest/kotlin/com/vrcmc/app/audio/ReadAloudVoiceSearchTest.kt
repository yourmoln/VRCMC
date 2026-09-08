package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadAloudVoiceSearchTest {
    private val voices = listOf(
        EdgeTtsVoice("ja-JP-NanamiNeural", "ja-JP", "Female"),
        EdgeTtsVoice("ja-JP-KeitaNeural", "ja-JP", "Male"),
        EdgeTtsVoice("en-US-GuyNeural", "en-US", "Male"),
    )

    @Test
    fun searchesNamesLocaleAndLocalizedLanguageWithMultipleTerms() {
        assertEquals(listOf(voices[0]), filterReadAloudVoices(voices, " 日语  女声 ", LocaleStringsZhHans))
        assertEquals(listOf(voices[0]), filterReadAloudVoices(voices, "nAnAmI", LocaleStringsEn))
        assertEquals(listOf(voices[2]), filterReadAloudVoices(voices, "en-US", LocaleStringsEn))
        assertEquals(voices, filterReadAloudVoices(voices, "  ", LocaleStringsEn))
    }

    @Test
    fun maleSearchDoesNotMatchFemale() {
        assertEquals(listOf(voices[1]), filterReadAloudVoices(voices, "Japanese male", LocaleStringsEn))
    }
}
