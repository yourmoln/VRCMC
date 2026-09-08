package com.vrcmc.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object UnsupportedLocalSpeechRecognizer : LocalSpeechRecognizer {
    override val supported = false
    override val status = MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing).asStateFlow()
    override suspend fun downloadModel() = false
    override suspend fun prepare() = false
    override suspend fun transcribe(wav: ByteArray, language: String) =
        VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
    override suspend fun release() = Unit
}
