package com.vrcmc.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

internal actual fun availableAudioOutputDevices(): List<AudioOutputDevice> = emptyList()
internal actual fun supportsAudioOutputSelection(): Boolean = false
internal actual fun createSpeechAudioPlayer(): SpeechAudioPlayer = IosSpeechAudioPlayer()

@OptIn(ExperimentalForeignApi::class)
private class IosSpeechAudioPlayer : SpeechAudioPlayer {
    private var player: AVAudioPlayer? = null

    override suspend fun play(mp3: ByteArray, outputDeviceId: String): Unit = withContext(Dispatchers.Main) {
        require(mp3.isNotEmpty())
        val data = mp3.usePinned { NSData.create(bytes = it.addressOf(0), length = mp3.size.toULong()) }
        val current = AVAudioPlayer(data = data, error = null)
        player = current
        try {
            check(current.prepareToPlay() && current.play()) { "Audio playback failed" }
            while (current.playing) delay(25)
        } finally {
            current.stop()
            if (player === current) player = null
        }
    }

    override fun stop() {
        player?.stop()
    }
}
