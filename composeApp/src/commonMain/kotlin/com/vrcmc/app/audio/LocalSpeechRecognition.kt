package com.vrcmc.app

import kotlinx.coroutines.flow.StateFlow

enum class VoiceInputProvider {
    QWEN,
    LOCAL_MODEL,
    ;

    companion object {
        fun fromStoredName(value: String?): VoiceInputProvider = when (value) {
            LOCAL_MODEL.name, "LOCAL_WHISPER" -> LOCAL_MODEL
            QWEN.name -> QWEN
            else -> QWEN
        }
    }
}

enum class LocalSpeechModel(
    val serializedName: String,
    val displayName: String,
    val downloadSizeMb: Int,
) {
    SENSE_VOICE_SMALL_INT8("sense-voice-small-int8", "SenseVoice Small INT8", 240),
    FUN_ASR_NANO_2512_INT8("fun-asr-nano-2512-int8", "Fun-ASR-Nano 2512 INT8", 265),
    QWEN3_ASR_0_6B_INT8("qwen3-asr-0.6b-int8", "Qwen3-ASR 0.6B INT8", 987),
    ;

    val displayLabel: String get() = "$displayName · $downloadSizeMb MB"

    companion object {
        fun fromSerializedName(value: String?): LocalSpeechModel =
            entries.firstOrNull { it.serializedName == value } ?: SENSE_VOICE_SMALL_INT8
    }
}

internal sealed interface LocalSpeechModelStatus {
    data object Missing : LocalSpeechModelStatus
    data object Downloaded : LocalSpeechModelStatus
    data object Preparing : LocalSpeechModelStatus
    data object Deleting : LocalSpeechModelStatus
    data class Downloading(val received: Long, val total: Long) : LocalSpeechModelStatus
    data object Ready : LocalSpeechModelStatus
    data class Failed(val detail: String) : LocalSpeechModelStatus
}

internal interface LocalSpeechRecognizer {
    val supported: Boolean
    val supportedModels: Set<LocalSpeechModel>
    fun status(model: LocalSpeechModel): StateFlow<LocalSpeechModelStatus>
    // Only an explicit user action may call this; prepare() only reads the local cache.
    suspend fun downloadModel(model: LocalSpeechModel): Boolean
    suspend fun deleteModel(model: LocalSpeechModel): Boolean
    suspend fun prepare(model: LocalSpeechModel): Boolean
    suspend fun transcribe(
        model: LocalSpeechModel,
        wav: ByteArray,
        language: String,
    ): VoiceTranscriptionResult
    suspend fun release()
}

internal expect val localSpeechRecognizer: LocalSpeechRecognizer

internal fun VoiceInputConfig.hasServiceConfiguration(): Boolean = when (provider) {
    VoiceInputProvider.QWEN -> apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()
    VoiceInputProvider.LOCAL_MODEL -> localSpeechRecognizer.supported
}

internal fun voiceInputReadinessFailure(config: VoiceInputConfig): VoiceTranscriptionResult.Failure? =
    when {
        config.provider == VoiceInputProvider.LOCAL_MODEL && !localSpeechRecognizer.supported ->
            VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
        config.provider == VoiceInputProvider.LOCAL_MODEL &&
            localSpeechRecognizer.status(config.localSpeechModel).value != LocalSpeechModelStatus.Ready ->
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
    VoiceInputProvider.LOCAL_MODEL ->
        localRecognizer.transcribe(config.localSpeechModel, wav, config.language)
}
