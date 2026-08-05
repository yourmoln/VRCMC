package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal suspend fun requestDeepL(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val endpoint =
        config.baseUrl.trim().trimEnd('/').let {
            if (it.endsWith("/translate")) it else "$it/translate"
        }
    val response =
        translationHttpClient.submitForm(
            endpoint,
            Parameters.build {
                append("text", text)
                append("target_lang", languageCode(targetLanguage).uppercase())
            },
        ) {
            timeout { requestTimeoutMillis = config.timeoutMillis() }
            header("Authorization", "DeepL-Auth-Key ${config.apiKey.trim()}")
        }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        return responseFailure(response.status.value, raw)
    }
    val translated =
        runCatching {
                translationJson
                    .parseToJsonElement(raw)
                    .jsonObject["translations"]!!
                    .jsonArray
                    .first()
                    .jsonObject["text"]!!
                    .jsonPrimitive
                    .content
            }
            .getOrNull()
    return translated?.takeIf { it.isNotBlank() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("DeepL 未返回可用文本", retryable = true)
        }
}

internal suspend fun requestLibre(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val endpoint =
        config.baseUrl.trim().trimEnd('/').let {
            if (it.endsWith("/translate")) it else "$it/translate"
        }
    val body = buildJsonObject {
        put("q", text)
        put("source", "auto")
        put("target", languageCode(targetLanguage))
        put("format", "text")
        if (config.apiKey.isNotBlank()) put("api_key", config.apiKey.trim())
    }
    val response =
        translationHttpClient.post(endpoint) {
            timeout { requestTimeoutMillis = config.timeoutMillis() }
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        return responseFailure(response.status.value, raw)
    }
    val translated =
        runCatching {
                translationJson
                    .parseToJsonElement(raw)
                    .jsonObject["translatedText"]!!
                    .jsonPrimitive
                    .content
            }
            .getOrNull()
    return translated?.takeIf { it.isNotBlank() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("LibreTranslate 未返回可用文本", retryable = true)
        }
}
