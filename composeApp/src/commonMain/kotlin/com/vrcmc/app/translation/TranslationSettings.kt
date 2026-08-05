package com.vrcmc.app

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.serialization.json.*

data class StoredTranslationSettings(
    val providerId: String = "deepseek",
    val translate: Boolean = false,
    val sendOriginalBeforeTranslation: Boolean = true,
    val targetLanguages: List<String> = listOf("English"),
    val outputOrder: List<String> = targetLanguages + originalOutputKey,
    val configs: Map<String, ProviderConfig> = emptyMap(),
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
                configs = configs,
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
