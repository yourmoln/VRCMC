package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OscValidationTest {
    @Test
    fun acceptsChatboxLimits() {
        assertTrue(isValidChatboxText("x".repeat(maxChatboxCharacters)))
        assertTrue(isValidChatboxText((1..maxChatboxLines).joinToString("\n") { "line" }))
    }

    @Test
    fun rejectsOversizedOrEmptyMessages() {
        assertFalse(isValidChatboxText("x".repeat(maxChatboxCharacters + 1)))
        assertFalse(isValidChatboxText((1..maxChatboxLines + 1).joinToString("\n") { "line" }))
        assertFalse(isValidChatboxText(""))
    }

    @Test
    fun inputLimitAccountsForTranslationOutputs() {
        assertEquals(144, chatboxInputCharacterLimit(false, 2))
        assertEquals(72, chatboxInputCharacterLimit(true, 1))
        assertEquals(48, chatboxInputCharacterLimit(true, 2))
    }
}
