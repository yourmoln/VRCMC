package com.vrcmc.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class OscListenerDesktopTest {
    @Test
    fun receivesMuteSelfFromConfiguredDevice() = runBlocking {
        val port = DatagramSocket(0).use { it.localPort }
        val event = async { withTimeout(2_000) { vrchatMuteSelfEvents("127.0.0.1", port).first() } }
        yield()
        delay(100)
        val packet = padded("/avatar/parameters/MuteSelf") + padded(",F")
        DatagramSocket().use { socket ->
            socket.send(
                DatagramPacket(packet, packet.size, InetAddress.getByName("127.0.0.1"), port)
            )
        }
        assertFalse(event.await())
    }

    private fun padded(value: String): ByteArray {
        val bytes = value.encodeToByteArray() + 0
        return bytes + ByteArray((4 - bytes.size % 4) % 4)
    }
}
