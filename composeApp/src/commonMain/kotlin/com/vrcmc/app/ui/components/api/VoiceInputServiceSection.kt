package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private data class AsrRegion(val id: String, val label: String, val baseUrl: String)

private val asrRegions = listOf(
    AsrRegion("singapore", "新加坡（国际）", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
    AsrRegion("china_mainland", "中国大陆", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    AsrRegion("japan", "日本（工作空间端点）", ""),
    AsrRegion("custom", "自定义", ""),
)

@Composable
internal fun VoiceInputServiceSection(
    config: VoiceInputConfig,
    strings: LocaleStrings,
    onUpdate: ((VoiceInputConfig) -> VoiceInputConfig) -> Unit,
) {
    var showKey by remember { mutableStateOf(false) }
    var regionMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var microphoneMenu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    val microphones = remember { availableAudioInputDevices() }

    SettingsCard(strings.voiceInputService, Icons.Default.RecordVoiceOver) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(strings.enableVoiceInput, style = MaterialTheme.typography.titleSmall)
                Text(
                    strings.voiceInputHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = config.enabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(enabled = enabled) } },
            )
        }
        if (!config.enabled) return@SettingsCard

        HorizontalDivider()
        OutlinedTextField(
            value = config.apiKey,
            onValueChange = { value -> onUpdate { it.copy(apiKey = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.qwenApiKey) },
            placeholder = { Text("sk-...") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton({ showKey = !showKey }) {
                    Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, strings.showApiKey)
                }
            },
            isError = config.apiKey.isBlank(),
            shape = MaterialTheme.shapes.large,
        )
        Box {
            OutlinedButton(
                onClick = { regionMenu = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Public, null)
                Spacer(Modifier.width(8.dp))
                Text(asrRegions.firstOrNull { it.id == config.region }?.label ?: config.region, Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(regionMenu, { regionMenu = false }) {
                asrRegions.forEach { region ->
                    DropdownMenuItem(
                        text = { Text(region.label) },
                        leadingIcon = { if (region.id == config.region) Icon(Icons.Default.Check, null) },
                        onClick = {
                            onUpdate { old ->
                                old.copy(
                                    region = region.id,
                                    baseUrl =
                                        if (region.baseUrl.isNotBlank()) region.baseUrl
                                        else if (old.region in setOf("japan", "custom")) old.baseUrl
                                        else "",
                                )
                            }
                            regionMenu = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = config.baseUrl,
            onValueChange = { value -> onUpdate { it.copy(baseUrl = value) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = config.region !in setOf("japan", "custom"),
            label = { Text("Base URL") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            isError = !config.baseUrl.startsWith("https://"),
            shape = MaterialTheme.shapes.large,
        )
        Box {
            OutlinedTextField(
                value = config.model,
                onValueChange = { value -> onUpdate { it.copy(model = value) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenModel) },
                trailingIcon = { IconButton({ modelMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) } },
                isError = config.model.isBlank(),
                shape = MaterialTheme.shapes.large,
            )
            DropdownMenu(modelMenu, { modelMenu = false }) {
                listOf("qwen3-asr-flash-2026-02-10", "qwen3-asr-flash").forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        leadingIcon = { if (model == config.model) Icon(Icons.Default.Check, null) },
                        onClick = { onUpdate { it.copy(model = model) }; modelMenu = false },
                    )
                }
            }
        }
        Box {
            OutlinedButton(
                onClick = { languageMenu = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.Language, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.qwenLanguage, Modifier.weight(1f))
                Text(config.language.ifBlank { "auto" })
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(languageMenu, { languageMenu = false }) {
                listOf("auto", "zh", "ja", "en", "ko", "de", "fr", "ru").forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        leadingIcon = { if (language == config.language) Icon(Icons.Default.Check, null) },
                        onClick = { onUpdate { it.copy(language = language) }; languageMenu = false },
                    )
                }
            }
        }
        if (isDesktopAudioPlatform()) {
            Box {
                OutlinedButton(
                    onClick = { microphoneMenu = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Default.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.microphone, Modifier.weight(1f))
                    Text(microphones.firstOrNull { it.id == config.microphoneId }?.name ?: strings.systemDefaultMicrophone)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(microphoneMenu, { microphoneMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.systemDefaultMicrophone) },
                        leadingIcon = { if (config.microphoneId.isBlank()) Icon(Icons.Default.Check, null) },
                        onClick = { onUpdate { it.copy(microphoneId = "") }; microphoneMenu = false },
                    )
                    microphones.forEach { microphone ->
                        DropdownMenuItem(
                            text = { Text(microphone.name) },
                            leadingIcon = { if (microphone.id == config.microphoneId) Icon(Icons.Default.Check, null) },
                            onClick = { onUpdate { it.copy(microphoneId = microphone.id) }; microphoneMenu = false },
                        )
                    }
                }
            }
        }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text(strings.advancedSettings, Modifier.weight(1f))
            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (advanced) {
            OutlinedTextField(
                value = config.sampleRate.toString(),
                onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { rate -> onUpdate { it.copy(sampleRate = rate.coerceIn(8_000, 48_000)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenSampleRate) },
                suffix = { Text("Hz") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.maxSegmentSeconds.toString(),
                onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { duration -> onUpdate { it.copy(maxSegmentSeconds = duration.coerceIn(1, 60)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenMaxSegment) },
                suffix = { Text("s") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.tailSilenceMillis.toString(),
                onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { duration -> onUpdate { it.copy(tailSilenceMillis = duration.coerceIn(200, 3_000)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenTailSilence) },
                suffix = { Text("ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.vadMinRms.toString(),
                onValueChange = { value -> value.toDoubleOrNull()?.let { rms -> onUpdate { it.copy(vadMinRms = rms.coerceIn(0.001, 0.5)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenVadMinRms) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.vadSpeechRatio.toString(),
                onValueChange = { value -> value.toDoubleOrNull()?.let { ratio -> onUpdate { it.copy(vadSpeechRatio = ratio.coerceIn(0.1, 1.0)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenVadSpeechRatio) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.partialIntervalMillis.toString(),
                onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { interval -> onUpdate { it.copy(partialIntervalMillis = interval.coerceIn(250, 2_000)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenPartialInterval) },
                suffix = { Text("ms") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
            )
            OutlinedTextField(
                value = config.timeoutSeconds.toString(),
                onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { timeout -> onUpdate { it.copy(timeoutSeconds = timeout.coerceIn(3, 120)) } } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.qwenTimeout) },
                suffix = { Text("s") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.large,
            )
        }
    }
}
