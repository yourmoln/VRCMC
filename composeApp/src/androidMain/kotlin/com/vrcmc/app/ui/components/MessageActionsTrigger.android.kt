package com.vrcmc.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

internal actual fun Modifier.messageActionsTrigger(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier = combinedClickable(
    enabled = enabled,
    onClick = {},
    onLongClick = onOpen,
)
