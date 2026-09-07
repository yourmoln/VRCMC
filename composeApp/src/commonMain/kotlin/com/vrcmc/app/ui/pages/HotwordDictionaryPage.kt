package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotwordDictionaryPage(state: AppState, strings: LocaleStrings) {
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var editIndex by remember { mutableIntStateOf(-1) }
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var entryError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var jsonMode by remember { mutableStateOf<String?>(null) }
    var jsonText by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<HotwordDictionary?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val dictionary = state.hotwordDictionary

    fun save(value: HotwordDictionary): Boolean = runCatching {
        state.updateHotwordDictionary(value)
        status = strings.dictionarySaved
    }.onFailure { status = strings.dictionarySaveFailed }.isSuccess

    fun prepareImport(value: String) {
        runCatching { hotwordDictionaryFromJson(value) }
            .onSuccess { pendingImport = it; jsonMode = null; jsonError = null }
            .onFailure {
                jsonError = strings.invalidDictionaryJson
                status = strings.invalidDictionaryJson
            }
    }

    val files = rememberJsonFileActions(
        onImport = ::prepareImport,
        onExport = { status = strings.dictionaryExported; jsonMode = null },
        onError = { status = strings.dictionarySaveFailed; jsonError = strings.dictionarySaveFailed },
    )

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings.hotwordDictionary, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { jsonMode = "import"; jsonText = ""; jsonError = null }) {
                Text(strings.importJson)
            }
            OutlinedButton(onClick = { jsonMode = "export"; jsonText = dictionary.toJson(); jsonError = null }) {
                Text(strings.exportJson)
            }
        }
        TabRow(selectedTabIndex = tab) {
            listOf(
                "${strings.keywordReplacement} (${dictionary.replacements.size})",
                "${strings.sentenceBlocking} (${dictionary.blockedSentences.size})",
            ).forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = {
                editIndex = -1; source = ""; target = ""; entryError = null; editing = true
            }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(strings.addDictionaryEntry)
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(Modifier.weight(1f)) {
            val entries = if (tab == 0) dictionary.replacements.map { it.keyword to it.target }
                else dictionary.blockedSentences.map { it to null }
            if (entries.isEmpty()) item { Text(strings.emptyDictionary, Modifier.padding(vertical = 16.dp)) }
            itemsIndexed(entries) { index, entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.first, style = MaterialTheme.typography.bodyLarge)
                        entry.second?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    DictionaryIconButton(strings.editDictionaryEntry, {
                        editIndex = index; source = entry.first; target = entry.second.orEmpty()
                        entryError = null; editing = true
                    }) { Icon(Icons.Default.Edit, null) }
                    DictionaryIconButton(strings.delete, {
                        save(if (tab == 0) dictionary.copy(replacements = dictionary.replacements.filterIndexed { i, _ -> i != index })
                            else dictionary.copy(blockedSentences = dictionary.blockedSentences.filterIndexed { i, _ -> i != index }))
                    }) { Icon(Icons.Default.Delete, null) }
                }
                HorizontalDivider()
            }
        }
    }

    if (editing) AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(if (editIndex < 0) strings.addDictionaryEntry else strings.editDictionaryEntry) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(source, { source = it; entryError = null }, Modifier.fillMaxWidth(),
                    maxLines = 4, label = { Text(if (tab == 0) strings.keyword else strings.blockedSentence) })
                if (tab == 0) OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(),
                    maxLines = 4, label = { Text(strings.replacementTarget) })
                entryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = source.isNotBlank(), onClick = {
                val key = if (tab == 0) source else source.trim()
                val duplicate = if (tab == 0) dictionary.replacements.withIndex().any { it.index != editIndex && it.value.keyword == key }
                    else dictionary.blockedSentences.withIndex().any { it.index != editIndex && it.value == key }
                if (duplicate) entryError = strings.duplicateDictionaryEntry
                else {
                    val updated = if (tab == 0) dictionary.copy(replacements = dictionary.replacements.toMutableList().apply {
                        val rule = KeywordReplacement(key, target)
                        if (editIndex < 0) add(rule) else set(editIndex, rule)
                    }) else dictionary.copy(blockedSentences = dictionary.blockedSentences.toMutableList().apply {
                        if (editIndex < 0) add(key) else set(editIndex, key)
                    })
                    if (save(updated)) editing = false else entryError = strings.dictionarySaveFailed
                }
            }) { Text(strings.save) }
        },
        dismissButton = { TextButton({ editing = false }) { Text(strings.cancel) } },
    )

    jsonMode?.let { mode ->
        val importing = mode == "import"
        AlertDialog(
            onDismissRequest = { jsonMode = null },
            title = { Text(if (importing) strings.importJson else strings.exportJson) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(jsonText, { jsonText = it; jsonError = null },
                        Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp),
                        readOnly = !importing, label = { Text("JSON") }, isError = jsonError != null)
                    jsonError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    files?.let { actions ->
                        TextButton(onClick = {
                            if (importing) actions.importFile() else actions.exportFile(jsonText)
                        }) { Text(if (importing) strings.importJsonFile else strings.exportJsonFile) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = jsonText.isNotBlank(), onClick = {
                    if (importing) prepareImport(jsonText)
                    else scope.launch {
                        runCatching { clipboard.setClipEntry(textClipEntry(jsonText)) }
                            .onSuccess { status = strings.dictionaryExported; jsonMode = null }
                            .onFailure { jsonError = strings.dictionarySaveFailed }
                    }
                }) { Text(if (importing) strings.importJson else strings.copyMessage) }
            },
            dismissButton = { TextButton({ jsonMode = null }) { Text(strings.cancel) } },
        )
    }
    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(strings.replaceDictionaryTitle) },
            text = { Text("${strings.replaceDictionaryMessage}\n${strings.keywordReplacement}: ${imported.replacements.size}\n${strings.sentenceBlocking}: ${imported.blockedSentences.size}") },
            confirmButton = { TextButton({
                if (save(imported)) { pendingImport = null; status = strings.dictionaryImported }
            }) { Text(strings.importJson) } },
            dismissButton = { TextButton({ pendingImport = null }) { Text(strings.cancel) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryIconButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }, content = content)
    }
}
