package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class OscListenerTest {
    @Test
    fun parsesMuteSelfBooleanPackets() {
        assertEquals(true, parseMuteSelfOsc(oscPacket("/avatar/parameters/MuteSelf", ",T")))
        assertEquals(false, parseMuteSelfOsc(oscPacket("/avatar/parameters/MuteSelf", ",F")))
    }

    @Test
    fun acceptsNumericMuteSelfAndIgnoresOtherMessages() {
        assertEquals(
            true,
            parseMuteSelfOsc(
                oscPacket("/avatar/parameters/muteself", ",i", byteArrayOf(0, 0, 0, 1))
            ),
        )
        assertEquals(
            false,
            parseMuteSelfOsc(
                oscPacket("/avatar/parameters/MuteSelf", ",i", byteArrayOf(0, 0, 0, 0))
            ),
        )
        assertNull(parseMuteSelfOsc(oscPacket("/avatar/parameters/Voice", ",T")))
        assertNull(parseMuteSelfOsc(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun retriesListenerAfterReceiveFailure() = runBlocking {
        var attempts = 0
        val failures = mutableListOf<String?>()
        var retryStarted = false
        val events =
            flow<Boolean> {
                    attempts++
                    if (attempts == 1) error("recvfrom failed: ECONNABORTED")
                    emit(false)
                }
                .retryListenerFailures(
                    retryDelayMillis = 0,
                    onFailure = { failures += it.message },
                    onRetry = { retryStarted = true },
                )
                .toList()

        assertEquals(listOf(false), events)
        assertEquals(2, attempts)
        assertEquals("recvfrom failed: ECONNABORTED", failures.single())
        assertEquals(true, retryStarted)
    }

    @Test
    fun stopsRetryingWhenListenerIsCancelled() = runBlocking {
        var attempts = 0
        withTimeoutOrNull(50) {
            flow<Boolean> {
                    attempts++
                    error("receive failed")
                }
                .retryListenerFailures(retryDelayMillis = 10_000, onFailure = {}, onRetry = {})
                .collect {}
        }

        assertEquals(1, attempts)
    }

    private fun oscPacket(
        address: String,
        tags: String,
        value: ByteArray = byteArrayOf(),
    ): ByteArray = padded(address) + padded(tags) + value

    private fun padded(value: String): ByteArray {
        val bytes = value.encodeToByteArray() + 0
        return bytes + ByteArray((4 - bytes.size % 4) % 4)
    }
}
