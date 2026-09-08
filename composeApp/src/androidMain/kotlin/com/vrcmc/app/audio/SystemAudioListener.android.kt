package com.vrcmc.app

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberSystemAudioListener(
    state: AppState,
    strings: LocaleStrings,
    dark: Boolean,
): SystemAudioListeningControl? = null
