package com.vrcmc.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object UnsupportedLocalSpeechRecognizer : LocalSpeechRecognizer {
    override val supported = false
    private val missing = MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing).asStateFlow()
    override fun status(model: LocalWhisperModel) = missing
    override suspend fun downloadModel(model: LocalWhisperModel) = false
    override suspend fun prepare(model: LocalWhisperModel) = false
    override suspend fun transcribe(model: LocalWhisperModel, wav: ByteArray, language: String) =
        VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
    override suspend fun release() = Unit
}
