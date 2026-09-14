package com.vrcmc.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.contextMenuOpenDetector
import androidx.compose.ui.Modifier

@OptIn(ExperimentalFoundationApi::class)
internal actual fun Modifier.messageActionsTrigger(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier = contextMenuOpenDetector(enabled = enabled) { onOpen() }
