package com.vrcmc.app

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.text.AttributedString

/** Renders caption data directly, so minimization and desktop occlusion do not affect VR. */
internal object SteamVrSubtitleRenderer {
    const val WIDTH = 720
    const val HEIGHT = 960
    private const val PADDING = 28

    private data class Line(val layout: TextLayout?, val color: Color) {
        val height: Float get() = layout?.let { it.ascent + it.descent + it.leading + 8f } ?: 18f
    }

    fun render(content: SteamVrOverlayContent, scalePercent: Int = 100): BufferedImage {
        // Keep the same GPU allocation at every size. Change the amount of content
        // laid out, compensating pixel density so the physical text size stays fixed.
        val scale = normalizedSteamVrOverlayScalePercent(scalePercent)
        val layoutWidth = WIDTH / 2 * scale / 100
        val layoutHeight = HEIGHT / 2 * scale / 100
        val contentScale = WIDTH.toDouble() / layoutWidth
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.scale(contentScale, contentScale)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val foreground = if (content.dark) Color(0xEEEAF4) else Color(0x211F26)
            val primary = if (content.dark) Color(0xD0BCFF) else Color(0x6750A4)
            val error = if (content.dark) Color(0xFFB4AB) else Color(0xB3261E)
            graphics.color = if (content.dark) Color(0x211F26) else Color(0xFFFBFE)
            graphics.fillRoundRect(0, 0, layoutWidth, layoutHeight, 28, 28)

            fun lines(text: String, size: Int, color: Color, bold: Boolean = false): List<Line> = buildList {
                for (paragraph in text.lineSequence()) {
                    if (paragraph.isBlank()) {
                        add(Line(null, color))
                        continue
                    }
                    val attributed = AttributedString(paragraph).apply {
                        addAttribute(TextAttribute.FONT, Font(Font.SANS_SERIF, if (bold) Font.BOLD else Font.PLAIN, size))
                    }
                    val measurer = LineBreakMeasurer(attributed.iterator, graphics.fontRenderContext)
                    while (measurer.position < paragraph.length) {
                        add(Line(measurer.nextLayout((layoutWidth - PADDING * 2).toFloat()), color))
                    }
                }
            }
            var headerY = PADDING.toFloat()
            for (line in lines("VRCMC · ${content.status}", 24, primary).take(3)) {
                line.layout?.draw(graphics.apply { this.color = line.color }, PADDING.toFloat(), headerY + line.layout.ascent)
                headerY += line.height
            }
            graphics.color = if (content.dark) Color(0x49454F) else Color(0xCAC4D0)
            graphics.drawLine(PADDING, headerY.toInt(), layoutWidth - PADDING, headerY.toInt())

            val captionLines = buildList {
                // Only recent captions can fit on the panel. Keeping their tail matches
                // the desktop window's automatic scroll to the most recent subtitle.
                for (caption in content.captions.takeLast(5)) {
                    if (caption.original.isNotBlank()) addAll(lines(caption.original, 28, foreground))
                    for ((_, translation) in caption.translations) addAll(lines(translation, 30, primary, bold = true))
                    caption.error?.let { addAll(lines(it, 26, error)) }
                    add(Line(null, foreground))
                }
            }
            val availableHeight = layoutHeight - PADDING - headerY - 20f
            var usedHeight = 0f
            val visibleLines = captionLines.asReversed().takeWhile { line ->
                (usedHeight + line.height <= availableHeight).also { if (it) usedHeight += line.height }
            }.asReversed()
            var y = headerY + 20f
            for (line in visibleLines) {
                graphics.color = line.color
                line.layout?.draw(graphics, PADDING.toFloat(), y + line.layout.ascent)
                y += line.height
            }
        } finally {
            graphics.dispose()
        }
        return image
    }

    fun renderRgba(content: SteamVrOverlayContent, target: ByteBuffer, scalePercent: Int = 100) {
        val image = render(content, scalePercent)
        val pixels = image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH)
        target.clear()
        for (argb in pixels) {
            target.put((argb ushr 16).toByte())
            target.put((argb ushr 8).toByte())
            target.put(argb.toByte())
            target.put((argb ushr 24).toByte())
        }
        target.flip()
    }
}
