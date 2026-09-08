package com.vrcmc.app

import com.sun.jna.Platform
import kotlin.test.*
import org.junit.Assume.assumeTrue
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.openvr.OpenVR
import org.lwjgl.openvr.Texture
import org.lwjgl.openvr.VRTextureBounds
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VROverlay.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree
import java.util.UUID

class SteamVrOverlayTextureTest {
    @Test
    fun repeatedUploadsKeepTheTextureAndContextHiddenAndContainCompleteFrames() {
        assumeTrue(Platform.isWindows())
        val width = 16
        val height = 12
        val pixels = memAlloc(width * height * 4)
        val readback = memAlloc(pixels.capacity())
        try {
            // Closing and reopening must also release GLFW's process-global state.
            repeat(2) {
                SteamVrOverlayTexture(width, height).use { texture ->
                    var firstId = 0
                    repeat(60) { frame ->
                        pixels.clear()
                        for (pixel in 0 until width * height) {
                            pixels.put(pixel.toByte()).put(frame.toByte()).put((pixel / width).toByte()).put(255.toByte())
                        }
                        pixels.flip()
                        val id = texture.upload(pixels)
                        if (firstId == 0) firstId = id
                        assertEquals(firstId, id, "Updating subtitles must not replace the GPU texture")
                        assertTrue(glIsTexture(id))
                        assertEquals(GLFW_FALSE, glfwGetWindowAttrib(glfwGetCurrentContext(), GLFW_VISIBLE))
                        glBindTexture(GL_TEXTURE_2D, id)
                        readback.clear()
                        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, readback)
                        assertEquals(GL_NO_ERROR, glGetError())
                        assertEquals(pixels, readback, "The complete new frame must be ready before SteamVR receives it")
                    }
                }
            }
        } finally {
            memFree(readback)
            memFree(pixels)
        }
    }

    @Test
    fun runningSteamVrAcceptsRepeatedGpuSubmissionsWithoutReplacingTheOverlay() {
        // Opt in for machines with SteamVR running. The test overlay stays hidden.
        assumeTrue(System.getenv("VRCMC_STEAMVR_INTEGRATION_TEST") == "1")
        assertTrue(isSteamVrRunning())
        var initialized = false
        var overlay = 0L
        try {
            stackPush().use { stack ->
                val error = stack.callocInt(1)
                val token = VR_InitInternal(error, EVRApplicationType_VRApplication_Overlay)
                assertEquals(EVRInitError_VRInitError_None, error[0], VR_GetVRInitErrorAsEnglishDescription(error[0]))
                initialized = true
                OpenVR.create(token)
                val result = stack.callocLong(1)
                assertEquals(0, VROverlay_CreateOverlay("vrcmc.texture-test.${UUID.randomUUID()}", "VRCMC texture test", result))
                overlay = result[0]
                assertEquals(0, setSteamVrOverlayTextureBounds(overlay))
                val bounds = VRTextureBounds.calloc(stack)
                val width = stack.callocInt(1)
                val height = stack.callocInt(1)
                val physicalWidth = stack.callocFloat(1)
                val submission = Texture.calloc(stack)
                    .eType(ETextureType_TextureType_OpenGL).eColorSpace(EColorSpace_ColorSpace_Gamma)
                val pixels = memAlloc(SteamVrSubtitleRenderer.WIDTH * SteamVrSubtitleRenderer.HEIGHT * 4)
                try {
                    SteamVrOverlayTexture(SteamVrSubtitleRenderer.WIDTH, SteamVrSubtitleRenderer.HEIGHT).use { texture ->
                        try {
                            repeat(20) { frame ->
                                val scale = steamVrOverlayScalePercents[frame % steamVrOverlayScalePercents.size]
                                val placement = steamVrOverlayPlacement(SteamVrOverlayPosition.SCREEN_CENTER, scale)
                                assertEquals(0, VROverlay_SetOverlayWidthInMeters(overlay, placement.widthMeters))
                                val content = SteamVrOverlayContent(
                                    if (frame % 2 == 0) "正在听…" else "正在等待说话",
                                    listOf(SystemAudioCaption(frame.toLong(), "Subtitle $frame", listOf("简体中文" to "字幕 $frame"))),
                                    dark = true,
                                )
                                SteamVrSubtitleRenderer.renderRgba(content, pixels, scale)
                                submission.handle(texture.upload(pixels).toLong())
                                val textureError = VROverlay_SetOverlayTexture(overlay, submission)
                                assertEquals(0, textureError, VROverlay_GetOverlayErrorNameFromEnum(textureError))
                                assertEquals(0, VROverlay_GetOverlayTextureSize(overlay, width, height))
                                assertEquals(SteamVrSubtitleRenderer.WIDTH, width[0])
                                assertEquals(SteamVrSubtitleRenderer.HEIGHT, height[0])
                                assertEquals(0, VROverlay_GetOverlayWidthInMeters(overlay, physicalWidth))
                                assertEquals(placement.widthMeters, physicalWidth[0])
                                assertEquals(0, VROverlay_GetOverlayTextureBounds(overlay, bounds))
                                assertEquals(0f, bounds.uMin(), "The left edge must remain on the left")
                                assertEquals(1f, bounds.uMax(), "The right edge must remain on the right")
                                assertEquals(1f, bounds.vMin(), "Map the top of the overlay to the Java2D image's top")
                                assertEquals(0f, bounds.vMax(), "The OpenGL vertical origin must be corrected on every frame")
                                assertFalse(VROverlay_IsOverlayVisible(overlay))
                            }
                        } finally {
                            VROverlay_ClearOverlayTexture(overlay)
                        }
                    }
                } finally {
                    memFree(pixels)
                }
            }
        } finally {
            if (overlay != 0L) VROverlay_DestroyOverlay(overlay)
            if (initialized) {
                VR_ShutdownInternal()
                OpenVR.destroy()
            }
        }
    }
}
