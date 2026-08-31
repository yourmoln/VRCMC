package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
internal fun PreferencesPage(
    theme: ThemeMode,
    language: AppLanguage,
    strings: LocaleStrings,
    setTheme: (ThemeMode) -> Unit,
    setLanguage: (AppLanguage) -> Unit,
    showJapaneseRomaji: Boolean,
    setShowJapaneseRomaji: (Boolean) -> Unit,
    japaneseDictionaryStatus: JapaneseDictionaryStatus,
    retryJapaneseDictionaryDownload: () -> Unit,
    disableDynamicInputLimit: Boolean,
    setDisableDynamicInputLimit: (Boolean) -> Unit,
    disableAutomaticUpdateCheck: Boolean,
    setDisableAutomaticUpdateCheck: (Boolean) -> Unit,
    showTypingStatus: Boolean,
    setShowTypingStatus: (Boolean) -> Unit,
    liveInputPreview: Boolean,
    setLiveInputPreview: (Boolean) -> Unit,
    liveInputPreviewDelaySeconds: Int,
    setLiveInputPreviewDelaySeconds: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val modes = ThemeMode.values()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(strings.preferences, style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(strings.appearance, style = MaterialTheme.typography.titleMedium)
                    Text(
                        strings.theme,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == theme,
                                onClick = { setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> strings.systemTheme
                                            ThemeMode.LIGHT -> strings.lightTheme
                                            ThemeMode.DARK -> strings.darkTheme
                                        },
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        strings.language,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Language, null)
                            Spacer(Modifier.width(8.dp))
                            Text(language.label, Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded, { expanded = false }) {
                            AppLanguage.values().forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.label) },
                                    leadingIcon = {
                                        if (item == language) Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        setLanguage(item)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                strings.showJapaneseRomaji,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            when (japaneseDictionaryStatus) {
                                JapaneseDictionaryStatus.Downloading ->
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(horizontal = 12.dp).size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                is JapaneseDictionaryStatus.Failed ->
                                    IconButton(onClick = retryJapaneseDictionaryDownload) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            strings.retryJapaneseDictionaryDownload,
                                        )
                                    }
                                else -> Unit
                            }
                            Switch(
                                checked = showJapaneseRomaji,
                                onCheckedChange = setShowJapaneseRomaji,
                                enabled =
                                    japaneseDictionaryStatus !=
                                        JapaneseDictionaryStatus.Downloading,
                            )
                        }
                        if (showJapaneseRomaji) {
                            when (japaneseDictionaryStatus) {
                                JapaneseDictionaryStatus.Downloading ->
                                    Text(
                                        strings.japaneseDictionaryDownloading,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                is JapaneseDictionaryStatus.Failed ->
                                    Text(
                                        strings.japaneseDictionaryDownloadFailed,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                else -> Unit
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strings.disableDynamicInputLimit,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(
                            checked = disableDynamicInputLimit,
                            onCheckedChange = setDisableDynamicInputLimit,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strings.disableAutomaticUpdateCheck,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(
                            checked = disableAutomaticUpdateCheck,
                            onCheckedChange = setDisableAutomaticUpdateCheck,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strings.showTypingStatus,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(
                            checked = showTypingStatus,
                            onCheckedChange = setShowTypingStatus,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            strings.liveInputPreview,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Switch(
                            checked = liveInputPreview,
                            onCheckedChange = setLiveInputPreview,
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "${strings.liveInputPreviewDelay}: ${strings.seconds(liveInputPreviewDelaySeconds.toString())}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = liveInputPreviewDelaySeconds.toFloat(),
                            onValueChange = { setLiveInputPreviewDelaySeconds(it.roundToInt()) },
                            valueRange = 0f..30f,
                            enabled = liveInputPreview,
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
}
