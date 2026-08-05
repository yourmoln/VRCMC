package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val recommendedProviderIds = setOf("deepseek", "qianwen", "gemini", "openai", "local_ai")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiPage(state: AppState, strings: LocaleStrings) {
    val provider = state.provider
    val config = state.providerConfig
    var showProviderPicker by remember { mutableStateOf(false) }
    var modelMenu by remember(provider.id) { mutableStateOf(false) }
    var fallbackModelMenu by remember(provider.id) { mutableStateOf(false) }
    var regionMenu by remember(provider.id) { mutableStateOf(false) }
    var showKey by remember(provider.id) { mutableStateOf(false) }
    var advanced by remember(provider.id) { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    val scope = rememberCoroutineScope()

    fun update(transform: (ProviderConfig) -> ProviderConfig) {
        result = null
        state.updateProviderConfig(transform)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard(strings.translationService, Icons.Default.Hub) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { showProviderPicker = true },
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProviderAvatar(provider.label)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(provider.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(provider.protocol.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.UnfoldMore, strings.provider)
                    }
                }
                Text(provider.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SettingsCard(strings.credentialsAndEndpoint, Icons.Default.Key) {
                if (provider.id !in setOf("google_web", "mymemory")) {
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { update { old -> old.copy(apiKey = it) } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (provider.keyRequired) strings.apiKey else strings.apiKeyOptional) },
                        placeholder = { Text(provider.keyPlaceholder) },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, strings.showApiKey) } },
                        isError = provider.keyRequired && config.apiKey.isBlank(),
                        supportingText = if (provider.keyRequired && config.apiKey.isBlank()) ({ Text(strings.apiKeyRequired) }) else null,
                        shape = MaterialTheme.shapes.large,
                    )
                }
                if (provider.regions.isNotEmpty()) {
                    Box {
                        val selectedRegion = provider.regions.firstOrNull { it.id == config.region }
                        OutlinedButton({ regionMenu = true }, Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                            Icon(Icons.Default.Public, null); Spacer(Modifier.width(8.dp)); Text(selectedRegion?.label ?: strings.customEndpoint, Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(regionMenu, { regionMenu = false }) {
                            provider.regions.forEach { region -> DropdownMenuItem({ Text(region.label) }, leadingIcon = { if (region.id == config.region) Icon(Icons.Default.Check, null) }, onClick = { update { old -> old.copy(region = region.id, baseUrl = region.baseUrl) }; regionMenu = false }) }
                        }
                    }
                }
                OutlinedTextField(
                    value = config.baseUrl, onValueChange = { update { old -> old.copy(baseUrl = it) } }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    readOnly = !provider.editableBaseUrl, label = { Text("Base URL") }, leadingIcon = { Icon(if (config.baseUrl.startsWith("https://")) Icons.Default.Lock else Icons.Default.Language, null) },
                    supportingText = { Text(if (provider.editableBaseUrl) strings.baseUrlHint else strings.officialEndpointLocked) },
                    isError = config.baseUrl.isBlank() || (!config.baseUrl.startsWith("https://") && !config.baseUrl.startsWith("http://")), shape = MaterialTheme.shapes.large,
                )
            }
        }

        item {
            SettingsCard(strings.model, Icons.Default.AutoAwesome) {
                Box {
                    OutlinedTextField(
                        value = config.model, onValueChange = { if (provider.editableModel) update { old -> old.copy(model = it) } }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        readOnly = !provider.editableModel, label = { Text(strings.model) },
                        trailingIcon = if (provider.models.isNotEmpty()) ({ IconButton({ modelMenu = true }) { Icon(Icons.Default.ArrowDropDown, strings.chooseModel) } }) else null,
                        supportingText = if (provider.editableModel) ({ Text(strings.customModelPreserved) }) else null, isError = config.model.isBlank(), shape = MaterialTheme.shapes.large,
                    )
                    if (provider.models.isNotEmpty()) DropdownMenu(modelMenu, { modelMenu = false }) {
                        provider.models.let { if (config.model.isNotBlank() && config.model !in it) listOf(config.model) + it else it }.forEach { model ->
                            DropdownMenuItem({ Text(model) }, leadingIcon = { if (model == config.model) Icon(Icons.Default.Check, null) }, onClick = {
                                update { old ->
                                    val replacementFallback = provider.models.firstOrNull { it != model }.orEmpty()
                                    old.copy(model = model, fallbackModel = old.fallbackModel.takeUnless { it == model } ?: replacementFallback)
                                }
                                modelMenu = false
                            })
                        }
                    }
                }
                if (provider.editableModel || provider.models.any { it != config.model } || config.fallbackEnabled) {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.enableFallbackModel)
                            Text(strings.fallbackModelHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(config.fallbackEnabled, { enabled ->
                            update { old ->
                                val suggested = provider.models.firstOrNull { it != old.model }.orEmpty()
                                old.copy(
                                    fallbackEnabled = enabled,
                                    fallbackModel = if (enabled && old.fallbackModel.isBlank()) suggested else old.fallbackModel,
                                )
                            }
                        })
                    }
                    if (config.fallbackEnabled) {
                        Box {
                            OutlinedTextField(
                                value = config.fallbackModel,
                                onValueChange = { if (provider.editableModel) update { old -> old.copy(fallbackModel = it) } },
                                modifier = Modifier.fillMaxWidth(), singleLine = true, readOnly = !provider.editableModel,
                                label = { Text(strings.fallbackModel) },
                                trailingIcon = if (provider.models.any { it != config.model }) ({ IconButton({ fallbackModelMenu = true }) { Icon(Icons.Default.ArrowDropDown, strings.chooseModel) } }) else null,
                                isError = config.fallbackModel.isBlank() || config.fallbackModel.trim() == config.model.trim(),
                                shape = MaterialTheme.shapes.large,
                            )
                            if (provider.models.any { it != config.model }) DropdownMenu(fallbackModelMenu, { fallbackModelMenu = false }) {
                                provider.models.filter { it != config.model }.let { models ->
                                    if (config.fallbackModel.isNotBlank() && config.fallbackModel !in models) listOf(config.fallbackModel) + models else models
                                }.forEach { model ->
                                    DropdownMenuItem({ Text(model) }, leadingIcon = { if (model == config.fallbackModel) Icon(Icons.Default.Check, null) }, onClick = { update { old -> old.copy(fallbackModel = model) }; fallbackModelMenu = false })
                                }
                            }
                        }
                        OutlinedTextField(
                            value = config.fallbackRetryCount.toString(),
                            onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { retries -> update { old -> old.copy(fallbackRetryCount = retries.coerceIn(0, 10)) } } },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(strings.fallbackRetryCount) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(strings.fallbackRetryCountHint) }, shape = MaterialTheme.shapes.large,
                        )
                    }
                }
            }
        }

        item {
            SettingsCard(strings.translationBehavior, Icons.Default.Tune) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(strings.translateBeforeSending); Text(strings.translateBehaviorHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(state.translate, { state.translate = it; state.persistTranslation() })
                }
                HorizontalDivider()
                TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text(strings.advancedSettings, Modifier.weight(1f)); Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (advanced) {
                    OutlinedTextField(
                        value = config.timeoutSeconds.toString(), onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { timeout -> update { old -> old.copy(timeoutSeconds = timeout.coerceIn(3, 300)) } } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(strings.requestTimeout) }, suffix = { Text("s") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(strings.timeoutHint) }, shape = MaterialTheme.shapes.large,
                    )
                    OutlinedTextField(
                        value = config.retryCount.toString(), onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { retries -> update { old -> old.copy(retryCount = retries.coerceIn(0, 10)) } } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(strings.retryCount) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(strings.retryCountHint) }, shape = MaterialTheme.shapes.large,
                    )
                    if (provider.supportsHeaders) OutlinedTextField(
                        value = config.customHeaders, onValueChange = { update { old -> old.copy(customHeaders = it) } }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                        label = { Text(strings.customHeaders) }, placeholder = { Text("X-Provider: value") }, supportingText = { Text(strings.customHeadersHint) }, shape = MaterialTheme.shapes.large,
                    )
                    if (provider.supportsStreaming) Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(strings.streamingResponse); Text(strings.streamingHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(config.streaming, { enabled -> update { old -> old.copy(streaming = enabled) } })
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    testing = true; result = null
                    scope.launch {
                        val outcomes = coroutineScope { state.languages.map { language -> async { language to translateText(provider, config, language, "Hello, how are you?") } }.awaitAll() }
                        val failure = outcomes.firstNotNullOfOrNull { it.second as? TranslationResult.Failure }
                        result = failure ?: TranslationResult.Success(outcomes.joinToString("\n") { (language, outcome) -> "$language: ${(outcome as TranslationResult.Success).text}" })
                        testing = false
                    }
                },
                enabled = !testing && config.baseUrl.isNotBlank() && config.model.isNotBlank() && (!provider.keyRequired || config.apiKey.isNotBlank()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.large,
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Icon(Icons.Default.NetworkCheck, null)
                Spacer(Modifier.width(8.dp)); Text(if (testing) strings.testing else strings.testConnection)
            }
        }

        result?.let { outcome -> item {
            val success = outcome is TranslationResult.Success
            Surface(color = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (success) Icons.Default.CheckCircle else Icons.Default.Error, null)
                    Spacer(Modifier.width(10.dp))
                    Column { Text(if (success) strings.connectionSuccessful else strings.connectionFailedTitle, style = MaterialTheme.typography.titleSmall); Text(when (outcome) { is TranslationResult.Success -> outcome.text; is TranslationResult.Failure -> outcome.message }) }
                }
            }
        } }
    }

    if (showProviderPicker) ProviderPickerDialog(provider.id, strings, onDismiss = { showProviderPicker = false }) { id -> state.selectProvider(id); result = null; showProviderPicker = false }
}

@Composable
private fun ProviderPickerDialog(selectedId: String, strings: LocaleStrings, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { translationProviders.filter { query.isBlank() || it.label.contains(query, true) || it.id.contains(query, true) || it.protocol.displayName().contains(query, true) }.sortedWith(compareByDescending<TranslationProvider> { it.id in recommendedProviderIds }.thenBy { it.label }) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.88f).widthIn(max = 540.dp).heightIn(max = 460.dp), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(strings.chooseProvider, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(strings.providerPickerHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onDismiss) { Icon(Icons.Default.Close, strings.done) }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(strings.searchProvider) }, shape = CircleShape)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { option ->
                        Surface(
                            Modifier.fillMaxWidth().clickable { onSelect(option.id) },
                            color = if (option.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProviderAvatar(option.label); Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { Text(option.label, fontWeight = FontWeight.Medium); if (option.id in recommendedProviderIds) { Spacer(Modifier.width(6.dp)); SuggestionChip({}, { Text(strings.recommended, style = MaterialTheme.typography.labelSmall) }, Modifier.height(26.dp)) } }
                                    Text(option.protocol.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (option.id == selectedId) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderAvatar(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(label.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer) }
    }
}

@Composable
private fun SettingsCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(9.dp)); Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}

private fun ProviderProtocol.displayName() = when (this) {
    ProviderProtocol.OPENAI -> "OpenAI-compatible"; ProviderProtocol.ANTHROPIC -> "Anthropic Messages"; ProviderProtocol.GOOGLE_WEB -> "Public Web API"
    ProviderProtocol.MYMEMORY -> "Translation Memory API"; ProviderProtocol.DEEPL -> "DeepL REST API"; ProviderProtocol.LIBRE -> "LibreTranslate REST API"
}
