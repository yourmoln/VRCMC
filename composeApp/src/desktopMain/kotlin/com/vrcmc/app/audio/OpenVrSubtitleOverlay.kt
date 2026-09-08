package com.vrcmc.app

import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import org.lwjgl.openvr.HmdMatrix34
import org.lwjgl.openvr.OpenVR
import org.lwjgl.openvr.Texture
import org.lwjgl.openvr.VR.*
import org.lwjgl.openvr.VREvent
import org.lwjgl.openvr.VRTextureBounds
import org.lwjgl.openvr.VROverlay.*
import org.lwjgl.openvr.VRSystem.*
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memFree

/** All methods, including construction and cleanup, run on the overlay worker. */
internal class OpenVrSubtitleOverlay : SteamVrOverlayBackend {
    private var initialized = false
    private var handle = 0L
    private var pixels: ByteBuffer? = null
    private var texture: SteamVrOverlayTexture? = null
    private var lastContent: SteamVrOverlayContent? = null
    private var lastPosition: SteamVrOverlayPosition? = null
    private var lastScalePercent = -1
    private var lastDevice = k_unTrackedDeviceIndexInvalid
    private var lastOpacity = -1
    private var visible = false

    init {
        try {
            check(VR_IsRuntimeInstalled()) { "SteamVR is not installed" }
            // Overlay initialization can launch SteamVR. Wait for a running server so
            // enabling subtitles or retrying after SteamVR exits never relaunches it.
            check(isSteamVrRunning()) { "SteamVR is not running" }
            check(VR_IsHmdPresent()) { "No SteamVR headset is available" }
            stackPush().use { stack ->
                val error = stack.callocInt(1)
                val token = VR_InitInternal(error, EVRApplicationType_VRApplication_Overlay)
                check(error[0] == EVRInitError_VRInitError_None) {
                    "${VR_GetVRInitErrorAsEnglishDescription(error[0])} (${error[0]})"
                }
                initialized = true
                OpenVR.create(token)
                check(OpenVR.VRSystem != null && OpenVR.VROverlay != null) { "SteamVR overlay interfaces are unavailable" }
                val result = stack.callocLong(1)
                checkOverlay(VROverlay_CreateOverlay("io.github.vrcmteam.vrcmc.subtitles.${UUID.randomUUID()}", "VRCMC Subtitles", result))
                handle = result[0]
                checkOverlay(setSteamVrOverlayTextureBounds(handle))
            }
            pixels = memAlloc(SteamVrSubtitleRenderer.WIDTH * SteamVrSubtitleRenderer.HEIGHT * 4)
            texture = SteamVrOverlayTexture(SteamVrSubtitleRenderer.WIDTH, SteamVrSubtitleRenderer.HEIGHT)
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    override fun update(frame: SteamVrOverlayFrame): SteamVrOverlayStatus {
        stackPush().use { stack ->
            val event = VREvent.calloc(stack)
            while (VRSystem_PollNextEvent(event)) {
                if (event.eventType() == EVREventType_VREvent_Quit) {
                    VRSystem_AcknowledgeQuit_Exiting()
                    error("SteamVR is shutting down")
                }
            }
            check(VRSystem_IsTrackedDeviceConnected(k_unTrackedDeviceIndex_Hmd)) { "SteamVR headset disconnected" }
            val device = when (frame.position) {
                SteamVrOverlayPosition.LEFT_HAND -> VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_LeftHand)
                SteamVrOverlayPosition.RIGHT_HAND -> VRSystem_GetTrackedDeviceIndexForControllerRole(ETrackedControllerRole_TrackedControllerRole_RightHand)
                SteamVrOverlayPosition.SCREEN_CENTER -> k_unTrackedDeviceIndex_Hmd
            }
            if (device == k_unTrackedDeviceIndexInvalid || !VRSystem_IsTrackedDeviceConnected(device)) {
                if (visible) checkOverlay(VROverlay_HideOverlay(handle))
                visible = false
                lastDevice = k_unTrackedDeviceIndexInvalid
                return SteamVrOverlayStatus.WAITING_FOR_CONTROLLER
            }
            val sizeChanged = frame.scalePercent != lastScalePercent
            if (frame.position != lastPosition || device != lastDevice || sizeChanged) {
                val preset = steamVrOverlayPlacement(frame.position, frame.scalePercent)
                val transform = HmdMatrix34.calloc(stack)
                preset.transform.forEachIndexed { index, value -> transform.m(index, value) }
                checkOverlay(VROverlay_SetOverlayWidthInMeters(handle, preset.widthMeters))
                checkOverlay(VROverlay_SetOverlayTransformTrackedDeviceRelative(handle, device, transform))
                lastPosition = frame.position
                lastScalePercent = frame.scalePercent
                lastDevice = device
            }
            if (frame.opacityPercent != lastOpacity) {
                checkOverlay(VROverlay_SetOverlayAlpha(handle, frame.opacityPercent.coerceIn(30, 100) / 100f))
                lastOpacity = frame.opacityPercent
            }
            if (frame.content != lastContent || sizeChanged) {
                val buffer = checkNotNull(pixels)
                SteamVrSubtitleRenderer.renderRgba(frame.content, buffer, frame.scalePercent)
                // SetOverlayRaw replaces SteamVR's image on every subtitle/status change.
                // Keep a single GPU texture alive, finish its upload, then submit it again.
                val textureId = checkNotNull(texture).upload(buffer)
                val submission = Texture.calloc(stack)
                    .handle(textureId.toLong())
                    .eType(ETextureType_TextureType_OpenGL)
                    .eColorSpace(EColorSpace_ColorSpace_Gamma)
                checkOverlay(VROverlay_SetOverlayTexture(handle, submission))
                lastContent = frame.content
            }
            if (!visible) {
                checkOverlay(VROverlay_ShowOverlay(handle))
                visible = true
            }
        }
        return SteamVrOverlayStatus.CONNECTED
    }

    override fun close() {
        try {
            if (handle != 0L && OpenVR.VROverlay != null) VROverlay_DestroyOverlay(handle)
        } finally {
            handle = 0L
            try {
                if (initialized) VR_ShutdownInternal()
            } finally {
                if (initialized) OpenVR.destroy()
                initialized = false
                try {
                    // SteamVR must release its reference before deleting the GPU texture.
                    texture?.close()
                } finally {
                    texture = null
                    pixels?.let { memFree(it) }
                    pixels = null
                }
            }
        }
    }

    private fun checkOverlay(result: Int) {
        check(result == EVROverlayError_VROverlayError_None) {
            "${VROverlay_GetOverlayErrorNameFromEnum(result)} ($result)"
        }
    }
}

internal data class SteamVrOverlayPlacement(val widthMeters: Float, val transform: List<Float>)

internal fun setSteamVrOverlayTextureBounds(overlay: Long): Int = stackPush().use { stack ->
    // Java2D writes the top row first; SteamVR's OpenGL submission uses the opposite
    // vertical origin. Flip V once while preserving the left-to-right U direction.
    val bounds = VRTextureBounds.calloc(stack).uMin(0f).uMax(1f).vMin(1f).vMax(0f)
    VROverlay_SetOverlayTextureBounds(overlay, bounds)
}

internal fun isSteamVrRunning(): Boolean = ProcessHandle.allProcesses().use { processes ->
    processes.anyMatch { File(it.info().command().orElse("")).name.equals("vrserver.exe", ignoreCase = true) }
}

internal fun steamVrOverlayPlacement(position: SteamVrOverlayPosition, scalePercent: Int = 100): SteamVrOverlayPlacement {
    val scale = normalizedSteamVrOverlayScalePercent(scalePercent) / 100f
    return if (position == SteamVrOverlayPosition.SCREEN_CENTER) {
        // OpenVR uses metres, +Y up and -Z forward. Centered 1.2 m in front of the HMD.
        // Height follows the 3:4 texture aspect ratio; at 100% it is 0.30 × 0.40 m.
        SteamVrOverlayPlacement(.3f * scale, listOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, -1.2f,
        ))
    } else {
        // Anchor the bottom-edge midpoint at the controller origin, keeping the panel
        // tilted 45 degrees toward the user. OpenVR positions overlays by their center,
        // so translate by the rotated half-height to place that bottom point at the hand.
        val width = .14f * scale
        val halfHeight = width * SteamVrSubtitleRenderer.HEIGHT / SteamVrSubtitleRenderer.WIDTH / 2f
        val angle = kotlin.math.sqrt(.5f)
        SteamVrOverlayPlacement(width, listOf(
            1f, 0f, 0f, 0f,
            0f, angle, angle, halfHeight * angle,
            0f, -angle, angle, -halfHeight * angle,
        ))
    }
}
