package com.vrcmc.app

import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class VoiceTranscriptionFailureReason {
    CUSTOM,
    NO_AUDIO,
    API_KEY_REQUIRED,
    BASE_URL_REQUIRED,
    MODEL_REQUIRED,
    INVALID_BASE_URL,
    EMPTY_RESPONSE,
    NETWORK_REQUEST_FAILED,
}

sealed interface VoiceTranscriptionResult {
    data class Success(val text: String) : VoiceTranscriptionResult
    data class Failure(
        val message: String = "",
        val reason: VoiceTranscriptionFailureReason = VoiceTranscriptionFailureReason.CUSTOM,
    ) : VoiceTranscriptionResult
}

suspend fun transcribeQwenAudio(
    config: VoiceInputConfig,
    wav: ByteArray,
    onApiFailure: (String) -> Unit = {},
): VoiceTranscriptionResult {
    if (wav.size <= 44)
        return VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.NO_AUDIO)
    if (config.apiKey.isBlank())
        return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.API_KEY_REQUIRED,
        )
    if (config.baseUrl.isBlank())
        return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.BASE_URL_REQUIRED,
        )
    if (config.model.isBlank())
        return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.MODEL_REQUIRED,
        )
    if (!isSupportedHttpEndpoint(config.baseUrl))
        return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.INVALID_BASE_URL,
        )

    val endpoint = config.baseUrl.trim().trimEnd('/').let {
        if (it.endsWith("/chat/completions")) it else "$it/chat/completions"
    }
    val body = buildJsonObject {
        put("model", config.model.trim())
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", "input_audio")
                        put("input_audio", buildJsonObject {
                            put("data", "data:audio/wav;base64,${encodeBase64(wav)}")
                        })
                    })
                })
            })
        })
        put("asr_options", buildJsonObject {
            put("enable_itn", false)
            if (config.language.isNotBlank() && config.language != "auto") {
                put("language", config.language.trim())
            }
        })
    }
    return try {
        val timeout = config.timeoutSeconds.coerceIn(3, 120) * 1_000L
        val response = translationHttpClient.post(endpoint) {
            timeout {
                requestTimeoutMillis = timeout
                connectTimeoutMillis = minOf(10_000L, timeout)
                socketTimeoutMillis = timeout
            }
            contentType(ContentType.Application.Json)
            bearerAuth(config.apiKey.trim())
            setBody(body.toString())
        }
        val raw = response.body<String>()
        if (!response.status.isSuccess()) {
            onApiFailure(raw)
            val failure = responseFailure(response.status.value, raw)
            VoiceTranscriptionResult.Failure(failure.message)
        } else {
            val text = parseQwenAsrResponse(raw)
            if (text.isNullOrBlank()) {
                onApiFailure(raw)
                VoiceTranscriptionResult.Failure(
                    reason = VoiceTranscriptionFailureReason.EMPTY_RESPONSE,
                )
            } else VoiceTranscriptionResult.Success(text.trim())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        VoiceTranscriptionResult.Failure(
            message =
                error.message?.takeIf(String::isNotBlank) ?: error::class.simpleName.orEmpty(),
            reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED,
        )
    }
}

internal fun parseQwenAsrResponse(raw: String): String? =
    runCatching {
        val content = translationJson.parseToJsonElement(raw).jsonObject["choices"]
            ?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
            ?: return@runCatching null
        when (content) {
            is kotlinx.serialization.json.JsonPrimitive -> content.contentOrNull
            is kotlinx.serialization.json.JsonArray -> content.mapNotNull { item ->
                (item as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            }.joinToString("").ifBlank { null }
            else -> null
        }
    }.getOrNull()

internal fun encodeBase64(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val result = StringBuilder((bytes.size + 2) / 3 * 4)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index++].toInt() and 0xff
        val second = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
        val third = if (index < bytes.size) bytes[index++].toInt() and 0xff else -1
        result.append(alphabet[first ushr 2])
        result.append(alphabet[((first and 3) shl 4) or if (second >= 0) second ushr 4 else 0])
        result.append(if (second >= 0) alphabet[((second and 15) shl 2) or if (third >= 0) third ushr 6 else 0] else '=')
        result.append(if (third >= 0) alphabet[third and 63] else '=')
    }
    return result.toString()
}
