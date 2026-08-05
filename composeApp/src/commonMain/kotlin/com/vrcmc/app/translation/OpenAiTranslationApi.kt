package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.serialization.json.*

internal suspend fun requestOpenAi(
    provider: TranslationProvider,
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    maxTokens: Int,
    onApiFailure: (String) -> Unit,
): TranslationResult {
    val endpoint =
        config.baseUrl.trim().trimEnd('/').let {
            if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
        }
    val (systemPrompt, userPrompt) = llmPrompts(provider, config.model, targetLanguage, text)
    val body = buildJsonObject {
        put("model", config.model.trim())
        put("temperature", 0.2)
        put("max_tokens", maxTokens)
        put("stream", config.streaming)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
            addJsonObject {
                put("role", "user")
                put("content", userPrompt)
            }
        }
        if (provider.id == "hunyuan") put("enable_enhancement", true)
        if (provider.id in setOf("xiaomi", "zhipu"))
            putJsonObject("thinking") { put("type", "disabled") }
    }
    val response =
        translationHttpClient.post(endpoint) {
            timeout {
                requestTimeoutMillis = config.timeoutMillis()
                connectTimeoutMillis = minOf(10_000, config.timeoutMillis())
                socketTimeoutMillis = config.timeoutMillis()
            }
            contentType(ContentType.Application.Json)
            if (config.apiKey.isNotBlank()) bearerAuth(config.apiKey.trim())
            config.applyHeaders(this)
            setBody(body.toString())
        }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        return responseFailure(response.status.value, raw)
    }
    val parsed = if (config.streaming) parseOpenAiStream(raw) else parseOpenAiResponse(raw)
    if (parsed == null) {
        onApiFailure(raw)
        return TranslationResult.Failure(
            "API response could not be parsed",
            response.status.value,
            retryable = true,
        )
    }
    if (parsed.finishReason == "length") {
        onApiFailure(raw)
        return TranslationResult.Failure(
            "Translation was truncated at the $maxTokens token limit",
            response.status.value,
            retryable = true,
        )
    }
    val translated = parsed.content
    return translated?.trim()?.takeIf { it.isNotEmpty() }?.let(TranslationResult::Success)
        ?: run {
            onApiFailure(raw)
            TranslationResult.Failure("服务返回成功，但没有可用的翻译内容", response.status.value, retryable = true)
        }
}

internal data class OpenAiOutput(val content: String?, val finishReason: String?)

internal fun parseOpenAiResponse(raw: String): OpenAiOutput? =
    runCatching {
            val choice =
                translationJson
                    .parseToJsonElement(raw)
                    .jsonObject["choices"]!!
                    .jsonArray
                    .first()
                    .jsonObject
            OpenAiOutput(
                content =
                    choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull,
                finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull,
            )
        }
        .getOrNull()

private fun parseOpenAiStream(raw: String): OpenAiOutput? {
    val content = StringBuilder()
    var finishReason: String? = null
    var parsedAny = false
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("data:") }
        .map { it.removePrefix("data:").trim() }
        .filter { it != "[DONE]" }
        .forEach { chunk ->
            runCatching {
                    translationJson
                        .parseToJsonElement(chunk)
                        .jsonObject["choices"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                }
                .getOrNull()
                ?.let { choice ->
                    parsedAny = true
                    choice["delta"]
                        ?.jsonObject
                        ?.get("content")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.let(content::append)
                    choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
                }
        }
    return if (parsedAny) OpenAiOutput(content.toString(), finishReason) else null
}
