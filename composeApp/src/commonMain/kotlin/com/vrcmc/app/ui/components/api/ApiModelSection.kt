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

@Composable
internal fun ApiModelSection(
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onUpdate: ((ProviderConfig) -> ProviderConfig) -> Unit,
) {
    var modelMenu by remember(provider.id) { mutableStateOf(false) }
    var fallbackModelMenu by remember(provider.id) { mutableStateOf(false) }
    SettingsCard(strings.model, Icons.Default.AutoAwesome) {
        Box {
            OutlinedTextField(
                value = config.model,
                onValueChange = {
                    if (provider.editableModel) onUpdate { old -> old.copy(model = it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = !provider.editableModel,
                label = { Text(strings.model) },
                trailingIcon =
                    if (provider.models.isNotEmpty())
                        ({
                            IconButton({ modelMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, strings.chooseModel)
                            }
                        })
                    else null,
                supportingText =
                    if (provider.editableModel) ({ Text(strings.customModelPreserved) }) else null,
                isError = config.model.isBlank(),
                shape = MaterialTheme.shapes.large,
            )
            if (provider.models.isNotEmpty())
                DropdownMenu(modelMenu, { modelMenu = false }) {
                    provider.models
                        .let {
                            if (config.model.isNotBlank() && config.model !in it)
                                listOf(config.model) + it
                            else it
                        }
                        .forEach { model ->
                            DropdownMenuItem(
                                { Text(model) },
                                leadingIcon = {
                                    if (model == config.model) Icon(Icons.Default.Check, null)
                                },
                                onClick = {
                                    onUpdate { old ->
                                        val replacementFallback =
                                            provider.models.firstOrNull { it != model }.orEmpty()
                                        old.copy(
                                            model = model,
                                            fallbackModel =
                                                old.fallbackModel.takeUnless { it == model }
                                                    ?: replacementFallback,
                                        )
                                    }
                                    modelMenu = false
                                },
                            )
                        }
                }
        }
        if (
            provider.editableModel ||
                provider.models.any { it != config.model } ||
                config.fallbackEnabled
        ) {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.enableFallbackModel)
                    Text(
                        strings.fallbackModelHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    config.fallbackEnabled,
                    { enabled ->
                        onUpdate { old ->
                            val suggested =
                                provider.models.firstOrNull { it != old.model }.orEmpty()
                            old.copy(
                                fallbackEnabled = enabled,
                                fallbackModel =
                                    if (enabled && old.fallbackModel.isBlank()) suggested
                                    else old.fallbackModel,
                            )
                        }
                    },
                )
            }
            if (config.fallbackEnabled) {
                Box {
                    OutlinedTextField(
                        value = config.fallbackModel,
                        onValueChange = {
                            if (provider.editableModel)
                                onUpdate { old -> old.copy(fallbackModel = it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = !provider.editableModel,
                        label = { Text(strings.fallbackModel) },
                        trailingIcon =
                            if (provider.models.any { it != config.model })
                                ({
                                    IconButton({ fallbackModelMenu = true }) {
                                        Icon(Icons.Default.ArrowDropDown, strings.chooseModel)
                                    }
                                })
                            else null,
                        isError =
                            config.fallbackModel.isBlank() ||
                                config.fallbackModel.trim() == config.model.trim(),
                        shape = MaterialTheme.shapes.large,
                    )
                    if (provider.models.any { it != config.model })
                        DropdownMenu(fallbackModelMenu, { fallbackModelMenu = false }) {
                            provider.models
                                .filter { it != config.model }
                                .let { models ->
                                    if (
                                        config.fallbackModel.isNotBlank() &&
                                            config.fallbackModel !in models
                                    )
                                        listOf(config.fallbackModel) + models
                                    else models
                                }
                                .forEach { model ->
                                    DropdownMenuItem(
                                        { Text(model) },
                                        leadingIcon = {
                                            if (model == config.fallbackModel)
                                                Icon(Icons.Default.Check, null)
                                        },
                                        onClick = {
                                            onUpdate { old -> old.copy(fallbackModel = model) }
                                            fallbackModelMenu = false
                                        },
                                    )
                                }
                        }
                }
                OutlinedTextField(
                    value = config.fallbackRetryCount.toString(),
                    onValueChange = { value ->
                        value.filter(Char::isDigit).toIntOrNull()?.let { retries ->
                            onUpdate { old ->
                                old.copy(fallbackRetryCount = retries.coerceIn(0, 10))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.fallbackRetryCount) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(strings.fallbackRetryCountHint) },
                    shape = MaterialTheme.shapes.large,
                )
            }
        }
    }
}
