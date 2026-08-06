package com.vrcmc.app

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine

private class DesktopAudioRecorder : AudioRecorder {
    private var line: TargetDataLine? = null
    private var worker: Thread? = null

    @Synchronized
    override fun start(sampleRate: Int, maxDurationSeconds: Int, onPcmData: (ByteArray) -> Unit, onStopped: () -> Unit, onError: (String) -> Unit) {
        if (worker != null) return
        try {
            val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val target = AudioSystem.getTargetDataLine(format)
            target.open(format)
            target.start()
            line = target
            worker = Thread {
                val buffer = ByteArray(sampleRate / 5)
                val maxBytes = sampleRate * 2 * maxDurationSeconds.coerceIn(1, 60)
                var capturedBytes = 0
                try {
                    while (line === target && capturedBytes < maxBytes) {
                        val count = target.read(buffer, 0, buffer.size)
                        if (count > 0) {
                            capturedBytes += count
                            onPcmData(buffer.copyOf(count))
                        }
                    }
                    onStopped()
                } catch (error: Throwable) {
                    onError(error.message ?: "无法读取麦克风")
                } finally {
                    target.stop()
                    target.close()
                    synchronized(this) {
                        if (line === target) line = null
                        worker = null
                    }
                }
            }.also { it.isDaemon = true; it.start() }
        } catch (error: Throwable) {
            onError(error.message ?: "无法打开麦克风")
        }
    }

    @Synchronized override fun stop() {
        line = null
    }

    override fun release() = stop()
}

actual fun createAudioRecorder(): AudioRecorder = DesktopAudioRecorder()
