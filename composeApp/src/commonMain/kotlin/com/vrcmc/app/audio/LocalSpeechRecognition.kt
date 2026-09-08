package com.vrcmc.app

import kotlinx.coroutines.flow.StateFlow

enum class VoiceInputProvider { QWEN, LOCAL_WHISPER }

internal sealed interface LocalSpeechModelStatus {
    data object Missing : LocalSpeechModelStatus
    data object Downloaded : LocalSpeechModelStatus
    data object Preparing : LocalSpeechModelStatus
    data class Downloading(val received: Long, val total: Long) : LocalSpeechModelStatus
    data object Ready : LocalSpeechModelStatus
    data class Failed(val detail: String) : LocalSpeechModelStatus
}

internal interface LocalSpeechRecognizer {
    val supported: Boolean
    val status: StateFlow<LocalSpeechModelStatus>
    // Only an explicit user action may call this; prepare() only reads the local cache.
    suspend fun downloadModel(): Boolean
    suspend fun prepare(): Boolean
    suspend fun transcribe(wav: ByteArray, language: String): VoiceTranscriptionResult
    suspend fun release()
}

internal expect val localSpeechRecognizer: LocalSpeechRecognizer

internal fun VoiceInputConfig.hasServiceConfiguration(): Boolean = when (provider) {
    VoiceInputProvider.QWEN -> apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
    VoiceInputProvider.LOCAL_WHISPER -> localSpeechRecognizer.supported
}

internal fun voiceInputReadinessFailure(config: VoiceInputConfig): VoiceTranscriptionResult.Failure? =
    when {
        config.provider == VoiceInputProvider.LOCAL_WHISPER && !localSpeechRecognizer.supported ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
        config.provider == VoiceInputProvider.LOCAL_WHISPER &&
            localSpeechRecognizer.status.value != LocalSpeechModelStatus.Ready ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_MODEL_NOT_READY)
        config.provider == VoiceInputProvider.QWEN && config.apiKey.isBlank() ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.API_KEY_REQUIRED)
        config.provider == VoiceInputProvider.QWEN && config.baseUrl.isBlank() ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.BASE_URL_REQUIRED)
        config.provider == VoiceInputProvider.QWEN && config.model.isBlank() ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.MODEL_REQUIRED)
        config.provider == VoiceInputProvider.QWEN && !isSupportedHttpEndpoint(config.baseUrl) ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.INVALID_BASE_URL)
        else -> null
    }

internal suspend fun transcribeVoiceAudio(
    config: VoiceInputConfig,
    wav: ByteArray,
    onApiFailure: (String) -> Unit = {},
    localRecognizer: LocalSpeechRecognizer = localSpeechRecognizer,
): VoiceTranscriptionResult = when (config.provider) {
    VoiceInputProvider.QWEN -> transcribeQwenAudio(config, wav, onApiFailure)
    VoiceInputProvider.LOCAL_WHISPER -> localRecognizer.transcribe(wav, config.language)
}
