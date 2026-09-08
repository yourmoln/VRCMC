package com.vrcmc.app

import androidx.compose.runtime.Composable

internal data class SystemAudioListeningControl(val isOpen: Boolean, val toggle: () -> Unit)

@Composable
internal expect fun rememberSystemAudioListener(
    state: AppState,
    strings: LocaleStrings,
    dark: Boolean,
): SystemAudioListeningControl?
