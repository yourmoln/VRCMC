package com.vrcmc.app

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private fun Mixer.Info.deviceId(): String = listOf(name, vendor, description, version).joinToString("|")

internal actual fun availableAudioOutputDevices(): List<AudioOutputDevice> =
    AudioSystem.getMixerInfo().mapNotNull { info ->
        val supported = AudioSystem.getMixer(info).sourceLineInfo.any {
            SourceDataLine::class.java.isAssignableFrom(it.lineClass)
        }
        if (supported) AudioOutputDevice(info.deviceId(), info.name) else null
    }.distinctBy { it.id }

internal actual fun supportsAudioOutputSelection(): Boolean = true
internal actual fun createSpeechAudioPlayer(): SpeechAudioPlayer = DesktopSpeechAudioPlayer()

private class DesktopSpeechAudioPlayer : SpeechAudioPlayer {
    @Volatile private var line: SourceDataLine? = null

    override suspend fun play(mp3: ByteArray, outputDeviceId: String) = withContext(Dispatchers.IO) {
        val stream = Bitstream(ByteArrayInputStream(mp3))
        val decoder = Decoder()
        var target: SourceDataLine? = null
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val header = stream.readFrame() ?: break
                val samples = decoder.decodeFrame(header, stream) as SampleBuffer
                val output = target ?: run {
                    val format = AudioFormat(samples.sampleFrequency.toFloat(), 16, samples.channelCount, true, false)
                    val info = DataLine.Info(SourceDataLine::class.java, format)
                    val selected = if (outputDeviceId.isBlank()) AudioSystem.getLine(info) as SourceDataLine
                    else {
                        val mixer = AudioSystem.getMixerInfo().firstOrNull { it.deviceId() == outputDeviceId }
                            ?: error("Selected playback device is unavailable")
                        AudioSystem.getMixer(mixer).getLine(info) as SourceDataLine
                    }
                    target = selected
                    selected.open(format)
                    currentCoroutineContext().ensureActive()
                    line = selected
                    selected.start()
                    selected
                }
                val pcm = ByteArray(samples.bufferLength * 2)
                for (index in 0 until samples.bufferLength) {
                    pcm[index * 2] = samples.buffer[index].toByte()
                    pcm[index * 2 + 1] = (samples.buffer[index].toInt() shr 8).toByte()
                }
                var offset = 0
                while (offset < pcm.size) {
                    currentCoroutineContext().ensureActive()
                    // Only write available space so cancellation never waits for a blocking drain.
                    val count = minOf(output.available(), pcm.size - offset)
                    if (count > 0) offset += output.write(pcm, offset, count) else delay(10)
                }
                stream.closeFrame()
            }
            val output = checkNotNull(target) { "No MP3 frames decoded" }
            while (output.isOpen && output.available() < output.bufferSize) {
                currentCoroutineContext().ensureActive()
                delay(10)
            }
        } finally {
            target?.let {
                it.stop()
                it.flush()
                it.close()
                if (line === it) line = null
            }
            stream.close()
        }
    }

    override fun stop() {
        line?.let { output ->
            line = null
            output.stop()
            output.flush()
            output.close()
        }
    }
}
