package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatTimestampTest {
    @Test
    fun firstMessageAlwaysShowsTimestamp() {
        assertTrue(shouldShowChatTimestamp(timestamp = 1_000, previousTimestamp = null))
    }

    @Test
    fun timestampIsHiddenWithinTenMinutes() {
        assertFalse(shouldShowChatTimestamp(timestamp = 599_999, previousTimestamp = 0))
    }

    @Test
    fun timestampShowsAtTenMinutes() {
        assertTrue(shouldShowChatTimestamp(timestamp = 600_000, previousTimestamp = 0))
    }

    @Test
    fun outOfOrderTimestampStartsANewGroup() {
        assertTrue(shouldShowChatTimestamp(timestamp = 1_000, previousTimestamp = 2_000))
    }

    @Test
    fun tenMinutesIsMeasuredFromLastDisplayedTimestamp() {
        assertEquals(
            listOf(true, false, true, false, true),
            chatTimestampVisibility(
                listOf(
                    0,
                    9 * 60 * 1_000L,
                    10 * 60 * 1_000L,
                    19 * 60 * 1_000L,
                    20 * 60 * 1_000L,
                ),
            ),
        )
    }
}
