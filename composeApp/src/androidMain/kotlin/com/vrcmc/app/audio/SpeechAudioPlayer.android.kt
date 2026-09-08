package com.vrcmc.app

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
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

    override suspend fun play(mp3: ByteArray, outputDeviceId: String): Unit = withContext(Dispatchers.Main) {
        val current = MediaPlayer()
        player = current
        try {
            current.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            current.setDataSource(object : MediaDataSource() {
                override fun getSize(): Long = mp3.size.toLong()
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position < 0 || position >= mp3.size) return -1
                    val count = minOf(size, mp3.size - position.toInt())
                    mp3.copyInto(buffer, offset, position.toInt(), position.toInt() + count)
                    return count
                }
                override fun close() = Unit
            })
            suspendCancellableCoroutine<Unit> { continuation ->
                current.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                current.setOnErrorListener { _, what, extra ->
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Audio playback failed: $what/$extra"))
                    true
                }
                current.setOnPreparedListener { if (continuation.isActive) it.start() }
                current.prepareAsync()
            }
        } finally {
            current.release()
            if (player === current) player = null
        }
    }

    override fun stop() {
        player?.let { runCatching { it.pause() } }
    }
}
