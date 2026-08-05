package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

sealed interface TranslationResult {
    data class Success(val text: String) : TranslationResult

    data class Failure(
        val message: String,
        val status: Int? = null,
        val retryable: Boolean = false,
    ) : TranslationResult
}

internal val translationHttpClient = HttpClient { expectSuccess = false }
internal val translationJson = Json { ignoreUnknownKeys = true }

internal fun isArabicDigitsOnly(text: String): Boolean =
    text.any { it in '0'..'9' } && text.all { it in '0'..'9' || it.isWhitespace() }

suspend fun translateText(
    provider: TranslationProvider,
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onRetry: (Int) -> Unit = {},
    onApiFailure: (String) -> Unit = {},
): TranslationResult {
    if (text.isBlank()) return TranslationResult.Failure("翻译内容为空")
    if (config.baseUrl.isBlank()) return TranslationResult.Failure("Base URL 不能为空")
    if (config.model.isBlank()) return TranslationResult.Failure("模型 ID 不能为空")
    if (provider.keyRequired && config.apiKey.isBlank())
        return TranslationResult.Failure("${provider.label} 需要 API Key")
    endpointSecurityError(config)?.let {
        return TranslationResult.Failure(it)
    }
    var openAiFailureCount = 0
    return translateWithFallback(
        sourceText = text,
        primaryModel = config.model,
        retryCount = config.retryCount,
        fallbackModel = config.fallbackModel.takeIf { config.fallbackEnabled }.orEmpty(),
        fallbackRetryCount = config.fallbackRetryCount,
        onRetry = onRetry,
    ) { model ->
        val requestConfig = config.copy(model = model)
        try {
            when (provider.protocol) {
                ProviderProtocol.OPENAI -> {
                    val maxTokens =
                        when (openAiFailureCount) {
                            0 -> 512
                            1 -> 1024
                            else -> 2048
                        }
                    requestOpenAi(
                            provider,
                            requestConfig,
                            targetLanguage,
                            text,
                            maxTokens,
                            onApiFailure,
                        )
                        .also { if (it is TranslationResult.Failure) openAiFailureCount++ }
                }
                ProviderProtocol.ANTHROPIC ->
                    requestAnthropic(requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.GOOGLE_WEB ->
                    requestGoogleWeb(requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.MYMEMORY ->
                    requestMyMemory(requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.DEEPL ->
                    requestDeepL(requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.LIBRE ->
                    requestLibre(requestConfig, targetLanguage, text, onApiFailure)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            TranslationResult.Failure(
                error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName ?: "网络请求失败",
                retryable = true,
            )
        }
    }
}

internal fun ProviderConfig.totalRetryCount(): Int {
    val primaryRetries = retryCount.coerceIn(0, 10)
    val hasFallback =
        fallbackEnabled && fallbackModel.isNotBlank() && fallbackModel.trim() != model.trim()
    return primaryRetries + if (hasFallback) fallbackRetryCount.coerceIn(0, 10) + 1 else 0
}

internal suspend fun translateWithFallback(
    sourceText: String,
    primaryModel: String,
    retryCount: Int,
    fallbackModel: String,
    fallbackRetryCount: Int,
    onRetry: (Int) -> Unit = {},
    request: suspend (String) -> TranslationResult,
): TranslationResult {
    val primaryRetries = retryCount.coerceIn(0, 10)
    val primaryResult =
        translateWithRetries(sourceText, primaryRetries, onRetry) { request(primaryModel) }
    if (
        primaryResult is TranslationResult.Success ||
            primaryResult !is TranslationResult.Failure ||
            !primaryResult.retryable
    )
        return primaryResult

    val fallback = fallbackModel.trim()
    if (fallback.isEmpty() || fallback == primaryModel.trim()) return primaryResult

    onRetry(primaryRetries + 1)
    return translateWithRetries(
        sourceText = sourceText,
        retryCount = fallbackRetryCount,
        onRetry = { attempt -> onRetry(primaryRetries + 1 + attempt) },
    ) {
        request(fallback)
    }
}

internal fun endpointSecurityError(config: ProviderConfig): String? {
    val endpoint = config.baseUrl.trim().lowercase()
    if (!endpoint.startsWith("https://") && !endpoint.startsWith("http://"))
        return "Base URL 必须以 http:// 或 https:// 开头"
    if (
        endpoint.startsWith("http://") &&
            (config.apiKey.isNotBlank() || config.customHeaders.isNotBlank())
    ) {
        return "HTTP 端点不能携带 API Key 或自定义请求头，请改用 HTTPS"
    }
    if (endpoint.startsWith("http://") && !isLocalNetworkEndpoint(config.baseUrl)) {
        return "HTTP 仅允许用于本机或局域网端点，公网端点必须使用 HTTPS"
    }
    return null
}

private fun isLocalNetworkEndpoint(value: String): Boolean =
    runCatching {
            val host = Url(value.trim()).host.lowercase().trim('[', ']')
            if (
                host == "localhost" ||
                    host.endsWith(".local") ||
                    host == "::1" ||
                    host.startsWith("127.")
            )
                return@runCatching true
            val ipv4 = host.split('.').map { it.toIntOrNull() }
            if (ipv4.size == 4 && ipv4.all { it != null && it in 0..255 }) {
                val first = ipv4[0]!!
                val second = ipv4[1]!!
                return@runCatching first == 10 ||
                    (first == 172 && second in 16..31) ||
                    (first == 192 && second == 168) ||
                    (first == 169 && second == 254)
            }
            host.startsWith("fc") ||
                host.startsWith("fd") ||
                host.startsWith("fe8") ||
                host.startsWith("fe9") ||
                host.startsWith("fea") ||
                host.startsWith("feb")
        }
        .getOrDefault(false)

internal suspend fun translateWithRetries(
    sourceText: String,
    retryCount: Int,
    onRetry: (Int) -> Unit = {},
    request: suspend () -> TranslationResult,
): TranslationResult {
    var lastFailure = TranslationResult.Failure("翻译接口未返回可用翻译", retryable = true)
    repeat(retryCount.coerceIn(0, 10) + 1) { attempt ->
        if (attempt > 0) onRetry(attempt)
        when (val result = request()) {
            is TranslationResult.Success -> {
                val translated = result.text.trim()
                if (translated.isNotEmpty() && translated != sourceText.trim())
                    return TranslationResult.Success(translated)
                lastFailure = TranslationResult.Failure("翻译接口未返回可用翻译", retryable = true)
            }
            is TranslationResult.Failure -> {
                lastFailure = result
                val successfulResponseWithoutContent = result.status?.let { it in 200..299 } == true
                if (!result.retryable && !successfulResponseWithoutContent) return result
            }
        }
    }
    return lastFailure
}
