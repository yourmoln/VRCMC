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
            val result = translateWithRetries("Hello", 3) {
                attempts++
                if (attempts < 4) TranslationResult.Failure("empty", retryable = true)
                else TranslationResult.Success("你好")
            }

            assertEquals(4, attempts)
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
}
