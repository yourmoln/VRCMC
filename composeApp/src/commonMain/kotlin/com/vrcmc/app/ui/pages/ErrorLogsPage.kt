package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun ErrorLogsPage(state: AppState, strings: LocaleStrings) {
    val confirmClear = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.errorLogs,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.errorLogs.isNotEmpty())
                IconButton({ confirmClear.value = true }) {
                    Icon(Icons.Default.DeleteSweep, strings.clearErrorLogs)
                }
        }
        Spacer(Modifier.height(8.dp))
        if (state.errorLogs.isEmpty())
            Text(strings.noErrorLogs, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.errorLogs.asReversed()) { log ->
                    SelectionContainer {
                        Text(
                            log.message,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider()
                }
            }
    }
    if (confirmClear.value)
        AlertDialog(
            onDismissRequest = { confirmClear.value = false },
            title = { Text(strings.clearErrorLogs) },
            text = { Text(strings.clearErrorLogsMessage) },
            confirmButton = {
                TextButton({
                    state.clearErrorLogs()
                    confirmClear.value = false
                }) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ confirmClear.value = false }) { Text(strings.cancel) } },
        )
}
