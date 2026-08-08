package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateTest {
    @Test
    fun comparesReleaseVersionsNumerically() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("release-2.0", "1.99.99"))
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
        assertFalse(isNewerVersion("latest", "1.0.0"))
    }
}
