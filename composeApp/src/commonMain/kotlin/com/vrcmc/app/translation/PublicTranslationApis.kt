package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal suspend fun requestGoogleWeb(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val response =
        translationHttpClient.get(config.baseUrl.trim()) {
            timeout { requestTimeoutMillis = config.timeoutMillis() }
            parameter("client", "gtx")
            parameter("sl", "auto")
            parameter("tl", languageCode(targetLanguage))
            parameter("dt", "t")
            parameter("q", text)
        }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        return responseFailure(response.status.value, raw)
    }
    val translated =
        runCatching {
                translationJson.parseToJsonElement(raw).jsonArray.first().jsonArray.joinToString(
                    ""
                ) {
                    it.jsonArray.first().jsonPrimitive.content
                }
            }
            .getOrNull()
    return translated?.takeIf { it.isNotBlank() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("Google Web 未返回可用文本", retryable = true)
        }
}

internal suspend fun requestMyMemory(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val response =
        translationHttpClient.get(config.baseUrl.trim()) {
            timeout { requestTimeoutMillis = config.timeoutMillis() }
            parameter("q", text)
            parameter("langpair", "Autodetect|${languageCode(targetLanguage)}")
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
                    .jsonObject["responseData"]!!
                    .jsonObject["translatedText"]!!
                    .jsonPrimitive
                    .content
            }
            .getOrNull()
    return translated?.takeIf { it.isNotBlank() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("MyMemory 未返回可用文本", retryable = true)
        }
}
