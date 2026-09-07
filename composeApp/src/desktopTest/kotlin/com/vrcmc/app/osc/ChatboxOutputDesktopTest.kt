package com.vrcmc.app

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class ChatboxOutputDesktopTest {
    @Test fun oversizedPayloadReachesOscReceiverWithoutTruncation() = runBlocking {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { receiver ->
            receiver.soTimeout = 2_000
            val device = Device("127.0.0.1", receivePort = receiver.localPort)
            val text = (1..10).joinToString("\n") { "替换后的长消息".repeat(4) }
            assertEquals(ChatboxSendResult.SENT_OVER_LIMIT, sendChatboxOutput(device, text))
            val received = DatagramPacket(ByteArray(4096), 4096)
            receiver.receive(received)
            assertContentEquals(chatboxPacket(text), received.data.copyOf(received.length))
        }
    }
}
