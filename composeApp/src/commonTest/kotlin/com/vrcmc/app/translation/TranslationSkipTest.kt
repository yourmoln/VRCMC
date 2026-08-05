package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationSkipTest {
    @Test
    fun detectsAsciiDigitsWithOptionalWhitespace() {
        assertTrue(isArabicDigitsOnly("123456"))
        assertTrue(isArabicDigitsOnly(" 123 456\n"))
        assertTrue(shouldSkipTranslation("123456"))
    }

    @Test
    fun doesNotSkipMixedOrEmptyText() {
        assertFalse(isArabicDigitsOnly("123abc"))
        assertFalse(isArabicDigitsOnly("12.5"))
        assertFalse(isArabicDigitsOnly("   "))
        assertFalse(shouldSkipTranslation("123abc"))
        assertFalse(shouldSkipTranslation("Hello!"))
        assertFalse(shouldSkipTranslation("你好👋 123!"))
        assertFalse(shouldSkipTranslation("   "))
    }

    @Test
    fun skipsPunctuationOnlyText() {
        assertTrue(shouldSkipTranslation("!?.,"))
        assertTrue(shouldSkipTranslation("12.5 👍🏽🎉 !@#"))
        assertTrue(shouldSkipTranslation("😀😂❤️"))
        assertTrue(shouldSkipTranslation(" 。，！？、\n"))
        assertTrue(shouldSkipTranslation("“”（）—…"))
        assertTrue(shouldSkipTranslation("!@#@$%#^$%$(*^^$%_)^+_{}||:<>?！@#@￥#%……￥……￥@￥@……@）（*%——@——#（{}{}：”》？《"))
    }
}
