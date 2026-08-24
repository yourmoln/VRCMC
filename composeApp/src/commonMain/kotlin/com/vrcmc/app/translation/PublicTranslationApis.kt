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
            TranslationResult.Failure(
                retryable = true,
                reason = TranslationFailureReason.EMPTY_RESPONSE,
                provider = "Google Web",
            )
        }
}

internal suspend fun requestMicrosoftEdgeWeb(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val response =
        translationHttpClient.post(config.baseUrl.trim()) {
            timeout { requestTimeoutMillis = config.timeoutMillis() }
            parameter("to", microsoftEdgeLanguageCode(targetLanguage))
            parameter("isEnterpriseClient", "false")
            header(HttpHeaders.UserAgent, "VRCMC/${AppInfo.VERSION}")
            contentType(ContentType.Application.Json)
            setBody(buildJsonArray { add(text) }.toString())
        }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        return responseFailure(response.status.value, raw)
    }
    return parseMicrosoftEdgeTranslation(raw)?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure(
                status = response.status.value,
                retryable = true,
                reason = TranslationFailureReason.EMPTY_RESPONSE,
                provider = "Microsoft Edge Web",
            )
        }
}

internal fun microsoftEdgeLanguageCode(targetLanguage: String): String {
    val code = languageCode(targetLanguage)
    return when (code.lowercase()) {
        "zh",
        "zh-cn",
        "zh-hans" -> "zh-Hans"
        "zh-tw",
        "zh-hant" -> "zh-Hant"
        else -> code
    }
}

internal fun parseMicrosoftEdgeTranslation(raw: String): String? =
    runCatching {
            translationJson
                .parseToJsonElement(raw)
                .jsonArray
                .first()
                .jsonObject["translations"]!!
                .jsonArray
                .first()
                .jsonObject["text"]!!
                .jsonPrimitive
                .content
                .trim()
                .takeIf { it.isNotEmpty() }
        }
        .getOrNull()

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
            TranslationResult.Failure(
                retryable = true,
                reason = TranslationFailureReason.EMPTY_RESPONSE,
                provider = "MyMemory",
            )
        }
}
