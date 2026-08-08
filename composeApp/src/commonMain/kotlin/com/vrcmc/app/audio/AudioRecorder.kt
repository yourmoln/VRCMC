package com.vrcmc.app

interface AudioRecorder {
    fun start(
        sampleRate: Int,
        maxDurationSeconds: Int,
        microphoneId: String = "",
        onPcmData: (ByteArray) -> Unit,
        onStopped: () -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()
    fun release()
}

expect fun createAudioRecorder(): AudioRecorder

expect fun requestAudioPermissionIfNeeded(onGranted: () -> Unit): Boolean

internal fun pcm16ToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val result = ByteArray(44 + pcm.size)
    fun putAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(result, offset)
    }
    fun putInt(offset: Int, value: Int) {
        result[offset] = value.toByte()
        result[offset + 1] = (value shr 8).toByte()
        result[offset + 2] = (value shr 16).toByte()
        result[offset + 3] = (value shr 24).toByte()
    }
    fun putShort(offset: Int, value: Int) {
        result[offset] = value.toByte()
        result[offset + 1] = (value shr 8).toByte()
    }
    putAscii(0, "RIFF")
    putInt(4, 36 + pcm.size)
    putAscii(8, "WAVE")
    putAscii(12, "fmt ")
    putInt(16, 16)
    putShort(20, 1)
    putShort(22, 1)
    putInt(24, sampleRate)
    putInt(28, sampleRate * 2)
    putShort(32, 2)
    putShort(34, 16)
    putAscii(36, "data")
    putInt(40, pcm.size)
    pcm.copyInto(result, 44)
    return result
}
