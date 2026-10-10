package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun ApiProviderSection(
    provider: TranslationProvider,
    config: ProviderConfig,
    strings: LocaleStrings,
    onClick: () -> Unit,
) {
    val providerLabel =
        if (provider.id == customCompatibleProviderId) strings.customCompatible
        else provider.label
    SettingsCard(strings.translationService, Icons.Default.Hub) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderAvatar(providerLabel)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        providerLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        provider.effectiveProtocol(config).displayName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Default.UnfoldMore, strings.provider)
            }
        }
        Text(
            strings.providerHint(provider),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
