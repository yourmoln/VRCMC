package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val availableTargetLanguages = listOf("English", "简体中文", "繁體中文", "日本語", "한국어", "Español", "Français", "Deutsch", "Русский")

@Composable
fun TranslationLanguagePage(state: AppState, strings: LocaleStrings) {
    val previewOriginal = "今晚一起玩吗？"
    val previewTranslations = state.languages.associateWith(::previewForLanguage)
    val preview = buildTranslationOutput(previewOriginal, previewTranslations, state.outputOrder)

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = enabled) {
                                if (checked && state.languages.size > 1) state.setLanguages(state.languages - language)
                                else if (!checked) state.setLanguages(state.languages + language)
                            }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected ->
                                if (!selected && state.languages.size > 1) state.setLanguages(state.languages - language)
                                else if (selected && state.languages.size < 2) state.setLanguages(state.languages + language)
                            },
                            enabled = enabled,
                        )
                        Text(
                            language,
                            modifier = Modifier.weight(1f),
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
                        )
                    }
                }
            }
        }

        item {
            LanguageSettingsSection(strings.displayOrder, Icons.Default.DragHandle) {
                state.outputOrder.forEachIndexed { index, key ->
                    val label = if (key == originalOutputKey) strings.originalText else key
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.Medium)
                                Text(
                                    if (key == originalOutputKey) previewOriginal else previewTranslations.getValue(key),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = { state.setOutputOrder(state.outputOrder.moved(index, index - 1)) },
                            ) { Icon(Icons.Default.ArrowUpward, strings.moveUp) }
                            IconButton(
                                enabled = index < state.outputOrder.lastIndex,
                                onClick = { state.setOutputOrder(state.outputOrder.moved(index, index + 1)) },
                            ) { Icon(Icons.Default.ArrowDownward, strings.moveDown) }
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
                    Text(preview, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun LanguageSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

private fun previewForLanguage(language: String): String = when (language) {
    "English" -> "Want to play together tonight?"
    "简体中文" -> "今晚一起玩吗？"
    "繁體中文" -> "今晚一起玩嗎？"
    "日本語" -> "今夜一緒に遊びませんか？"
    "한국어" -> "오늘 밤 같이 놀래요?"
    "Español" -> "¿Jugamos juntos esta noche?"
    "Français" -> "On joue ensemble ce soir ?"
    "Deutsch" -> "Spielen wir heute Abend zusammen?"
    "Русский" -> "Поиграем вместе сегодня вечером?"
    else -> language
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().apply {
    add(to, removeAt(from))
}
