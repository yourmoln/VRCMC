package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TranslationLanguagePage(state: AppState, strings: LocaleStrings) {
    val previewOriginal = "今晚一起玩吗？"
    val previewTranslations = state.languages.associateWith(::previewForLanguage)
    val visibleOutputOrder =
        state.outputOrder.filter { state.showOriginalText || it != originalOutputKey }
    val preview =
        buildTranslationOutput(
            previewOriginal,
            previewTranslations,
            state.outputOrder,
            state.lineBreakOutput,
            state.showOriginalText,
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(strings.languageSettings, style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            LanguageSettingsSection(strings.selectedLanguages, Icons.Default.Language) {
                availableTargetLanguages.forEach { language ->
                    val checked = language in state.languages
                    val enabled = checked || state.languages.size < 2
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(enabled = enabled) {
                                    if (checked && state.languages.size > 1)
                                        state.setLanguages(state.languages - language)
                                    else if (!checked)
                                        state.setLanguages(state.languages + language)
                                }
                                .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected ->
                                if (!selected && state.languages.size > 1)
                                    state.setLanguages(state.languages - language)
                                else if (selected && state.languages.size < 2)
                                    state.setLanguages(state.languages + language)
                            },
                            enabled = enabled,
                        )
                        Text(
                            language,
                            modifier = Modifier.weight(1f),
                            color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                        )
                    }
                }
            }
        }

        item {
            LanguageSettingsSection(strings.lineBreakOutput, Icons.AutoMirrored.Filled.WrapText) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.enableLineBreakOutput)
                        Text(
                            strings.lineBreakOutputHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.lineBreakOutput,
                        onCheckedChange = state::updateLineBreakOutput,
                    )
                }
            }
        }

        item {
            LanguageSettingsSection(strings.originalTextOutput, Icons.Default.Visibility) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.showOriginalText)
                        Text(
                            strings.showOriginalTextHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.showOriginalText,
                        onCheckedChange = state::updateShowOriginalText,
                    )
                }
            }
        }

        item {
            LanguageSettingsSection(strings.displayOrder, Icons.Default.DragHandle) {
                visibleOutputOrder.forEachIndexed { index, key ->
                    val label = if (key == originalOutputKey) strings.originalText else key
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(
                                    start = 14.dp,
                                    end = 4.dp,
                                    top = 6.dp,
                                    bottom = 6.dp,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Medium)
                                Text(
                                    if (key == originalOutputKey) previewOriginal
                                    else previewTranslations.getValue(key),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    state.setOutputOrder(
                                        state.outputOrder.reorderedVisibleItems(
                                            visibleOutputOrder.moved(index, index - 1)
                                        )
                                    )
                                },
                            ) {
                                Icon(Icons.Default.ArrowUpward, strings.moveUp)
                            }
                            IconButton(
                                enabled = index < visibleOutputOrder.lastIndex,
                                onClick = {
                                    state.setOutputOrder(
                                        state.outputOrder.reorderedVisibleItems(
                                            visibleOutputOrder.moved(index, index + 1)
                                        )
                                    )
                                },
                            ) {
                                Icon(Icons.Default.ArrowDownward, strings.moveDown)
                            }
                        }
                    }
                }
            }
        }

        item {
            LanguageSettingsSection(strings.effectPreview, Icons.Default.Translate) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        preview,
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        item {
            LanguageSettingsSection(strings.translationBehavior, Icons.AutoMirrored.Filled.Send) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.sendOriginalBeforeTranslation)
                        Text(
                            strings.sendOriginalBeforeTranslationHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.sendOriginalBeforeTranslation,
                        onCheckedChange = state::updateSendOriginalBeforeTranslation,
                    )
                }
            }
        }
    }
}

private fun List<String>.reorderedVisibleItems(visibleItems: List<String>): List<String> {
    val visibleSet = visibleItems.toSet()
    val reordered = visibleItems.iterator()
    return map { item -> if (item in visibleSet) reordered.next() else item }
}
