package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun ApiCredentialsSection(
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onUpdate: ((ProviderConfig) -> ProviderConfig) -> Unit,
) {
    var regionMenu by remember(provider.id) { mutableStateOf(false) }
    var showKey by remember(provider.id) { mutableStateOf(false) }
    SettingsCard(strings.credentialsAndEndpoint, Icons.Default.Key) {
        if (provider.id !in setOf("google_web", "mymemory")) {
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { onUpdate { old -> old.copy(apiKey = it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(if (provider.keyRequired) strings.apiKey else strings.apiKeyOptional)
                },
                placeholder = { Text(provider.keyPlaceholder) },
                visualTransformation =
                    if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton({ showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            strings.showApiKey,
                        )
                    }
                },
                isError = provider.keyRequired && config.apiKey.isBlank(),
                supportingText =
                    if (provider.keyRequired && config.apiKey.isBlank())
                        ({ Text(strings.apiKeyRequired) })
                    else null,
                shape = MaterialTheme.shapes.large,
            )
        }
        if (provider.regions.isNotEmpty()) {
            Box {
                val selectedRegion = provider.regions.firstOrNull { it.id == config.region }
                OutlinedButton(
                    { regionMenu = true },
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Default.Public, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        selectedRegion?.let { strings.providerRegionLabel(provider.id, it) }
                            ?: strings.customEndpoint,
                        Modifier.weight(1f),
                    )
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(regionMenu, { regionMenu = false }) {
                    provider.regions.forEach { region ->
                        DropdownMenuItem(
                            { Text(strings.providerRegionLabel(provider.id, region)) },
                            leadingIcon = {
                                if (region.id == config.region) Icon(Icons.Default.Check, null)
                            },
                            onClick = {
                                onUpdate { old ->
                                    old.copy(region = region.id, baseUrl = region.baseUrl)
                                }
                                regionMenu = false
                            },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = config.baseUrl,
            onValueChange = { onUpdate { old -> old.copy(baseUrl = it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = !provider.editableBaseUrl,
            label = { Text("Base URL") },
            leadingIcon = {
                Icon(
                    if (config.baseUrl.startsWith("https://")) Icons.Default.Lock
                    else Icons.Default.Language,
                    null,
                )
            },
            supportingText = {
                Text(
                    if (provider.editableBaseUrl) strings.baseUrlHint
                    else strings.officialEndpointLocked
                )
            },
            isError =
                config.baseUrl.isBlank() ||
                    (!config.baseUrl.startsWith("https://") &&
                        !config.baseUrl.startsWith("http://")),
            shape = MaterialTheme.shapes.large,
        )
    }
}
