package com.vrcmc.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private enum class ThemeMode { SYSTEM, DARK, LIGHT }
private enum class AppScreen { CHAT, DEVICES, API, TRANSLATION_LANGUAGE, SIMULTANEOUS_INTERPRETATION, CONFIGURE_VRC, PREFERENCES, ABOUT, ERROR_LOGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VrcmcApp() {
    val state = remember { AppState() }
    var screen by remember { mutableStateOf(AppScreen.CHAT) }
    var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
    var language by remember { mutableStateOf(AppLanguage.ZH_HANS) }
    var showAddDevice by remember { mutableStateOf(false) }
    var showClearHistory by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val strings = localeStrings(language)
    val dark = when (theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val listenPort = state.activeDevice()?.sendPort ?: defaultVrchatSendPort
    val listenAddress = state.activeDevice()?.address.orEmpty()
    LaunchedEffect(state.simultaneousInterpretationEnabled, listenAddress, listenPort) {
        if (!state.simultaneousInterpretationEnabled) return@LaunchedEffect
        state.simultaneousListenerError = null
        if (listenAddress.isBlank()) return@LaunchedEffect
        vrchatMuteSelfEvents(listenAddress, listenPort)
            .distinctUntilChanged()
            .retryListenerFailures(
                onFailure = {
                    state.simultaneousListenerError = it.message ?: it::class.simpleName
                    state.addErrorLog("OSC listener failed: ${it.message ?: it::class.simpleName}")
                },
                onRetry = { state.simultaneousListenerError = null },
            )
            .collect(state::handleVrchatMuteSelf)
    }

    BackHandler(enabled = screen != AppScreen.CHAT) {
        screen = AppScreen.CHAT
    }

    MaterialTheme(if (dark) darkColorScheme() else lightColorScheme()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(Modifier.width(260.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("VRCMC", style = MaterialTheme.typography.titleMedium)
                    }
                    NavigationDrawerItem(
                        label = { Text(strings.chat) },
                        selected = screen == AppScreen.CHAT,
                        icon = { Icon(Icons.Default.ChatBubble, null) },
                        onClick = {
                            screen = AppScreen.CHAT
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.deviceManagement) },
                        selected = screen == AppScreen.DEVICES,
                        icon = { Icon(Icons.Default.Devices, null) },
                        onClick = {
                            screen = AppScreen.DEVICES
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.apiConfiguration) },
                        selected = screen == AppScreen.API,
                        icon = { Icon(Icons.Default.Tune, null) },
                        onClick = {
                            screen = AppScreen.API
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.languageSettings) },
                        selected = screen == AppScreen.TRANSLATION_LANGUAGE,
                        icon = { Icon(Icons.Default.Translate, null) },
                        onClick = {
                            screen = AppScreen.TRANSLATION_LANGUAGE
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.simultaneousInterpretation) },
                        selected = screen == AppScreen.SIMULTANEOUS_INTERPRETATION,
                        icon = { Icon(Icons.Default.RecordVoiceOver, null) },
                        onClick = {
                            screen = AppScreen.SIMULTANEOUS_INTERPRETATION
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    NavigationDrawerItem(
                        label = { Text(strings.configureVrc) },
                        selected = screen == AppScreen.CONFIGURE_VRC,
                        icon = { Icon(Icons.Default.SettingsInputAntenna, null) },
                        onClick = {
                            screen = AppScreen.CONFIGURE_VRC
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.preferences) },
                        selected = screen == AppScreen.PREFERENCES,
                        icon = { Icon(Icons.Default.Settings, null) },
                        onClick = {
                            screen = AppScreen.PREFERENCES
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text(strings.aboutApp) },
                        selected = screen == AppScreen.ABOUT,
                        icon = { Icon(Icons.Default.Info, null) },
                        onClick = { screen = AppScreen.ABOUT; scope.launch { drawerState.close() } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton({ scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, strings.openMenu)
                            }
                        },
                        title = {
                            DeviceSelector(
                                state = state,
                                strings = strings,
                                onAddDevice = { showAddDevice = true },
                            )
                        },
                        actions = {
                            if (screen == AppScreen.CHAT && state.messages.isNotEmpty()) {
                                IconButton({ showClearHistory = true }) {
                                    Icon(Icons.Default.DeleteSweep, strings.clearHistory)
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Box(
                    Modifier
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .fillMaxSize(),
                ) {
                    when (screen) {
                        AppScreen.CHAT -> ChatPage(state, strings)
                        AppScreen.DEVICES -> DeviceManagementPage(state, strings) { showAddDevice = true }
                        AppScreen.API -> ApiPage(state, strings)
                        AppScreen.TRANSLATION_LANGUAGE -> TranslationLanguagePage(state, strings)
                        AppScreen.SIMULTANEOUS_INTERPRETATION -> SimultaneousInterpretationPage(state, strings)
                        AppScreen.CONFIGURE_VRC -> ConfigureVrcPage(strings)
                        AppScreen.PREFERENCES -> PreferencesPage(theme, language, strings, { theme = it }, { language = it })
                        AppScreen.ABOUT -> AboutPage(state, strings) { screen = AppScreen.ERROR_LOGS }
                        AppScreen.ERROR_LOGS -> ErrorLogsPage(state, strings)
                    }
                }
            }
        }

        if (showAddDevice) {
            AddDeviceDialog(state, strings) { showAddDevice = false }
        }
        if (showClearHistory) {
            AlertDialog(
                onDismissRequest = { showClearHistory = false },
                icon = { Icon(Icons.Default.DeleteSweep, null) },
                title = { Text(strings.clearHistoryTitle) },
                text = { Text(strings.clearHistoryMessage) },
                confirmButton = {
                    TextButton(onClick = {
                        state.clearChatHistory()
                        showClearHistory = false
                    }) { Text(strings.delete, color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ showClearHistory = false }) { Text(strings.cancel) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimultaneousInterpretationPage(state: AppState, strings: LocaleStrings) {
    val device = state.activeDevice()
    val endpoint = device?.let { "${it.address}:${it.sendPort}" } ?: "-:$defaultVrchatSendPort"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(strings.simultaneousInterpretation, style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.KeyboardVoice,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        strings.simultaneousInterpretationInputHint,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.simultaneousInterpretation, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.enableSimultaneousInterpretation, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(strings.simultaneousInterpretationHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.simultaneousInterpretationEnabled,
                            onCheckedChange = state::updateSimultaneousInterpretationEnabled,
                        )
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("${strings.listeningPort}: $endpoint", Modifier.weight(1f))
                        if (state.isSimultaneousInterpretationActive) {
                            Text(strings.interpreting, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(
                        when {
                            !state.simultaneousInterpretationEnabled -> strings.listenerStopped
                            state.simultaneousListenerError != null -> strings.listenerFailed.replace("%s", state.simultaneousListenerError.orEmpty())
                            else -> strings.listenerReady
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.simultaneousListenerError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.alwaysInterpretation, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.enableAlwaysInterpretation, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(strings.alwaysInterpretationHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.alwaysInterpretationEnabled,
                            onCheckedChange = state::updateAlwaysInterpretationEnabled,
                        )
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.alwaysInterpretationDelay, style = MaterialTheme.typography.labelLarge)
                            Text(strings.alwaysInterpretationDelayHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(strings.seconds(formatDelaySeconds(state.alwaysInterpretationDelayMillis)))
                    }
                    Slider(
                        value = state.alwaysInterpretationDelayMillis / 1_000f,
                        onValueChange = {
                            val halfSeconds = (it * 2).roundToInt()
                            state.updateAlwaysInterpretationDelayMillis(halfSeconds * 500)
                        },
                        valueRange = .5f..10f,
                        thumb = {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                thumbTrackGapSize = 0.dp,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceSelector(state: AppState, strings: LocaleStrings, onAddDevice: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.activeDevice()
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
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
                    Text(active.sendPort.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(device.receivePort.toString(), style = MaterialTheme.typography.bodySmall)
                                Text(device.sendPort.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.Computer, null, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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

@Composable
private fun AddDeviceDialog(state: AppState, strings: LocaleStrings, close: () -> Unit) {
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
                    onValueChange = { endpoint = it; invalid = false },
                    singleLine = true,
                    label = { Text(strings.deviceAddress) },
                    placeholder = { Text("9000:192.168.1.10:9001") },
                    supportingText = { Text(if (invalid) strings.invalidDeviceAddress else strings.defaultPortHint) },
                    isError = invalid,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state.addDevice(endpoint)) close() else invalid = true
            }, enabled = endpoint.isNotBlank()) { Text(strings.addDevice) }
        },
        dismissButton = { TextButton(close) { Text(strings.cancel) } },
    )
}

@Composable
private fun DeviceManagementPage(state: AppState, strings: LocaleStrings, onAddDevice: () -> Unit) {
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
                Text(strings.deviceManagement, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onAddDevice) { Icon(Icons.Default.Add, strings.addDevice) }
            }
        }
        if (state.devices.isEmpty()) {
            item {
                Text(strings.noDevices, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(device.address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${strings.receivePort}: ${device.receivePort}  /  ${strings.sendPort}: ${device.sendPort}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ editing = device }) { Icon(Icons.Default.Edit, strings.editDevice) }
                        IconButton({ state.removeDevice(device) }) { Icon(Icons.Default.Delete, strings.deleteDevice, tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        if (localNetworkScanSupported) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(strings.deviceIpHint, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                item {
                    Text(strings.scanResults, style = MaterialTheme.typography.titleMedium)
                }
                if (results.isEmpty()) {
                    item {
                        Text(strings.noScanResults, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(results, key = { "scan-${it.ipAddress}" }) { device ->
                        val added = state.devices.any { it.address == device.ipAddress }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name.ifBlank { strings.unknownDeviceName }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(device.ipAddress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    editing?.let { device ->
        EditDeviceDialog(state, device, strings) { editing = null }
    }
}

@Composable
private fun EditDeviceDialog(state: AppState, device: Device, strings: LocaleStrings, close: () -> Unit) {
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
                OutlinedTextField(address, { address = it; invalid = false }, label = { Text(strings.ipAddress) }, singleLine = true, isError = invalid)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(receivePort, { receivePort = it.filter(Char::isDigit); invalid = false }, Modifier.weight(1f), label = { Text(strings.receivePort) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = invalid)
                    OutlinedTextField(sendPort, { sendPort = it.filter(Char::isDigit); invalid = false }, Modifier.weight(1f), label = { Text(strings.sendPort) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = invalid)
                }
                if (invalid) Text(strings.invalidDeviceAddress, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({ if (state.updateDevice(device, address, receivePort, sendPort)) close() else invalid = true }) { Text(strings.save) }
        },
        dismissButton = { TextButton(close) { Text(strings.cancel) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesPage(theme: ThemeMode, language: AppLanguage, strings: LocaleStrings, setTheme: (ThemeMode) -> Unit, setLanguage: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val modes = ThemeMode.values()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(strings.preferences, style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(strings.appearance, style = MaterialTheme.typography.titleMedium)
                    Text(strings.theme, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == theme,
                                onClick = { setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> strings.systemTheme
                                            ThemeMode.LIGHT -> strings.lightTheme
                                            ThemeMode.DARK -> strings.darkTheme
                                        },
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(strings.language, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(8.dp))
                            Text(language.label, Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            AppLanguage.values().forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = { if (item == language) Icon(Icons.Default.Check, null) },
                                    onClick = { setLanguage(item); expanded = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDelaySeconds(delayMillis: Int): String =
    if (delayMillis % 1_000 == 0) (delayMillis / 1_000).toString() else (delayMillis / 1_000f).toString()
