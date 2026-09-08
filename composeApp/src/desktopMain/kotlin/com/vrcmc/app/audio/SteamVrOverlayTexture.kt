package com.vrcmc.app

import java.nio.ByteBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.system.MemoryUtil.NULL

/** Owns one persistent texture and an invisible GL context on the overlay worker. */
internal class SteamVrOverlayTexture(private val width: Int, private val height: Int) : AutoCloseable {
    private var initialized = false
    private var window = NULL
    private var textureId = 0

    init {
        try {
            check(glfwInit()) { "Could not initialize the SteamVR graphics context" }
            initialized = true
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            glfwWindowHint(GLFW_FOCUSED, GLFW_FALSE)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
            window = glfwCreateWindow(1, 1, "VRCMC SteamVR texture", NULL, NULL)
            check(window != NULL) { "Could not create the SteamVR graphics context" }
            glfwMakeContextCurrent(window)
            GL.createCapabilities()
            textureId = glGenTextures()
            check(textureId != 0) { "Could not create the SteamVR subtitle texture" }
            glBindTexture(GL_TEXTURE_2D, textureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, NULL)
            checkGl("allocate")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun upload(rgba: ByteBuffer): Int {
        check(textureId != 0) { "SteamVR subtitle texture is closed" }
        require(rgba.isDirect && rgba.remaining() == width * height * 4)
        glBindTexture(GL_TEXTURE_2D, textureId)
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba)
        // SteamVR consumes the texture in another process. Submit only once the complete
        // image is on the GPU, retaining the same texture handle and allocation throughout.
        glFinish()
        checkGl("upload")
        return textureId
    }

    override fun close() {
        try {
            if (textureId != 0) glDeleteTextures(textureId)
        } finally {
            textureId = 0
            if (window != NULL) {
                GL.setCapabilities(null)
                glfwMakeContextCurrent(NULL)
                glfwDestroyWindow(window)
                window = NULL
            }
            if (initialized) glfwTerminate()
            initialized = false
        }
    }

    private fun checkGl(operation: String) {
        val result = glGetError()
        check(result == GL_NO_ERROR) { "SteamVR texture $operation failed (OpenGL $result)" }
    }
}
