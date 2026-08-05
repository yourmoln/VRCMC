package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun EditDeviceDialog(
    state: AppState,
    device: Device,
    strings: LocaleStrings,
    close: () -> Unit,
) {
    var address by remember(device) { mutableStateOf(device.address) }
    var receivePort by remember(device) { mutableStateOf(device.receivePort.toString()) }
    var sendPort by remember(device) { mutableStateOf(device.sendPort.toString()) }
    var invalid by remember(device) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.Edit, null) },
        title = { Text(strings.editDevice) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    address,
                    {
                        address = it
                        invalid = false
                    },
                    label = { Text(strings.ipAddress) },
                    singleLine = true,
                    isError = invalid,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        receivePort,
                        {
                            receivePort = it.filter(Char::isDigit)
                            invalid = false
                        },
                        Modifier.weight(1f),
                        label = { Text(strings.receivePort) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = invalid,
                    )
                    OutlinedTextField(
                        sendPort,
                        {
                            sendPort = it.filter(Char::isDigit)
                            invalid = false
                        },
                        Modifier.weight(1f),
                        label = { Text(strings.sendPort) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = invalid,
                    )
                }
                if (invalid)
                    Text(
                        strings.invalidDeviceAddress,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
            }
        },
        confirmButton = {
            TextButton({
                if (state.updateDevice(device, address, receivePort, sendPort)) close()
                else invalid = true
            }) {
                Text(strings.save)
            }
        },
        dismissButton = { TextButton(close) { Text(strings.cancel) } },
    )
}
