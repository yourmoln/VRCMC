package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SimultaneousInterpretationPage(state: AppState, strings: LocaleStrings) {
    val device = state.activeDevice()
    val endpoint = device?.let { "${it.address}:${it.sendPort}" } ?: "-:$defaultVrchatSendPort"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    strings.simultaneousInterpretation,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.KeyboardVoice,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        strings.simultaneousInterpretationInputHint,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(strings.interpretationVoiceInput, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        strings.interpretationVoiceInputHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = state.interpretationVoiceInputEnabled,
                    onCheckedChange = state::updateInterpretationVoiceInputEnabled,
                )
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        strings.simultaneousInterpretation,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.enableSimultaneousInterpretation,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.simultaneousInterpretationHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.simultaneousInterpretationEnabled,
                            onCheckedChange = state::updateSimultaneousInterpretationEnabled,
                        )
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Sensors,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${strings.listeningPort}: $endpoint", Modifier.weight(1f))
                        if (state.isSimultaneousInterpretationActive) {
                            Text(
                                strings.interpreting,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Text(
                        when {
                            !state.simultaneousInterpretationEnabled -> strings.listenerStopped
                            state.simultaneousListenerError != null ->
                                strings.listenerFailed.replace(
                                    "%s",
                                    state.simultaneousListenerError.orEmpty(),
                                )
                            else -> strings.listenerReady
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (state.simultaneousListenerError == null)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(strings.alwaysInterpretation, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.enableAlwaysInterpretation,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                strings.alwaysInterpretationHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.alwaysInterpretationEnabled,
                            onCheckedChange = state::updateAlwaysInterpretationEnabled,
                        )
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings.alwaysInterpretationDelay,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                strings.alwaysInterpretationDelayHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            strings.seconds(
                                formatDelaySeconds(state.alwaysInterpretationDelayMillis)
                            )
                        )
                    }
                    Slider(
                        value = state.alwaysInterpretationDelayMillis / 1_000f,
                        onValueChange = {
                            val halfSeconds = (it * 2).roundToInt()
                            state.updateAlwaysInterpretationDelayMillis(halfSeconds * 500)
                        },
                        valueRange = .5f..10f,
                        thumb = {
                            Box(
                                Modifier.size(16.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                thumbTrackGapSize = 0.dp,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun formatDelaySeconds(delayMillis: Int): String =
    if (delayMillis % 1_000 == 0) {
        (delayMillis / 1_000).toString()
    } else {
        (delayMillis / 1_000f).toString()
    }
