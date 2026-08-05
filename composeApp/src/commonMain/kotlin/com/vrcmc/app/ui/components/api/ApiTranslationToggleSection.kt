package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun ApiTranslationToggleSection(state: AppState, strings: LocaleStrings) {
    SettingsCard(strings.translationBehavior, Icons.Default.Translate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(strings.translateBeforeSending)
                Text(
                    strings.translateBehaviorHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.translate,
                onCheckedChange = {
                    state.translate = it
                    state.persistTranslation()
                },
            )
        }
    }
}
