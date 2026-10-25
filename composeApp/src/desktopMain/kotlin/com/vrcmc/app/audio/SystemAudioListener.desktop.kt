package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Platform
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import java.awt.Dimension
import java.awt.geom.RoundRectangle2D
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun rememberSystemAudioListener(
    state: AppState,
    strings: LocaleStrings,
    dark: Boolean,
): SystemAudioListeningControl? {
    if (!Platform.isWindows()) return null
    var isOpen by remember { mutableStateOf(false) }
    if (isOpen) {
        SystemAudioSubtitleWindow(
            settings = SystemAudioListeningSettings(
                state.voiceInputConfig, state.provider, state.providerConfig, state.languages.toList(),
            ),
            strings = strings,
            dark = dark,
            onClose = { isOpen = false },
            onError = state::addErrorLog,
            languageConfig = state.systemAudioLanguages,
            onLanguageConfigChange = state::updateSystemAudioLanguages,
        )
    }
    return SystemAudioListeningControl(isOpen) { isOpen = !isOpen }
}

@Composable
internal fun SystemAudioSubtitleWindow(
    settings: SystemAudioListeningSettings,
    strings: LocaleStrings,
    dark: Boolean,
    onClose: () -> Unit,
    onError: (String) -> Unit,
    languageConfig: SystemAudioLanguageConfig,
    onLanguageConfigChange: (SystemAudioLanguageConfig) -> Unit,
) {
    val config = languageConfig.normalized()
    // Appearance changes must not cancel capture or clear completed captions.
    val listeningSettings = settings.withLanguages(config)
    val localModelStatusFlow = remember(settings.voice.localSpeechModel) {
        localSpeechRecognizer.status(settings.voice.localSpeechModel)
    }
    val localModelStatus by localModelStatusFlow.collectAsState()
    val localModelReady = settings.voice.provider != VoiceInputProvider.LOCAL_MODEL ||
        localModelStatus == LocalSpeechModelStatus.Ready
    val windowState = rememberWindowState(width = 360.dp, height = 480.dp, position = WindowPosition(24.dp, 24.dp))
    var showSettings by remember { mutableStateOf(false) }
    val captions = remember { mutableStateListOf<SystemAudioCaption>() }
    var failure by remember { mutableStateOf<String?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var lagging by remember { mutableStateOf(false) }
    val overlayStatus = rememberSteamVrSubtitleOverlay(
        config = config,
        content = SteamVrOverlayContent(
            status = failure ?: if (lagging) strings.systemAudioTooSlow
                else if (speaking) strings.systemAudioListening else strings.voiceWaitingForSpeech,
            captions = captions.toList(),
            dark = dark,
        ),
        onError = onError,
    )
    // Reconfiguration cancels capture and in-flight requests before starting the new session.
    LaunchedEffect(listeningSettings, strings, localModelReady) {
        failure = null
        speaking = false
        lagging = false
        captions.clear()
        val readinessFailure = voiceInputReadinessFailure(listeningSettings.voice)
        if (readinessFailure != null) {
            failure = if (listeningSettings.voice.provider == VoiceInputProvider.QWEN) strings.apiNotConfiguredVoiceInput
                else strings.voiceTranscriptionFailureMessage(readinessFailure)
            return@LaunchedEffect
        }
        if (!listeningSettings.provider.isConfigured(listeningSettings.providerConfig)) {
            failure = strings.apiNotConfiguredTranslation
            return@LaunchedEffect
        }
        try {
            val uiScope = this
            withContext(Dispatchers.Default) {
                SileroSpeechDetector().use { detector ->
                    SystemAudioListeningSession().run(
                        listeningSettings, WindowsLoopbackCapture(), strings,
                        onCaption = { caption ->
                            uiScope.launch {
                                lagging = false
                                captions.add(caption)
                                captions.sortBy { it.id }
                                while (captions.size > 50) captions.removeAt(0)
                            }
                        },
                        onSpeechState = { value -> uiScope.launch { speaking = value } },
                        speechDetector = detector::isSpeech,
                        onBacklog = { uiScope.launch { lagging = true } },
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failure = if (error is LinkageError) strings.systemAudioVadFailed else strings.systemAudioFailed
            onError("System audio capture: ${error::class.simpleName}: ${error.message.orEmpty()}")
        } finally {
            speaking = false
        }
    }

    Window(
        onCloseRequest = onClose,
        title = "VRCMC · ${strings.listenToOthers}",
        icon = painterResource(Res.drawable.logo),
        state = windowState,
        undecorated = true,
        alwaysOnTop = true,
    ) {
        SideEffect {
            // AWT already applies display scaling to its logical window dimensions.
            window.minimumSize = Dimension(320, 200)
            window.undecoratedResizerThickness = 6.dp
            window.opacity = config.opacityPercent / 100f
        }
        LaunchedEffect(window, windowState.size, config.opacityPercent) {
            // Windows 11 rounds the native window, including its shadow. Older Windows
            // versions use the same corner radius through AWT's native window region.
            if (!setWindowsRoundedCorners(window)) {
                window.shape = RoundRectangle2D.Double(0.0, 0.0, window.width.toDouble(), window.height.toDouble(), 16.0, 16.0)
            }
        }
        MaterialTheme(if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.fillMaxSize().padding(6.dp)) {
                    WindowDraggableArea(Modifier.fillMaxWidth().testTag("systemAudioWindowToolbar")) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f).height(40.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                                if (failure == null) {
                                    Text(
                                        if (speaking) strings.systemAudioListening else strings.voiceWaitingForSpeech,
                                        color = if (speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = strings.settings, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { windowState.isMinimized = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Remove, contentDescription = strings.minimizeWindow, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Close, contentDescription = strings.closeWindow, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (failure != null) {
                            Text(failure!!, color = MaterialTheme.colorScheme.error)
                        } else if (lagging) {
                            Text(
                                strings.systemAudioTooSlow,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        val listState = rememberLazyListState()
                        val overlayMessage = when (overlayStatus) {
                            SteamVrOverlayStatus.UNAVAILABLE -> strings.steamVrOverlayUnavailable
                            SteamVrOverlayStatus.WAITING_FOR_CONTROLLER -> strings.steamVrOverlayWaitingForController
                            else -> null
                        }
                        if (overlayMessage != null) {
                            Text(overlayMessage, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LaunchedEffect(captions.toList()) {
                            if (captions.isNotEmpty()) listState.scrollToItem(captions.lastIndex)
                        }
                        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(captions, key = { it.id }) { caption ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (caption.original.isNotBlank()) {
                                                Text(caption.original, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                                            }
                                            caption.translations.forEach { (_, text) ->
                                                Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                                            }
                                            caption.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showSettings) {
        SystemAudioLanguageSettingsWindow(
            config = config,
            strings = strings,
            dark = dark,
            position = windowState.position,
            onConfigChange = onLanguageConfigChange,
            onClose = { showSettings = false },
        )
    }
}

@Composable
internal fun SystemAudioLanguageSettingsWindow(
    config: SystemAudioLanguageConfig,
    strings: LocaleStrings,
    dark: Boolean,
    position: WindowPosition = WindowPosition(24.dp, 24.dp),
    onConfigChange: (SystemAudioLanguageConfig) -> Unit,
    onClose: () -> Unit,
) {
    var current by remember { mutableStateOf(config.normalized()) }
    LaunchedEffect(config) { current = config.normalized() }
    fun apply(updated: SystemAudioLanguageConfig) {
        val normalized = updated.normalized()
        current = normalized
        onConfigChange(normalized)
    }
    val windowState = rememberWindowState(width = 360.dp, height = 480.dp, position = position)
    Window(
        onCloseRequest = onClose,
        title = "VRCMC · ${strings.settings}",
        icon = painterResource(Res.drawable.logo),
        state = windowState,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
    ) {
        SideEffect {
            // Keep the rounded corners transparent while the settings content stays fully opaque.
            window.background = java.awt.Color(0, 0, 0, 0)
            window.opacity = 1f
            window.isResizable = false
        }
        LaunchedEffect(window) {
            if (!setWindowsRoundedCorners(window)) {
                window.shape = RoundRectangle2D.Double(
                    0.0,
                    0.0,
                    window.width.toDouble(),
                    window.height.toDouble(),
                    16.0,
                    16.0,
                )
            }
        }
        MaterialTheme(if (dark) darkColorScheme() else lightColorScheme()) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("systemAudioLanguageSettings"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().testTag("systemAudioSettingsScroll")
                            .verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(strings.settings, style = MaterialTheme.typography.titleLarge)
                        Column {
                            SystemAudioLanguagePicker(
                                label = strings.systemAudioSourceLanguage,
                                selected = current.sourceLanguage,
                                options = listOf("auto" to strings.systemAudioAutomaticLanguage) +
                                    systemAudioSourceLanguages.toList(),
                                optionTestTag = "systemAudioSourceLanguage",
                                onSelect = { apply(current.copy(sourceLanguage = it)) },
                            )
                            SystemAudioLanguagePicker(
                                label = strings.targetLanguage,
                                selected = current.targetLanguage,
                                options = availableTargetLanguages.map { it to it },
                                optionTestTag = "systemAudioTargetLanguage",
                                onSelect = { apply(current.copy(targetLanguage = it)) },
                            )
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    strings.steamVrOverlay,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Switch(
                                    checked = current.steamVrOverlayEnabled,
                                    onCheckedChange = {
                                        apply(current.copy(steamVrOverlayEnabled = it))
                                    },
                                    modifier = Modifier.semantics { contentDescription = strings.steamVrOverlay },
                                )
                            }
                            if (current.steamVrOverlayEnabled) {
                                SystemAudioLanguagePicker(
                                    label = strings.steamVrOverlayPosition,
                                    selected = current.steamVrOverlayPosition.name,
                                    options = listOf(
                                        SteamVrOverlayPosition.LEFT_HAND.name to strings.steamVrOverlayLeftHand,
                                        SteamVrOverlayPosition.RIGHT_HAND.name to strings.steamVrOverlayRightHand,
                                        SteamVrOverlayPosition.SCREEN_CENTER.name to strings.steamVrOverlayScreenCenter,
                                    ),
                                    optionTestTag = "steamVrOverlayPosition",
                                    onSelect = {
                                        apply(
                                            current.copy(
                                                steamVrOverlayPosition = SteamVrOverlayPosition.valueOf(it),
                                            ),
                                        )
                                    },
                                )
                                SystemAudioLanguagePicker(
                                    label = strings.steamVrOverlaySize,
                                    selected = current.steamVrOverlayScalePercent.toString(),
                                    options = steamVrOverlayScalePercents.map {
                                        it.toString() to if (it == 100) strings.steamVrOverlayDefaultSize else "$it%"
                                    },
                                    optionTestTag = "steamVrOverlaySize",
                                    onSelect = {
                                        apply(current.copy(steamVrOverlayScalePercent = it.toInt()))
                                    },
                                )
                                Text(
                                    strings.steamVrOverlaySizeHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                SystemAudioLanguagePicker(
                                    label = strings.steamVrOverlayFontSize,
                                    selected = current.steamVrOverlayFontSizePercent.toString(),
                                    options = steamVrOverlayFontSizePercents.map {
                                        it.toString() to
                                            if (it == 100) strings.steamVrOverlayDefaultFontSize else "$it%"
                                    },
                                    optionTestTag = "steamVrOverlayFontSize",
                                    onSelect = {
                                        apply(current.copy(steamVrOverlayFontSizePercent = it.toInt()))
                                    },
                                )
                                Text(
                                    strings.steamVrOverlayFontSizeHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    strings.steamVrOverlayHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    strings.systemAudioWindowOpacity,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text("${current.opacityPercent}%", style = MaterialTheme.typography.labelLarge)
                            }
                            Slider(
                                value = current.opacityPercent.toFloat(),
                                onValueChange = {
                                    apply(current.copy(opacityPercent = it.roundToInt()))
                                },
                                valueRange = 30f..100f,
                                modifier = Modifier.fillMaxWidth().semantics {
                                    contentDescription = strings.systemAudioWindowOpacity
                                    stateDescription = "${current.opacityPercent}%"
                                },
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClose) { Text(strings.done) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemAudioLanguagePicker(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    optionTestTag: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.weight(1.6f)) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag("$optionTestTag-picker").semantics {
                    contentDescription = label
                    stateDescription = selectedLabel
                },
            ) {
                Text(selectedLabel, Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        leadingIcon = { if (value == selected) Icon(Icons.Default.Check, contentDescription = null) },
                        modifier = Modifier.testTag("$optionTestTag-option-$value"),
                        onClick = { onSelect(value); expanded = false },
                    )
                }
            }
        }
    }
}
