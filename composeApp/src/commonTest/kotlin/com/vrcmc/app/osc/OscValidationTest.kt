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

    @Test
    fun livePreviewWaitIgnoresItsOwnUpdates() {
        assertEquals(0, liveInputPreviewDelayRemainingMillis(5_000, null, 10))
        assertEquals(6_000, liveInputPreviewDelayRemainingMillis(5_000, 1_000, 10))
        assertEquals(0, liveInputPreviewDelayRemainingMillis(11_000, 1_000, 10))
    }

    @Test
    fun typingPacketUsesVrchatTypingAddressAndTrueTag() {
        val packet = chatboxTypingPacket()
        val addressEnd = packet.indexOf(0)
        assertEquals("/chatbox/typing", packet.copyOf(addressEnd).decodeToString())
        val tagStart = addressEnd + 1
        assertEquals(",T", packet.copyOfRange(tagStart, tagStart + 2).decodeToString())
    }

    @Test
    fun typingPacketUsesFalseTagWhenTypingStops() {
        val packet = chatboxTypingPacket(false)
        val tagStart = packet.indexOf(0) + 1
        assertEquals(",F", packet.copyOfRange(tagStart, tagStart + 2).decodeToString())
    }

    @Test
    fun oscUpdatesHaveOneSecondCooldown() {
        assertEquals(0, oscCooldownRemainingMillis(1_000, null))
        assertEquals(1_000, oscCooldownRemainingMillis(1_000, 1_000))
        assertEquals(0, oscCooldownRemainingMillis(2_000, 1_000))
    }
}
