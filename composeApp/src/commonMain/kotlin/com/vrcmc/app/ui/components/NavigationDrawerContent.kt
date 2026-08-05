package com.vrcmc.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vrcmc.app.generated.resources.Res
import com.vrcmc.app.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

internal enum class AppScreen {
    CHAT,
    DEVICES,
    API,
    TRANSLATION_LANGUAGE,
    SIMULTANEOUS_INTERPRETATION,
    CONFIGURE_VRC,
    PREFERENCES,
    ABOUT,
    ERROR_LOGS,
}

private data class NavigationItem(val screen: AppScreen, val label: String, val icon: ImageVector)

@Composable
internal fun VrcmcNavigationDrawer(
    selectedScreen: AppScreen,
    strings: LocaleStrings,
    onSelect: (AppScreen) -> Unit,
) {
    val primaryItems =
        listOf(
            NavigationItem(AppScreen.CHAT, strings.chat, Icons.Default.ChatBubble),
            NavigationItem(AppScreen.DEVICES, strings.deviceManagement, Icons.Default.Devices),
            NavigationItem(AppScreen.API, strings.apiConfiguration, Icons.Default.Tune),
            NavigationItem(
                AppScreen.TRANSLATION_LANGUAGE,
                strings.languageSettings,
                Icons.Default.Translate,
            ),
            NavigationItem(
                AppScreen.SIMULTANEOUS_INTERPRETATION,
                strings.simultaneousInterpretation,
                Icons.Default.RecordVoiceOver,
            ),
        )
    val secondaryItems =
        listOf(
            NavigationItem(
                AppScreen.CONFIGURE_VRC,
                strings.configureVrc,
                Icons.Default.SettingsInputAntenna,
            ),
            NavigationItem(AppScreen.PREFERENCES, strings.preferences, Icons.Default.Settings),
            NavigationItem(AppScreen.ABOUT, strings.aboutApp, Icons.Default.Info),
        )

    ModalDrawerSheet(Modifier.width(260.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Text("VRCMC", style = MaterialTheme.typography.titleMedium)
        }
        primaryItems.forEach { item -> NavigationItemRow(item, selectedScreen, onSelect) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        secondaryItems.forEach { item -> NavigationItemRow(item, selectedScreen, onSelect) }
    }
}

@Composable
private fun NavigationItemRow(
    item: NavigationItem,
    selectedScreen: AppScreen,
    onSelect: (AppScreen) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(item.label) },
        selected = selectedScreen == item.screen,
        icon = { Icon(item.icon, contentDescription = null) },
        onClick = { onSelect(item.screen) },
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
