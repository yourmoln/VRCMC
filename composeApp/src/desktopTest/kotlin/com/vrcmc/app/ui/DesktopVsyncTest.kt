package com.vrcmc.app

import org.jetbrains.skiko.SkikoProperties
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopVsyncTest {
    @Test
    fun enablesVsyncForNormalAndImmediateRedraws() {
        val properties = listOf(
            "skiko.vsync.enabled",
            "skiko.rendering.windows.waitForFrameVsyncOnRedrawImmediately",
        )
        val previousValues = properties.associateWith(System::getProperty)
        try {
            properties.forEach { System.setProperty(it, "false") }

            configureDesktopVsync()

            properties.forEach { assertEquals("true", System.getProperty(it)) }
            assertTrue(SkikoProperties.vsyncEnabled)
            assertTrue(SkikoProperties.windowsWaitForVsyncOnRedrawImmediately)
        } finally {
            previousValues.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }
}
