package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal suspend fun requestAnthropic(
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val endpoint =
        config.baseUrl.trim().trimEnd('/').let {
            if (it.endsWith("/v1/messages")) it else "$it/v1/messages"
        }
    val (systemPrompt, userPrompt) = llmPrompts(null, config.model, targetLanguage, text)
    val body = buildJsonObject {
        put("model", config.model.trim())
        put("max_tokens", 192)
        put("temperature", 0.2)
        put("system", systemPrompt)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "user")
                put("content", userPrompt)
            }
        }
    }
    val response =
        translationHttpClient.post(endpoint) {
            timeout {
                requestTimeoutMillis = config.timeoutMillis()
                connectTimeoutMillis = minOf(10_000, config.timeoutMillis())
                socketTimeoutMillis = config.timeoutMillis()
            }
            contentType(ContentType.Application.Json)
            header("x-api-key", config.apiKey.trim())
            header("anthropic-version", "2023-06-01")
            config.applyHeaders(this)
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
                    .jsonObject["content"]!!
                    .jsonArray
                    .first()
                    .jsonObject["text"]!!
                    .jsonPrimitive
                    .content
            }
            .getOrNull()
    return translated?.trim()?.takeIf { it.isNotEmpty() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("Claude 未返回可用文本", response.status.value, retryable = true)
        }
}
