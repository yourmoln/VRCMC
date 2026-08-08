package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class TranslationRetryTest {
    @Test
    fun buildsQwenMtRequestWithItsDedicatedTranslationContract() {
        val provider = providerById("qianwen")
        val body =
            buildOpenAiRequestBody(
                provider,
                defaultProviderConfig(provider).copy(model = "qwen-mt-plus"),
                "简体中文",
                "今日はちょっと眠いかも",
            )

        val messages = body.getValue("messages").jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages.single().jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals(
            "今日はちょっと眠いかも",
            messages.single().jsonObject.getValue("content").jsonPrimitive.content,
        )
        val options = body.getValue("translation_options").jsonObject
        assertEquals("auto", options.getValue("source_lang").jsonPrimitive.content)
        assertEquals("zh", options.getValue("target_lang").jsonPrimitive.content)
        assertFalse("temperature" in body)
    }

    @Test
    fun regularOpenAiRequestStillUsesSystemAndUserMessages() {
        val provider = providerById("openai")
        val body =
            buildOpenAiRequestBody(
                provider,
                defaultProviderConfig(provider),
                "English",
                "你好",
            )

        val messages = body.getValue("messages").jsonArray
        assertEquals(listOf("system", "user"), messages.map { it.jsonObject.getValue("role").jsonPrimitive.content })
        assertTrue("temperature" in body)
        assertFalse("translation_options" in body)
    }

    @Test
    fun parsesCompletedOpenAiOutput() {
        val output =
            parseOpenAiResponse(
                """{"choices":[{"message":{"content":"complete"},"finish_reason":"stop"}]}"""
            )

        assertEquals("complete", output?.content)
    }

    @Test
    fun retriesThreeTimesAfterTheInitialAttempt() {
        runBlocking {
            var attempts = 0
            val retries = mutableListOf<Int>()
            val result =
                translateWithRetries("Hello", 3, retries::add) {
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
            val result =
                translateWithRetries("Hello", 3) {
                    attempts++
                    TranslationResult.Failure("invalid key", status = 401)
                }

            assertEquals(1, attempts)
            assertIs<TranslationResult.Failure>(result)
        }
    }

    @Test
    fun retriesWhenSuccessfulResponseHasNoUsableContent() {
        runBlocking {
            var attempts = 0
            val result =
                translateWithRetries("Hello", 3) {
                    attempts++
                    if (attempts == 1) {
                        TranslationResult.Failure(
                            "service returned no usable content",
                            status = 200,
                        )
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
            val result =
                translateWithFallback(
                    sourceText = "Hello",
                    primaryModel = "deepseek-v4-flash",
                    retryCount = 2,
                    fallbackModel = "deepseek-v4-pro",
                    fallbackRetryCount = 3,
                    onRetry = retries::add,
                ) { model ->
                    requestedModels += model
                    if (requestedModels.size < 5)
                        TranslationResult.Failure("unavailable", retryable = true)
                    else TranslationResult.Success("你好")
                }

            assertEquals(
                listOf(
                    "deepseek-v4-flash",
                    "deepseek-v4-flash",
                    "deepseek-v4-flash",
                    "deepseek-v4-pro",
                    "deepseek-v4-pro",
                ),
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
            val result =
                translateWithFallback("Hello", "primary", 3, "fallback", 3) { model ->
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
            val result =
                translateWithFallback("Hello", "primary", 3, "fallback", 3) { model ->
                    requestedModels += model
                    TranslationResult.Success("Bonjour")
                }

            assertEquals(listOf("primary"), requestedModels)
            assertEquals(TranslationResult.Success("Bonjour"), result)
        }
    }
}
