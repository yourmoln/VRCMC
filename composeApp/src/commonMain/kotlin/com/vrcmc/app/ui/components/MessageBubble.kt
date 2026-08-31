package com.vrcmc.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MessageBubble(
    message: ChatMessage,
    strings: LocaleStrings,
    retryAttempt: Int,
    retryLimit: Int,
    resendEnabled: Boolean,
    showJapaneseRomaji: Boolean,
    onCopy: () -> Unit,
    onResend: () -> Unit,
) {
    val user = message.role == MessageRole.USER
    val bubbleColor =
        if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    var menuExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (user) Spacer(Modifier.weight(1f))
            Box {
                Surface(
                    modifier =
                        Modifier.widthIn(max = 320.dp)
                            .wrapContentWidth()
                            .combinedClickable(
                                enabled = !message.isLoading,
                                onClick = {},
                                onLongClick = { menuExpanded = true },
                            ),
                    shape = RoundedCornerShape(8.dp),
                    color = bubbleColor,
                    contentColor =
                        if (user) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                        if (!user && message.language != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    null,
                                    Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    message.language,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                        }
                        if (message.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    if (retryAttempt > 0)
                                        strings.translatingRetry(retryAttempt, retryLimit)
                                    else strings.translating,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            JapaneseMessageText(message, showJapaneseRomaji)
                        }
                    }
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(strings.copyMessage) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        },
                    )
                    if (user) {
                        DropdownMenuItem(
                            text = { Text(strings.resendMessage) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = resendEnabled,
                            onClick = {
                                menuExpanded = false
                                onResend()
                            },
                        )
                    }
                }
            }
            if (!user) Spacer(Modifier.weight(1f))
        }
    }
}
