package com.vrcmc.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object UnsupportedLocalSpeechRecognizer : LocalSpeechRecognizer {
    override val supported = false
    override val supportedModels: Set<LocalSpeechModel> = emptySet()
    private val missing = MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing).asStateFlow()
    override fun status(model: LocalSpeechModel) = missing
    override suspend fun downloadModel(model: LocalSpeechModel) = false
    override suspend fun deleteModel(model: LocalSpeechModel) = false
    override suspend fun prepare(model: LocalSpeechModel) = false
    override suspend fun transcribe(model: LocalSpeechModel, wav: ByteArray, language: String) =
        VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
    override suspend fun release() = Unit
}
