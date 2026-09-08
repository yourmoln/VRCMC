package com.vrcmc.app

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Files
import java.nio.file.Path
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
    private val model = ByteArray(2048) { it.toByte() }
    private val wav = pcm16ToWav(ByteArray(32_000) { if (it % 2 == 0) 0 else 16 }, 16_000)

    private fun store(): LocalWhisperModelStore = LocalWhisperModelStore(
        directory.root.toPath().resolve("ggml-small-q5_1.bin"), model.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(model).toHexString(),
    )

    @Test
    fun verifiesCacheAndPreservesItAfterTruncatedOrCorruptDownloads() = runBlocking {
        val store = store()
        assertFalse(store.isValid())
        store.install(ByteReadChannel(model)) { _, _ -> }
        assertTrue(store.isValid())
        for (bad in listOf(model.copyOf(20), ByteArray(model.size), model + byteArrayOf(0))) {
            assertTrue(runCatching { store.install(ByteReadChannel(bad)) { _, _ -> } }.isFailure)
            assertTrue(store.isValid())
        }
        Files.list(directory.root.toPath()).use { assertEquals(1, it.count()) }
        Files.write(store.path, ByteArray(model.size))
        assertFalse(store.isValid())
    }

    @Test
    fun cancelledDownloadRemovesTemporaryFileAndCanRetry() = runBlocking {
        val store = store()
        val opened = CompletableDeferred<Unit>()
        val channel = ByteChannel()
        val job = launch { store.install(channel) { _, _ -> opened.complete(Unit) } }
        withTimeout(5_000) { opened.await() }
        job.cancelAndJoin()
        channel.cancel(null)
        assertFalse(Files.exists(store.path))
        Files.list(directory.root.toPath()).use { assertEquals(0, it.count()) }
        store.install(ByteReadChannel(model)) { _, _ -> }
        assertTrue(store.isValid())
    }

    @Test
    fun preparationNeverDownloadsAndExplicitRetryReusesVerifiedModel() = runBlocking {
        val store = store()
        var downloads = 0
        var loads = 0
        var closes = 0
        val recognizer = DesktopLocalSpeechRecognizer(store, supported = true,
            download = { cache, progress ->
                downloads++
                if (downloads == 1) error("Simulated connection failure")
                cache.install(ByteReadChannel(model), progress)
            },
            createEngine = {
                loads++
                object : LocalWhisperEngine {
                    override fun transcribe(samples: FloatArray, language: String) = "$language: local speech"
                    override fun close() { closes++ }
                }
            })
        assertFalse(recognizer.prepare())
        assertEquals(LocalSpeechModelStatus.Missing, recognizer.status.value)
        assertEquals(0, downloads)
        assertFalse(recognizer.downloadModel())
        assertIs<LocalSpeechModelStatus.Failed>(recognizer.status.value)
        assertTrue(recognizer.downloadModel())
        assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status.value)
        assertEquals(0, loads)
        assertTrue(recognizer.prepare())
        assertEquals(LocalSpeechModelStatus.Ready, recognizer.status.value)
        assertTrue(recognizer.prepare())
        assertEquals(1, loads)
        assertEquals(VoiceTranscriptionResult.Success("ja: local speech"), recognizer.transcribe(wav, "ja"))
        recognizer.release()
        assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status.value)
        assertEquals(1, closes)
        assertEquals(VoiceTranscriptionFailureReason.LOCAL_MODEL_NOT_READY,
            assertIs<VoiceTranscriptionResult.Failure>(recognizer.transcribe(wav, "ja")).reason)
        assertTrue(recognizer.prepare())
        assertEquals(2, downloads)
        assertEquals(2, loads)
        recognizer.release()
        assertEquals(2, closes)
    }

    @Test
    fun cancelledDownloadLeavesMissingStatusAndRequiresManualRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var attempt = 0
        val recognizer = DesktopLocalSpeechRecognizer(store(), supported = true,
            download = { cache, progress ->
                if (++attempt == 1) {
                    started.complete(Unit)
                    kotlinx.coroutines.awaitCancellation()
                }
                cache.install(ByteReadChannel(model), progress)
            }, createEngine = { fakeEngine() })
        val preparation = launch { recognizer.downloadModel() }
        withTimeout(5_000) { started.await() }
        preparation.cancelAndJoin()
        assertEquals(LocalSpeechModelStatus.Missing, recognizer.status.value)
        assertFalse(recognizer.prepare())
        assertEquals(1, attempt)
        assertTrue(recognizer.downloadModel())
        assertTrue(recognizer.prepare())
        recognizer.release()
    }

    @Test
    fun unsupportedPlatformDoesNotDownloadOrLoadNativeCode() = runBlocking {
        val recognizer = DesktopLocalSpeechRecognizer(store(), supported = false,
            download = { _, _ -> error("Must not download") }, createEngine = { error("Must not load JNI") })
        assertFalse(recognizer.prepare())
        assertFalse(recognizer.downloadModel())
        assertEquals(VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED,
            assertIs<VoiceTranscriptionResult.Failure>(recognizer.transcribe(wav, "auto")).reason)
    }

    @Test
    fun applicationDownloadSurvivesServiceChangesUntilExplicitCancellation() = runBlocking {
        val cache = store()
        val started = CompletableDeferred<Unit>()
        val stream = ByteChannel()
        val preparations = Channel<Unit>(Channel.UNLIMITED)
        val releases = Channel<Unit>(Channel.UNLIMITED)
        var downloads = 0
        var loads = 0
        val recognizer = DesktopLocalSpeechRecognizer(cache, supported = true,
            download = { store, progress ->
                if (++downloads == 1) {
                    store.install(stream) { received, total ->
                        progress(received, total)
                        started.complete(Unit)
                    }
                } else store.install(ByteReadChannel(model), progress)
            }, createEngine = { loads++; fakeEngine() })
        val observed = object : LocalSpeechRecognizer by recognizer {
            override suspend fun prepare() = recognizer.prepare().also { preparations.send(Unit) }
            override suspend fun release() { recognizer.release(); releases.send(Unit) }
        }
        val lifetime = Job()
        val controller = LocalSpeechModelController(CoroutineScope(coroutineContext + lifetime), observed)
        val local = VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_WHISPER)
        try {
            withTimeout(5_000) {
                controller.updateConfig(local)
                preparations.receive()
                assertEquals(0, downloads)
                assertEquals(LocalSpeechModelStatus.Missing, recognizer.status.value)
                controller.downloadModel()
                controller.downloadModel()
                started.await()
                assertEquals(1, downloads)

                controller.updateConfig(local.copy(provider = VoiceInputProvider.QWEN))
                releases.receive()
                assertIs<LocalSpeechModelStatus.Downloading>(recognizer.status.value)
                controller.updateConfig(local)
                preparations.receive()
                assertIs<LocalSpeechModelStatus.Downloading>(recognizer.status.value)
                controller.updateConfig(local.copy(enabled = false))
                releases.receive()
                assertIs<LocalSpeechModelStatus.Downloading>(recognizer.status.value)

                controller.cancelDownload()
                recognizer.status.first { it == LocalSpeechModelStatus.Missing }
                Files.list(directory.root.toPath()).use { assertEquals(0, it.count()) }
                // Cleanup has finished before the UI exposes another Download model button.
                controller.downloadModel()
                recognizer.status.first { it == LocalSpeechModelStatus.Downloaded }
                assertEquals(2, downloads)
                assertTrue(cache.isValid())
                assertEquals(0, loads)
                controller.updateConfig(local)
                recognizer.status.first { it == LocalSpeechModelStatus.Ready }
                assertEquals(1, loads)
                controller.updateConfig(local.copy(provider = VoiceInputProvider.QWEN))
                releases.receive()
                assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status.value)
                assertEquals(2, downloads)
            }
        } finally {
            lifetime.cancelAndJoin()
            stream.cancel(null)
        }
    }

    @Test
    fun manualDownloadAutomaticallyLoadsWhenLocalServiceIsStillEnabled() = runBlocking {
        val cache = store()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var loads = 0
        val recognizer = DesktopLocalSpeechRecognizer(cache, supported = true,
            download = { store, progress ->
                started.complete(Unit)
                finish.await()
                store.install(ByteReadChannel(model), progress)
            }, createEngine = { loads++; fakeEngine() })
        val lifetime = Job()
        val controller = LocalSpeechModelController(CoroutineScope(coroutineContext + lifetime), recognizer)
        try {
            withTimeout(5_000) {
                controller.updateConfig(VoiceInputConfig(enabled = true, provider = VoiceInputProvider.LOCAL_WHISPER))
                controller.downloadModel()
                started.await()
                assertEquals(0, loads)
                finish.complete(Unit)
                recognizer.status.first { it == LocalSpeechModelStatus.Ready }
                assertEquals(1, loads)
                assertEquals(VoiceTranscriptionResult.Success("local speech"), recognizer.transcribe(wav, "zh"))
            }
        } finally {
            lifetime.cancelAndJoin()
        }
        assertEquals(LocalSpeechModelStatus.Downloaded, recognizer.status.value)
    }

    @Test
    fun localServiceAcceptsNoCloudCredentialsAndCloudSelectionKeepsValidation() = runBlocking {
        val store = store()
        store.install(ByteReadChannel(model)) { _, _ -> }
        val recognizer = DesktopLocalSpeechRecognizer(store, supported = true, createEngine = { fakeEngine() })
        assertTrue(recognizer.prepare())
        val config = VoiceInputConfig(provider = VoiceInputProvider.LOCAL_WHISPER, apiKey = "", baseUrl = "", model = "")
        assertEquals(VoiceTranscriptionResult.Success("local speech"), transcribeVoiceAudio(config, wav, localRecognizer = recognizer))
        assertEquals(VoiceTranscriptionFailureReason.API_KEY_REQUIRED,
            assertIs<VoiceTranscriptionResult.Failure>(transcribeVoiceAudio(config.copy(provider = VoiceInputProvider.QWEN), wav, localRecognizer = recognizer)).reason)
        recognizer.release()
    }

    @Test
    fun cancellationDiscardsNativeResultAndDisposalWaitsForInference() = runBlocking {
        val store = store()
        store.install(ByteReadChannel(model)) { _, _ -> }
        val started = CompletableDeferred<Unit>()
        val finish = CountDownLatch(1)
        var closed = false
        val recognizer = DesktopLocalSpeechRecognizer(store, supported = true, createEngine = {
            object : LocalWhisperEngine {
                override fun transcribe(samples: FloatArray, language: String): String {
                    started.complete(Unit)
                    check(finish.await(5, TimeUnit.SECONDS))
                    assertFalse(closed)
                    return "stale text"
                }
                override fun close() { closed = true }
            }
        })
        assertTrue(recognizer.prepare())
        var published = false
        val inference = launch { recognizer.transcribe(wav, "en"); published = true }
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
    fun convertsDifferentRecordingSampleRatesToWhisperPcm() {
        for (rate in listOf(8_000, 16_000, 44_100, 48_000)) {
            val audio = pcm16ToWav(ByteArray(rate * 2) { if (it % 2 == 0) 0 else 32 }, rate)
            val samples = whisperPcmSamples(audio)
            assertTrue(samples.size in 15_950..16_050, "rate=$rate samples=${samples.size}")
            assertEquals(0.25f, samples[samples.size / 2], 0.001f)
        }
        assertTrue(runCatching { whisperPcmSamples(ByteArray(45)) }.isFailure)
    }

    private fun fakeEngine() = object : LocalWhisperEngine {
        override fun transcribe(samples: FloatArray, language: String) = "local speech"
        override fun close() = Unit
    }
}
