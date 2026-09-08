package com.vrcmc.app

import kotlin.test.*

class EdgeTtsProtocolTest {
    @Test
    fun gecUsesWindowsEpochAndFiveMinuteBuckets() {
        assertEquals("42301B335578FEFDAE2637DED1ABD614505D432559EC08032B82048483726AFF", edgeTtsGec(1_700_000_000_000))
        assertEquals(edgeTtsGec(1_700_000_000_000), edgeTtsGec(1_700_000_099_999))
        assertNotEquals(edgeTtsGec(1_700_000_000_000), edgeTtsGec(1_700_000_100_000))
        assertEquals(64, edgeTtsGec(1_700_000_000_000).length)
    }

    @Test
    fun ssmlEscapesTextAndAttributesAndRemovesControlCharacters() {
        val xml = edgeTtsSsml("<&\"'\u0000\n你好", "voice'><break/>")
        assertTrue(xml.contains("&lt;&amp;&quot;&apos; \n你好"))
        assertTrue(xml.contains("name='voice&apos;&gt;&lt;break/&gt;'"))
        assertFalse(xml.contains("<break/>"))
        assertFalse(xml.contains('\u0000'))
    }

    @Test
    fun extractsOnlyAudioFramesWithBigEndianHeaderLength() {
        val payload = byteArrayOf(0, 1, -1, 13, 10)
        assertContentEquals(payload, edgeTtsAudioPayload(frame("X-Padding:${"a".repeat(260)}\r\nPath:audio\r\n", payload)))
        assertNull(edgeTtsAudioPayload(frame("Path:audio.metadata\r\n", payload)))
        assertContentEquals(byteArrayOf(), edgeTtsAudioPayload(frame("Path:audio\r\n", byteArrayOf())))
        assertFailsWith<IllegalArgumentException> { edgeTtsAudioPayload(byteArrayOf(0)) }
        assertFailsWith<IllegalArgumentException> { edgeTtsAudioPayload(byteArrayOf(1, 0, 0)) }
    }

    private fun frame(headers: String, payload: ByteArray): ByteArray {
        val bytes = headers.encodeToByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes + payload
    }
}
