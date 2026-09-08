package com.vrcmc.app

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** WASAPI shared-mode loopback of the default multimedia render endpoint (not a microphone). */
internal class WindowsLoopbackCapture : SystemAudioCapture {
    override suspend fun capture(sampleRate: Int, onPcm: suspend (ByteArray) -> Unit) {
        // COM initialization, all interface calls and release must stay on the same OS thread.
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "VRCMC-WASAPI").apply { isDaemon = true }
        }.asCoroutineDispatcher().use { dispatcher ->
            withContext(dispatcher) { captureOnComThread(sampleRate, onPcm) }
        }
    }

    private suspend fun captureOnComThread(sampleRate: Int, onPcm: suspend (ByteArray) -> Unit) {
        require(sampleRate in 8_000..48_000)
        val ole = Ole32.INSTANCE
        checkHr(ole.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).toInt(), "CoInitializeEx")
        var enumerator: AudioComObject? = null
        var device: AudioComObject? = null
        var client: AudioComObject? = null
        var capture: AudioComObject? = null
        var volume: AudioComObject? = null
        var started = false
        try {
            val result = PointerByReference()
            checkHr(ole.CoCreateInstance(
                Guid.GUID("{BCDE0395-E52F-467C-8E3D-C4579291692E}"), null,
                WTypes.CLSCTX_ALL,
                Guid.GUID("{A95664D2-9614-4F35-A746-DE8DB63617E6}"), result,
            ).toInt(), "Create device enumerator")
            enumerator = AudioComObject(result.value)
            // IMMDeviceEnumerator::GetDefaultAudioEndpoint(eRender, eMultimedia).
            device = enumerator.getInterface(4, 0, 1)
            // Loopback taps audio before endpoint mute/volume. Explicitly honor an inaudible output.
            volume = device.getInterface(3,
                Guid.GUID("{5CDF2C82-841E-4546-9722-0CF74078229A}"), WTypes.CLSCTX_ALL, null,
            )
            // IMMDevice::Activate(IAudioClient).
            client = device.getInterface(3,
                Guid.GUID("{1CB9AD4C-DBFA-4C32-B178-C2F568A703B2}"), WTypes.CLSCTX_ALL, null,
            )
            Memory(18).use { format ->
                format.clear()
                format.setShort(0, 1) // WAVE_FORMAT_PCM
                format.setShort(2, 1) // mono
                format.setInt(4, sampleRate)
                format.setInt(8, sampleRate * 2)
                format.setShort(12, 2)
                format.setShort(14, 16)
                // Let the Windows audio engine downmix/resample with its high-quality converter.
                client.call(3, 0, 0x80000000.toInt() or 0x08000000 or 0x00020000,
                    1_000_000L, 0L, format, null)
            }
            capture = client.getInterface(14, Guid.GUID("{C8ADBD64-E71E-48A0-A4DE-185C395CD317}"))
            client.call(10) // IAudioClient::Start
            started = true
            val packetFrames = IntByReference()
            val data = PointerByReference()
            val frames = IntByReference()
            val flags = IntByReference()
            val muted = IntByReference()
            val level = FloatByReference()
            var lastPacketNanos = System.nanoTime()
            while (true) {
                currentCoroutineContext().ensureActive()
                volume.call(15, muted) // IAudioEndpointVolume::GetMute
                volume.call(9, level) // GetMasterVolumeLevelScalar
                capture.call(5, packetFrames) // IAudioCaptureClient::GetNextPacketSize
                if (packetFrames.value > 0) {
                    capture.call(3, data, frames, flags, null, null)
                    val pcm = try {
                        readLoopbackPacket(data.value, frames.value, flags.value, muted.value != 0, level.value)
                    } finally {
                        capture.call(4, frames.value) // ReleaseBuffer before any suspending callback.
                    }
                    onPcm(pcm)
                    lastPacketNanos = System.nanoTime()
                } else {
                    // Loopback stops delivering packets when no app is playing. Feed silence so
                    // the final spoken sentence is still recognized when playback ends/pauses.
                    val now = System.nanoTime()
                    if (now - lastPacketNanos >= 30_000_000L) {
                        onPcm(ByteArray(sampleRate * 30 / 1_000 * 2))
                        lastPacketNanos = now
                    }
                    delay(10)
                }
            }
        } finally {
            if (started) runCatching { client?.call(11) }
            capture?.close()
            volume?.close()
            client?.close()
            device?.close()
            enumerator?.close()
            ole.CoUninitialize()
        }
    }
}

internal fun readLoopbackPacket(data: Pointer?, frames: Int, flags: Int, muted: Boolean, volume: Float): ByteArray =
    if (flags and 2 != 0 || muted || !volume.isFinite() || volume <= 0.0001f) {
        ByteArray(frames * 2)
    } else {
        checkNotNull(data).getByteArray(0, frames * 2)
    }

/** IUnknown vtable dispatch; only the documented WASAPI methods above are used. */
private class AudioComObject(private val pointer: Pointer) : AutoCloseable {
    private fun invoke(index: Int, vararg args: Any?): Int {
        val method = pointer.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)
        return Function.getFunction(method, Function.ALT_CONVENTION)
            .invokeInt(arrayOf(pointer, *args))
    }

    fun call(index: Int, vararg args: Any?) = checkHr(invoke(index, *args), "WASAPI method $index")

    fun getInterface(index: Int, vararg args: Any?): AudioComObject {
        val result = PointerByReference()
        call(index, *args, result)
        return AudioComObject(result.value ?: error("WASAPI returned an empty interface"))
    }

    override fun close() { invoke(2) } // IUnknown::Release returns a reference count, not HRESULT.
}

private fun checkHr(result: Int, operation: String) {
    check(result >= 0) { "$operation failed (HRESULT 0x${result.toUInt().toString(16)})" }
}
