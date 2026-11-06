package com.vrcmc.app

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class LocalSpeechRecognitionTest {
    @get:Rule val directory = TemporaryFolder()
    private val model = LocalSpeechModel.SENSE_VOICE_SMALL_INT8
    private val modelBytes = ByteArray(2048) { it.toByte() }
    private val tokens = ByteArray(256) { (it * 3).toByte() }
    private val wav = pcm16ToWav(ByteArray(32_000) { if (it % 2 == 0) 0 else 16 }, 16_000)

    private fun store(): LocalSpeechModelStore = LocalSpeechModelStore(
        path = directory.root.toPath().resolve("model.int8.onnx"),
        expectedSize = modelBytes.size.toLong(),
        expectedHash = sha256(modelBytes),
        auxiliaryPath = directory.root.toPath().resolve("tokens.txt"),
        expectedAuxiliarySize = tokens.size.toLong(),
        expectedAuxiliaryHash = sha256(tokens),
    )

    @Test
    fun modelArtifactMatchesPinnedSenseVoiceFile() {
        assertEquals("model.int8.onnx", model.modelFileName())
        assertEquals(239_233_841L, model.modelSize())
        assertEquals(
            "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            model.modelSha256(),
        )
        val funAsr = LocalSpeechModel.FUN_ASR_NANO_2512_INT8
        assertEquals("model.int8.onnx", funAsr.modelFileName())
        assertEquals(263_531_902L, funAsr.modelSize())
        assertEquals(
            "9dc6e72aa8bc6f5966cf2857a0ce3a425b1d72e91500e147d66329f407c017a1",
            funAsr.modelSha256(),
        )
        val qwen = LocalSpeechModel.QWEN3_ASR_0_6B_INT8
        assertEquals("encoder.int8.onnx", qwen.modelFileName())
        assertEquals(182_491_662L, qwen.modelSize())
        assertEquals(
            "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9",
            qwen.modelSha256(),
        )
        assertEquals(987, qwen.downloadSizeMb)
    }

    @Test
    fun verifiesModelAndAuxiliaryCacheAndPreservesValidFiles() = runBlocking {
        val cache = store()
        assertFalse(cache.isComplete())
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        assertFalse(cache.isComplete())
        cache.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        assertTrue(cache.isComplete())

        for (bad in listOf(modelBytes.copyOf(20), ByteArray(modelBytes.size), modelBytes + byteArrayOf(0))) {
            assertTrue(runCatching { cache.install(ByteReadChannel(bad)) { _, _ -> } }.isFailure)
            assertTrue(cache.isComplete())
        }
        Files.delete(cache.auxiliaryPath)
        assertFalse(cache.isComplete())
    }

    @Test
    fun cancelledDownloadRemovesTemporaryFileAndCanRetry() = runBlocking {
        val cache = store()
        val opened = CompletableDeferred<Unit>()
        val channel = ByteChannel()
        val job = launch { cache.install(channel) { _, _ -> opened.complete(Unit) } }
        withTimeout(5_000) { opened.await() }
        job.cancelAndJoin()
        channel.cancel(null)
        assertFalse(Files.exists(cache.path))
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        assertTrue(cache.isValid())
    }

    @Test
    fun multiFileModelDeletesEveryDownloadedArtifact() = runBlocking {
        val modelDirectory = directory.root.toPath().resolve("qwen")
        val main = ByteArray(2048) { (it * 5).toByte() }
        val tokenizer = ByteArray(512) { (it * 7).toByte() }
        val mainFile = LocalSpeechStoreFile(modelDirectory.resolve("encoder.int8.onnx"), main.size.toLong(), sha256(main))
        val tokenizerFile =
            LocalSpeechStoreFile(modelDirectory.resolve("tokenizer/vocab.json"), tokenizer.size.toLong(), sha256(tokenizer))
        val cache = LocalSpeechModelStore(mainFile.path, listOf(mainFile, tokenizerFile))

        cache.install(mainFile, ByteReadChannel(main)) { _, _ -> }
        cache.install(tokenizerFile, ByteReadChannel(tokenizer)) { _, _ -> }
        assertTrue(cache.isComplete())
        assertTrue(Files.exists(mainFile.path))
        assertTrue(Files.exists(tokenizerFile.path))

        cache.delete()

        assertFalse(Files.exists(mainFile.path))
        assertFalse(Files.exists(tokenizerFile.path))
        assertFalse(Files.exists(modelDirectory))
    }

    @Test
    fun preparationLoadsEngineAndTranscribes() = runBlocking {
        val cache = store()
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        cache.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        var loads = 0
        var closes = 0
        val recognizer = JvmLocalSpeechRecognizer(cache, supported = true, createEngine = { _, _ ->
            loads++
            object : LocalSpeechEngine {
                override fun transcribe(samples: FloatArray, language: String) = "$language: local speech"
                override fun close() {
                    closes++
                }
            }
        })

        assertTrue(recognizer.prepare(model))
        assertEquals(LocalSpeechModelStatus.Ready, recognizer.status(model).value)
        assertEquals(
            VoiceTranscriptionResult.Success("zh: local speech"),
            recognizer.transcribe(model, wav, "zh"),
        )
        recognizer.release()
        assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status(model).value)
        assertEquals(1, loads)
        assertEquals(1, closes)
        assertEquals(
            VoiceTranscriptionFailureReason.LOCAL_MODEL_NOT_READY,
            assertIs<VoiceTranscriptionResult.Failure>(recognizer.transcribe(model, wav, "zh")).reason,
        )
    }

    @Test
    fun deletingLoadedModelClosesEngineAndRemovesFiles() = runBlocking {
        val cache = store()
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        cache.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        var closes = 0
        val recognizer = JvmLocalSpeechRecognizer(cache, supported = true, createEngine = { _, _ ->
            object : LocalSpeechEngine {
                override fun transcribe(samples: FloatArray, language: String) = "local speech"
                override fun close() {
                    closes++
                }
            }
        })

        assertTrue(recognizer.prepare(model))
        assertEquals(LocalSpeechModelStatus.Ready, recognizer.status(model).value)
        assertTrue(recognizer.deleteModel(model))
        assertEquals(LocalSpeechModelStatus.Missing, recognizer.status(model).value)
        assertEquals(1, closes)
        assertFalse(Files.exists(cache.path))
        assertFalse(Files.exists(checkNotNull(cache.auxiliaryPath)))
        assertFalse(recognizer.prepare(model))
    }

    @Test
    fun switchingModelsClosesThePreviousEngine() = runBlocking {
        val sense = LocalSpeechModel.SENSE_VOICE_SMALL_INT8
        val funAsr = LocalSpeechModel.FUN_ASR_NANO_2512_INT8
        fun modelStore(name: String) = LocalSpeechModelStore(
            path = directory.root.toPath().resolve(name).resolve("model.int8.onnx"),
            expectedSize = modelBytes.size.toLong(),
            expectedHash = sha256(modelBytes),
            auxiliaryPath = directory.root.toPath().resolve(name).resolve("tokens.txt"),
            expectedAuxiliarySize = tokens.size.toLong(),
            expectedAuxiliaryHash = sha256(tokens),
        )
        val senseStore = modelStore("sense")
        val funStore = modelStore("fun")
        for (store in listOf(senseStore, funStore)) {
            store.install(ByteReadChannel(modelBytes)) { _, _ -> }
            store.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        }
        val closed = mutableListOf<LocalSpeechModel>()
        val recognizer = JvmLocalSpeechRecognizer(
            stores = mapOf(sense to senseStore, funAsr to funStore),
            supported = true,
            createEngine = { selected, _ ->
                object : LocalSpeechEngine {
                    override fun transcribe(samples: FloatArray, language: String) = selected.serializedName
                    override fun close() {
                        closed += selected
                    }
                }
            },
        )

        assertTrue(recognizer.prepare(sense))
        assertEquals(
            VoiceTranscriptionResult.Success(sense.serializedName),
            recognizer.transcribe(sense, wav, "zh"),
        )
        assertTrue(recognizer.prepare(funAsr))
        assertEquals(listOf(sense), closed)
        assertEquals(
            VoiceTranscriptionResult.Success(funAsr.serializedName),
            recognizer.transcribe(funAsr, wav, "zh"),
        )
        recognizer.release()
        assertEquals(listOf(sense, funAsr), closed)
    }

    @Test
    fun preparationNeverDownloadsAndExplicitRetryReusesVerifiedFiles() = runBlocking {
        val cache = store()
        var downloads = 0
        val recognizer = JvmLocalSpeechRecognizer(
            cache,
            supported = true,
            download = { target, progress ->
                downloads++
                if (downloads == 1) error("Simulated connection failure")
                target.install(ByteReadChannel(modelBytes), progress)
                target.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
            },
            createEngine = { _, _ -> fakeEngine() },
        )
        assertFalse(recognizer.prepare(model))
        assertEquals(LocalSpeechModelStatus.Missing, recognizer.status(model).value)
        assertEquals(0, downloads)
        assertFalse(recognizer.downloadModel(model))
        assertIs<LocalSpeechModelStatus.Failed>(recognizer.status(model).value)
        assertTrue(recognizer.downloadModel(model))
        assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status(model).value)
        assertTrue(recognizer.prepare(model))
        assertEquals(LocalSpeechModelStatus.Ready, recognizer.status(model).value)
    }

    @Test
    fun unsupportedPlatformDoesNotLoadOrDownload() = runBlocking {
        val recognizer = JvmLocalSpeechRecognizer(
            store(),
            supported = false,
            download = { _, _ -> error("Must not download") },
            createEngine = { _, _ -> error("Must not load ONNX Runtime") },
        )
        assertFalse(recognizer.prepare(model))
        assertFalse(recognizer.downloadModel(model))
        assertFalse(recognizer.deleteModel(model))
        assertEquals(
            VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED,
            assertIs<VoiceTranscriptionResult.Failure>(
                recognizer.transcribe(model, wav, "auto"),
            ).reason,
        )
    }

    @Test
    fun applicationDownloadSurvivesServiceChangesUntilExplicitCancellation(): Unit = runBlocking {
        val cache = store()
        val started = CompletableDeferred<Unit>()
        val stream = ByteChannel()
        val preparations = Channel<Unit>(Channel.UNLIMITED)
        val releases = Channel<Unit>(Channel.UNLIMITED)
        var downloads = 0
        val recognizer = JvmLocalSpeechRecognizer(
            cache,
            supported = true,
            download = { target, progress ->
                if (++downloads == 1) {
                    target.install(stream) { received, total ->
                        progress(received, total)
                        started.complete(Unit)
                    }
                } else {
                    target.install(ByteReadChannel(modelBytes), progress)
                    target.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
                }
            },
            createEngine = { _, _ -> fakeEngine() },
        )
        val observed = object : LocalSpeechRecognizer by recognizer {
            override suspend fun prepare(model: LocalSpeechModel) =
                recognizer.prepare(model).also { preparations.send(Unit) }

            override suspend fun release() {
                recognizer.release()
                releases.send(Unit)
            }
        }
        val lifetime = Job()
        val controller = LocalSpeechModelController(CoroutineScope(coroutineContext + lifetime), observed)
        val local = VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_MODEL)
        try {
            withTimeout(10_000) {
                controller.updateConfig(local)
                preparations.receive()
                controller.downloadModel(model)
                controller.downloadModel(model)
                started.await()
                controller.updateConfig(local.copy(provider = VoiceInputProvider.QWEN))
                releases.receive()
                controller.updateConfig(local)
                preparations.receive()
                controller.cancelDownload(model)
                recognizer.status(model).first { it == LocalSpeechModelStatus.Missing }
                assertFalse(Files.exists(cache.path))

                controller.downloadModel(model)
                recognizer.status(model).first { it == LocalSpeechModelStatus.Downloaded }
                assertTrue(cache.isComplete())
                controller.updateConfig(local)
                recognizer.status(model).first { it == LocalSpeechModelStatus.Ready }
            }
        } finally {
            lifetime.cancelAndJoin()
            stream.cancel(null)
        }
    }

    @Test
    fun localServiceAcceptsNoCloudCredentialsAndCloudSelectionKeepsValidation() = runBlocking {
        val cache = store()
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        cache.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        val recognizer = JvmLocalSpeechRecognizer(
            cache,
            supported = true,
            createEngine = { _, _ -> fakeEngine() },
        )
        assertTrue(recognizer.prepare(model))
        val config = VoiceInputConfig(provider = VoiceInputProvider.LOCAL_MODEL)
        assertEquals(
            VoiceTranscriptionResult.Success("local speech"),
            transcribeVoiceAudio(config, wav, localRecognizer = recognizer),
        )
        assertEquals(
            VoiceTranscriptionFailureReason.API_KEY_REQUIRED,
            assertIs<VoiceTranscriptionResult.Failure>(
                transcribeVoiceAudio(config.copy(provider = VoiceInputProvider.QWEN), wav, localRecognizer = recognizer),
            ).reason,
        )
        recognizer.release()
    }

    @Test
    fun cancellationDiscardsNativeResultAndDisposalWaitsForInference() = runBlocking {
        val cache = store()
        cache.install(ByteReadChannel(modelBytes)) { _, _ -> }
        cache.installAuxiliary(ByteReadChannel(tokens)) { _, _ -> }
        val started = CompletableDeferred<Unit>()
        val finish = CountDownLatch(1)
        var closed = false
        val recognizer = JvmLocalSpeechRecognizer(cache, supported = true, createEngine = { _, _ ->
            object : LocalSpeechEngine {
                override fun transcribe(samples: FloatArray, language: String): String {
                    started.complete(Unit)
                    check(finish.await(5, TimeUnit.SECONDS))
                    assertFalse(closed)
                    return "stale text"
                }

                override fun close() {
                    closed = true
                }
            }
        })
        assertTrue(recognizer.prepare(model))
        var published = false
        val inference = launch {
            recognizer.transcribe(model, wav, "zh")
            published = true
        }
        withTimeout(5_000) { started.await() }
        inference.cancel()
        val release = async(Dispatchers.IO) { recognizer.release() }
        finish.countDown()
        inference.join()
        release.await()
        assertFalse(published)
        assertTrue(closed)
    }

    @Test
    fun convertsDifferentRecordingSampleRatesToModelPcm() {
        for (rate in listOf(8_000, 16_000, 44_100, 48_000)) {
            val audio = pcm16ToWav(ByteArray(rate * 2) { if (it % 2 == 0) 0 else 32 }, rate)
            val samples = speechPcmSamples(audio)
            assertTrue(samples.size in 15_950..16_050, "rate=$rate samples=${samples.size}")
            assertEquals(0.25f, samples[samples.size / 2], 0.001f)
        }
        assertTrue(runCatching { speechPcmSamples(ByteArray(45)) }.isFailure)
    }

    private fun fakeEngine() = object : LocalSpeechEngine {
        override fun transcribe(samples: FloatArray, language: String) = "local speech"
        override fun close() = Unit
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()
}
