package com.vrcmc.app

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

private class DesktopAudioRecorder : AudioRecorder {
    private var line: TargetDataLine? = null
    private var worker: Thread? = null

    @Synchronized
    override fun start(sampleRate: Int, maxDurationSeconds: Int, microphoneId: String, onPcmData: (ByteArray) -> Unit, onStopped: () -> Unit, onError: (String) -> Unit) {
        if (worker != null) return
        try {
            val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            val target = if (microphoneId.isBlank()) {
                AudioSystem.getTargetDataLine(format)
            } else {
                val mixerInfo = AudioSystem.getMixerInfo().firstOrNull { it.name == microphoneId }
                mixerInfo?.let {
                    AudioSystem.getMixer(it)
                        .getLine(DataLine.Info(TargetDataLine::class.java, format)) as TargetDataLine
                }
                    ?: AudioSystem.getTargetDataLine(format)
            }
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

actual fun availableAudioInputDevices(): List<AudioInputDevice> =
    AudioSystem.getMixerInfo().mapNotNull { info ->
        runCatching {
            val mixer = AudioSystem.getMixer(info)
            if (mixer.targetLineInfo.any { it.lineClass == TargetDataLine::class.java || TargetDataLine::class.java.isAssignableFrom(it.lineClass) })
                AudioInputDevice(info.name, info.name)
            else null
        }.getOrNull()
    }.distinctBy { it.id }

actual fun isDesktopAudioPlatform(): Boolean = true
