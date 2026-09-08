package com.vrcmc.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal val EdgeTtsVoice.displayName: String
    get() = shortName.removePrefix("$locale-").removeSuffix("Neural")

internal fun EdgeTtsVoice.languageLabel(): String = when (locale.substringBefore('-')) {
    "zh" -> if (locale.startsWith("zh-TW") || locale.startsWith("zh-HK")) "繁體中文" else "简体中文"
    "en" -> "English"
    "ja" -> "日本語"
    "ko" -> "한국어"
    "es" -> "Español"
    "fr" -> "Français"
    "de" -> "Deutsch"
    "ru" -> "Русский"
    else -> locale
}

internal fun EdgeTtsVoice.description(strings: LocaleStrings): String =
    listOf(languageLabel(), locale, when (gender) {
        "Female" -> strings.readAloudFemaleVoice
        "Male" -> strings.readAloudMaleVoice
        else -> gender
    }).filter(String::isNotBlank).distinct().joinToString(" · ")

internal fun filterReadAloudVoices(voices: List<EdgeTtsVoice>, query: String, strings: LocaleStrings): List<EdgeTtsVoice> {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return voices.filter { voice ->
        val aliases = when (voice.locale.substringBefore('-')) {
            "zh" -> "Chinese 中文 国语 國語 汉语 漢語 普通话 普通話"
            "en" -> "英文 英语 英語"
            "ja" -> "Japanese 日语 日語 日文"
            "ko" -> "Korean 韩语 韓語 韩文 韓文"
            "es" -> "Spanish 西班牙语 西班牙語"
            "fr" -> "French 法语 法語"
            "de" -> "German 德语 德語"
            "ru" -> "Russian 俄语 俄語"
            else -> ""
        }
        val searchable = "${voice.shortName} ${voice.description(strings)} ${voice.gender} $aliases"
        terms.all { term ->
            when {
                term.equals("male", ignoreCase = true) || term == strings.readAloudMaleVoice -> voice.gender == "Male"
                term.equals("female", ignoreCase = true) || term == strings.readAloudFemaleVoice -> voice.gender == "Female"
                else -> searchable.contains(term, ignoreCase = true)
            }
        }
    }
}

@Composable
internal fun ReadAloudVoiceDialog(
    voices: List<EdgeTtsVoice>,
    currentVoice: String,
    strings: LocaleStrings,
    loading: Boolean,
    loadFailed: Boolean,
    previewBusy: Boolean,
    previewFailed: Boolean,
    onRefresh: () -> Unit,
    onPreview: (EdgeTtsVoice) -> Unit,
    onStopPreview: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(currentVoice) }
    var previewStarted by remember { mutableStateOf(false) }
    val stopPreview by rememberUpdatedState(onStopPreview)
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val options = remember(voices, currentVoice) {
        val saved = voices.firstOrNull { it.shortName == currentVoice }
            ?: EdgeTtsVoice(currentVoice, currentVoice.substringBeforeLast('-'), "")
        listOf(saved) + voices.filterNot { it.shortName == currentVoice }
    }
    val filtered = remember(options, query, strings) { filterReadAloudVoices(options, query, strings) }
    val candidate = options.firstOrNull { it.shortName == selected }
    LaunchedEffect(query) { listState.scrollToItem(0) }
    DisposableEffect(Unit) { onDispose { if (previewStarted) stopPreview() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) { searchFocus.requestFocus() }
        Surface(
            modifier = Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 680.dp)
                .testTag("readAloudVoiceDialog"),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.readAloudVoice, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onRefresh, enabled = !loading) {
                        Icon(Icons.Default.Refresh, strings.readAloudRefresh)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(strings.searchReadAloudVoice) },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = ""; searchFocus.requestFocus() }) {
                            Icon(Icons.Default.Close, strings.readAloudClearSearch)
                        }
                    },
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (loadFailed) Text(strings.readAloudVoicesFailed,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 120.dp), Alignment.Center) {
                        Text(strings.readAloudNoVoices, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .heightIn(max = 340.dp).selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.shortName }) { voice ->
                            val checked = selected == voice.shortName
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (checked) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                                    .selectable(selected = checked, role = Role.RadioButton, onClick = {
                                        if (previewStarted) { onStopPreview(); previewStarted = false }
                                        selected = voice.shortName
                                    })
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(voice.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(voice.description(strings), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                RadioButton(selected = checked, onClick = null)
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(strings.readAloudSelectedVoice, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(candidate?.displayName ?: selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(enabled = candidate != null, onClick = {
                        if (previewStarted && previewBusy) { onStopPreview(); previewStarted = false }
                        else candidate?.let { previewStarted = true; onPreview(it) }
                    }) {
                        Icon(if (previewStarted && previewBusy) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (previewStarted && previewBusy) strings.readAloudStop else strings.readAloudPreview)
                    }
                }
                if (previewStarted && previewFailed) Text(strings.readAloudFailed,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(strings.cancel) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onConfirm(selected) }, enabled = candidate != null) { Text(strings.done) }
                }
            }
        }
    }
}
