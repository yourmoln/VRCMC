package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals

class TranslationOutputTest {
    @Test
    fun defaultsToTranslationsBeforeOriginal() {
        val order = normalizeOutputOrder(listOf("English", "日本語"), emptyList())
        assertEquals(listOf("English", "日本語", originalOutputKey), order)
        assertEquals(
            "Good evening\nこんばんは\n晚上好",
            buildTranslationOutput(
                "晚上好",
                mapOf("English" to "Good evening", "日本語" to "こんばんは"),
                order,
            ),
        )
    }

    @Test
    fun appliesCustomOrderWithoutLanguageLabels() {
        val order = listOf(originalOutputKey, "日本語", "English")
        assertEquals(
            "晚上好\nこんばんは\nGood evening",
            buildTranslationOutput(
                "晚上好",
                mapOf("English" to "Good evening", "日本語" to "こんばんは"),
                order,
            ),
        )
    }

    @Test
    fun inlineOutputWrapsTheSecondEffectiveItemInAsciiParentheses() {
        assertEquals(
            "Original(Hello)Konnichiwa",
            buildTranslationOutput(
                original = "Original",
                translations = mapOf("English" to "Hello", "Japanese" to "Konnichiwa"),
                outputOrder = listOf(originalOutputKey, "English", "Japanese"),
                lineBreakOutput = false,
            ),
        )
    }

    @Test
    fun inlineOutputIgnoresMissingItemsBeforeApplyingParentheses() {
        assertEquals(
            "Original(Hello)",
            buildTranslationOutput(
                original = "Original",
                translations = mapOf("English" to "Hello"),
                outputOrder = listOf(originalOutputKey, "French", "English"),
                lineBreakOutput = false,
            ),
        )
    }

    @Test
    fun hidesOriginalTextAfterTranslationWhenDisabled() {
        assertEquals(
            "Hello\nKonnichiwa",
            buildTranslationOutput(
                original = "Original",
                translations = mapOf("English" to "Hello", "Japanese" to "Konnichiwa"),
                outputOrder = listOf("English", originalOutputKey, "Japanese"),
                showOriginalText = false,
            ),
        )
    }

    @Test
    fun inlineOutputAppliesParenthesesAfterHiddenOriginalIsFiltered() {
        assertEquals(
            "Hello(Konnichiwa)",
            buildTranslationOutput(
                original = "Original",
                translations = mapOf("English" to "Hello", "Japanese" to "Konnichiwa"),
                outputOrder = listOf("English", originalOutputKey, "Japanese"),
                lineBreakOutput = false,
                showOriginalText = false,
            ),
        )
    }
}
