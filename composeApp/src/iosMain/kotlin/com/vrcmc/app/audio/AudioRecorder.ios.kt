package com.vrcmc.app

private class IosAudioRecorder : AudioRecorder {
    override fun start(sampleRate: Int, maxDurationSeconds: Int, microphoneId: String, onPcmData: (ByteArray) -> Unit, onStopped: () -> Unit, onError: (String) -> Unit) =
        onError("iOS 暂不支持应用内麦克风录音")
    override fun stop() = Unit
    override fun release() = Unit
}

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()

actual fun availableAudioInputDevices(): List<AudioInputDevice> = emptyList()

actual fun isDesktopAudioPlatform(): Boolean = false
