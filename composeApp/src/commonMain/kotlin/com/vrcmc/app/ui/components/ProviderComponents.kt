package com.vrcmc.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val recommendedProviderIds = setOf("deepseek", "qianwen", "gemini", "openai", "local_ai")

@Composable
internal fun ProviderPickerDialog(
    selectedId: String,
    strings: LocaleStrings,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(query) {
            translationProviders
                .filter {
                    query.isBlank() ||
                        it.label.contains(query, true) ||
                        it.id.contains(query, true) ||
                        it.protocol.displayName().contains(query, true)
                }
                .sortedWith(
                    compareByDescending<TranslationProvider> { it.id in recommendedProviderIds }
                        .thenBy { it.label }
                )
        }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(.88f).widthIn(max = 540.dp).heightIn(max = 460.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings.chooseProvider,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            strings.providerPickerHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onDismiss) { Icon(Icons.Default.Close, strings.done) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(strings.searchProvider) },
                    shape = CircleShape,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filtered, key = { it.id }) { option ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onSelect(option.id) },
                            color =
                                if (option.id == selectedId)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProviderAvatar(option.label)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(option.label, fontWeight = FontWeight.Medium)
                                        if (option.id in recommendedProviderIds) {
                                            Spacer(Modifier.width(6.dp))
                                            SuggestionChip(
                                                {},
                                                {
                                                    Text(
                                                        strings.recommended,
                                                        style = MaterialTheme.typography.labelSmall,
                                                    )
                                                },
                                                Modifier.height(26.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        option.protocol.displayName(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (option.id == selectedId)
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProviderAvatar(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Text(
                label.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

internal fun ProviderProtocol.displayName() =
    when (this) {
        ProviderProtocol.OPENAI -> "OpenAI-compatible"
        ProviderProtocol.ANTHROPIC -> "Anthropic Messages"
        ProviderProtocol.GOOGLE_WEB -> "Public Web API"
        ProviderProtocol.MYMEMORY -> "Translation Memory API"
        ProviderProtocol.DEEPL -> "DeepL REST API"
        ProviderProtocol.LIBRE -> "LibreTranslate REST API"
    }
