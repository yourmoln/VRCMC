package com.vrcmc.app

import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.Buffer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    LOCAL_UNSUPPORTED,
    LOCAL_MODEL_NOT_READY,
    LOCAL_RECOGNITION_FAILED,
}

sealed interface VoiceTranscriptionResult {
    data class Success(val text: String) : VoiceTranscriptionResult
    data class Failure(
        val message: String = "",
        val reason: VoiceTranscriptionFailureReason = VoiceTranscriptionFailureReason.CUSTOM,
    ) : VoiceTranscriptionResult
}

private const val funAsrModel = "fun-asr"
private const val defaultPollIntervalMillis = 1_000L
private const val maxPollIntervalMillis = 5_000L

/**
 * Converts the compatible-mode URL used by the settings UI to the DashScope HTTP API URL.
 * Fun-ASR is not served by the OpenAI-compatible /chat/completions endpoint.
 */
internal fun funAsrApiBaseUrl(value: String): String? {
    val base = value.trim().trimEnd('/')
    if (!isSupportedHttpEndpoint(base)) return null
    return when {
        base.endsWith("/compatible-mode/v1", ignoreCase = true) ->
            base.dropLast("/compatible-mode/v1".length) + "/api/v1"
        base.endsWith("/compatible-mode", ignoreCase = true) ->
            base.dropLast("/compatible-mode".length) + "/api/v1"
        base.endsWith("/api/v1", ignoreCase = true) -> base
        base.endsWith("/v1", ignoreCase = true) -> base
        else -> "$base/api/v1"
    }
}

/**
 * Fun-ASR follows the DashScope file-transcription API. The SDK obtains a short-lived OSS
 * upload policy first, uploads the bytes, then submits the returned oss:// URL as input.
 */
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
    val apiBase = funAsrApiBaseUrl(config.baseUrl)
        ?: return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.INVALID_BASE_URL,
        )

    return try {
        val timeout = config.timeoutSeconds.coerceIn(3, 120) * 1_000L
        val fileUrl = uploadFunAsrAudio(apiBase, config.apiKey.trim(), wav, timeout, onApiFailure)
        val taskId = submitFunAsrTask(
            apiBase = apiBase,
            apiKey = config.apiKey.trim(),
            fileUrl = fileUrl,
            language = config.language,
            timeoutMillis = timeout,
            onApiFailure = onApiFailure,
        ) ?: return VoiceTranscriptionResult.Failure(
            reason = VoiceTranscriptionFailureReason.EMPTY_RESPONSE,
        )
        pollFunAsrTask(
            apiBase = apiBase,
            apiKey = config.apiKey.trim(),
            taskId = taskId,
            timeoutMillis = timeout,
            onApiFailure = onApiFailure,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        VoiceTranscriptionResult.Failure(
            message = error.message?.takeIf(String::isNotBlank) ?: error::class.simpleName.orEmpty(),
            reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED,
        )
    }
}

private suspend fun uploadFunAsrAudio(
    apiBase: String,
    apiKey: String,
    wav: ByteArray,
    timeoutMillis: Long,
    onApiFailure: (String) -> Unit,
): String {
    val policyResponse = translationHttpClient.get("$apiBase/uploads") {
        timeout {
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = minOf(10_000L, timeoutMillis)
            socketTimeoutMillis = timeoutMillis
        }
        bearerAuth(apiKey)
        parameter("action", "getPolicy")
        parameter("model", funAsrModel)
    }
    val policyRaw = policyResponse.body<String>()
    if (!policyResponse.status.isSuccess()) {
        onApiFailure(policyRaw)
        error("HTTP ${policyResponse.status.value}: ${policyMessage(policyRaw)}")
    }
    val policy = parseUploadPolicy(policyRaw)
        ?: error("DashScope upload policy was missing from the response")
    val uploadHost = policy["upload_host"]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: error("DashScope upload policy did not contain upload_host")
    val uploadDir = policy["upload_dir"]?.jsonPrimitive?.contentOrNull
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)
        ?: error("DashScope upload policy did not contain upload_dir")
    val objectKey = "$uploadDir/vrcmc-${kotlin.random.Random.nextLong().toString(16)}.wav"
    val uploadResponse = translationHttpClient.post(uploadHost) {
        timeout {
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = minOf(10_000L, timeoutMillis)
            socketTimeoutMillis = timeoutMillis
        }
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        setBody(buildOssMultipartContent(policy, objectKey, wav))
    }
    val uploadRaw = uploadResponse.body<String>()
    if (!uploadResponse.status.isSuccess()) {
        onApiFailure(uploadRaw)
        error("OSS upload failed with HTTP ${uploadResponse.status.value}: ${policyMessage(uploadRaw)}")
    }
    return "oss://$objectKey"
}

private fun buildOssMultipartContent(
    policy: JsonObject,
    objectKey: String,
    wav: ByteArray,
): MultiPartFormDataContent {
    val parts = buildList {
        addOssTextPart("OSSAccessKeyId", policy["oss_access_key_id"]?.jsonPrimitive?.contentOrNull.orEmpty())
        addOssTextPart("Signature", policy["signature"]?.jsonPrimitive?.contentOrNull.orEmpty())
        addOssTextPart("policy", policy["policy"]?.jsonPrimitive?.contentOrNull.orEmpty())
        addOssTextPart("key", objectKey)
        addOssTextPart("x-oss-object-acl", policy["x_oss_object_acl"]?.jsonPrimitive?.contentOrNull.orEmpty())
        addOssTextPart("x-oss-forbid-overwrite", policy["x_oss_forbid_overwrite"]?.jsonPrimitive?.contentOrNull.orEmpty())
        addOssTextPart("success_action_status", "200")
        addOssTextPart("x-oss-content-type", "audio/wav")
        add(
            PartData.BinaryItem(
                provider = { Buffer().apply { write(wav) } },
                dispose = {},
                partHeaders = Headers.build {
                    append(HttpHeaders.ContentType, "audio/wav")
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"audio.wav\"")
                    append(HttpHeaders.ContentLength, wav.size.toString())
                },
            ),
        )
    }
    val boundary = "vrcmc-${kotlin.random.Random.nextLong().toString(16)}"
    return MultiPartFormDataContent(
        parts = parts,
        boundary = boundary,
        contentType = ContentType.parse("multipart/form-data; boundary=$boundary"),
    )
}

private fun MutableList<PartData>.addOssTextPart(
    name: String,
    value: String,
) {
    add(
        PartData.FormItem(
            value = value,
            dispose = {},
            partHeaders = Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"$name\"")
            },
        ),
    )
}

internal fun parseUploadPolicy(raw: String): JsonObject? = runCatching {
    val root = translationJson.parseToJsonElement(raw).jsonObject
    // DashScope's SDK normalizes both response shapes: newer endpoints return the
    // certificate in `output`, while the uploads endpoint can return the
    // certificate directly at the root (with an optional `request_id`).
    val candidates = listOfNotNull(
        root["output"]?.jsonObject,
        root["data"]?.jsonObject,
        root,
    )
    candidates.firstOrNull { policy ->
        policy["upload_host"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            policy["upload_dir"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            policy["oss_access_key_id"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            policy["signature"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true &&
            policy["policy"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    }
}.getOrNull()

private fun policyMessage(raw: String): String = runCatching {
    val root = translationJson.parseToJsonElement(raw).jsonObject
    root["message"]?.jsonPrimitive?.contentOrNull
        ?: root["code"]?.jsonPrimitive?.contentOrNull
        ?: raw.take(300)
}.getOrDefault(raw.take(300))

private suspend fun submitFunAsrTask(
    apiBase: String,
    apiKey: String,
    fileUrl: String,
    language: String,
    timeoutMillis: Long,
    onApiFailure: (String) -> Unit,
): String? {
    val body = buildFunAsrRequest(fileUrl, language)
    val response = translationHttpClient.post("$apiBase/services/audio/asr/transcription") {
        timeout {
            requestTimeoutMillis = timeoutMillis
            connectTimeoutMillis = minOf(10_000L, timeoutMillis)
            socketTimeoutMillis = timeoutMillis
        }
        contentType(ContentType.Application.Json)
        bearerAuth(apiKey)
        header("X-DashScope-Async", "enable")
        setBody(body)
    }
    val raw = response.body<String>()
    if (!response.status.isSuccess()) {
        onApiFailure(raw)
        error("HTTP ${response.status.value}: ${policyMessage(raw)}")
    }
    return parseFunAsrTaskId(raw)
}

internal fun buildFunAsrRequest(fileUrl: String, language: String): String =
    buildJsonObject {
        put("model", funAsrModel)
        put("input", buildJsonObject {
            put("file_urls", buildJsonArray { add(JsonPrimitive(fileUrl)) })
        })
        put("parameters", buildJsonObject {
            put("channel_id", buildJsonArray { add(JsonPrimitive(0)) })
            if (language.isNotBlank() && !language.equals("auto", ignoreCase = true)) {
                put("language_hints", buildJsonArray { add(JsonPrimitive(language.trim())) })
            }
        })
    }.toString()

internal fun parseFunAsrTaskId(raw: String): String? = runCatching {
    val root = translationJson.parseToJsonElement(raw).jsonObject
    root["output"]?.jsonObject?.get("task_id")?.jsonPrimitive?.contentOrNull
        ?: root["task_id"]?.jsonPrimitive?.contentOrNull
}.getOrNull()?.takeIf(String::isNotBlank)

private suspend fun pollFunAsrTask(
    apiBase: String,
    apiKey: String,
    taskId: String,
    timeoutMillis: Long,
    onApiFailure: (String) -> Unit,
): VoiceTranscriptionResult = withTimeoutOrNull<VoiceTranscriptionResult>(timeoutMillis) {
    var pollInterval = defaultPollIntervalMillis
    while (true) {
        val response = translationHttpClient.get("$apiBase/tasks/$taskId") {
            timeout {
                requestTimeoutMillis = minOf(timeoutMillis, 15_000L)
                connectTimeoutMillis = minOf(10_000L, timeoutMillis)
                socketTimeoutMillis = minOf(timeoutMillis, 15_000L)
            }
            bearerAuth(apiKey)
        }
        val raw = response.body<String>()
        if (!response.status.isSuccess()) {
            onApiFailure(raw)
            return@withTimeoutOrNull VoiceTranscriptionResult.Failure(
                message = "HTTP ${response.status.value}: ${policyMessage(raw)}",
                reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED,
            )
        }
        val task = parseFunAsrTask(raw)
        when (task.status.uppercase()) {
            "SUCCEEDED" -> {
                val directText = parseFunAsrResponse(raw)
                if (!directText.isNullOrBlank()) return@withTimeoutOrNull VoiceTranscriptionResult.Success(directText.trim())
                val url = task.transcriptionUrl
                    ?: return@withTimeoutOrNull VoiceTranscriptionResult.Failure(
                        reason = VoiceTranscriptionFailureReason.EMPTY_RESPONSE,
                    )
                val resultResponse = translationHttpClient.get(url) {
                    timeout {
                        requestTimeoutMillis = minOf(timeoutMillis, 15_000L)
                        connectTimeoutMillis = minOf(10_000L, timeoutMillis)
                        socketTimeoutMillis = minOf(timeoutMillis, 15_000L)
                    }
                }
                val resultRaw = resultResponse.body<String>()
                if (!resultResponse.status.isSuccess()) {
                    onApiFailure(resultRaw)
                    return@withTimeoutOrNull VoiceTranscriptionResult.Failure(
                        message = "HTTP ${resultResponse.status.value}: ${policyMessage(resultRaw)}",
                        reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED,
                    )
                }
                val text = parseFunAsrResponse(resultRaw)
                return@withTimeoutOrNull if (text.isNullOrBlank()) {
                    onApiFailure(resultRaw)
                    VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.EMPTY_RESPONSE)
                } else {
                    VoiceTranscriptionResult.Success(text.trim())
                }
            }
            "FAILED", "CANCELED", "CANCELLED", "UNKNOWN" ->
                return@withTimeoutOrNull VoiceTranscriptionResult.Failure(
                    message = task.message,
                    reason = VoiceTranscriptionFailureReason.CUSTOM,
                )
        }
        delay(pollInterval)
        pollInterval = (pollInterval * 2).coerceAtMost(maxPollIntervalMillis)
    }
    error("Fun-ASR polling exited unexpectedly")
} ?: VoiceTranscriptionResult.Failure(
    message = "Fun-ASR task timed out",
    reason = VoiceTranscriptionFailureReason.NETWORK_REQUEST_FAILED,
)

private data class FunAsrTask(
    val status: String,
    val transcriptionUrl: String?,
    val message: String,
)

private fun parseFunAsrTask(raw: String): FunAsrTask = runCatching {
    val root = translationJson.parseToJsonElement(raw).jsonObject
    val output = root["output"]?.jsonObject ?: root
    val status = output["task_status"]?.jsonPrimitive?.contentOrNull
        ?: root["task_status"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val results = output["results"]?.jsonArray.orEmpty()
    val result = results.firstOrNull()?.jsonObject
    FunAsrTask(
        status = status,
        transcriptionUrl = result?.get("transcription_url")?.jsonPrimitive?.contentOrNull,
        message = output["message"]?.jsonPrimitive?.contentOrNull
            ?: output["code"]?.jsonPrimitive?.contentOrNull
            ?: "Fun-ASR task failed",
    )
}.getOrDefault(FunAsrTask("", null, "Fun-ASR returned an invalid task response"))

/** Parses both the task response shortcut and the downloaded Fun-ASR transcript JSON. */
internal fun parseFunAsrResponse(raw: String): String? = runCatching {
    val root = translationJson.parseToJsonElement(raw).jsonObject
    root["output"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
        ?: root["output"]?.jsonObject?.get("output")?.jsonObject?.get("sentence")?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull
        ?: root["text"]?.jsonPrimitive?.contentOrNull
        ?: root["transcripts"]?.jsonArray?.mapNotNull { transcript ->
            transcript.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }?.joinToString("")?.takeIf(String::isNotBlank)
}.getOrNull()

/** Kept as a source-compatible alias for callers from older builds. */
internal fun parseQwenAsrResponse(raw: String): String? = parseFunAsrResponse(raw)
