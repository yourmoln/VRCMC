package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

@Composable
internal fun ReadAloudSettingsSection(
    config: ReadAloudConfig,
    strings: LocaleStrings,
    onChange: (ReadAloudConfig) -> Unit,
    controller: ReadAloudController,
) {
    var voices by remember { mutableStateOf<List<EdgeTtsVoice>>(emptyList()) }
    var devices by remember { mutableStateOf<List<AudioOutputDevice>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var deviceLoadFailed by remember { mutableStateOf(false) }
    var voiceRefresh by remember { mutableIntStateOf(0) }
    var deviceRefresh by remember { mutableIntStateOf(0) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    val selectOutput = remember { supportsAudioOutputSelection() }

    LaunchedEffect(config.enabled, voiceRefresh) {
        if (!config.enabled) return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            voices = EdgeTtsService.voices()
        } catch (_: TimeoutCancellationException) {
            loadFailed = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadFailed = true
        } finally {
            loading = false
        }
    }
    LaunchedEffect(config.enabled, deviceRefresh) {
        if (!config.enabled || !selectOutput) return@LaunchedEffect
        deviceLoadFailed = false
        try {
            devices = withContext(Dispatchers.Default) { availableAudioOutputDevices() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            deviceLoadFailed = true
        }
    }
    SettingsCard(strings.readAloud, Icons.AutoMirrored.Filled.VolumeUp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.enableReadAloud, Modifier.weight(1f))
            Switch(config.enabled, { onChange(config.copy(enabled = it)) })
        }
        Text(strings.readAloudHint, style = MaterialTheme.typography.bodySmall)
        if (config.enabled) {
            ReadAloudChoice(
                strings.readAloudContent,
                config.source.label(strings),
                ReadAloudSource.entries.map { it to it.label(strings) },
            ) { onChange(config.copy(source = it)) }
            Text(strings.readAloudVoice, style = MaterialTheme.typography.labelLarge)
            val selectedVoice = voices.firstOrNull { it.shortName == config.voice }
                ?: EdgeTtsVoice(config.voice, config.voice.substringBeforeLast('-'), "")
            OutlinedButton(onClick = { showVoiceDialog = true }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(14.dp)) {
                Icon(Icons.Default.RecordVoiceOver, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(selectedVoice.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selectedVoice.description(strings), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.ChevronRight, null)
            }
            if (loadFailed) Text(strings.readAloudVoicesFailed, color = MaterialTheme.colorScheme.error)
            if (selectOutput) {
                ReadAloudChoice(
                    strings.readAloudOutputDevice,
                    if (config.outputDeviceId.isEmpty()) strings.readAloudSystemDefault
                    else devices.firstOrNull { it.id == config.outputDeviceId }?.name
                        ?: strings.readAloudDeviceUnavailable,
                    listOf("" to strings.readAloudSystemDefault) + devices.map { it.id to it.name },
                ) { onChange(config.copy(outputDeviceId = it)) }
                if (deviceLoadFailed || (config.outputDeviceId.isNotEmpty() && devices.none { it.id == config.outputDeviceId })) {
                    Text(strings.readAloudDeviceUnavailable, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectOutput) OutlinedButton(onClick = { deviceRefresh++ }) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(6.dp))
                    Text(strings.readAloudRefreshDevices)
                }
                Button(onClick = {
                    if (controller.busy) controller.stop()
                    else controller.enqueue(strings.readAloudSample, config)
                }) {
                    Icon(if (controller.busy) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (controller.busy) strings.readAloudStop else strings.readAloudPreview)
                }
            }
            if (controller.failed) Text(strings.readAloudFailed, color = MaterialTheme.colorScheme.error)
        }
    }
    if (config.enabled && showVoiceDialog) {
        ReadAloudVoiceDialog(
            voices = voices,
            currentVoice = config.voice,
            strings = strings,
            loading = loading,
            loadFailed = loadFailed,
            previewBusy = controller.previewVoice != null,
            previewFailed = controller.failed,
            onRefresh = { voiceRefresh++ },
            onPreview = { voice ->
                val sample = if (voice.languageLabel() in availableTargetLanguages)
                    previewForLanguage(voice.languageLabel()) else strings.readAloudSample
                controller.preview(sample, voice.shortName)
            },
            onStopPreview = controller::stopPreview,
            onConfirm = {
                onChange(config.copy(voice = it))
                showVoiceDialog = false
            },
            onDismiss = { showVoiceDialog = false },
        )
    }
}

private fun ReadAloudSource.label(strings: LocaleStrings): String = when (this) {
    ReadAloudSource.ORIGINAL -> strings.readAloudOriginal
    ReadAloudSource.TRANSLATION -> strings.readAloudTranslation
    ReadAloudSource.OUTGOING -> strings.readAloudOutgoing
}

@Composable
private fun <T> ReadAloudChoice(label: String, selected: String, items: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected, Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 320.dp)) {
            items.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
