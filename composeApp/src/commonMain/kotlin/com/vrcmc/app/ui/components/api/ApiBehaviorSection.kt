package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ApiBehaviorSection(
    state: AppState,
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onUpdate: ((ProviderConfig) -> ProviderConfig) -> Unit,
) {
    var advanced by remember(provider.id) { mutableStateOf(false) }
    SettingsCard(strings.translationBehavior, Icons.Default.Tune) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(strings.translateBeforeSending)
                Text(
                    strings.translateBehaviorHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                state.translate,
                {
                    state.translate = it
                    state.persistTranslation()
                },
            )
        }
        HorizontalDivider()
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text(strings.advancedSettings, Modifier.weight(1f))
            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (advanced) {
            OutlinedTextField(
                value = config.timeoutSeconds.toString(),
                onValueChange = { value ->
                    value.filter(Char::isDigit).toIntOrNull()?.let { timeout ->
                        onUpdate { old -> old.copy(timeoutSeconds = timeout.coerceIn(3, 300)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.requestTimeout) },
                suffix = { Text("s") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(strings.timeoutHint) },
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.retryCount.toString(),
                onValueChange = { value ->
                    value.filter(Char::isDigit).toIntOrNull()?.let { retries ->
                        onUpdate { old -> old.copy(retryCount = retries.coerceIn(0, 10)) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.retryCount) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(strings.retryCountHint) },
                shape = MaterialTheme.shapes.large,
            )
            if (provider.supportsHeaders)
                OutlinedTextField(
                    value = config.customHeaders,
                    onValueChange = { onUpdate { old -> old.copy(customHeaders = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(strings.customHeaders) },
                    placeholder = { Text("X-Provider: value") },
                    supportingText = { Text(strings.customHeadersHint) },
                    shape = MaterialTheme.shapes.large,
                )
            if (provider.supportsStreaming)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.streamingResponse)
                        Text(
                            strings.streamingHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        config.streaming,
                        { enabled -> onUpdate { old -> old.copy(streaming = enabled) } },
                    )
                }
        }
    }
}
