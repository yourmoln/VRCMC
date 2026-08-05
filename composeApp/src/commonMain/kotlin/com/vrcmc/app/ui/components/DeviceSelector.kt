package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DeviceSelector(state: AppState, strings: LocaleStrings, onAddDevice: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.activeDevice()
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Default.Computer, null)
            Spacer(Modifier.width(8.dp))
            if (active == null) {
                Text(strings.selectDevice, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(
                    active.address,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(active.receivePort.toString(), style = MaterialTheme.typography.bodySmall)
                    Text(
                        active.sendPort.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.devices.forEach { device ->
                val selected = device.address == state.activeAddress
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                device.address,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    device.receivePort.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    device.sendPort.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            if (selected) Icons.Default.CheckCircle else Icons.Default.Computer,
                            null,
                            tint =
                                if (selected) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current,
                        )
                    },
                    trailingIcon = {
                        IconButton({ state.removeDevice(device) }) {
                            Icon(Icons.Default.Delete, strings.deleteDevice)
                        }
                    },
                    onClick = {
                        state.activeAddress = device.address
                        state.persist()
                        expanded = false
                    },
                )
            }
            if (state.devices.isNotEmpty()) HorizontalDivider()
            DropdownMenuItem(
                text = { Text(strings.addDevice) },
                leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = {
                    expanded = false
                    onAddDevice()
                },
            )
        }
    }
}
