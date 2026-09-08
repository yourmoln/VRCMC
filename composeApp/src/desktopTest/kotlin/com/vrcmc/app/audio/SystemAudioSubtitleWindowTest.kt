package com.vrcmc.app

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.sun.jna.Platform
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SystemAudioSubtitleWindowTest {
    @get:Rule val compose = createComposeRule()

    private fun waitForSelection(label: String, value: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().any {
                it.config[SemanticsProperties.StateDescription] == value
            }
        }
    }

    private fun assertRoundedWindow(window: Window) {
        val dwm = Native.load("dwmapi", SubtitleDwmTestApi::class.java)
        Memory(Int.SIZE_BYTES.toLong()).use { value ->
            val result = dwm.DwmGetWindowAttribute(HWND(Native.getWindowPointer(window)), 33, value, Int.SIZE_BYTES)
            if (result == 0) {
                assertEquals(2, value.getInt(0))
            } else {
                val shape = window.shape
                assertTrue(shape != null)
                assertEquals(window.width, shape.bounds.width)
                assertEquals(window.height, shape.bounds.height)
            }
        }
        dwm.DwmFlush()
    }

    @Test
    fun borderlessWindowSupportsOpacityMinimizeRestoreAndBothCloseActions() {
        assumeTrue(Platform.isWindows())
        val provider = providerById("microsoft_edge_web")
        var visible by mutableStateOf(true)
        var closeCount = 0
        var config by mutableStateOf(SystemAudioLanguageConfig())
        compose.setContent {
            if (visible) SystemAudioSubtitleWindow(
                settings = SystemAudioListeningSettings(
                    VoiceInputConfig(), provider, defaultProviderConfig(provider), listOf("English"),
                ),
                strings = LocaleStringsZhHans,
                dark = true,
                onClose = { closeCount++; visible = false },
                onError = { error(it) },
                languageConfig = config,
                onLanguageConfigChange = { config = it },
            )
        }
        compose.onNodeWithText(LocaleStringsZhHans.apiNotConfiguredVoiceInput).assertIsDisplayed()
        compose.onNodeWithText(LocaleStringsZhHans.voiceWaitingForSpeech).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.systemAudioListening).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.systemAudioHint).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.stopListeningToOthers).assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        // Native Window uses the AWT thread. Semantic actions dispatch there; synthetic
        // pointer injection can race its layout from the test worker thread.
        compose.onNodeWithContentDescription(LocaleStringsZhHans.settings)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitForSelection(LocaleStringsZhHans.systemAudioSourceLanguage, "自动")
        compose.onNodeWithContentDescription(LocaleStringsZhHans.targetLanguage).assertIsDisplayed()
        compose.onNodeWithText(LocaleStringsZhHans.cancel)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("systemAudioLanguageSettings").fetchSemanticsNodes().isEmpty() }
        lateinit var subtitleWindow: Window
        compose.runOnIdle {
            subtitleWindow = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 听别人说话"
            }
            assertTrue(subtitleWindow.isAlwaysOnTop)
            assertTrue((subtitleWindow as Frame).isUndecorated)
            assertTrue((subtitleWindow as Frame).isResizable)
            assertTrue(subtitleWindow.iconImages.isNotEmpty())
            assertEquals(.8f, subtitleWindow.opacity)
            assertEquals(3.0 / 4.0, subtitleWindow.width.toDouble() / subtitleWindow.height, .005)
            assertEquals(24, subtitleWindow.x)
            assertEquals(24, subtitleWindow.y)
            assertRoundedWindow(subtitleWindow)
        }
        val windowScreenshot = Robot().createScreenCapture(Rectangle(subtitleWindow.locationOnScreen, subtitleWindow.size))
        val windowOutput = File("build/test-screenshots/system-audio-window.png")
        windowOutput.parentFile.mkdirs()
        ImageIO.write(windowScreenshot, "png", windowOutput)
        // The settings remain usable even after shrinking the borderless window.
        compose.runOnIdle { subtitleWindow.size = subtitleWindow.minimumSize }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.settings)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "80%")
        compose.onNodeWithTag("systemAudioSettingsScroll")
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 500f) }
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("systemAudioSettingsScroll").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].let { it.value() == it.maxValue() }
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(65f) }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "65%")
        compose.runOnIdle {
            assertEquals(.8f, subtitleWindow.opacity)
            assertEquals(80, config.opacityPercent)
        }
        compose.onNodeWithText(LocaleStringsZhHans.save).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { subtitleWindow.opacity == .65f }
        compose.runOnIdle {
            assertEquals(65, config.opacityPercent)
            assertRoundedWindow(subtitleWindow)
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.minimizeWindow)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { (subtitleWindow as Frame).extendedState and Frame.ICONIFIED != 0 }
        compose.runOnIdle {
            assertTrue(visible)
            assertEquals(0, closeCount)
            (subtitleWindow as Frame).extendedState = Frame.NORMAL
        }
        compose.waitUntil(5_000) { (subtitleWindow as Frame).extendedState and Frame.ICONIFIED == 0 }
        compose.runOnIdle { assertRoundedWindow(subtitleWindow) }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.closeWindow)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertFalse(visible)
            assertEquals(1, closeCount)
            assertFalse(subtitleWindow.isDisplayable)
            visible = true
        }
        compose.onNodeWithText(LocaleStringsZhHans.apiNotConfiguredVoiceInput).assertIsDisplayed()
        compose.runOnIdle {
            val reopened = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 听别人说话"
            }
            assertEquals(.65f, reopened.opacity)
            reopened.dispatchEvent(WindowEvent(reopened, WindowEvent.WINDOW_CLOSING))
        }
        compose.runOnIdle {
            assertFalse(visible)
            assertEquals(2, closeCount)
        }
    }

    @Test
    fun settingsApplyLanguagesAndOpacityOnlyWhenSaved() {
        assumeTrue(Platform.isWindows())
        val provider = providerById("microsoft_edge_web")
        val services = SystemAudioListeningSettings(
            VoiceInputConfig(language = "ja"), provider, defaultProviderConfig(provider), listOf("English"),
        )
        var config by mutableStateOf(SystemAudioLanguageConfig())
        var visible by mutableStateOf(true)
        var saves = 0
        compose.setContent {
            MaterialTheme {
                if (visible) SystemAudioLanguageSettingsDialog(
                    config = config, strings = LocaleStringsZhHans,
                    onSave = { config = it; saves++; visible = false },
                    onDismiss = { visible = false },
                )
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage)
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "自动"))
        compose.onNodeWithContentDescription(LocaleStringsZhHans.targetLanguage)
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "简体中文"))
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "80%"))
        val screenshot = compose.onNodeWithTag("systemAudioLanguageSettings").captureToImage()
        val output = File("build/test-screenshots/system-audio-languages.png")
        output.parentFile.mkdirs()
        ImageIO.write(screenshot.toAwtImage(), "png", output)
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("English").performScrollTo().performClick()
        waitForSelection(LocaleStringsZhHans.systemAudioSourceLanguage, "English")
        compose.onNodeWithContentDescription(LocaleStringsZhHans.targetLanguage).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("日本語").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("日本語").performScrollTo().performClick()
        waitForSelection(LocaleStringsZhHans.targetLanguage, "日本語")
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "70%")
        compose.runOnIdle {
            assertEquals(SystemAudioLanguageConfig(), config)
            assertEquals(0, saves)
        }
        compose.onNodeWithText(LocaleStringsZhHans.save).performClick()
        compose.waitUntil(5_000) { saves == 1 }
        compose.runOnIdle {
            assertEquals(SystemAudioLanguageConfig("en", "日本語", 70), config)
            assertEquals(1, saves)
            val effective = services.withLanguages(config)
            assertEquals("en", effective.voice.language)
            assertEquals(listOf("日本語"), effective.languages)
            assertEquals("ja", services.voice.language)
            assertEquals(listOf("English"), services.languages)
            assertEquals(effective, services.withLanguages(config.copy(opacityPercent = 30)))
        }
        compose.runOnIdle { visible = true }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "English"))
        compose.onNodeWithContentDescription(LocaleStringsZhHans.targetLanguage)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "日本語"))
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "70%"))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "30%")
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("自动").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("自动").performScrollTo().performClick()
        waitForSelection(LocaleStringsZhHans.systemAudioSourceLanguage, "自动")
        compose.onNodeWithText(LocaleStringsZhHans.cancel).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("systemAudioLanguageSettings").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle {
            assertEquals(SystemAudioLanguageConfig("en", "日本語", 70), config)
            assertEquals(1, saves)
        }
    }
}

private interface SubtitleDwmTestApi : StdCallLibrary {
    fun DwmGetWindowAttribute(window: HWND, attribute: Int, value: Pointer, size: Int): Int
    fun DwmFlush(): Int
}
