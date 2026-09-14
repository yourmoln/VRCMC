package com.vrcmc.app

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val whisperModelRevision = "5359861c739e955e79d9a303bcbc70fb988958b1"

private data class LocalWhisperModelArtifact(
    val fileName: String,
    val size: Long,
    val sha256: String,
)

private fun LocalWhisperModel.artifact(): LocalWhisperModelArtifact = when (this) {
    LocalWhisperModel.SMALL_Q5_1 -> LocalWhisperModelArtifact(
        fileName = "ggml-small-q5_1.bin",
        size = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
    )
    LocalWhisperModel.MEDIUM_Q5_0 -> LocalWhisperModelArtifact(
        fileName = "ggml-medium-q5_0.bin",
        size = 539_212_467L,
        sha256 = "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f",
    )
}

internal fun LocalWhisperModel.modelFileName(): String = artifact().fileName
internal fun LocalWhisperModel.modelSize(): Long = artifact().size
internal fun LocalWhisperModel.modelSha256(): String = artifact().sha256

// Call on Dispatchers.IO. A failed or cancelled download must never replace a valid model.
internal class LocalWhisperModelStore(
    val path: Path,
    internal val expectedSize: Long = LocalWhisperModel.SMALL_Q5_1.modelSize(),
    private val expectedHash: String = LocalWhisperModel.SMALL_Q5_1.modelSha256(),
) {
    suspend fun isValid(): Boolean {
        if (!Files.isRegularFile(path) || Files.size(path) != expectedSize) return false
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString() == expectedHash
    }

    suspend fun install(channel: ByteReadChannel, onProgress: (Long, Long) -> Unit) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = Files.createTempFile(path.toAbsolutePath().parent, "whisper-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            onProgress(0, expectedSize)
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    received += count
                    check(received <= expectedSize) { "Model exceeds expected size" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (received - reported >= 256 * 1024 || received == expectedSize) {
                        onProgress(received, expectedSize)
                        reported = received
                    }
                }
            }
            check(received == expectedSize) { "Incomplete model download: $received / $expectedSize bytes" }
            check(digest.digest().toHexString() == expectedHash) { "Model checksum mismatch" }
            currentCoroutineContext().ensureActive()
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal suspend fun downloadWhisperModel(
    model: LocalWhisperModel,
    store: LocalWhisperModelStore,
    onProgress: (Long, Long) -> Unit,
) {
    val client = createVrcmcHttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 1_200_000
        }
    }
    try {
        var lastFailure: Exception? = null
        // Both sources serve the same pinned file; SHA-256 is checked before it can be loaded.
        for (host in listOf("huggingface.co", "hf-mirror.com")) {
            try {
                client.prepareGet(
                    "https://$host/ggerganov/whisper.cpp/resolve/$whisperModelRevision/${model.modelFileName()}",
                ).execute { response ->
                    check(response.status.isSuccess()) { "Model server returned HTTP ${response.status.value}" }
                    store.install(response.bodyAsChannel(), onProgress)
                }
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
            }
        }
        throw checkNotNull(lastFailure)
    } finally {
        client.close()
    }
}
