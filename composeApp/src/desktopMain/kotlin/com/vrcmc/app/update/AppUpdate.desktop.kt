package com.vrcmc.app

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.awt.Desktop
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

actual fun appUpdateDownloadUrl(release: AppRelease): String? =
    release.exeUrl.takeIf { System.getProperty("os.name").startsWith("Windows", ignoreCase = true) }

actual suspend fun installAppUpdate(
    release: AppRelease,
    onProgress: (Float?) -> Unit,
): Result<Unit> = try {
    withContext(Dispatchers.IO) {
        val source = requireNotNull(appUpdateDownloadUrl(release)) { "This release has no installer for this platform" }
        onProgress(null)
        val downloadUrl = findFastestAppUpdateUrl(source)
        val directory = (System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let { Path.of(it, "VRCMC") }
            ?: Path.of(System.getProperty("user.home"), ".vrcmc")).resolve("updates")
        val installer = downloadWindowsUpdate(downloadUrl, release, directory, onProgress)
        currentCoroutineContext().ensureActive()
        // Open through the Windows shell so the installer can request elevation if needed.
        Desktop.getDesktop().open(installer.toFile())
    }
    Result.success(Unit)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

internal suspend fun downloadWindowsUpdate(
    source: String,
    release: AppRelease,
    directory: Path,
    onProgress: (Float?) -> Unit,
): Path {
    val client = createVrcmcHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }
    return try {
        client.prepareGet(source) {
            header(HttpHeaders.UserAgent, "VRCMC/${AppInfo.VERSION}")
        }.execute { response ->
            check(response.status.isSuccess()) { "Update server returned HTTP ${response.status.value}" }
            val length = response.contentLength()
            check(release.exeSize == null || length == null || release.exeSize == length) {
                "Installer size does not match the release"
            }
            saveWindowsUpdate(response.bodyAsChannel(), release, directory, release.exeSize ?: length, onProgress)
        }
    } finally {
        client.close()
    }
}

// Only expose an EXE after the entire download has passed validation.
internal suspend fun saveWindowsUpdate(
    channel: ByteReadChannel,
    release: AppRelease,
    directory: Path,
    expectedSize: Long?,
    onProgress: (Float?) -> Unit,
): Path {
    Files.createDirectories(directory)
    val version = release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    val temporary = Files.createTempFile(directory, "VRCMC-$version-", ".part")
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L
        var reported = 0L
        onProgress(if (expectedSize != null && expectedSize > 0) 0f else null)
        Files.newOutputStream(temporary).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = channel.readAvailable(buffer, 0, buffer.size)
                if (count < 0) break
                if (count == 0) continue
                received += count
                check(expectedSize == null || received <= expectedSize) { "Installer exceeds expected size" }
                output.write(buffer, 0, count)
                digest.update(buffer, 0, count)
                if (received - reported >= 256 * 1024) {
                    onProgress(expectedSize?.takeIf { it > 0 }?.let { (received.toFloat() / it).coerceIn(0f, 1f) })
                    reported = received
                }
            }
        }
        check(received > 0 && (expectedSize == null || received == expectedSize)) { "Incomplete installer download" }
        release.exeSha256?.let { expectedHash ->
            check(digest.digest().toHexString().equals(expectedHash, ignoreCase = true)) { "Installer checksum mismatch" }
        }
        verifyWindowsExecutable(temporary)
        currentCoroutineContext().ensureActive()
        onProgress(1f)
        val installer = temporary.resolveSibling(temporary.fileName.toString().removeSuffix(".part") + ".exe")
        return Files.move(temporary, installer)
    } finally {
        Files.deleteIfExists(temporary)
    }
}

private fun verifyWindowsExecutable(path: Path) {
    RandomAccessFile(path.toFile(), "r").use { file ->
        check(file.length() >= 64 && file.readUnsignedShort() == 0x4d5a) { "Downloaded file is not a Windows executable" }
        file.seek(0x3c)
        val peOffset = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()))
        check(peOffset >= 64 && peOffset <= file.length() - 4) { "Invalid Windows executable header" }
        file.seek(peOffset)
        check(file.readInt() == 0x50450000) { "Invalid Windows executable signature" }
    }
}
