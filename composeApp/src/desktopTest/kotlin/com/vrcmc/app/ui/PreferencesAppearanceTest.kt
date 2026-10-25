package com.vrcmc.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class PreferencesAppearanceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun appearanceUsesLanguageThemeModeThenThemeColorAndUpdatesSelection() {
        var language by mutableStateOf(AppLanguage.ZH_HANS)
        var theme by mutableStateOf(ThemeMode.SYSTEM)
        var themeColor by mutableStateOf(ThemeColor.BLUE)
        val strings = LocaleStringsZhHans

        compose.setContent {
            MaterialTheme(colorScheme = vrcmcColorScheme(themeColor, dark = true)) {
                Box(
                    Modifier.size(540.dp, 390.dp)
                        .testTag("appearance-preview")
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Surface(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        AppearancePreferences(
                            theme = theme,
                            themeColor = themeColor,
                            language = language,
                            strings = strings,
                            setTheme = { theme = it },
                            setThemeColor = { themeColor = it },
                            setLanguage = { language = it },
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }

        val languageTop = compose.onNodeWithText(strings.language).getUnclippedBoundsInRoot().top
        val modeTop = compose.onNodeWithText(strings.themeMode).getUnclippedBoundsInRoot().top
        val colorTop = compose.onNodeWithText(strings.themeColor).getUnclippedBoundsInRoot().top
        assertTrue(languageTop < modeTop)
        assertTrue(modeTop < colorTop)
        compose.onNodeWithTag("appearance-language-group").assertIsDisplayed()
        compose.onNodeWithTag("appearance-theme-mode-group").assertIsDisplayed()
        compose.onNodeWithTag("appearance-theme-color-group").assertIsDisplayed()

        val screenshot = compose.onNodeWithTag("appearance-preview").captureToImage()
        val output = File("build/test-screenshots/appearance-preferences-dark.png")
        output.parentFile.mkdirs()
        ImageIO.write(screenshot.toAwtImage(), "png", output)

        compose.onNodeWithText(AppLanguage.EN.label).performClick()
        compose.onNodeWithText(strings.darkTheme).performClick()
        compose.onNodeWithTag("theme-color-purple").performClick()

        compose.onNodeWithText(AppLanguage.EN.label).assertIsSelected()
        compose.onNodeWithText(strings.darkTheme).assertIsSelected()
        compose.onNodeWithTag("theme-color-purple").assertIsSelected()
        compose.onNodeWithTag("theme-color-blue").assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(AppLanguage.EN, language)
            assertEquals(ThemeMode.DARK, theme)
            assertEquals(ThemeColor.PURPLE, themeColor)
        }
    }

    @Test
    fun englishLabelsFitCompactPreferenceWidth() {
        compose.setContent {
            MaterialTheme(colorScheme = vrcmcColorScheme(ThemeColor.CYAN, dark = true)) {
                Box(Modifier.size(360.dp, 700.dp).background(MaterialTheme.colorScheme.background)) {
                    Surface(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        AppearancePreferences(
                            theme = ThemeMode.SYSTEM,
                            themeColor = ThemeColor.CYAN,
                            language = AppLanguage.EN,
                            strings = LocaleStringsEn,
                            setTheme = {},
                            setThemeColor = {},
                            setLanguage = {},
                            modifier = Modifier.padding(18.dp),
                        )
                    }
                }
            }
        }

        AppLanguage.entries.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
        listOf(
                LocaleStringsEn.systemTheme,
                LocaleStringsEn.darkTheme,
                LocaleStringsEn.lightTheme,
            )
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }

        val screenshot = compose.onRoot().captureToImage()
        val output = File("build/test-screenshots/appearance-preferences-compact.png")
        output.parentFile.mkdirs()
        ImageIO.write(screenshot.toAwtImage(), "png", output)
    }

    @Test
    fun allLocalesProvideThemeControlLabels() {
        AppLanguage.entries.forEach { language ->
            val strings = localeStrings(language)
            assertTrue(strings.themeMode.isNotBlank())
            assertTrue(strings.themeColor.isNotBlank())
            listOf(
                    strings.colorBlue,
                    strings.colorPurple,
                    strings.colorPink,
                    strings.colorOrange,
                    strings.colorGreen,
                    strings.colorCyan,
                )
                .forEach { assertTrue(it.isNotBlank()) }
        }
    }
}
