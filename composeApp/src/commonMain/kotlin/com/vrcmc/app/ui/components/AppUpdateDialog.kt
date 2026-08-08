package com.vrcmc.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AppUpdateDialog(
    release: AppRelease,
    strings: LocaleStrings,
    onUpdate: () -> Unit,
    onDismiss: (ignoreVersion: Boolean) -> Unit,
) {
    var ignoreVersion by remember(release.tagName) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(ignoreVersion) },
        icon = { Icon(Icons.Default.SystemUpdate, null) },
        title = { Text(strings.updateAvailable) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(release.name.ifBlank { "VRCMC ${release.tagName}" })
                if (release.body.isNotBlank()) {
                    Text(
                        release.body,
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable { ignoreVersion = !ignoreVersion }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = ignoreVersion,
                        onCheckedChange = { ignoreVersion = it },
                    )
                    Text(strings.ignoreThisVersion)
                }
            }
        },
        confirmButton = { TextButton(onUpdate) { Text(strings.updateNow) } },
        dismissButton = {
            TextButton(onClick = { onDismiss(ignoreVersion) }) { Text(strings.later) }
        },
    )
}
