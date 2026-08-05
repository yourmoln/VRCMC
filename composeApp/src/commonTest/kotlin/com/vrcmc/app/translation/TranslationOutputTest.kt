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
}
