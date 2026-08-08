package com.vrcmc.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

private class AndroidAudioRecorder : AudioRecorder {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    @Synchronized
    override fun start(sampleRate: Int, maxDurationSeconds: Int, microphoneId: String, onPcmData: (ByteArray) -> Unit, onStopped: () -> Unit, onError: (String) -> Unit) {
        if (worker != null) return
        val context = audioApplicationContext()
        if (context == null || ContextCompat.checkSelfPermission(context, "android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            onError("请先授予麦克风权限")
            return
        }
        try {
            val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audio = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, (min * 2).coerceAtLeast(2048))
            audio.startRecording()
            recorder = audio
            worker = Thread {
                val buffer = ByteArray((sampleRate / 5).coerceAtLeast(1024))
                val maxBytes = sampleRate * 2 * maxDurationSeconds.coerceIn(1, 60)
                var capturedBytes = 0
                try {
                    while (recorder === audio && capturedBytes < maxBytes) {
                        val count = audio.read(buffer, 0, buffer.size)
                        if (count > 0) {
                            capturedBytes += count
                            onPcmData(buffer.copyOf(count))
                        }
                    }
                    onStopped()
                } catch (error: Throwable) {
                    onError(error.message ?: "无法读取麦克风")
                } finally {
                    runCatching { audio.stop() }
                    audio.release()
                    synchronized(this) {
                        if (recorder === audio) recorder = null
                        worker = null
                    }
                }
            }.also { it.isDaemon = true; it.start() }
        } catch (error: Throwable) {
            onError(error.message ?: "无法打开麦克风")
        }
    }

    @Synchronized override fun stop() { recorder = null }
    override fun release() = stop()
}

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()

actual fun availableAudioInputDevices(): List<AudioInputDevice> = emptyList()

actual fun isDesktopAudioPlatform(): Boolean = false
