package com.vrcmc.app

import com.sun.jna.Platform
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.lwjgl.openvr.OpenVR
import org.lwjgl.openvr.VR.VR_IsHmdPresent
import org.lwjgl.openvr.VR.VR_IsRuntimeInstalled

class SteamVrSubtitleOverlayTest {
    private val content = SteamVrOverlayContent(
        "正在听…",
        listOf(SystemAudioCaption(1, "こんにちは。Welcome to VRChat!", listOf("简体中文" to "你好，欢迎来到 VRChat！"))),
        dark = true,
    )
    private val initial = SteamVrOverlayFrame(SteamVrOverlayPosition.LEFT_HAND, 80, content)

    @Test
    fun positionOpacityAndCaptionsUpdateWithinOneSessionAndCancellationClosesIt() = runBlocking {
        withTimeout(5_000) {
            val frames = MutableStateFlow(initial)
            val updates = Channel<SteamVrOverlayFrame>(Channel.UNLIMITED)
            var opens = 0
            var closes = 0
            val job = launch {
                runSteamVrOverlay(frames, open = {
                    opens++
                    object : SteamVrOverlayBackend {
                        override fun update(frame: SteamVrOverlayFrame): SteamVrOverlayStatus {
                            updates.trySend(frame)
                            return SteamVrOverlayStatus.CONNECTED
                        }
                        override fun close() { closes++ }
                    }
                }, onStatus = {}, onError = { fail(it) }, refreshMillis = 1)
            }
            try {
                assertEquals(initial, updates.receive())
                for (position in SteamVrOverlayPosition.entries) for (scale in steamVrOverlayScalePercents) {
                    val next = initial.copy(position = position, opacityPercent = 65, scalePercent = scale)
                    frames.value = next
                    while (updates.receive() != next) { /* Wait for the worker to observe the new frame. */ }
                }
                val next = frames.value.copy(content = content.copy(captions = content.captions + SystemAudioCaption(2, "Next sentence")))
                frames.value = next
                while (updates.receive() != next) { }
                assertEquals(1, opens)
                assertEquals(0, closes)
            } finally {
                job.cancelAndJoin()
            }
            assertEquals(1, closes)
        }
    }

    @Test
    fun unavailableRuntimeRetriesWithoutRepeatedLogsAndClosesBeforeReconnect() = runBlocking {
        withTimeout(5_000) {
            val frames = MutableStateFlow(initial)
            val statuses = Channel<SteamVrOverlayStatus>(Channel.UNLIMITED)
            val errors = mutableListOf<String>()
            var attempts = 0
            var closes = 0
            val job = launch {
                runSteamVrOverlay(frames, open = {
                    attempts++
                    if (attempts <= 2) throw IllegalStateException("Runtime unavailable")
                    if (attempts == 4) assertEquals(1, closes)
                    val attempt = attempts
                    object : SteamVrOverlayBackend {
                        override fun update(frame: SteamVrOverlayFrame): SteamVrOverlayStatus {
                            if (attempt == 3 && frame.opacityPercent == 65) error("Runtime disconnected")
                            assertEquals(content, frame.content)
                            return if (frame.position == SteamVrOverlayPosition.RIGHT_HAND)
                                SteamVrOverlayStatus.WAITING_FOR_CONTROLLER else SteamVrOverlayStatus.CONNECTED
                        }
                        override fun close() { closes++ }
                    }
                }, onStatus = { statuses.trySend(it) }, onError = { errors.add(it) }, retryMillis = 1, refreshMillis = 1)
            }
            try {
                assertEquals(SteamVrOverlayStatus.UNAVAILABLE, statuses.receive())
                assertEquals(SteamVrOverlayStatus.CONNECTED, statuses.receive())
                assertEquals(1, errors.size)
                frames.value = initial.copy(opacityPercent = 65)
                assertEquals(SteamVrOverlayStatus.UNAVAILABLE, statuses.receive())
                assertEquals(SteamVrOverlayStatus.CONNECTED, statuses.receive())
                assertEquals(4, attempts)
                frames.value = frames.value.copy(position = SteamVrOverlayPosition.RIGHT_HAND)
                assertEquals(SteamVrOverlayStatus.WAITING_FOR_CONTROLLER, statuses.receive())
                frames.value = frames.value.copy(position = SteamVrOverlayPosition.SCREEN_CENTER)
                assertEquals(SteamVrOverlayStatus.CONNECTED, statuses.receive())
                assertEquals(4, attempts)
                assertEquals(2, errors.size)
            } finally {
                job.cancelAndJoin()
            }
            assertEquals(2, closes)
        }
    }

    @Test
    fun windowsNativesLoadAndAbsentRuntimeDoesNotLeaveOpenVrInitialized() {
        assumeTrue(Platform.isWindows())
        // This also loads the bundled DLLs. Real hardware runs are performed separately.
        assumeTrue(!VR_IsRuntimeInstalled() || !isSteamVrRunning() || !VR_IsHmdPresent())
        repeat(2) {
            assertFailsWith<IllegalStateException> { OpenVrSubtitleOverlay() }
            assertNull(OpenVR.VRSystem)
            assertNull(OpenVR.VROverlay)
        }
    }

    @Test
    fun presetsFaceTheUserAndAnchorHandPanelsAtTheBottomCenter() {
        val left = steamVrOverlayPlacement(SteamVrOverlayPosition.LEFT_HAND)
        val right = steamVrOverlayPlacement(SteamVrOverlayPosition.RIGHT_HAND)
        val center = steamVrOverlayPlacement(SteamVrOverlayPosition.SCREEN_CENTER)
        assertEquals(.3f, center.widthMeters)
        assertEquals(.14f, left.widthMeters)
        assertEquals(.4f, center.widthMeters * SteamVrSubtitleRenderer.HEIGHT / SteamVrSubtitleRenderer.WIDTH)
        assertEquals(left.widthMeters, right.widthMeters)
        for (scale in steamVrOverlayScalePercents) for (handPosition in listOf(SteamVrOverlayPosition.LEFT_HAND, SteamVrOverlayPosition.RIGHT_HAND)) {
            val hand = steamVrOverlayPlacement(handPosition, scale)
            assertEquals(.14f * scale / 100f, hand.widthMeters, .000001f)
            assertEquals(.3f * scale / 100f, steamVrOverlayPlacement(SteamVrOverlayPosition.SCREEN_CENTER, scale).widthMeters, .000001f)
            val halfHeight = hand.widthMeters * SteamVrSubtitleRenderer.HEIGHT / SteamVrSubtitleRenderer.WIDTH / 2f
            // Transform the panel's bottom-edge midpoint (0, -height/2, 0) into
            // controller space: it must coincide with the controller origin on all axes.
            for (row in 0..2) {
                assertEquals(0f, hand.transform[row * 4 + 3] - halfHeight * hand.transform[row * 4 + 1], .000001f)
            }
        }
        assertTrue(left.transform[7] > 0f)
        assertTrue(left.transform[10] > 0f)
        assertEquals(listOf(0f, 0f, -1.2f), listOf(center.transform[3], center.transform[7], center.transform[11]))
        for (preset in listOf(left, right, center)) {
            // Every preset must be a rigid, non-mirrored transform: orthonormal axes.
            val m = preset.transform
            for (a in 0..2) for (b in 0..2) {
                val dot = (0..2).sumOf { column -> (m[a * 4 + column] * m[b * 4 + column]).toDouble() }
                assertEquals(if (a == b) 1.0 else 0.0, dot, .00001)
            }
        }
    }

    @Test
    fun everyPanelSizePreservesTheOriginalPhysicalTextSize() {
        val foreground = Color(0xEEEAF4)
        val sampleText = "TEST"
        // Reference the original 28 px font on the original 720 px, 0.60 m panel.
        val original = BufferedImage(720, 960, BufferedImage.TYPE_INT_ARGB)
        original.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            color = foreground
            font = Font(Font.SANS_SERIF, Font.PLAIN, 28)
            drawString(sampleText, 28, 80)
            dispose()
        }
        fun glyphHeight(image: BufferedImage): Int {
            val rows = (0 until image.height).filter { y ->
                (0 until image.width).any { x -> image.getRGB(x, y) == foreground.rgb }
            }
            assertTrue(rows.isNotEmpty())
            return rows.last() - rows.first() + 1
        }
        val originalHeight = glyphHeight(original)
        for (scale in steamVrOverlayScalePercents) {
            val rendered = SteamVrSubtitleRenderer.render(content.copy(
                captions = listOf(SystemAudioCaption(1, sampleText)),
            ), scale)
            val renderedHeight = glyphHeight(rendered)
            for ((position, originalWidth) in listOf(
                SteamVrOverlayPosition.SCREEN_CENTER to .6,
                SteamVrOverlayPosition.LEFT_HAND to .28,
                SteamVrOverlayPosition.RIGHT_HAND to .28,
            )) {
                val currentWidth = steamVrOverlayPlacement(position, scale).widthMeters.toDouble()
                assertEquals(
                    originalHeight * originalWidth / original.width,
                    renderedHeight * currentWidth / rendered.width,
                    2 * currentWidth / rendered.width + 1e-8, // Pixel hinting and Float metre conversion.
                )
            }
        }
    }

    @Test
    fun rendererWrapsMultilingualCaptionsAndUploadsStraightRgbaWithTransparentCorners() {
        for (dark in listOf(true, false)) {
            val sample = content.copy(dark = dark, captions = content.captions + listOf(
                SystemAudioCaption(2, "長い文章の折り返しも確認します。".repeat(7), listOf("English" to "This longer translated sentence should wrap neatly across multiple lines without leaving the subtitle panel.")),
                SystemAudioCaption(3, "안녕하세요! 👋", listOf("简体中文" to "你好！"), error = "翻译服务暂不可用"),
            ))
            val image = SteamVrSubtitleRenderer.render(sample)
            val buffer = ByteBuffer.allocate(SteamVrSubtitleRenderer.WIDTH * SteamVrSubtitleRenderer.HEIGHT * 4)
            SteamVrSubtitleRenderer.renderRgba(sample, buffer)
            assertEquals(0, buffer.position())
            assertEquals(buffer.capacity(), buffer.remaining())
            assertEquals(0, image.getRGB(0, 0) ushr 24)
            assertEquals(255, image.getRGB(10, 100) ushr 24)
            for ((x, y) in listOf(0 to 0, 10 to 100, 150 to 130, 360 to 450)) {
                val argb = image.getRGB(x, y)
                val offset = (y * image.width + x) * 4
                assertEquals((argb ushr 16).toByte(), buffer[offset])
                assertEquals((argb ushr 8).toByte(), buffer[offset + 1])
                assertEquals(argb.toByte(), buffer[offset + 2])
                assertEquals((argb ushr 24).toByte(), buffer[offset + 3])
            }
            val output = File("build/test-screenshots/steamvr-subtitles-${if (dark) "dark" else "light"}.png")
            output.parentFile.mkdirs()
            ImageIO.write(image, "png", output)
        }
        ImageIO.write(SteamVrSubtitleRenderer.render(content), "png",
            File("build/test-screenshots/steamvr-subtitles-readable.png"))
    }
}
