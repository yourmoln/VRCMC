package com.vrcmc.app

import com.atilika.kuromoji.ipadic.Tokenizer
import dev.esnault.wanakana.core.Wanakana

private val japaneseTokenizer: Tokenizer by lazy(::Tokenizer)

internal actual fun platformJapaneseRubySegments(text: String): List<JapaneseRubySegment> =
    assembleJapaneseRubySegments(
        text = text,
        tokens = japaneseReadingTokens(text),
        romanizePronunciation = ::romanizeJapanesePronunciation,
    )

private fun japaneseReadingTokens(text: String): List<JapaneseReadingToken> =
    japaneseTokenizer.tokenize(text).map { token ->
        val surface = token.surface
        val partOfSpeech1 = token.partOfSpeechLevel1
        val partOfSpeech2 = token.partOfSpeechLevel2
        val whitespace = surface.any(Char::isWhitespace)
        val punctuation = partOfSpeech1 == "記号" && !whitespace
        val pronunciation =
            when {
                whitespace || punctuation -> null
                surface == "を" && partOfSpeech1 == "助詞" -> "オ"
                else -> token.pronunciation?.takeUnless { it.isBlank() || it == "*" }
            }
        JapaneseReadingToken(
            position = token.position,
            surface = surface,
            pronunciation = pronunciation,
            joinsPrevious =
                partOfSpeech1 == "助動詞" ||
                    partOfSpeech2 == "接続助詞" ||
                    partOfSpeech2 == "接尾",
            joinsNext = partOfSpeech1 == "接頭詞" || partOfSpeech2 == "接頭",
            punctuation = punctuation,
            contractsFinalOu =
                surface == "う" && partOfSpeech1 == "助動詞",
        )
    }

private fun romanizeJapanesePronunciation(pronunciation: String): String {
    val placeholder = '\uE000'
    val converted = Wanakana.toRomaji(pronunciation.replace('ー', placeholder))
    return buildString(converted.length) {
        converted.forEach { char ->
            if (char != placeholder) {
                append(char)
            } else if (isNotEmpty()) {
                val macron =
                    when (last()) {
                        'a' -> 'ā'
                        'i' -> 'ī'
                        'u' -> 'ū'
                        'e' -> 'ē'
                        'o' -> 'ō'
                        else -> null
                    }
                if (macron != null) setCharAt(lastIndex, macron)
            }
        }
    }
}
