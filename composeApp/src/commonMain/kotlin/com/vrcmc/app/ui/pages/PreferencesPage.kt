package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val preferenceLanguages =
    listOf(AppLanguage.EN, AppLanguage.JA, AppLanguage.ZH_HANS, AppLanguage.ZH_HANT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreferencesPage(
    theme: ThemeMode,
    themeColor: ThemeColor,
    language: AppLanguage,
    strings: LocaleStrings,
    setTheme: (ThemeMode) -> Unit,
    setThemeColor: (ThemeColor) -> Unit,
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
    readAloudConfig: ReadAloudConfig,
    setReadAloudConfig: (ReadAloudConfig) -> Unit,
    readAloudController: ReadAloudController,
) {
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
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppearancePreferences(
                        theme = theme,
                        themeColor = themeColor,
                        language = language,
                        strings = strings,
                        setTheme = setTheme,
                        setThemeColor = setThemeColor,
                        setLanguage = setLanguage,
                    )
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
        item {
            ReadAloudSettingsSection(readAloudConfig, strings, setReadAloudConfig, readAloudController)
        }
    }
}

@Composable
internal fun AppearancePreferences(
    theme: ThemeMode,
    themeColor: ThemeColor,
    language: AppLanguage,
    strings: LocaleStrings,
    setTheme: (ThemeMode) -> Unit,
    setThemeColor: (ThemeColor) -> Unit,
    setLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        PreferenceGroupLabel(strings.language)
        Spacer(Modifier.height(10.dp))
        PreferenceSegmentGroup(
            options = preferenceLanguages,
            selected = language,
            label = AppLanguage::label,
            onSelect = setLanguage,
            testTag = "appearance-language-group",
        )
        Spacer(Modifier.height(22.dp))
        PreferenceGroupLabel(strings.themeMode)
        Spacer(Modifier.height(10.dp))
        PreferenceSegmentGroup(
            options = ThemeMode.entries,
            selected = theme,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> strings.systemTheme
                    ThemeMode.DARK -> strings.darkTheme
                    ThemeMode.LIGHT -> strings.lightTheme
                }
            },
            onSelect = setTheme,
            testTag = "appearance-theme-mode-group",
        )
        Spacer(Modifier.height(22.dp))
        PreferenceGroupLabel(strings.themeColor)
        Spacer(Modifier.height(10.dp))
        ThemeColorSelector(
            selected = themeColor,
            strings = strings,
            onSelect = setThemeColor,
        )
    }
}

@Composable
private fun PreferenceGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> PreferenceSegmentGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
) {
    val selectedBackground =
        MaterialTheme.colorScheme.onSurface.copy(alpha = .18f)
            .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) selectedBackground else Color.Transparent)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(option) },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSelector(
    selected: ThemeColor,
    strings: LocaleStrings,
    onSelect: (ThemeColor) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("appearance-theme-color-group"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            ThemeColor.entries.forEach { color ->
                val isSelected = color == selected
                val colorLabel = color.localizedName(strings)
                val interactionSource = remember(color) { MutableInteractionSource() }
                Box(
                    modifier =
                        Modifier.weight(1f)
                            .height(52.dp)
                            .testTag("theme-color-${color.storageKey}")
                            .semantics { contentDescription = colorLabel }
                            .selectable(
                                selected = isSelected,
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(color) },
                                role = Role.RadioButton,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier.size(42.dp)
                                .border(
                                    width = 2.dp,
                                    color =
                                        if (isSelected) color.swatch
                                        else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(4.dp),
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(color.swatch, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint =
                                        if (color.swatch.luminance() > .35f) {
                                            Color(0xFF111111)
                                        } else {
                                            Color.White
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ThemeColor.localizedName(strings: LocaleStrings): String =
    when (this) {
        ThemeColor.BLUE -> strings.colorBlue
        ThemeColor.PURPLE -> strings.colorPurple
        ThemeColor.PINK -> strings.colorPink
        ThemeColor.ORANGE -> strings.colorOrange
        ThemeColor.GREEN -> strings.colorGreen
        ThemeColor.CYAN -> strings.colorCyan
    }
