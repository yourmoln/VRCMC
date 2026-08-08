package com.vrcmc.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutPage(
    state: AppState,
    strings: LocaleStrings,
    onOpenLogs: () -> Unit,
    onUpdateAvailable: (AppRelease) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        ) {
            Image(
                painterResource(Res.drawable.logo),
                "VRCMC",
                Modifier.size(128.dp).clip(RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text("VRCMC", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${strings.version} ${AppInfo.VERSION}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(
                onClick = { uriHandler.openUri(AppInfo.REPOSITORY_URL) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Code, null)
                Spacer(Modifier.width(10.dp))
                Text(strings.repository)
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        if (isCheckingForUpdates) return@launch
                        isCheckingForUpdates = true
                        updateStatus = null
                        checkForAppUpdate()
                            .onSuccess { result ->
                                if (result.updateAvailable) {
                                    onUpdateAvailable(result.release)
                                } else {
                                    updateStatus = strings.alreadyLatestVersion
                                }
                            }
                            .onFailure { updateStatus = strings.updateCheckFailed }
                        isCheckingForUpdates = false
                    }
                },
                enabled = !isCheckingForUpdates,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isCheckingForUpdates) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, null)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isCheckingForUpdates) strings.checkingForUpdates
                    else strings.checkForUpdates
                )
            }
            updateStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onOpenLogs,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.ErrorOutline, null)
                Spacer(Modifier.width(10.dp))
                Text(strings.errorLogs)
                if (state.errorLogs.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "(${state.errorLogs.size})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
