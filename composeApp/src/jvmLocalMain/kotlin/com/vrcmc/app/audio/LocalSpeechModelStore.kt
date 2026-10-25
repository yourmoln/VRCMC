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

private const val senseVoiceTokensFileName = "tokens.txt"

private data class LocalSpeechModelArtifact(
    val fileName: String,
    val size: Long,
    val sha256: String,
    val repository: String,
    val revision: String,
    val tokensSize: Long,
    val tokensSha256: String,
)

private fun LocalSpeechModel.artifact(): LocalSpeechModelArtifact = when (this) {
    LocalSpeechModel.SENSE_VOICE_SMALL_INT8 -> LocalSpeechModelArtifact(
        fileName = "model.int8.onnx",
        size = 239_233_841L,
        sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
        repository = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
        revision = "2365baeacb507f821a0c8120fcee3d484dba7a07",
        tokensSize = 315_894L,
        tokensSha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
    )
    LocalSpeechModel.FUN_ASR_NANO_2512_INT8 -> LocalSpeechModelArtifact(
        fileName = "model.int8.onnx",
        size = 263_531_902L,
        sha256 = "9dc6e72aa8bc6f5966cf2857a0ce3a425b1d72e91500e147d66329f407c017a1",
        repository = "csukuangfj/sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17",
        revision = "ebb95fbda2a034d9a8f3b2d2cba32c104b60bed3",
        tokensSize = 939_815L,
        tokensSha256 = "2db4bb25d046e8849e336c9465e248e1694914d996c58c71bdeca18cfb722992",
    )
}

internal fun LocalSpeechModel.modelFileName(): String = artifact().fileName
internal fun LocalSpeechModel.modelSize(): Long = artifact().size
internal fun LocalSpeechModel.modelSha256(): String = artifact().sha256

// Call on Dispatchers.IO. A failed or cancelled download must never replace a valid model.
internal class LocalSpeechModelStore(
    val path: Path,
    internal val expectedSize: Long = LocalSpeechModel.SENSE_VOICE_SMALL_INT8.modelSize(),
    private val expectedHash: String = LocalSpeechModel.SENSE_VOICE_SMALL_INT8.modelSha256(),
    val auxiliaryPath: Path? = null,
    private val expectedAuxiliarySize: Long = 0,
    private val expectedAuxiliaryHash: String = "",
) {
    internal val expectedTotalSize: Long
        get() = expectedSize + if (auxiliaryPath != null) expectedAuxiliarySize else 0

    suspend fun isValid(): Boolean = verifyFile(path, expectedSize, expectedHash)

    suspend fun isAuxiliaryValid(): Boolean {
        val target = auxiliaryPath ?: return true
        return verifyFile(target, expectedAuxiliarySize, expectedAuxiliaryHash)
    }

    suspend fun isComplete(): Boolean = isValid() && isAuxiliaryValid()

    private suspend fun verifyFile(target: Path, expectedBytes: Long, expectedDigest: String): Boolean {
        if (!Files.isRegularFile(target) || Files.size(target) != expectedBytes) return false
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(target).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHexString() == expectedDigest
    }

    suspend fun install(channel: ByteReadChannel, onProgress: (Long, Long) -> Unit) {
        installFile(path, expectedSize, expectedHash, channel, onProgress)
    }

    suspend fun installAuxiliary(channel: ByteReadChannel, onProgress: (Long, Long) -> Unit) {
        val target = checkNotNull(auxiliaryPath)
        installFile(target, expectedAuxiliarySize, expectedAuxiliaryHash, channel, onProgress)
    }

    private suspend fun installFile(
        target: Path,
        expectedBytes: Long,
        expectedDigest: String,
        channel: ByteReadChannel,
        onProgress: (Long, Long) -> Unit,
    ) {
        Files.createDirectories(target.toAbsolutePath().parent)
        val temporary = Files.createTempFile(target.toAbsolutePath().parent, "${target.fileName}-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            onProgress(0, expectedBytes)
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    received += count
                    check(received <= expectedBytes) { "Model exceeds expected size" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (received - reported >= 256 * 1024 || received == expectedBytes) {
                        onProgress(received, expectedBytes)
                        reported = received
                    }
                }
            }
            check(received == expectedBytes) { "Incomplete model download: $received / $expectedBytes bytes" }
            check(digest.digest().toHexString() == expectedDigest) { "Model checksum mismatch" }
            currentCoroutineContext().ensureActive()
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal fun LocalSpeechModel.createStore(path: Path): LocalSpeechModelStore =
    LocalSpeechModelStore(
        path = path,
        expectedSize = modelSize(),
        expectedHash = modelSha256(),
        auxiliaryPath = path.resolveSibling(senseVoiceTokensFileName),
        expectedAuxiliarySize = artifact().tokensSize,
        expectedAuxiliaryHash = artifact().tokensSha256,
    )

internal suspend fun downloadSpeechModel(
    model: LocalSpeechModel,
    store: LocalSpeechModelStore,
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
        var modelValid = store.isValid()
        var auxiliaryValid = store.isAuxiliaryValid()
        if (modelValid && auxiliaryValid) return
        val total = store.expectedTotalSize
        val artifact = model.artifact()
        // Both sources serve the same pinned files; SHA-256 is checked before they can be loaded.
        for (host in listOf("huggingface.co", "hf-mirror.com")) {
            try {
                if (!modelValid) {
                    client.prepareGet(
                        "https://$host/${artifact.repository}/resolve/" +
                            "${artifact.revision}/${artifact.fileName}",
                    ).execute { response ->
                        check(response.status.isSuccess()) { "Model server returned HTTP ${response.status.value}" }
                        store.install(response.bodyAsChannel()) { received, _ ->
                            onProgress(received, total)
                        }
                    }
                    modelValid = true
                }
                if (!auxiliaryValid) {
                    client.prepareGet(
                        "https://$host/${artifact.repository}/resolve/" +
                            "${artifact.revision}/$senseVoiceTokensFileName",
                    ).execute { response ->
                        check(response.status.isSuccess()) { "Model server returned HTTP ${response.status.value}" }
                        store.installAuxiliary(response.bodyAsChannel()) { received, _ ->
                            onProgress(store.expectedSize + received, total)
                        }
                    }
                    auxiliaryValid = true
                }
                if (modelValid && auxiliaryValid) return
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
