package com.vrcmc.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class NavigationDrawerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private var selected = AppScreen.CHAT

    private fun showDrawer(height: Dp) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.5f)) {
                MaterialTheme {
                    Box(Modifier.size(390.dp, height)) {
                        ModalNavigationDrawer(
                            drawerState = rememberDrawerState(DrawerValue.Open),
                            drawerContent = {
                                VrcmcNavigationDrawer(
                                    selectedScreen = selected,
                                    translationConfigured = true,
                                    translationEnabled = false,
                                    onTranslationEnabledChange = {},
                                    strings = LocaleStringsEn,
                                    onSelect = { selected = it },
                                    modifier = Modifier.testTag("drawer"),
                                    windowInsets = WindowInsets(top = 24.dp, bottom = 24.dp),
                                )
                            },
                        ) {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    @Test fun drawerFillsTallPhoneHeight() {
        showDrawer(900.dp)
        compose.onNodeWithTag("drawer").assertHeightIsEqualTo(900.dp)
        compose.onNodeWithText(LocaleStringsEn.hotwordDictionary).assertIsDisplayed()
        compose.onNodeWithText(LocaleStringsEn.aboutApp).assertIsDisplayed()
    }

    @Test fun shortPhoneCanScrollToLastMenuItemWithoutMovingSheet() {
        showDrawer(480.dp)
        val drawer = compose.onNodeWithTag("drawer")
        val before = drawer.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(LocaleStringsEn.aboutApp).performScrollTo().assertIsDisplayed()
            .performClick()
        assertEquals(
            456.dp,
            compose.onNodeWithText(LocaleStringsEn.aboutApp).getUnclippedBoundsInRoot().bottom,
        )
        compose.runOnIdle { assertEquals(AppScreen.ABOUT, selected) }
        assertEquals(before, drawer.fetchSemanticsNode().boundsInRoot)
        drawer.assertHeightIsEqualTo(480.dp)
        compose.onNodeWithText(LocaleStringsEn.chat).performScrollTo().assertIsDisplayed()
    }
}
