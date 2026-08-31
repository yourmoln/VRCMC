package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun JapaneseMessageText(
    message: ChatMessage,
    showJapaneseRomaji: Boolean,
) {
    if (!shouldShowJapaneseRomaji(message, showJapaneseRomaji)) {
        Text(message.text, style = MaterialTheme.typography.bodyLarge)
        return
    }

    var segments by remember(message.text) { mutableStateOf<List<JapaneseRubySegment>?>(null) }
    LaunchedEffect(message.text) {
        segments = JapaneseRomanizer.romanize(message.text)
    }
    val annotated = segments?.takeIf { it.any { segment -> segment.romaji != null } }
    if (annotated == null) {
        Text(message.text, style = MaterialTheme.typography.bodyLarge)
    } else {
        JapaneseRubyText(message.text, annotated)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JapaneseRubyText(
    originalText: String,
    segments: List<JapaneseRubySegment>,
) {
    val romajiStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    val japaneseStyle = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.sp)
    val contentColor = LocalContentColor.current
    Column(
        modifier =
            Modifier.clearAndSetSemantics {
                text = AnnotatedString(originalText)
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        splitJapaneseRubyLines(segments).forEach { line ->
            if (line.isEmpty()) {
                Text("", minLines = 1, style = japaneseStyle)
            } else if (line.none { it.romaji != null }) {
                Text(
                    line.joinToString(separator = "", transform = JapaneseRubySegment::surface),
                    style = japaneseStyle,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.Start,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    line.forEach { segment ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val romaji = segment.romaji
                            if (romaji == null) {
                                Text("", minLines = 1, maxLines = 1, style = romajiStyle)
                            } else {
                                Text(
                                    romaji,
                                    color = contentColor.copy(alpha = .72f),
                                    style = romajiStyle,
                                )
                            }
                            Text(segment.surface, style = japaneseStyle)
                        }
                    }
                }
            }
        }
    }
}

internal fun splitJapaneseRubyLines(
    segments: List<JapaneseRubySegment>
): List<List<JapaneseRubySegment>> {
    val lines = mutableListOf(mutableListOf<JapaneseRubySegment>())
    segments.forEach { segment ->
        val parts = segment.surface.split('\n')
        parts.forEachIndexed { index, part ->
            if (part.isNotEmpty()) {
                lines.last() +=
                    segment.copy(
                        surface = part,
                        romaji = segment.romaji.takeIf { parts.size == 1 },
                    )
            }
            if (index < parts.lastIndex) lines += mutableListOf<JapaneseRubySegment>()
        }
    }
    return lines
}
