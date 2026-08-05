package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp

@Composable
internal fun AddDeviceDialog(state: AppState, strings: LocaleStrings, close: () -> Unit) {
    var endpoint by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.AddToQueue, null) },
        title = { Text(strings.addDevice) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.deviceIpHint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = {
                        endpoint = it
                        invalid = false
                    },
                    singleLine = true,
                    label = { Text(strings.deviceAddress) },
                    placeholder = { Text("9000:192.168.1.10:9001") },
                    supportingText = {
                        Text(if (invalid) strings.invalidDeviceAddress else strings.defaultPortHint)
                    },
                    isError = invalid,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (state.addDevice(endpoint)) close() else invalid = true },
                enabled = endpoint.isNotBlank(),
            ) {
                Text(strings.addDevice)
            }
        },
        dismissButton = { TextButton(close) { Text(strings.cancel) } },
    )
}
