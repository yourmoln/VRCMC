package com.vrcmc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

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
    val dark =
        when (theme) {
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
                },
                onRetry = { state.simultaneousListenerError = null },
            )
            .collect(state::handleVrchatMuteSelf)
    }

    BackHandler(enabled = screen != AppScreen.CHAT) { screen = AppScreen.CHAT }

    MaterialTheme(if (dark) darkColorScheme() else lightColorScheme()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                VrcmcNavigationDrawer(screen, strings) { selectedScreen ->
                    screen = selectedScreen
                    scope.launch { drawerState.close() }
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
                }
            ) { padding ->
                Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                    ChatPage(state, strings)
                    if (screen != AppScreen.CHAT) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            when (screen) {
                                AppScreen.CHAT -> Unit
                                AppScreen.DEVICES ->
                                    DeviceManagementPage(state, strings) { showAddDevice = true }
                                AppScreen.API -> ApiPage(state, strings)
                                AppScreen.TRANSLATION_LANGUAGE ->
                                    TranslationLanguagePage(state, strings)
                                AppScreen.SIMULTANEOUS_INTERPRETATION ->
                                    SimultaneousInterpretationPage(state, strings)
                                AppScreen.QUICK_START ->
                                    QuickStartPage(state, strings) { screen = AppScreen.CHAT }
                                AppScreen.CONFIGURE_VRC -> ConfigureVrcPage(strings)
                                AppScreen.PREFERENCES ->
                                    PreferencesPage(
                                        theme,
                                        language,
                                        strings,
                                        { theme = it },
                                        { language = it },
                                    )
                                AppScreen.ABOUT ->
                                    AboutPage(state, strings) { screen = AppScreen.ERROR_LOGS }
                                AppScreen.ERROR_LOGS -> ErrorLogsPage(state, strings)
                            }
                        }
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
                    TextButton(
                        onClick = {
                            state.clearChatHistory()
                            showClearHistory = false
                        }
                    ) {
                        Text(strings.delete, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton({ showClearHistory = false }) { Text(strings.cancel) }
                },
            )
        }
    }
}
