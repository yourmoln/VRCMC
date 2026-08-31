package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JapaneseRomanizationDesktopTest {
    @Test
    fun greetingUsesSpokenParticlePronunciation() {
        assertEquals(
            listOf(JapaneseRubySegment("こんにちは", "konnichiwa")),
            platformJapaneseRubySegments("こんにちは"),
        )
    }

    @Test
    fun longVowelsAndVolitionalAuxiliaryUseMacrons() {
        assertEquals(
            listOf(
                JapaneseRubySegment("東京", "tōkyō"),
                JapaneseRubySegment("で", "de"),
                JapaneseRubySegment("遊ぼう", "asobō"),
            ),
            platformJapaneseRubySegments("東京で遊ぼう"),
        )
    }

    @Test
    fun connectiveParticleAndTrailingPunctuationStayWithTheirWords() {
        assertEquals(
            listOf(
                JapaneseRubySegment("写真", "shashin"),
                JapaneseRubySegment("を", "o"),
                JapaneseRubySegment("撮って", "totte"),
                JapaneseRubySegment("も", "mo"),
                JapaneseRubySegment("いい？", "ii"),
            ),
            platformJapaneseRubySegments("写真を撮ってもいい？"),
        )
    }

    @Test
    fun lexicalOuIsNotCollapsedIntoALongVowel() {
        assertEquals(
            listOf(JapaneseRubySegment("思う", "omou")),
            platformJapaneseRubySegments("思う"),
        )
    }

    @Test
    fun numbersRemainUnannotated() {
        assertEquals(
            listOf(
                JapaneseRubySegment("123"),
                JapaneseRubySegment("円", "en"),
            ),
            platformJapaneseRubySegments("123円"),
        )
    }

    @Test
    fun mixedTextAndLineBreaksReconstructExactly() {
        val input = "VRChatで会おう！\n龘XYZ"
        val segments = platformJapaneseRubySegments(input)

        assertEquals(input, segments.joinToString(separator = "", transform = JapaneseRubySegment::surface))
        assertNull(segments.first { it.surface == "VRChat" }.romaji)
        assertNull(segments.last { "XYZ" in it.surface }.romaji)
    }
}
