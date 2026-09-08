package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun selectsWindowsSetupWithItsMetadataAndKeepsAndroidApk() {
        val release = parseAppRelease("""
            {
              "tag_name": "v1.3.0", "name": "VRCMC 1.3.0", "body": "Release notes",
              "html_url": "https://github.com/yourmoln/VRCMC/releases/tag/v1.3.0",
              "assets": [
                {"browser_download_url": "https://example.com/VRCMC.exe", "size": 1},
                {"browser_download_url": "https://example.com/VRCMC-v1.3.0.apk"},
                {"browser_download_url": "https://example.com/VRCMC-v1.3.0-SETUP.EXE", "size": 12345, "digest": "sha256:abc123"},
                {"browser_download_url": "https://example.com/VRCMC.zip", "size": 99}
              ]
            }
        """.trimIndent())

        assertEquals("v1.3.0", release.tagName)
        assertEquals("VRCMC 1.3.0", release.name)
        assertEquals("Release notes", release.body)
        assertEquals("https://example.com/VRCMC-v1.3.0.apk", release.apkUrl)
        assertEquals("https://example.com/VRCMC-v1.3.0-SETUP.EXE", release.exeUrl)
        assertEquals(12345L, release.exeSize)
        assertEquals("abc123", release.exeSha256)
    }

    @Test
    fun supportsOlderExeReleasesWithoutSizeOrDigest() {
        val release = parseAppRelease("""
            {"tag_name": "v1.3.0", "html_url": "https://example.com/release",
             "assets": [{"browser_download_url": "https://example.com/VRCMC-1.3.0.exe", "digest": null}]}
        """.trimIndent())

        assertEquals("https://example.com/VRCMC-1.3.0.exe", release.exeUrl)
        assertNull(release.exeSize)
        assertNull(release.exeSha256)
        assertNull(release.apkUrl)
    }

    @Test
    fun releasesWithoutInstallersCanFallBackToReleasePage() {
        for (assets in listOf("", """, "assets": []""", """, "assets": [{"browser_download_url": "https://example.com/source.zip"}]""")) {
            val release = parseAppRelease("""
                {"tag_name": "v1.3.0", "name": null, "body": null, "html_url": "https://example.com/release"$assets}
            """.trimIndent())
            assertNull(release.apkUrl)
            assertNull(release.exeUrl)
            assertEquals("", release.name)
            assertEquals("", release.body)
            assertEquals("https://example.com/release", release.htmlUrl)
        }
    }
}
