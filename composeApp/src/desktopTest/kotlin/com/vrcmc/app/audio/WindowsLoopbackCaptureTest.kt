package com.vrcmc.app

import com.sun.jna.Platform
import com.sun.jna.Memory
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue

class WindowsLoopbackCaptureTest {
    @Test
    fun silentOrMutedPacketsNeverExposeDriverAudio() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        Memory(4).use { memory ->
            memory.write(0, pcm, 0, pcm.size)
            assertContentEquals(pcm, readLoopbackPacket(memory, 2, 0, false, 0.5f))
            assertContentEquals(ByteArray(4), readLoopbackPacket(memory, 2, 0, true, 0.5f))
            assertContentEquals(ByteArray(4), readLoopbackPacket(memory, 2, 0, false, 0f))
            // A silent WASAPI packet is allowed to have no readable data pointer at all.
            assertContentEquals(ByteArray(4), readLoopbackPacket(null, 2, 2, false, 0.5f))
        }
    }

    @Test
    fun livePlaybackTriggersSpeechRequestsAndSilenceDoesNot() = runBlocking {
        assumeTrue(Platform.isWindows() && System.getenv("VRCMC_LOOPBACK_SMOKE") == "1")
        val provider = providerById("microsoft_edge_web")
        val settings = SystemAudioListeningSettings(VoiceInputConfig(), provider, defaultProviderConfig(provider), listOf("English"))
        var requests = 0
        val captured = CompletableDeferred<Unit>()
        val native = WindowsLoopbackCapture()
        SileroSpeechDetector().use { detector ->
            val recording = launch {
                SystemAudioListeningSession(
                    transcribe = { _, wav ->
                        requests++
                        assertTrue(wav.size <= 44 + 16_000 * 2 * 6)
                        VoiceTranscriptionResult.Success("test speech")
                    },
                    translate = { _, _, _, text -> TranslationResult.Success(text) },
                ).run(settings, SystemAudioCapture { rate, emit ->
                    native.capture(rate) { captured.complete(Unit); emit(it) }
                }, LocaleStringsEn, {}, {}, detector::isSpeech)
            }
            try {
                withTimeout(5_000) { captured.await() }
                delay(2_000)
                assertEquals(0, requests, "Idle playback must not call ASR")
                launch(Dispatchers.IO) {
                    val format = AudioFormat(16_000f, 16, 1, true, false)
                    AudioSystem.getSourceDataLine(format).use { line ->
                        line.open(format)
                        line.start()
                        val pcm = speechFixture()
                        line.write(pcm, 0, pcm.size)
                        line.drain()
                    }
                }.join()
                delay(1_500)
                assertTrue(requests > 0, "Spoken media must pass actual WASAPI and VAD")
                val afterSpeech = requests
                delay(2_000)
                assertEquals(afterSpeech, requests, "Silence after playback must not call ASR again")
            } finally { recording.cancelAndJoin() }
        }
    }

    @Test
    fun capturesPlaybackAndCanReopenAfterCancellation() = runBlocking {
        assumeTrue(Platform.isWindows() && System.getenv("VRCMC_LOOPBACK_SMOKE") == "1")
        repeat(2) {
            val firstPacket = CompletableDeferred<Unit>()
            var peakRms = 0.0
            val recording = launch {
                WindowsLoopbackCapture().capture(16_000) { pcm ->
                    firstPacket.complete(Unit)
                    var squareSum = 0.0
                    for (offset in pcm.indices step 2) {
                        val sample = ((pcm[offset].toInt() and 255) or (pcm[offset + 1].toInt() shl 8)).toShort() / 32768.0
                        squareSum += sample * sample
                    }
                    if (pcm.isNotEmpty()) peakRms = maxOf(peakRms, sqrt(squareSum / (pcm.size / 2)))
                }
            }
            try {
                withTimeout(5_000) { firstPacket.await() }
                val playback = launch(Dispatchers.IO) {
                    val format = AudioFormat(48_000f, 16, 1, true, false)
                    AudioSystem.getSourceDataLine(format).use { line ->
                        line.open(format)
                        line.start()
                        val pcm = tone(48_000, 800, amplitude = 0.06)
                        line.write(pcm, 0, pcm.size)
                        line.drain()
                    }
                }
                playback.join()
                delay(200)
            } finally { recording.cancelAndJoin() }
            assertTrue(peakRms > 0.001, "Expected the rendered tone in loopback, RMS=$peakRms")
        }
    }
}
