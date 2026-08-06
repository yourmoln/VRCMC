package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun ApiRequestSettings(
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onUpdate: ((ProviderConfig) -> ProviderConfig) -> Unit,
    contentAfterRetry: @Composable () -> Unit,
) {
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
    contentAfterRetry()
    if (provider.supportsHeaders) {
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
    }
    if (provider.supportsStreaming) {
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
                checked = config.streaming,
                onCheckedChange = { enabled -> onUpdate { old -> old.copy(streaming = enabled) } },
            )
        }
    }
}
