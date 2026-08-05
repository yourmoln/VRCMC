package com.vrcmc.app

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TranslationRetryTest {
    @Test
    fun retriesThreeTimesAfterTheInitialAttempt() {
        runBlocking {
            var attempts = 0
            val retries = mutableListOf<Int>()
            val result = translateWithRetries("Hello", 3, retries::add) {
                attempts++
                if (attempts < 4) TranslationResult.Failure("empty", retryable = true)
                else TranslationResult.Success("你好")
            }

            assertEquals(4, attempts)
            assertEquals(listOf(1, 2, 3), retries)
            assertEquals(TranslationResult.Success("你好"), result)
        }
    }

    @Test
    fun stopsImmediatelyForNonRetryableErrors() {
        runBlocking {
            var attempts = 0
            val result = translateWithRetries("Hello", 3) {
                attempts++
                TranslationResult.Failure("invalid key", status = 401)
            }

            assertEquals(1, attempts)
            assertIs<TranslationResult.Failure>(result)
        }
    }

    @Test
    fun retriesWhenTheServiceReturnsTheSourceText() {
        runBlocking {
            var attempts = 0
            val result = translateWithRetries("Hello", 3) {
                attempts++
                if (attempts == 1) TranslationResult.Success("Hello") else TranslationResult.Success("你好")
            }

            assertEquals(2, attempts)
            assertEquals(TranslationResult.Success("你好"), result)
        }
    }

    @Test
    fun retriesWhenSuccessfulResponseHasNoUsableContent() {
        runBlocking {
            var attempts = 0
            val result = translateWithRetries("Hello", 3) {
                attempts++
                if (attempts == 1) {
                    TranslationResult.Failure("service returned no usable content", status = 200)
                } else {
                    TranslationResult.Success("Bonjour")
                }
            }

            assertEquals(2, attempts)
            assertEquals(TranslationResult.Success("Bonjour"), result)
        }
    }

    @Test
    fun switchesToFallbackOnlyAfterPrimaryRetriesAreExhausted() {
        runBlocking {
            val requestedModels = mutableListOf<String>()
            val retries = mutableListOf<Int>()
            val result = translateWithFallback(
                sourceText = "Hello",
                primaryModel = "deepseek-v4-flash",
                retryCount = 2,
                fallbackModel = "deepseek-v4-pro",
                fallbackRetryCount = 3,
                onRetry = retries::add,
            ) { model ->
                requestedModels += model
                if (requestedModels.size < 5) TranslationResult.Failure("unavailable", retryable = true)
                else TranslationResult.Success("你好")
            }

            assertEquals(
                listOf("deepseek-v4-flash", "deepseek-v4-flash", "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-pro"),
                requestedModels,
            )
            assertEquals(listOf(1, 2, 3, 4), retries)
            assertEquals(TranslationResult.Success("你好"), result)
        }
    }

    @Test
    fun doesNotUseFallbackForNonRetryableFailure() {
        runBlocking {
            val requestedModels = mutableListOf<String>()
            val result = translateWithFallback("Hello", "primary", 3, "fallback", 3) { model ->
                requestedModels += model
                TranslationResult.Failure("invalid key", status = 401)
            }

            assertEquals(listOf("primary"), requestedModels)
            assertIs<TranslationResult.Failure>(result)
        }
    }

    @Test
    fun successfulPrimaryNeverUsesFallback() {
        runBlocking {
            val requestedModels = mutableListOf<String>()
            val result = translateWithFallback("Hello", "primary", 3, "fallback", 3) { model ->
                requestedModels += model
                TranslationResult.Success("Bonjour")
            }

            assertEquals(listOf("primary"), requestedModels)
            assertEquals(TranslationResult.Success("Bonjour"), result)
        }
    }
}
