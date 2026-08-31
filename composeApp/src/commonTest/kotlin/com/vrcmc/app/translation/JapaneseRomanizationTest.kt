package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JapaneseRomanizationTest {
    @Test
    fun rubyLineSplittingPreservesExplicitBlankLines() {
        val lines =
            splitJapaneseRubyLines(
                listOf(
                    JapaneseRubySegment("東京", "tōkyō"),
                    JapaneseRubySegment("\n\n"),
                    JapaneseRubySegment("写真", "shashin"),
                )
            )

        assertEquals(3, lines.size)
        assertEquals(listOf(JapaneseRubySegment("東京", "tōkyō")), lines[0])
        assertTrue(lines[1].isEmpty())
        assertEquals(listOf(JapaneseRubySegment("写真", "shashin")), lines[2])
    }

    @Test
    fun onlyCompletedJapaneseAssistantMessagesAreAnnotated() {
        val japanese = ChatMessage("こんにちは", MessageRole.ASSISTANT, language = "日本語")

        assertTrue(shouldShowJapaneseRomaji(japanese, enabled = true))
        assertFalse(shouldShowJapaneseRomaji(japanese, enabled = false))
        assertFalse(
            shouldShowJapaneseRomaji(
                japanese.copy(role = MessageRole.USER),
                enabled = true,
            )
        )
        assertFalse(
            shouldShowJapaneseRomaji(
                japanese.copy(language = "English"),
                enabled = true,
            )
        )
        assertFalse(
            shouldShowJapaneseRomaji(
                japanese.copy(isLoading = true),
                enabled = true,
            )
        )
    }
}
