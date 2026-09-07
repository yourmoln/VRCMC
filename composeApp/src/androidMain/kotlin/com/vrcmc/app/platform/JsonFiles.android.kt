package com.vrcmc.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberJsonFileActions(
    onImport: (String) -> Unit,
    onExport: () -> Unit,
    onError: () -> Unit,
): JsonFileActions? {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf("") }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }.onSuccess(onImport).onFailure { onError() }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val value = pendingExport
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openOutputStream(uri, "wt")).bufferedWriter(Charsets.UTF_8).use { it.write(value) }
                }
            }.onSuccess { onExport() }.onFailure { onError() }
        }
    }
    return JsonFileActions(
        importFile = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        exportFile = { pendingExport = it; exporter.launch("hotword-dictionary.json") },
    )
}
