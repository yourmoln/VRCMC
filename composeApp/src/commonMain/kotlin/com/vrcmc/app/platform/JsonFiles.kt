package com.vrcmc.app

import androidx.compose.runtime.Composable

class JsonFileActions(val importFile: () -> Unit, val exportFile: (String) -> Unit)

@Composable
expect fun rememberJsonFileActions(
    onImport: (String) -> Unit,
    onExport: () -> Unit,
    onError: () -> Unit,
): JsonFileActions?
