package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class TranslationBingFallbackTest {
    private val provider = providerById("qianwen")
    private val config =
        defaultProviderConfig(provider).copy(
            apiKey = "primary-secret",
            customHeaders = "Authorization: Bearer primary-secret",
            retryCount = 2,
            fallbackEnabled = true,
            fallbackModel = "qwen-mt-flash",
            fallbackRetryCount = 1,
            bingFallbackEnabled = true,
        )
    private val unavailable = TranslationResult.Failure("unavailable", retryable = true)

    @Test
    fun usesBingOnlyAfterPrimaryAndFallbackRetriesAreExhausted() = runBlocking {
        val models = mutableListOf<String>()
        val retries = mutableListOf<Int>()
        val result =
            translateWithBingFallback(provider, config, "Hello", retries::add) {
                requestProvider, requestConfig ->
                models += requestConfig.model
                if (requestProvider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB)
                    TranslationResult.Success("  你好  ")
                else unavailable
            }

        assertEquals(
            listOf(
                "qwen-mt-plus", "qwen-mt-plus", "qwen-mt-plus",
                "qwen-mt-flash", "qwen-mt-flash", "microsoft-edge-web",
            ),
            models,
        )
        assertEquals(listOf(1, 2, 3, 4, 5), retries)
        assertEquals(retries.last(), config.totalRetryCount(provider))
        assertEquals(TranslationResult.Success("你好"), result)
    }

    @Test
    fun disabledBingFallbackPreservesTheLastModelFailure() = runBlocking {
        val requestedProviders = mutableListOf<String>()
        val result =
            translateWithBingFallback(provider, config.copy(bingFallbackEnabled = false), "Hello") {
                requestProvider, _ ->
                requestedProviders += requestProvider.id
                unavailable
            }

        assertEquals(List(5) { provider.id }, requestedProviders)
        assertEquals(unavailable, result)
        assertEquals(4, config.copy(bingFallbackEnabled = false).totalRetryCount(provider))
    }

    @Test
    fun successfulPrimaryOrFallbackNeverCallsBing() = runBlocking {
        for (successfulModel in listOf(config.model, config.fallbackModel)) {
            val models = mutableListOf<String>()
            val result =
                translateWithBingFallback(provider, config, "Hello") { requestProvider, requestConfig ->
                    assertEquals(provider, requestProvider)
                    models += requestConfig.model
                    if (requestConfig.model == successfulModel) TranslationResult.Success("你好")
                    else unavailable
                }

            val expectedModels =
                if (successfulModel == config.model) listOf(config.model)
                else List(3) { config.model } + config.fallbackModel
            assertEquals(expectedModels, models)
            assertEquals(TranslationResult.Success("你好"), result)
        }
    }

    @Test
    fun bingDoesNotRequireAnEnabledOrValidFallbackModel() = runBlocking {
        val configs =
            listOf(
                config.copy(fallbackEnabled = false),
                config.copy(fallbackModel = ""),
                config.copy(fallbackModel = " ${config.model} "),
            )
        for (currentConfig in configs) {
            val models = mutableListOf<String>()
            val retries = mutableListOf<Int>()
            val result =
                translateWithBingFallback(provider, currentConfig, "Hello", retries::add) {
                    requestProvider, requestConfig ->
                    models += requestConfig.model
                    if (requestProvider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB)
                        TranslationResult.Success("你好")
                    else unavailable
                }

            assertEquals(List(3) { config.model } + "microsoft-edge-web", models)
            assertEquals(listOf(1, 2, 3), retries)
            assertEquals(retries.last(), currentConfig.totalRetryCount(provider))
            assertEquals(TranslationResult.Success("你好"), result)
        }
    }

    @Test
    fun nonRetryableProviderErrorsCanStillUseBing() = runBlocking {
        val providers = mutableListOf<String>()
        val retries = mutableListOf<Int>()
        val result =
            translateWithBingFallback(provider, config, "Hello", retries::add) { requestProvider, _ ->
                providers += requestProvider.id
                if (requestProvider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB)
                    TranslationResult.Success("你好")
                else TranslationResult.Failure("invalid key", status = 401)
            }

        assertEquals(listOf(provider.id, "microsoft_edge_web"), providers)
        assertEquals(listOf(1), retries)
        assertEquals(TranslationResult.Success("你好"), result)
    }

    @Test
    fun bingUsesItsOwnEndpointWithoutPrimaryCredentialsOrHeaders() = runBlocking {
        var bingCalls = 0
        val primaryConfig = config.copy(timeoutSeconds = 42, streaming = true)
        translateWithBingFallback(provider, primaryConfig, "Hello") { requestProvider, requestConfig ->
            if (requestProvider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB) {
                bingCalls++
                assertEquals("https://edge.microsoft.com/translate/translatetext", requestConfig.baseUrl)
                assertEquals("microsoft-edge-web", requestConfig.model)
                assertEquals("", requestConfig.apiKey)
                assertEquals("", requestConfig.customHeaders)
                assertEquals(42, requestConfig.timeoutSeconds)
                assertEquals(0, requestConfig.retryCount)
                assertFalse(requestConfig.streaming)
                assertFalse(requestConfig.fallbackEnabled)
                assertFalse(requestConfig.bingFallbackEnabled)
                TranslationResult.Success("你好")
            } else unavailable
        }
        assertEquals(1, bingCalls)
    }

    @Test
    fun failedBingRequestIsReturnedWithoutFurtherAttempts() = runBlocking {
        var attempts = 0
        val bingFailure = TranslationResult.Failure("Bing unavailable", status = 503, retryable = true)
        val result =
            translateWithBingFallback(provider, config, "Hello") { requestProvider, _ ->
                attempts++
                if (requestProvider.protocol == ProviderProtocol.MICROSOFT_EDGE_WEB) bingFailure
                else unavailable
            }

        assertEquals(6, attempts)
        assertEquals(bingFailure, result)
    }

    @Test
    fun emptyResponsesExhaustModelRetriesAndCannotCountAsBingSuccess() = runBlocking {
        var attempts = 0
        val result =
            translateWithBingFallback(provider, config, "Hello") { _, _ ->
                attempts++
                TranslationResult.Success(" \n ")
            }

        assertEquals(6, attempts)
        assertEquals(TranslationFailureReason.EMPTY_RESPONSE, assertIs<TranslationResult.Failure>(result).reason)
    }

    @Test
    fun bingProviderDoesNotFallBackToItself() = runBlocking {
        val bingProvider = providerById("microsoft_edge_web")
        val bingConfig = defaultProviderConfig(bingProvider).copy(retryCount = 2, bingFallbackEnabled = true)
        var attempts = 0
        val result =
            translateWithBingFallback(bingProvider, bingConfig, "Hello") { requestProvider, _ ->
                assertEquals(bingProvider, requestProvider)
                attempts++
                unavailable
            }

        assertEquals(3, attempts)
        assertEquals(2, bingConfig.totalRetryCount(bingProvider))
        assertEquals(unavailable, result)
    }

    @Test
    fun cancellationStopsTheEntireFallbackChain() = runBlocking {
        for (cancelOnAttempt in listOf(1, 4, 6)) {
            var attempts = 0
            assertFailsWith<CancellationException> {
                translateWithBingFallback(provider, config, "Hello") { _, _ ->
                    attempts++
                    if (attempts == cancelOnAttempt) throw CancellationException("cancelled")
                    unavailable
                }
            }
            assertEquals(cancelOnAttempt, attempts)
        }
    }
}
