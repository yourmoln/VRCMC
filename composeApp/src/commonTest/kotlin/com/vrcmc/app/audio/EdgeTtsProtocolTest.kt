package com.vrcmc.app

import kotlin.test.*

class EdgeTtsProtocolTest {
    @Test
    fun gecV2MatchesReferenceDigests() {
        // Fixed vectors checked against the independent edge-tts v2 implementation.
        assertEquals("52BC7A17B50DFEC9-28C4394775C8751CD2F6859C030C06DB1489ACAB3B3A7651831FC26895177ED6", edgeTtsGec(0))
        assertEquals("2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2", edgeTtsGec(1_700_000_000_000))
        assertEquals("0919602B5750900A-EC9DD394F23CB5635CF62A0E8E24136DCD32B26D10C543B10082E482D7516397", edgeTtsGec(1_700_000_100_000))
        assertEquals("6E827916EE7ED2C3-2890DCA2DD77037E97401E1154545B6FA4F0EEA7152C9439EA74DAD02C362321", edgeTtsGec(1_793_948_400_000))
    }

    @Test
    fun gecUsesWindowsEpochAndFiveMinuteBuckets() {
        assertEquals(edgeTtsGec(1_700_000_000_000), edgeTtsGec(1_700_000_099_999))
        assertNotEquals(edgeTtsGec(1_700_000_000_000), edgeTtsGec(1_700_000_100_000))
        assertEquals(edgeTtsGec(1_700_000_100_000), edgeTtsGec(1_700_000_399_999))
        assertNotEquals(edgeTtsGec(1_700_000_399_999), edgeTtsGec(1_700_000_400_000))
        assertTrue(edgeTtsGec(1_700_000_000_000).matches(Regex("[0-9A-F]{16}-[0-9A-F]{64}")))
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
        assertFailsWith<IllegalArgumentException> { edgeTtsAudioPayload(ByteArray(edgeTtsMaxFrameBytes + 1)) }
    }

    private fun frame(headers: String, payload: ByteArray): ByteArray {
        val bytes = headers.encodeToByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes + payload
    }
}
