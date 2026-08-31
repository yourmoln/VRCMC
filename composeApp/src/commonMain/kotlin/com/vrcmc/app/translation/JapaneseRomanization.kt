package com.vrcmc.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class JapaneseRubySegment(
    val surface: String,
    val romaji: String? = null,
)

internal data class JapaneseReadingToken(
    val position: Int,
    val surface: String,
    val pronunciation: String?,
    val joinsPrevious: Boolean = false,
    val joinsNext: Boolean = false,
    val punctuation: Boolean = false,
    val contractsFinalOu: Boolean = false,
)

private data class MutableJapaneseWord(
    val position: Int,
    val surface: StringBuilder,
    val pronunciation: StringBuilder?,
    var joinsNext: Boolean,
    var contractsFinalOu: Boolean,
)

internal fun assembleJapaneseRubySegments(
    text: String,
    tokens: List<JapaneseReadingToken>,
    romanizePronunciation: (String) -> String?,
): List<JapaneseRubySegment> {
    if (text.isEmpty()) return emptyList()
    val words = mutableListOf<MutableJapaneseWord>()
    var cursor = 0

    fun appendGap(end: Int) {
        if (end <= cursor) return
        words +=
            MutableJapaneseWord(
                position = cursor,
                surface = StringBuilder(text.substring(cursor, end)),
                pronunciation = null,
                joinsNext = false,
                contractsFinalOu = false,
            )
        cursor = end
    }

    tokens.sortedBy(JapaneseReadingToken::position).forEach { token ->
        val position = token.position.coerceIn(cursor, text.length)
        appendGap(position)
        val surface = token.surface.takeIf(String::isNotEmpty) ?: return@forEach
        val end = (position + surface.length).coerceAtMost(text.length)
        val previous = words.lastOrNull()
        val adjacent = previous != null && previous.position + previous.surface.length == position
        val canJoinPrevious =
            adjacent &&
                (token.punctuation ||
                    (previous?.pronunciation != null &&
                        token.pronunciation != null &&
                        (token.joinsPrevious || previous.joinsNext)))

        if (canJoinPrevious) {
            checkNotNull(previous)
            previous.surface.append(surface)
            if (!token.punctuation) {
                token.pronunciation?.let { previous.pronunciation?.append(it) }
            }
            previous.joinsNext = token.joinsNext
            previous.contractsFinalOu =
                previous.contractsFinalOu || token.contractsFinalOu
        } else {
            words +=
                MutableJapaneseWord(
                    position = position,
                    surface = StringBuilder(surface),
                    pronunciation = token.pronunciation?.let(::StringBuilder),
                    joinsNext = token.joinsNext,
                    contractsFinalOu = token.contractsFinalOu,
                )
        }
        cursor = maxOf(cursor, end)
    }
    appendGap(text.length)

    return words.map { word ->
        val romanized =
            word.pronunciation
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.let(romanizePronunciation)
                ?.trim()
                ?.lowercase()
                ?.let { value ->
                    if (word.contractsFinalOu && value.endsWith("ou")) {
                        value.dropLast(2) + "ō"
                    } else {
                        value
                    }
                }
                ?.takeIf { it.isNotBlank() && !it.equals(word.surface.toString(), true) }
        JapaneseRubySegment(word.surface.toString(), romanized)
    }
}

internal expect fun platformJapaneseRubySegments(text: String): List<JapaneseRubySegment>

internal fun String.hasJapaneseScript(): Boolean =
    any { char ->
        char in '\u3040'..'\u30ff' ||
            char in '\u31f0'..'\u31ff' ||
            char in '\u3400'..'\u4dbf' ||
            char in '\u4e00'..'\u9fff' ||
            char in '\uf900'..'\ufaff'
    }

private fun attachTrailingSymbols(
    segments: List<JapaneseRubySegment>
): List<JapaneseRubySegment> {
    val result = mutableListOf<JapaneseRubySegment>()
    segments.forEach { segment ->
        val trailingSymbol =
            segment.romaji == null &&
                segment.surface.isNotEmpty() &&
                segment.surface.none(Char::isWhitespace) &&
                segment.surface.none(Char::isLetterOrDigit)
        val previous = result.lastOrNull()
        if (trailingSymbol && previous != null && previous.surface.none(Char::isWhitespace)) {
            result[result.lastIndex] = previous.copy(surface = previous.surface + segment.surface)
        } else {
            result += segment
        }
    }
    return result
}

internal object JapaneseRomanizer {
    private const val maxCacheEntries = 200
    private val cacheMutex = Mutex()
    private val cache = LinkedHashMap<String, List<JapaneseRubySegment>>()

    suspend fun romanize(text: String): List<JapaneseRubySegment> {
        if (text.isEmpty()) return emptyList()
        cacheMutex.withLock { cache[text] }?.let { return it }

        val computed =
            withContext(Dispatchers.Default) {
                runCatching { attachTrailingSymbols(platformJapaneseRubySegments(text)) }
                    .getOrElse { listOf(JapaneseRubySegment(text)) }
                    .takeIf { segments ->
                        segments.joinToString(separator = "", transform = JapaneseRubySegment::surface) ==
                            text
                    }
                    ?: listOf(JapaneseRubySegment(text))
            }

        cacheMutex.withLock {
            cache[text] = computed
            while (cache.size > maxCacheEntries) cache.remove(cache.keys.first())
        }
        return computed
    }
}

internal fun shouldShowJapaneseRomaji(message: ChatMessage, enabled: Boolean): Boolean =
    enabled &&
        !message.isLoading &&
        message.role == MessageRole.ASSISTANT &&
        languageCode(message.language.orEmpty()) == "ja"
