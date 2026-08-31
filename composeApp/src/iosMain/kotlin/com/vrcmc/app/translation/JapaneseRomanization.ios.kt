@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.vrcmc.app

import kotlinx.cinterop.useContents
import platform.CoreFoundation.CFLocaleCreate
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFStringTokenizerAdvanceToNextToken
import platform.CoreFoundation.CFStringTokenizerCopyCurrentTokenAttribute
import platform.CoreFoundation.CFStringTokenizerCreate
import platform.CoreFoundation.CFStringTokenizerGetCurrentTokenRange
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFStringTokenizerAttributeLatinTranscription
import platform.CoreFoundation.kCFStringTokenizerTokenNone
import platform.CoreFoundation.kCFStringTokenizerUnitWord
import platform.Foundation.CFBridgingRelease

internal actual fun platformJapaneseRubySegments(text: String): List<JapaneseRubySegment> {
    if (text.isEmpty()) return emptyList()
    return text.withCFString { cfText ->
        "ja_JP".withCFString localeBlock@{ localeIdentifier ->
            val locale =
                CFLocaleCreate(null, localeIdentifier)
                    ?: return@localeBlock listOf(JapaneseRubySegment(text))
            val tokenizer =
                CFStringTokenizerCreate(
                    alloc = null,
                    string = cfText,
                    range = CFRangeMake(0, CFStringGetLength(cfText)),
                    options = kCFStringTokenizerUnitWord,
                    locale = locale,
                )
                    ?: run {
                        CFRelease(locale)
                        return@localeBlock listOf(JapaneseRubySegment(text))
                    }
            try {
                val tokens = mutableListOf<JapaneseReadingToken>()
                while (
                    CFStringTokenizerAdvanceToNextToken(tokenizer) !=
                        kCFStringTokenizerTokenNone
                ) {
                    val range = CFStringTokenizerGetCurrentTokenRange(tokenizer)
                    range.useContents {
                        val start = location.toInt()
                        val end = (start + length.toInt()).coerceAtMost(text.length)
                        if (start !in 0 until end) return@useContents
                        val surface = text.substring(start, end)
                        val transcription =
                            if (surface.hasJapaneseScript()) {
                                when (surface) {
                                    "は" -> "wa"
                                    "へ" -> "e"
                                    "を" -> "o"
                                    else -> tokenizer.latinTranscription()
                                }
                            } else {
                                null
                            }
                        tokens +=
                            JapaneseReadingToken(
                                position = start,
                                surface = surface,
                                pronunciation = transcription,
                            )
                    }
                }
                assembleJapaneseRubySegments(text, tokens) { it }
            } finally {
                CFRelease(tokenizer)
                CFRelease(locale)
            }
        } ?: listOf(JapaneseRubySegment(text))
    } ?: listOf(JapaneseRubySegment(text))
}

private fun platform.CoreFoundation.CFStringTokenizerRef.latinTranscription(): String? {
    val value =
        CFStringTokenizerCopyCurrentTokenAttribute(
            this,
            kCFStringTokenizerAttributeLatinTranscription,
        ) ?: return null
    return (CFBridgingRelease(value) as? String)?.trim()?.takeIf(String::isNotEmpty)
}

private inline fun <T> String.withCFString(block: (CFStringRef) -> T): T? {
    val value =
        CFStringCreateWithCString(
            alloc = null,
            cStr = this,
            encoding = kCFStringEncodingUTF8,
        ) ?: return null
    return try {
        block(value)
    } finally {
        CFRelease(value)
    }
}
