package com.vrcmc.app

import androidx.compose.ui.Modifier

internal expect fun Modifier.messageActionsTrigger(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier
