package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OscListenerTest {
    @Test
    fun parsesMuteSelfBooleanPackets() {
        assertEquals(true, parseMuteSelfOsc(oscPacket("/avatar/parameters/MuteSelf", ",T")))
        assertEquals(false, parseMuteSelfOsc(oscPacket("/avatar/parameters/MuteSelf", ",F")))
    }

    @Test
    fun acceptsNumericMuteSelfAndIgnoresOtherMessages() {
        assertEquals(true, parseMuteSelfOsc(oscPacket("/avatar/parameters/muteself", ",i", byteArrayOf(0, 0, 0, 1))))
        assertEquals(false, parseMuteSelfOsc(oscPacket("/avatar/parameters/MuteSelf", ",i", byteArrayOf(0, 0, 0, 0))))
        assertNull(parseMuteSelfOsc(oscPacket("/avatar/parameters/Voice", ",T")))
        assertNull(parseMuteSelfOsc(byteArrayOf(1, 2, 3)))
    }

    private fun oscPacket(address: String, tags: String, value: ByteArray = byteArrayOf()): ByteArray =
        padded(address) + padded(tags) + value

    private fun padded(value: String): ByteArray {
        val bytes = value.encodeToByteArray() + 0
        return bytes + ByteArray((4 - bytes.size % 4) % 4)
    }
}
