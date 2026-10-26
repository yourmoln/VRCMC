package com.vrcmc.app

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private data class LocalSpeechFileArtifact(
    val relativePath: String,
    val size: Long,
    val sha256: String,
)

private data class LocalSpeechModelArtifact(
    val repository: String,
    val revision: String,
    val files: List<LocalSpeechFileArtifact>,
)

internal data class LocalSpeechStoreFile(
    val path: Path,
    val expectedSize: Long,
    val expectedHash: String,
)

private fun modelFile(
    name: String,
    size: Long,
    sha256: String,
): LocalSpeechFileArtifact = LocalSpeechFileArtifact(name, size, sha256)

private fun LocalSpeechModel.artifact(): LocalSpeechModelArtifact = when (this) {
    LocalSpeechModel.SENSE_VOICE_SMALL_INT8 -> LocalSpeechModelArtifact(
        repository = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17",
        revision = "2365baeacb507f821a0c8120fcee3d484dba7a07",
        files = listOf(
            modelFile(
                "model.int8.onnx",
                239_233_841L,
                "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            ),
            modelFile(
                "tokens.txt",
                315_894L,
                "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            ),
        ),
    )

    LocalSpeechModel.FUN_ASR_NANO_2512_INT8 -> LocalSpeechModelArtifact(
        repository = "csukuangfj/sherpa-onnx-sense-voice-funasr-nano-int8-2025-12-17",
        revision = "ebb95fbda2a034d9a8f3b2d2cba32c104b60bed3",
        files = listOf(
            modelFile(
                "model.int8.onnx",
                263_531_902L,
                "9dc6e72aa8bc6f5966cf2857a0ce3a425b1d72e91500e147d66329f407c017a1",
            ),
            modelFile(
                "tokens.txt",
                939_815L,
                "2db4bb25d046e8849e336c9465e248e1694914d996c58c71bdeca18cfb722992",
            ),
        ),
    )

    LocalSpeechModel.QWEN3_ASR_0_6B_INT8 -> LocalSpeechModelArtifact(
        repository = "csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25",
        revision = "68818b2313fe77bd06f6a7c5068ff3ef59d02b8a",
        files = listOf(
            modelFile(
                "encoder.int8.onnx",
                182_491_662L,
                "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9",
            ),
            modelFile(
                "decoder.int8.onnx",
                755_914_231L,
                "4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771",
            ),
            modelFile(
                "conv_frontend.onnx",
                44_148_281L,
                "d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e",
            ),
            modelFile(
                "tokenizer/merges.txt",
                1_671_853L,
                "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5",
            ),
            modelFile(
                "tokenizer/tokenizer_config.json",
                12_487L,
                "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c",
            ),
            modelFile(
                "tokenizer/vocab.json",
                2_776_833L,
                "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910",
            ),
        ),
    )
}

private fun LocalSpeechModel.primaryArtifact(): LocalSpeechFileArtifact = artifact().files.first()

internal fun LocalSpeechModel.modelFileName(): String = primaryArtifact().relativePath.substringAfterLast('/')
internal fun LocalSpeechModel.modelSize(): Long = primaryArtifact().size
internal fun LocalSpeechModel.modelSha256(): String = primaryArtifact().sha256

// Call on Dispatchers.IO. A failed or cancelled download must never replace a valid model.
internal class LocalSpeechModelStore internal constructor(
    val path: Path,
    internal val files: List<LocalSpeechStoreFile>,
) {
    internal constructor(
        path: Path,
        expectedSize: Long,
        expectedHash: String,
        auxiliaryPath: Path? = null,
        expectedAuxiliarySize: Long = 0,
        expectedAuxiliaryHash: String = "",
    ) : this(
        path = path,
        files = buildList {
            add(LocalSpeechStoreFile(path, expectedSize, expectedHash))
            if (auxiliaryPath != null) {
                add(LocalSpeechStoreFile(auxiliaryPath, expectedAuxiliarySize, expectedAuxiliaryHash))
            }
        },
    )

    internal val expectedTotalSize: Long
        get() = files.sumOf(LocalSpeechStoreFile::expectedSize)

    val auxiliaryPath: Path?
        get() = files.getOrNull(1)?.path

    suspend fun isValid(): Boolean = isValid(files.first())

    suspend fun isAuxiliaryValid(): Boolean {
        val auxiliary = files.getOrNull(1) ?: return true
        return isValid(auxiliary)
    }

    suspend fun isComplete(): Boolean = files.all { isValid(it) }

    internal suspend fun isValid(file: LocalSpeechStoreFile): Boolean =
        verifyFile(file.path, file.expectedSize, file.expectedHash)

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
        install(files.first(), channel, onProgress)
    }

    suspend fun installAuxiliary(channel: ByteReadChannel, onProgress: (Long, Long) -> Unit) {
        val target = files.getOrNull(1) ?: error("Model has no auxiliary file")
        install(target, channel, onProgress)
    }

    internal suspend fun install(
        file: LocalSpeechStoreFile,
        channel: ByteReadChannel,
        onProgress: (Long, Long) -> Unit,
    ) {
        val target = file.path
        Files.createDirectories(target.toAbsolutePath().parent)
        val temporary = Files.createTempFile(target.toAbsolutePath().parent, "${target.fileName}-", ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var reported = 0L
            onProgress(0, file.expectedSize)
            Files.newOutputStream(temporary).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    if (count < 0) break
                    if (count == 0) continue
                    received += count
                    check(received <= file.expectedSize) { "Model exceeds expected size" }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    if (received - reported >= 256 * 1024 || received == file.expectedSize) {
                        onProgress(received, file.expectedSize)
                        reported = received
                    }
                }
            }
            check(received == file.expectedSize) {
                "Incomplete model download: $received / ${file.expectedSize} bytes"
            }
            check(digest.digest().toHexString() == file.expectedHash) { "Model checksum mismatch" }
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

    internal suspend fun delete() {
        files.forEach { file ->
            currentCoroutineContext().ensureActive()
            Files.deleteIfExists(file.path)
            currentCoroutineContext().ensureActive()
        }
        files.map { it.path.toAbsolutePath().parent }
            .distinct()
            .sortedByDescending { it.nameCount }
            .forEach { directory ->
                try {
                    Files.deleteIfExists(directory)
                } catch (_: DirectoryNotEmptyException) {
                    // Shared model directories may contain another model or unrelated user data.
                }
            }
    }
}

internal fun LocalSpeechModel.createStore(path: Path): LocalSpeechModelStore {
    val artifact = artifact()
    val modelDirectory = path.toAbsolutePath().parent
    return LocalSpeechModelStore(
        path = path,
        files = artifact.files.map { file ->
            LocalSpeechStoreFile(
                path = modelDirectory.resolve(file.relativePath),
                expectedSize = file.size,
                expectedHash = file.sha256,
            )
        },
    )
}

internal suspend fun downloadSpeechModel(
    model: LocalSpeechModel,
    store: LocalSpeechModelStore,
    onProgress: (Long, Long) -> Unit,
) {
    val artifact = model.artifact()
    val client = createVrcmcHttpClient {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 1_200_000
        }
    }
    try {
        var lastFailure: Exception? = null
        val valid = store.files.associateWith { store.isValid(it) }.toMutableMap()
        if (valid.values.all { it }) return
        val total = store.expectedTotalSize
        var completedBytes = store.files
            .filter { valid[it] == true }
            .sumOf(LocalSpeechStoreFile::expectedSize)

        // Both sources serve the same pinned files; SHA-256 is checked before they can be loaded.
        for (host in listOf("huggingface.co", "hf-mirror.com")) {
            try {
                artifact.files.zip(store.files).forEach { (remote, local) ->
                    if (valid[local] == true) return@forEach
                    client.prepareGet(
                        "https://$host/${artifact.repository}/resolve/" +
                            "${artifact.revision}/${remote.relativePath}",
                    ).execute { response ->
                        check(response.status.isSuccess()) {
                            "Model server returned HTTP ${response.status.value}"
                        }
                        store.install(local, response.bodyAsChannel()) { received, _ ->
                            onProgress(completedBytes + received, total)
                        }
                    }
                    valid[local] = true
                    completedBytes += local.expectedSize
                    onProgress(completedBytes, total)
                }
                if (valid.values.all { it }) return
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
