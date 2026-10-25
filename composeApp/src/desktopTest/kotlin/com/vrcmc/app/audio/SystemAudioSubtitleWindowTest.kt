package com.vrcmc.app

import androidx.compose.runtime.*
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

    private fun SemanticsNodeInteraction.performClickOnUiThread() =
        performSemanticsAction(SemanticsActions.OnClick) { it() }

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
        compose.onNodeWithText(LocaleStringsZhHans.cancel).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.save).assertDoesNotExist()
        lateinit var subtitleWindow: Window
        lateinit var settingsWindow: Window
        compose.runOnIdle {
            subtitleWindow = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 听别人说话"
            }
            settingsWindow = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 设置"
            }
            assertTrue(settingsWindow is Frame)
            assertFalse(settingsWindow is java.awt.Dialog)
            val settingsFrame = settingsWindow as Frame
            assertTrue(settingsFrame.isUndecorated)
            assertFalse(settingsFrame.isResizable)
            assertTrue(settingsWindow.isAlwaysOnTop)
            assertEquals(1f, settingsWindow.opacity)
            assertEquals(subtitleWindow.width, settingsWindow.width)
            assertEquals(subtitleWindow.height, settingsWindow.height)
            assertEquals(subtitleWindow.location, settingsWindow.location)
            assertRoundedWindow(settingsWindow)
        }
        compose.onNodeWithText(LocaleStringsZhHans.done)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("systemAudioLanguageSettings").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle {
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
        compose.runOnIdle {
            settingsWindow = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 设置"
            }
            assertEquals(1f, settingsWindow.opacity)
        }
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
        compose.waitUntil(5_000) { subtitleWindow.opacity == .65f && config.opacityPercent == 65 }
        compose.runOnIdle {
            assertEquals(65, config.opacityPercent)
            assertRoundedWindow(subtitleWindow)
            assertEquals(subtitleWindow.location, settingsWindow.location)
        }
        compose.onNodeWithText(LocaleStringsZhHans.done)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("systemAudioLanguageSettings").fetchSemanticsNodes().isEmpty() }
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
    fun steamVrSettingsApplyImmediatelyInAnIndependentOpaqueWindow() {
        assumeTrue(Platform.isWindows())
        var config by mutableStateOf(SystemAudioLanguageConfig())
        var visible by mutableStateOf(true)
        var changes = 0
        compose.setContent {
            if (visible) SystemAudioLanguageSettingsWindow(
                config = config,
                strings = LocaleStringsZhHans,
                dark = true,
                onConfigChange = { config = it; changes++ },
                onClose = { visible = false },
            )
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(LocaleStringsZhHans.cancel).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.save).assertDoesNotExist()
        lateinit var settingsWindow: Window
        compose.runOnIdle {
            settingsWindow = Window.getWindows().single {
                it.isShowing && (it as? Frame)?.title == "VRCMC · 设置"
            }
            assertTrue(settingsWindow is Frame)
            assertFalse(settingsWindow is java.awt.Dialog)
            val settingsFrame = settingsWindow as Frame
            assertTrue(settingsFrame.isUndecorated)
            assertFalse(settingsFrame.isResizable)
            assertTrue(settingsWindow.isAlwaysOnTop)
            assertEquals(1f, settingsWindow.opacity)
            assertEquals(0, settingsWindow.background.alpha)
            assertEquals(360, settingsWindow.width)
            assertEquals(480, settingsWindow.height)
            assertRoundedWindow(settingsWindow)
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlay).assertIsOff().performClickOnUiThread()
        compose.waitUntil(5_000) { config.steamVrOverlayEnabled }
        waitForSelection(LocaleStringsZhHans.steamVrOverlayPosition, "左手")
        waitForSelection(LocaleStringsZhHans.steamVrOverlaySize, LocaleStringsZhHans.steamVrOverlayDefaultSize)
        waitForSelection(LocaleStringsZhHans.steamVrOverlayFontSize, LocaleStringsZhHans.steamVrOverlayDefaultFontSize)
        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlaySize)
            .performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("steamVrOverlaySize-option-150").fetchSemanticsNodes().isNotEmpty()
        }
        val sizeTag = "steamVrOverlaySize-option-150"
        compose.onNodeWithTag(sizeTag).performClickOnUiThread()
        compose.waitUntil(5_000) { config.steamVrOverlayScalePercent == 150 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(sizeTag).fetchSemanticsNodes().isEmpty() }
        waitForSelection(LocaleStringsZhHans.steamVrOverlaySize, "150%")

        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlayFontSize)
            .performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("steamVrOverlayFontSize-option-175").fetchSemanticsNodes().isNotEmpty()
        }
        val fontSizeTag = "steamVrOverlayFontSize-option-175"
        compose.onNodeWithTag(fontSizeTag).performClickOnUiThread()
        compose.waitUntil(5_000) { config.steamVrOverlayFontSizePercent == 175 }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(fontSizeTag).fetchSemanticsNodes().isEmpty() }
        waitForSelection(LocaleStringsZhHans.steamVrOverlayFontSize, "175%")

        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlayPosition)
            .performClickOnUiThread()
        val positionTag = "steamVrOverlayPosition-option-${SteamVrOverlayPosition.SCREEN_CENTER.name}"
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(positionTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(positionTag).performClickOnUiThread()
        compose.waitUntil(5_000) { config.steamVrOverlayPosition == SteamVrOverlayPosition.SCREEN_CENTER }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(positionTag).fetchSemanticsNodes().isEmpty()
        }
        waitForSelection(LocaleStringsZhHans.steamVrOverlayPosition, "屏幕中间")
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(55f) }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "55%")
        val expected = SystemAudioLanguageConfig(
            opacityPercent = 55,
            steamVrOverlayEnabled = true,
            steamVrOverlayPosition = SteamVrOverlayPosition.SCREEN_CENTER,
            steamVrOverlayScalePercent = 150,
            steamVrOverlayFontSizePercent = 175,
        )
        compose.runOnIdle {
            assertEquals(expected, config)
            assertTrue(changes >= 5)
        }
        val image = compose.onNodeWithTag("systemAudioLanguageSettings").captureToImage()
        val imageAwt = image.toAwtImage()
        assertEquals(0, imageAwt.getRGB(0, 0) ushr 24)
        assertEquals(0, imageAwt.getRGB(imageAwt.width - 1, 0) ushr 24)
        val output = File("build/test-screenshots/system-audio-steamvr-settings.png")
        output.parentFile.mkdirs()
        ImageIO.write(imageAwt, "png", output)
        val windowScreenshot = Robot().createScreenCapture(
            Rectangle(settingsWindow.locationOnScreen, settingsWindow.size),
        )
        ImageIO.write(windowScreenshot, "png", File("build/test-screenshots/system-audio-settings-window.png"))
        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlay)
            .assertIsOn().performClickOnUiThread()
        compose.waitUntil(5_000) { !config.steamVrOverlayEnabled }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(LocaleStringsZhHans.steamVrOverlayPosition)
                .fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.steamVrOverlaySize).assertDoesNotExist()
        compose.runOnIdle { assertEquals(expected.copy(steamVrOverlayEnabled = false), config) }
        compose.onNodeWithText(LocaleStringsZhHans.done).performClickOnUiThread()
        compose.waitUntil(5_000) { !visible }
    }

    @Test
    fun settingsApplyLanguagesAndOpacityImmediatelyWithoutSaveOrCancel() {
        assumeTrue(Platform.isWindows())
        val provider = providerById("microsoft_edge_web")
        val services = SystemAudioListeningSettings(
            VoiceInputConfig(language = "ja"), provider, defaultProviderConfig(provider), listOf("English"),
        )
        var config by mutableStateOf(SystemAudioLanguageConfig())
        var visible by mutableStateOf(true)
        var changes = 0
        compose.setContent {
            if (visible) SystemAudioLanguageSettingsWindow(
                config = config,
                strings = LocaleStringsZhHans,
                dark = false,
                onConfigChange = { config = it; changes++ },
                onClose = { visible = false },
            )
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
        compose.onNodeWithText(LocaleStringsZhHans.cancel).assertDoesNotExist()
        compose.onNodeWithText(LocaleStringsZhHans.save).assertDoesNotExist()
        val screenshot = compose.onNodeWithTag("systemAudioLanguageSettings").captureToImage()
        val output = File("build/test-screenshots/system-audio-languages.png")
        output.parentFile.mkdirs()
        ImageIO.write(screenshot.toAwtImage(), "png", output)
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioSourceLanguage).performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("systemAudioSourceLanguage-option-en").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("systemAudioSourceLanguage-option-en").performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("systemAudioSourceLanguage-option-en").fetchSemanticsNodes().isEmpty()
        }
        waitForSelection(LocaleStringsZhHans.systemAudioSourceLanguage, "English")
        compose.runOnIdle { assertEquals("en", config.sourceLanguage) }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.targetLanguage).performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("systemAudioTargetLanguage-option-日本語").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("systemAudioTargetLanguage-option-日本語")
            .performClickOnUiThread()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("systemAudioTargetLanguage-option-日本語").fetchSemanticsNodes().isEmpty()
        }
        waitForSelection(LocaleStringsZhHans.targetLanguage, "日本語")
        compose.runOnIdle { assertEquals("日本語", config.targetLanguage) }
        compose.onNodeWithContentDescription(LocaleStringsZhHans.systemAudioWindowOpacity)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        waitForSelection(LocaleStringsZhHans.systemAudioWindowOpacity, "70%")
        compose.runOnIdle {
            assertEquals(SystemAudioLanguageConfig("en", "日本語", 70), config)
            assertTrue(changes >= 3)
            val effective = services.withLanguages(config)
            assertEquals("en", effective.voice.language)
            assertEquals(listOf("日本語"), effective.languages)
            assertEquals("ja", services.voice.language)
            assertEquals(listOf("English"), services.languages)
            assertEquals(effective, services.withLanguages(config.copy(opacityPercent = 30)))
        }
        compose.onNodeWithText(LocaleStringsZhHans.done).performClickOnUiThread()
        compose.waitUntil(5_000) { !visible }
    }
}

private interface SubtitleDwmTestApi : StdCallLibrary {
    fun DwmGetWindowAttribute(window: HWND, attribute: Int, value: Pointer, size: Int): Int
    fun DwmFlush(): Int
}
