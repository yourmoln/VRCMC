package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ApiModelSection(
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onUpdate: ((ProviderConfig) -> ProviderConfig) -> Unit,
) {
    var modelMenu by remember(provider.id) { mutableStateOf(false) }
    var advanced by remember(provider.id) { mutableStateOf(false) }
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
        HorizontalDivider()
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text(strings.advancedSettings, Modifier.weight(1f))
            Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (advanced) {
            ApiRequestSettings(
                provider = provider,
                config = config,
                strings = strings,
                onUpdate = onUpdate,
                contentAfterRetry = {
                    ApiFallbackModelSettings(provider, config, strings, onUpdate)
                },
            )
        }
    }
}
