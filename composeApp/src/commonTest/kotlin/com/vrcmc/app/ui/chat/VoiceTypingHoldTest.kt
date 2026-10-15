package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceTypingHoldTest {
    @Test
    fun typingStaysOnAfterSpeechEndsUntilDispatchFinishes() {
        val hold = VoiceTypingHold()
        assertTrue(
            hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = true)
        )

        hold.onSpeechEnded()
        // Recognition and translation still run after the voice goes quiet.
        assertTrue(
            hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = false)
        )

        hold.onDispatchFinished()
        assertFalse(
            hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = false)
        )
    }

    @Test
    fun overlappingSegmentsKeepTypingOnUntilEveryDispatchFinishes() {
        val hold = VoiceTypingHold()
        hold.onSpeechEnded()
        hold.onSpeechEnded()

        hold.onDispatchFinished()
        assertTrue(hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = false))

        hold.onDispatchFinished()
        assertFalse(hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = false))
    }

    @Test
    fun dispatchFinishWithoutPendingSegmentStaysIdle() {
        val hold = VoiceTypingHold()
        hold.onDispatchFinished()
        assertFalse(hold.active)
        assertFalse(hold.typingActive(showTypingStatus = true, voiceRecording = true, voiceSpeaking = false))
    }

    @Test
    fun typingRequiresEnabledStatusAndRecording() {
        val hold = VoiceTypingHold()
        hold.onSpeechEnded()
        assertFalse(hold.typingActive(showTypingStatus = false, voiceRecording = true, voiceSpeaking = false))
        assertFalse(hold.typingActive(showTypingStatus = true, voiceRecording = false, voiceSpeaking = false))
    }

    @Test
    fun resetClearsPendingSegments() {
        val hold = VoiceTypingHold()
        hold.onSpeechEnded()
        hold.reset()
        assertFalse(hold.active)
    }
}
