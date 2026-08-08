package com.vrcmc.app

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.*

data class StoredTranslationSettings(
    val providerId: String = "deepseek",
    val translate: Boolean = false,
    val sendOriginalBeforeTranslation: Boolean = true,
    val targetLanguages: List<String> = listOf("English"),
    val outputOrder: List<String> = targetLanguages + originalOutputKey,
    val lineBreakOutput: Boolean = true,
    val configs: Map<String, ProviderConfig> = emptyMap(),
    val voiceInput: VoiceInputConfig = VoiceInputConfig(),
    val interpretationVoiceInputEnabled: Boolean = false,
    val disableDynamicInputLimit: Boolean = false,
)

data class VoiceInputConfig(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val region: String = "singapore",
    val baseUrl: String = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
    val model: String = "qwen3-asr-flash-2026-02-10",
    val language: String = "ja",
    val microphoneId: String = "",
    val sampleRate: Int = 16_000,
    val maxSegmentSeconds: Int = 6,
    val tailSilenceMillis: Int = 700,
    val vadActivationMillis: Int = 200,
    val vadMinRms: Double = 0.012,
    val vadSpeechRatio: Double = 0.6,
    val partialIntervalMillis: Int = 500,
    val partialMinSpeechMillis: Int = 450,
    val timeoutSeconds: Int = 25,
)

data class ProviderSecrets(val apiKey: String = "", val customHeaders: String = "")

fun StoredTranslationSettings.toJson(): String =
    buildJsonObject {
            put("provider", providerId)
            put("translate", translate)
            put("sendOriginalBeforeTranslation", sendOriginalBeforeTranslation)
            putJsonArray("targetLanguages") { targetLanguages.take(2).forEach(::add) }
            putJsonArray("outputOrder") {
                normalizeOutputOrder(targetLanguages, outputOrder).forEach(::add)
            }
            put("lineBreakOutput", lineBreakOutput)
            putJsonObject("configs") {
                configs.forEach { (id, value) ->
                    putJsonObject(id) {
                        put("baseUrl", value.baseUrl)
                        put("model", value.model)
                        put("region", value.region)
                        put("timeout", value.timeoutSeconds)
                        put("streaming", value.streaming)
                        put("retries", value.retryCount)
                        put("fallbackModel", value.fallbackModel)
                        put("fallbackRetries", value.fallbackRetryCount)
                        put("fallbackEnabled", value.fallbackEnabled)
                    }
                }
            }
            putJsonObject("voiceInput") {
                put("enabled", voiceInput.enabled)
                put("region", voiceInput.region)
                put("baseUrl", voiceInput.baseUrl)
                put("model", voiceInput.model)
                put("language", voiceInput.language)
                put("microphoneId", voiceInput.microphoneId)
                put("sampleRate", voiceInput.sampleRate)
                put("maxSegmentSeconds", voiceInput.maxSegmentSeconds)
                put("tailSilenceMillis", voiceInput.tailSilenceMillis)
                put("vadActivationMillis", voiceInput.vadActivationMillis)
                put("vadMinRms", voiceInput.vadMinRms)
                put("vadSpeechRatio", voiceInput.vadSpeechRatio)
                put("partialIntervalMillis", voiceInput.partialIntervalMillis)
                put("partialMinSpeechMillis", voiceInput.partialMinSpeechMillis)
                put("timeout", voiceInput.timeoutSeconds)
            }
            put("interpretationVoiceInputEnabled", interpretationVoiceInputEnabled)
            put("disableDynamicInputLimit", disableDynamicInputLimit)
        }
        .toString()

fun Map<String, ProviderSecrets>.toSecretsJson(): String =
    buildJsonObject {
            forEach { (id, value) ->
                if (value.apiKey.isNotBlank() || value.customHeaders.isNotBlank()) {
                    putJsonObject(id) {
                        put("apiKey", value.apiKey)
                        put("headers", value.customHeaders)
                    }
                }
            }
        }
        .toString()

fun storedProviderSecretsFromJson(value: String): Map<String, ProviderSecrets> =
    runCatching {
            if (value.isBlank()) return emptyMap()
            Json.parseToJsonElement(value).jsonObject.mapValues { (_, element) ->
                element.jsonObject.let { obj ->
                    ProviderSecrets(
                        apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
                        customHeaders = obj["headers"]?.jsonPrimitive?.content.orEmpty(),
                    )
                }
            }
        }
        .getOrDefault(emptyMap())

fun storedTranslationSettingsFromJson(value: String): StoredTranslationSettings =
    runCatching {
            val root = Json.parseToJsonElement(value).jsonObject
            val configs =
                root["configs"]
                    ?.jsonObject
                    ?.mapValues { (_, element) ->
                        element.jsonObject.let { obj ->
                            ProviderConfig(
                                apiKey = obj["apiKey"]?.jsonPrimitive?.content.orEmpty(),
                                baseUrl = obj["baseUrl"]?.jsonPrimitive?.content.orEmpty(),
                                model = obj["model"]?.jsonPrimitive?.content.orEmpty(),
                                region = obj["region"]?.jsonPrimitive?.content.orEmpty(),
                                timeoutSeconds = obj["timeout"]?.jsonPrimitive?.intOrNull ?: 20,
                                customHeaders = obj["headers"]?.jsonPrimitive?.content.orEmpty(),
                                streaming = obj["streaming"]?.jsonPrimitive?.booleanOrNull ?: false,
                                retryCount =
                                    (obj["retries"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(0, 10),
                                fallbackModel =
                                    obj["fallbackModel"]?.jsonPrimitive?.content.orEmpty(),
                                fallbackRetryCount =
                                    (obj["fallbackRetries"]?.jsonPrimitive?.intOrNull ?: 3)
                                        .coerceIn(0, 10),
                                fallbackEnabled =
                                    obj["fallbackEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                            )
                        }
                    }
                    .orEmpty()
            val languages =
                root["targetLanguages"]
                    ?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.take(2)
                    ?: listOf(root["targetLanguage"]?.jsonPrimitive?.contentOrNull ?: "English")
            val selectedLanguages = languages.ifEmpty { listOf("English") }
            val storedOrder =
                root["outputOrder"]
                    ?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
            StoredTranslationSettings(
                providerId = root["provider"]?.jsonPrimitive?.content ?: "deepseek",
                translate = root["translate"]?.jsonPrimitive?.booleanOrNull ?: false,
                sendOriginalBeforeTranslation =
                    root["sendOriginalBeforeTranslation"]?.jsonPrimitive?.booleanOrNull ?: true,
                targetLanguages = selectedLanguages,
                outputOrder = normalizeOutputOrder(selectedLanguages, storedOrder),
                lineBreakOutput =
                    root["lineBreakOutput"]?.jsonPrimitive?.booleanOrNull ?: true,
                configs = configs,
                voiceInput =
                    root["voiceInput"]?.jsonObject?.let { obj ->
                        VoiceInputConfig(
                            enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                            region = obj["region"]?.jsonPrimitive?.contentOrNull ?: "singapore",
                            baseUrl = obj["baseUrl"]?.jsonPrimitive?.contentOrNull
                                ?: "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
                            model = obj["model"]?.jsonPrimitive?.contentOrNull
                                ?: "qwen3-asr-flash-2026-02-10",
                            language = obj["language"]?.jsonPrimitive?.contentOrNull ?: "ja",
                            microphoneId = obj["microphoneId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            sampleRate = (obj["sampleRate"]?.jsonPrimitive?.intOrNull ?: 16_000)
                                .coerceIn(8_000, 48_000),
                            maxSegmentSeconds =
                                (obj["maxSegmentSeconds"]?.jsonPrimitive?.intOrNull ?: 6)
                                    .coerceIn(1, 60),
                            tailSilenceMillis =
                                (obj["tailSilenceMillis"]?.jsonPrimitive?.intOrNull ?: 700)
                                    .coerceIn(200, 3_000),
                            vadActivationMillis =
                                (obj["vadActivationMillis"]?.jsonPrimitive?.intOrNull ?: 200)
                                    .coerceIn(60, 1_000),
                            vadMinRms =
                                (obj["vadMinRms"]?.jsonPrimitive?.doubleOrNull ?: 0.012)
                                    .coerceIn(0.001, 0.5),
                            vadSpeechRatio =
                                (obj["vadSpeechRatio"]?.jsonPrimitive?.doubleOrNull ?: 0.6)
                                    .coerceIn(0.1, 1.0),
                            partialIntervalMillis =
                                (obj["partialIntervalMillis"]?.jsonPrimitive?.intOrNull ?: 500)
                                    .coerceIn(250, 2_000),
                            partialMinSpeechMillis =
                                (obj["partialMinSpeechMillis"]?.jsonPrimitive?.intOrNull ?: 450)
                                    .coerceIn(200, 2_000),
                            timeoutSeconds = (obj["timeout"]?.jsonPrimitive?.intOrNull ?: 25)
                                .coerceIn(3, 120),
                        )
                    } ?: VoiceInputConfig(),
                interpretationVoiceInputEnabled =
                    root["interpretationVoiceInputEnabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                disableDynamicInputLimit =
                    root["disableDynamicInputLimit"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        .getOrDefault(StoredTranslationSettings())

fun initialProviderConfigs(
    stored: Map<String, ProviderConfig>,
    secrets: Map<String, ProviderSecrets> = emptyMap(),
) =
    mutableStateMapOf<String, ProviderConfig>().apply {
        translationProviders.forEach { provider ->
            val value = stored[provider.id] ?: defaultProviderConfig(provider)
            val protected = secrets[provider.id]
            put(
                provider.id,
                value.copy(
                    apiKey = protected?.apiKey ?: value.apiKey,
                    customHeaders = protected?.customHeaders ?: value.customHeaders,
                ),
            )
        }
    }
