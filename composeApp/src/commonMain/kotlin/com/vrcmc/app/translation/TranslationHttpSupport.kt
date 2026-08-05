package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal fun ProviderConfig.timeoutMillis() = timeoutSeconds.coerceIn(3, 300) * 1_000L

internal fun ProviderConfig.applyHeaders(builder: HttpRequestBuilder) {
    customHeaders
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { line ->
            val separator = line.indexOf(':')
            if (separator > 0) {
                val name = line.substring(0, separator).trim()
                builder.headers.remove(name)
                builder.headers.append(name, line.substring(separator + 1).trim())
            }
        }
}

internal fun responseFailure(status: Int, raw: String): TranslationResult.Failure {
    val detail =
        runCatching {
                val root = translationJson.parseToJsonElement(raw).jsonObject
                root["error"]?.let { error ->
                    if (error is JsonObject) error["message"]?.jsonPrimitive?.contentOrNull
                    else error.jsonPrimitive.contentOrNull
                }
                    ?: root["message"]?.jsonPrimitive?.contentOrNull
                    ?: root["detail"]?.jsonPrimitive?.contentOrNull
            }
            .getOrNull()
            ?.take(300)
    val retryable =
        status == 408 || status == 409 || status == 425 || status == 429 || status in 500..599
    return TranslationResult.Failure(
        "HTTP $status${detail?.let { ": $it" }.orEmpty()}",
        status,
        retryable,
    )
}

internal fun languageCode(language: String): String =
    when (language.trim().lowercase()) {
        "english",
        "英语",
        "英文" -> "en"
        "简体中文",
        "chinese",
        "chinese (simplified)",
        "中文" -> "zh-CN"
        "繁體中文",
        "繁体中文",
        "chinese (traditional)" -> "zh-TW"
        "日本語",
        "日语",
        "japanese" -> "ja"
        "한국어",
        "韩语",
        "korean" -> "ko"
        "español",
        "西班牙语",
        "spanish" -> "es"
        "français",
        "法语",
        "french" -> "fr"
        "deutsch",
        "德语",
        "german" -> "de"
        "русский",
        "俄语",
        "russian" -> "ru"
        else -> language.takeIf { it.length in 2..8 && !it.contains(' ') } ?: "en"
    }
