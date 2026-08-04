package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationSkipTest {
    @Test
    fun detectsAsciiDigitsWithOptionalWhitespace() {
        assertTrue(isArabicDigitsOnly("123456"))
        assertTrue(isArabicDigitsOnly(" 123 456\n"))
    }

    @Test
    fun doesNotSkipMixedOrEmptyText() {
        assertFalse(isArabicDigitsOnly("123abc"))
        assertFalse(isArabicDigitsOnly("12.5"))
        assertFalse(isArabicDigitsOnly("   "))
    }
}
