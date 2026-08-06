package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val qianwenUrl = "https://www.qianwenai.com"

@Composable
internal fun QuickStartPage(
    state: AppState,
    strings: LocaleStrings,
    onFinish: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(1) }
    var qwenConfigured by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        QuickStartProgress(currentStep, strings)
        HorizontalDivider()
        when (currentStep) {
            1 ->
                QwenQuickStartStep(
                    state = state,
                    strings = strings,
                    onConfigured = {
                        qwenConfigured = true
                        currentStep = 2
                    },
                    onSkip = { currentStep = 2 },
                )
            2 -> {
                if (qwenConfigured) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(strings.autoConfigureSuccess, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ConfigureVrcPage(strings, Modifier.weight(1f))
                Button(
                    onClick = { currentStep = 3 },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(strings.vrcConfigured)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
            else -> DeviceQuickStartStep(state, strings, onFinish)
        }
    }
}

@Composable
private fun QuickStartProgress(currentStep: Int, strings: LocaleStrings) {
    val labels = listOf(strings.quickStartQwen, strings.quickStartVrc, strings.quickStartDevice)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.quickStart, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text(
                strings.stepProgress.replace("%d", currentStep.toString()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LinearProgressIndicator(
            progress = { currentStep / 3f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (index + 1 <= currentStep) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QwenQuickStartStep(
    state: AppState,
    strings: LocaleStrings,
    onConfigured: () -> Unit,
    onSkip: () -> Unit,
) {
    val provider = remember { providerById("qianwen") }
    val regions = remember { provider.regions.filter { it.baseUrl.isNotBlank() } }
    var selectedRegionId by remember { mutableStateOf("china") }
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var apiKey by remember {
        mutableStateOf(state.providerConfigs[provider.id]?.apiKey.orEmpty())
    }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val selectedRegion = regions.firstOrNull { it.id == selectedRegionId } ?: regions.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(strings.quickStartQwen, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                strings.quickStartQwenIntro,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedButton(
                onClick = { uriHandler.openUri(qianwenUrl) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.openQianwen)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(strings.qwenApiKey) },
                        placeholder = { Text("sk-...") },
                        visualTransformation =
                            if (showKey) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton({ showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    strings.showApiKey,
                                )
                            }
                        },
                        isError = error != null,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { regionMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Public, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                strings.providerRegionLabel(provider.id, selectedRegion),
                                Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(
                            expanded = regionMenuExpanded,
                            onDismissRequest = { regionMenuExpanded = false },
                        ) {
                            regions.forEach { region ->
                                DropdownMenuItem(
                                    text = {
                                        Text(strings.providerRegionLabel(provider.id, region))
                                    },
                                    leadingIcon = {
                                        if (region.id == selectedRegionId) {
                                            Icon(Icons.Default.Check, null)
                                        }
                                    },
                                    onClick = {
                                        selectedRegionId = region.id
                                        regionMenuExpanded = false
                                        error = null
                                    },
                                )
                            }
                        }
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            testing = true
                            error = null
                            scope.launch {
                                val testConfig =
                                    defaultProviderConfig(provider).copy(
                                        apiKey = apiKey.trim(),
                                        baseUrl = selectedRegion.baseUrl,
                                        model = "qwen-mt-plus",
                                        region = selectedRegion.id,
                                        retryCount = 0,
                                        fallbackEnabled = false,
                                    )
                                when (
                                    val result =
                                        translateText(
                                            provider,
                                            testConfig,
                                            "English",
                                            "你好，很高兴认识你。",
                                        )
                                ) {
                                    is TranslationResult.Success -> {
                                        state.configureQwenServices(apiKey, selectedRegion.id)
                                        onConfigured()
                                    }
                                    is TranslationResult.Failure -> error = result.message
                                }
                                testing = false
                            }
                        },
                        enabled = !testing && apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Default.AutoFixHigh, null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (testing) strings.autoConfiguring else strings.autoConfigure)
                    }
                }
            }
        }
        item {
            TextButton(
                onClick = onSkip,
                enabled = !testing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.SkipNext, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.skipQwenConfiguration)
            }
        }
    }
}

@Composable
private fun DeviceQuickStartStep(
    state: AppState,
    strings: LocaleStrings,
    onFinish: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(strings.quickStartDevice, style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .38f),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(strings.computerIpGuide, Modifier.weight(1f))
                }
            }
        }
        item {
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.ipAddress) },
                placeholder = { Text(strings.computerIpPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Lan, null) },
                supportingText = {
                    Text(if (invalid) strings.invalidDeviceAddress else strings.defaultPortHint)
                },
                isError = invalid,
            )
        }
        item {
            Button(
                onClick = {
                    if (state.addDevice(address)) onFinish() else invalid = true
                },
                enabled = address.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.addAndFinish)
            }
        }
    }
}
