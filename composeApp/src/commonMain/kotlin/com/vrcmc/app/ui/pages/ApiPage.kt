package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiPage(state: AppState, strings: LocaleStrings) {
    val provider = state.provider
    val config = state.providerConfig
    var showProviderPicker by remember { mutableStateOf(false) }
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
        item { ApiTranslationToggleSection(state, strings) }
        item { ApiProviderSection(provider, strings) { showProviderPicker = true } }
        item { ApiCredentialsSection(provider, config, strings, ::update) }
        item { ApiModelSection(provider, config, strings, ::update) }
        item {
            Button(
                onClick = {
                    testing = true
                    result = null
                    scope.launch {
                        val outcomes = coroutineScope {
                            state.languages
                                .map { language ->
                                    async {
                                        language to
                                            translateText(
                                                provider,
                                                config,
                                                language,
                                                "Hello, how are you?",
                                            )
                                    }
                                }
                                .awaitAll()
                        }
                        val failure =
                            outcomes.firstNotNullOfOrNull {
                                it.second as? TranslationResult.Failure
                            }
                        result =
                            failure
                                ?: TranslationResult.Success(
                                    outcomes.joinToString("\n") { (language, outcome) ->
                                        "$language: ${(outcome as TranslationResult.Success).text}"
                                    }
                                )
                        testing = false
                    }
                },
                enabled =
                    !testing &&
                        config.baseUrl.isNotBlank() &&
                        config.model.isNotBlank() &&
                        (!provider.keyRequired || config.apiKey.isNotBlank()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                if (testing)
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                else Icon(Icons.Default.NetworkCheck, null)
                Spacer(Modifier.width(8.dp))
                Text(if (testing) strings.testing else strings.testConnection)
            }
        }

        result?.let { outcome ->
            item {
                val success = outcome is TranslationResult.Success
                Surface(
                    color =
                        if (success) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(if (success) Icons.Default.CheckCircle else Icons.Default.Error, null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (success) strings.connectionSuccessful
                                else strings.connectionFailedTitle,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                when (outcome) {
                                    is TranslationResult.Success -> outcome.text
                                    is TranslationResult.Failure -> outcome.message
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            VoiceInputServiceSection(
                config = state.voiceInputConfig,
                strings = strings,
                onUpdate = state::updateVoiceInputConfig,
            )
        }
    }

    if (showProviderPicker) {
        ProviderPickerDialog(
            selectedId = provider.id,
            strings = strings,
            onDismiss = { showProviderPicker = false },
            onSelect = { id ->
                state.selectProvider(id)
                result = null
                showProviderPicker = false
            },
        )
    }
}
