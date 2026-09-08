package com.vrcmc.app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*

enum class TranslationFailureReason {
    CUSTOM,
    EMPTY_INPUT,
    BASE_URL_REQUIRED,
    MODEL_REQUIRED,
    API_KEY_REQUIRED,
    INVALID_BASE_URL,
    RESPONSE_PARSE_FAILED,
    EMPTY_RESPONSE,
    NETWORK_REQUEST_FAILED,
}

sealed interface TranslationResult {
    data class Success(val text: String) : TranslationResult

    data class Failure(
        val message: String = "",
        val status: Int? = null,
        val retryable: Boolean = false,
        val reason: TranslationFailureReason = TranslationFailureReason.CUSTOM,
        val provider: String = "",
    ) : TranslationResult
}

internal val translationHttpClient = createVrcmcHttpClient { expectSuccess = false }
internal val translationJson = Json { ignoreUnknownKeys = true }

internal fun isArabicDigitsOnly(text: String): Boolean =
    text.any { it in '0'..'9' } && text.all { it in '0'..'9' || it.isWhitespace() }

internal fun shouldSkipTranslation(text: String): Boolean =
    text.any { !it.isWhitespace() } && text.none(Char::isLetter)

suspend fun translateText(
    provider: TranslationProvider,
    config: ProviderConfig,
    targetLanguage: String,
    text: String,
    onRetry: (Int) -> Unit = {},
    onApiFailure: (String) -> Unit = {},
): TranslationResult {
    if (text.isBlank())
        return TranslationResult.Failure(reason = TranslationFailureReason.EMPTY_INPUT)
    if (config.baseUrl.isBlank())
        return TranslationResult.Failure(reason = TranslationFailureReason.BASE_URL_REQUIRED)
    if (config.model.isBlank())
        return TranslationResult.Failure(reason = TranslationFailureReason.MODEL_REQUIRED)
    if (provider.keyRequired && config.apiKey.isBlank())
        return TranslationResult.Failure(
            reason = TranslationFailureReason.API_KEY_REQUIRED,
            provider = provider.label,
        )
    if (!isSupportedHttpEndpoint(config.baseUrl))
        return TranslationResult.Failure(reason = TranslationFailureReason.INVALID_BASE_URL)
    return translateWithBingFallback(
        provider = provider,
        config = config,
        sourceText = text,
        onRetry = onRetry,
    ) { requestProvider, requestConfig ->
        try {
            when (requestProvider.protocol) {
                ProviderProtocol.OPENAI -> {
                    requestOpenAi(
                            requestProvider,
                            requestConfig,
                            targetLanguage,
                            text,
                            onApiFailure,
                        )
                }
                ProviderProtocol.ANTHROPIC ->
                    requestAnthropic(requestProvider, requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.GOOGLE_WEB ->
                    requestGoogleWeb(requestConfig, targetLanguage, text, onApiFailure)
                ProviderProtocol.MICROSOFT_EDGE_WEB ->
                    requestMicrosoftEdgeWeb(requestConfig, targetLanguage, text, onApiFailure)
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
                message =
                    error.message?.takeIf { it.isNotBlank() }
                        ?: error::class.simpleName.orEmpty(),
                retryable = true,
                reason = TranslationFailureReason.NETWORK_REQUEST_FAILED,
            )
        }
    }
}

internal fun ProviderConfig.totalRetryCount(provider: TranslationProvider): Int {
    val primaryRetries = retryCount.coerceIn(0, 10)
    val hasFallback =
        fallbackEnabled && fallbackModel.isNotBlank() && fallbackModel.trim() != model.trim()
    val modelFallbackAttempts = if (hasFallback) fallbackRetryCount.coerceIn(0, 10) + 1 else 0
    val bingFallbackAttempts =
        if (bingFallbackEnabled && provider.protocol != ProviderProtocol.MICROSOFT_EDGE_WEB) 1 else 0
    return primaryRetries + modelFallbackAttempts + bingFallbackAttempts
}

internal suspend fun translateWithBingFallback(
    provider: TranslationProvider,
    config: ProviderConfig,
    sourceText: String,
    onRetry: (Int) -> Unit = {},
    request: suspend (TranslationProvider, ProviderConfig) -> TranslationResult,
): TranslationResult {
    var lastRetryAttempt = 0
    val result =
        translateWithFallback(
            sourceText = sourceText,
            primaryModel = config.model,
            retryCount = config.retryCount,
            fallbackModel = config.fallbackModel.takeIf { config.fallbackEnabled }.orEmpty(),
            fallbackRetryCount = config.fallbackRetryCount,
            onRetry = { attempt ->
                lastRetryAttempt = attempt
                onRetry(attempt)
            },
        ) { model ->
            request(provider, config.copy(model = model))
        }
    if (
        result !is TranslationResult.Failure ||
            !config.bingFallbackEnabled ||
            provider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB
    )
        return result

    currentCoroutineContext().ensureActive()
    val bingProvider = providerById("microsoft_edge_web")
    val bingConfig =
        defaultProviderConfig(bingProvider).copy(
            timeoutSeconds = config.timeoutSeconds,
            retryCount = 0,
        )
    onRetry(lastRetryAttempt + 1)
    return translateWithRetries(sourceText, retryCount = 0) {
        request(bingProvider, bingConfig)
    }
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

internal fun isSupportedHttpEndpoint(value: String): Boolean =
    value.trim().lowercase().let { it.startsWith("https://") || it.startsWith("http://") }

internal suspend fun translateWithRetries(
    sourceText: String,
    retryCount: Int,
    onRetry: (Int) -> Unit = {},
    request: suspend () -> TranslationResult,
): TranslationResult {
    var lastFailure =
        TranslationResult.Failure(
            retryable = true,
            reason = TranslationFailureReason.EMPTY_RESPONSE,
        )
    repeat(retryCount.coerceIn(0, 10) + 1) { attempt ->
        if (attempt > 0) onRetry(attempt)
        when (val result = request()) {
            is TranslationResult.Success -> {
                val translated = result.text.trim()
                if (translated.isNotEmpty())
                    return TranslationResult.Success(translated)
                lastFailure =
                    TranslationResult.Failure(
                        retryable = true,
                        reason = TranslationFailureReason.EMPTY_RESPONSE,
                    )
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
