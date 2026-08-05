package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun DeviceManagementPage(
    state: AppState,
    strings: LocaleStrings,
    onAddDevice: () -> Unit,
) {
    var editing by remember { mutableStateOf<Device?>(null) }
    var discoveredDevices by remember { mutableStateOf<List<DiscoveredNetworkDevice>?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.deviceManagement,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onAddDevice) { Icon(Icons.Default.Add, strings.addDevice) }
            }
        }
        if (state.devices.isEmpty()) {
            item {
                Text(
                    strings.noDevices,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.devices.size, key = { state.devices[it].address }) { index ->
                val device = state.devices[index]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.address,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${strings.receivePort}: ${device.receivePort}  /  ${strings.sendPort}: ${device.sendPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton({ editing = device }) {
                            Icon(Icons.Default.Edit, strings.editDevice)
                        }
                        IconButton({ state.removeDevice(device) }) {
                            Icon(
                                Icons.Default.Delete,
                                strings.deleteDevice,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        if (localNetworkScanSupported) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            strings.deviceIpHint,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isScanning = true
                                discoveredDevices = scanLocalNetworkDevices()
                                isScanning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Radar, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isScanning) strings.scanningNetwork else strings.scanNetwork)
                    }
                    if (isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            discoveredDevices?.let { results ->
                item { Text(strings.scanResults, style = MaterialTheme.typography.titleMedium) }
                if (results.isEmpty()) {
                    item {
                        Text(
                            strings.noScanResults,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(results, key = { "scan-${it.ipAddress}" }) { device ->
                        val added = state.devices.any { it.address == device.ipAddress }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Computer,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.name.ifBlank { strings.unknownDeviceName },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    device.ipAddress,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { state.addDevice(device.ipAddress) },
                                enabled = !added,
                            ) {
                                Icon(if (added) Icons.Default.Check else Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (added) strings.alreadyAdded else strings.addDevice)
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { device -> EditDeviceDialog(state, device, strings) { editing = null } }
}
