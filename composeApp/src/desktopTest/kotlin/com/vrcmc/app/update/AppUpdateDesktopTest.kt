package com.vrcmc.app

import com.sun.net.httpserver.HttpServer
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class AppUpdateDesktopTest {
    @get:Rule val directory = TemporaryFolder()
    // Minimal DOS and PE signatures for validation; this fixture is never executed.
    private val executable = ByteArray(600_000).apply {
        this[0] = 0x4d
        this[1] = 0x5a
        this[0x3c] = 0x40
        this[0x40] = 0x50
        this[0x41] = 0x45
    }
    private val release = AppRelease(
        tagName = "v1.3.0",
        name = "",
        body = "",
        htmlUrl = "https://example.com/release",
        apkUrl = "https://example.com/app.apk",
        exeUrl = "https://example.com/app-setup.exe",
        exeSize = executable.size.toLong(),
        exeSha256 = MessageDigest.getInstance("SHA-256").digest(executable).toHexString(),
    )

    @Test
    fun desktopSelectsExeOnlyOnWindows() {
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            assertEquals(release.exeUrl, appUpdateDownloadUrl(release))
        } else {
            assertNull(appUpdateDownloadUrl(release))
        }
        assertNull(appUpdateDownloadUrl(release.copy(exeUrl = null)))
    }

    @Test
    fun savesVerifiedExeAndReportsProgress() = runBlocking {
        val progress = mutableListOf<Float?>()
        val target = save(executable, onProgress = progress::add)
        assertTrue(target.fileName.toString().endsWith(".exe"))
        assertContentEquals(executable, Files.readAllBytes(target))
        assertEquals(0f, progress.first())
        assertEquals(1f, progress.last())
        assertTrue(progress.filterNotNull().any { it > 0f && it < 1f })
        assertTrue(progress.filterNotNull().zipWithNext().all { (before, after) -> before <= after })
        assertFileCount(1)
    }

    @Test
    fun rejectsIncompleteCorruptAndOversizedDownloadsAndRemovesTemporaryFiles() = runBlocking {
        for (bytes in listOf(ByteArray(0), executable.copyOf(100), ByteArray(executable.size), executable + byteArrayOf(0))) {
            assertFailsWith<IllegalStateException> { save(bytes) }
            assertFileCount(0)
        }
        save(executable)
        assertFileCount(1)
    }

    @Test
    fun rejectsHtmlAndInvalidPeHeadersWithoutDigest() = runBlocking {
        val withoutMetadata = release.copy(exeSize = null, exeSha256 = null)
        for (bytes in listOf(
            "<html>This mirror is unavailable</html>".toByteArray(),
            executable.copyOf().apply { this[0x40] = 0 },
            executable.copyOf().apply { this[0x3f] = 0x7f },
        )) {
            assertFailsWith<IllegalStateException> { save(bytes, withoutMetadata) }
            assertFileCount(0)
        }
    }

    @Test
    fun supportsUnknownLengthAndSanitizesVersionInFileName() = runBlocking {
        val progress = mutableListOf<Float?>()
        val target = save(executable, release.copy(tagName = "../../v1:3?0", exeSize = null, exeSha256 = null), progress::add)
        assertEquals(directory.root.toPath(), target.parent)
        assertNull(progress.first())
        assertEquals(1f, progress.last())
        assertContentEquals(executable, Files.readAllBytes(target))
    }

    @Test
    fun cancellationRemovesPartialDownloadAndAllowsRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val channel = ByteChannel()
        val job = launch {
            saveWindowsUpdate(channel, release, directory.root.toPath(), release.exeSize) { started.complete(Unit) }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        channel.cancel(null)
        assertTrue(job.isCancelled)
        assertFileCount(0)
        save(executable)
        assertFileCount(1)
    }

    @Test
    fun downloadsThroughRedirectAndRejectsHttpErrorsOrWrongLength() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/installer") { exchange ->
            exchange.sendResponseHeaders(200, executable.size.toLong())
            exchange.responseBody.use { it.write(executable) }
            exchange.close()
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/installer")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val target = downloadWindowsUpdate("$base/redirect", release, directory.root.toPath()) {}
            assertContentEquals(executable, Files.readAllBytes(target))
            assertFailsWith<IllegalStateException> {
                downloadWindowsUpdate("$base/missing", release, directory.root.toPath()) {}
            }
            assertFailsWith<IllegalStateException> {
                downloadWindowsUpdate("$base/installer", release.copy(exeSize = 1), directory.root.toPath()) {}
            }
            assertFileCount(1)
        } finally {
            server.stop(0)
        }
    }

    private suspend fun save(
        bytes: ByteArray,
        metadata: AppRelease = release,
        onProgress: (Float?) -> Unit = {},
    ): Path = saveWindowsUpdate(ByteReadChannel(bytes), metadata, directory.root.toPath(), metadata.exeSize, onProgress)

    private fun assertFileCount(expected: Long) {
        Files.list(directory.root.toPath()).use { assertEquals(expected, it.count()) }
    }
}
