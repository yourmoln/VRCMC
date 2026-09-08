package com.vrcmc.app

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()
internal actual fun supportsAudioOutputSelection(): Boolean = false
internal actual fun createSpeechAudioPlayer(): SpeechAudioPlayer = AndroidSpeechAudioPlayer()

private class AndroidSpeechAudioPlayer : SpeechAudioPlayer {
    private var player: MediaPlayer? = null

    override suspend fun play(mp3: ByteArray, outputDeviceId: String): Unit = withContext(Dispatchers.IO) {
        val context = checkNotNull(audioApplicationContext()) { "Audio context is not initialized" }
        val audioFile = File.createTempFile("vrcmc-tts-", ".mp3", context.cacheDir)
        try {
            audioFile.writeBytes(mp3)
            withContext(Dispatchers.Main) {
                val current = MediaPlayer()
                player = current
                try {
                    current.setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    // A regular seekable descriptor avoids vendor-specific MediaDataSource bridges.
                    FileInputStream(audioFile).use { source ->
                        current.setDataSource(source.fd, 0, mp3.size.toLong())
                    }
                    suspendCancellableCoroutine<Unit> { continuation ->
                        current.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                        current.setOnErrorListener { _, what, extra ->
                            if (continuation.isActive) continuation.resumeWithException(
                                SpeechAudioPlaybackException(what, extra))
                            true
                        }
                        current.setOnPreparedListener {
                            if (continuation.isActive) {
                                try {
                                    it.start()
                                } catch (error: Exception) {
                                    continuation.resumeWithException(error)
                                }
                            }
                        }
                        current.prepareAsync()
                    }
                } finally {
                    current.release()
                    if (player === current) player = null
                }
            }
        } finally {
            // This also runs after cancellation or a decoder error, on the I/O dispatcher.
            audioFile.delete()
        }
    }

    override fun stop() {
        player?.let { runCatching { it.pause() } }
    }
}
