package com.vrcmc.app

import androidx.compose.runtime.*
import java.awt.EventQueue
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberJsonFileActions(
    onImport: (String) -> Unit,
    onExport: () -> Unit,
    onError: () -> Unit,
): JsonFileActions? {
    val scope = rememberCoroutineScope()
    val importCallback by rememberUpdatedState(onImport)
    val exportCallback by rememberUpdatedState(onExport)
    val errorCallback by rememberUpdatedState(onError)
    return remember(scope) { JsonFileActions(
        importFile = {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { chooseJsonFile(false)?.readText(Charsets.UTF_8) }
                }.onSuccess { it?.let(importCallback) }.onFailure { errorCallback() }
            }
        },
        exportFile = { value ->
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        chooseJsonFile(true)?.let { it.writeText(value, Charsets.UTF_8); true } ?: false
                    }
                }.onSuccess { if (it) exportCallback() }.onFailure { errorCallback() }
            }
        },
    ) }
}

private fun chooseJsonFile(save: Boolean): File? {
    var selected: File? = null
    EventQueue.invokeAndWait {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("JSON", "json")
            if (save) selectedFile = File("hotword-dictionary.json")
        }
        val result = if (save) chooser.showSaveDialog(owner) else chooser.showOpenDialog(owner)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile.let {
                if (save && !it.name.endsWith(".json", ignoreCase = true)) File(it.parentFile, "${it.name}.json") else it
            }
            if (!save || !file.exists() || JOptionPane.showConfirmDialog(
                    owner, file.absolutePath, "Overwrite?", JOptionPane.YES_NO_OPTION,
                ) == JOptionPane.YES_OPTION) selected = file
        }
    }
    return selected
}
