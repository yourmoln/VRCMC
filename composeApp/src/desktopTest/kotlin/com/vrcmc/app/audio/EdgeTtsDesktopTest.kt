package com.vrcmc.app

import java.io.ByteArrayInputStream
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class EdgeTtsDesktopTest {
    @Test
    fun liveServiceReturnsDecodableMp3AndListsPlaybackDevices() = runBlocking {
        assumeTrue("Opt in with VRCMC_TTS_SMOKE=1", System.getenv("VRCMC_TTS_SMOKE") == "1")
        val voices = EdgeTtsService.voices()
        assertTrue(voices.any { it.shortName == ReadAloudConfig().voice })
        val audio = EdgeTtsService.synthesize("你好，这是朗读测试。", ReadAloudConfig().voice)
        val stream = Bitstream(ByteArrayInputStream(audio))
        try {
            val decoder = Decoder()
            var sampleCount = 0
            while (true) {
                val header = stream.readFrame() ?: break
                val samples = decoder.decodeFrame(header, stream) as SampleBuffer
                assertEquals(24_000, samples.sampleFrequency)
                assertEquals(1, samples.channelCount)
                sampleCount += samples.bufferLength
                stream.closeFrame()
            }
            assertTrue(sampleCount > 24_000)
            val devices = availableAudioOutputDevices()
            println("Edge TTS smoke: ${voices.size} voices, ${audio.size} MP3 bytes, $sampleCount samples, ${devices.size} playback devices")
        } finally { stream.close() }
    }
}
