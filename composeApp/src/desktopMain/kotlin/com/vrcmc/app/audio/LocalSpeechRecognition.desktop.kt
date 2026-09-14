package com.vrcmc.app

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import io.github.givimad.whisperjni.WhisperContextParams
import io.github.givimad.whisperjni.WhisperFullParams
import io.github.givimad.whisperjni.WhisperJNI
import io.github.givimad.whisperjni.WhisperSamplingStrategy
import java.io.ByteArrayInputStream
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal actual val localSpeechRecognizer: LocalSpeechRecognizer by lazy {
    DesktopLocalSpeechRecognizer(
        stores = LocalWhisperModel.entries.associateWith { model ->
            LocalWhisperModelStore(
                path = localWhisperModelPath(model),
                expectedSize = model.modelSize(),
                expectedHash = model.modelSha256(),
            )
        },
    )
}

private fun localWhisperModelPath(model: LocalWhisperModel): Path {
    val overrideProperty = when (model) {
        LocalWhisperModel.SMALL_Q5_1 -> "vrcmc.whisperModel.path"
        LocalWhisperModel.MEDIUM_Q5_0 -> "vrcmc.whisperMediumModel.path"
    }
    System.getProperty(overrideProperty)?.takeIf(String::isNotBlank)?.let { return Path.of(it) }
    val root = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let { Path.of(it, "VRCMC") }
        ?: Path.of(System.getProperty("user.home"), ".vrcmc")
    val directory = when (model) {
        LocalWhisperModel.SMALL_Q5_1 -> "whisper-small"
        LocalWhisperModel.MEDIUM_Q5_0 -> "whisper-medium"
    }
    return root.resolve("models").resolve(directory).resolve(model.modelFileName())
}

internal fun localWhisperSupported(): Boolean =
    Platform.isWindows() && Platform.is64Bit() && !Platform.isARM() &&
        // The bundled Windows JNI binary requires AVX2; reject unsupported CPUs before loading it.
        Kernel32.INSTANCE.IsProcessorFeaturePresent(40)

internal interface LocalWhisperEngine : AutoCloseable {
    fun transcribe(samples: FloatArray, language: String): String
}

internal class DesktopLocalSpeechRecognizer(
    private val stores: Map<LocalWhisperModel, LocalWhisperModelStore>,
    override val supported: Boolean = localWhisperSupported(),
    private val download: suspend (
        LocalWhisperModel,
        LocalWhisperModelStore,
        (Long, Long) -> Unit,
    ) -> Unit = ::downloadWhisperModel,
    private val createEngine: (Path) -> LocalWhisperEngine = ::WhisperJniEngine,
) : LocalSpeechRecognizer {
    internal constructor(
        store: LocalWhisperModelStore,
        supported: Boolean = localWhisperSupported(),
        download: suspend (LocalWhisperModelStore, (Long, Long) -> Unit) -> Unit = { cache, progress ->
            downloadWhisperModel(LocalWhisperModel.SMALL_Q5_1, cache, progress)
        },
        createEngine: (Path) -> LocalWhisperEngine = ::WhisperJniEngine,
    ) : this(
        stores = mapOf(LocalWhisperModel.SMALL_Q5_1 to store),
        supported = supported,
        download = { _, cache, progress -> download(cache, progress) },
        createEngine = createEngine,
    )

    // Loading, inference and disposal share a lock: microphone and system audio may overlap.
    private val mutex = Mutex()
    private val downloadMutexes = LocalWhisperModel.entries.associateWith { Mutex() }
    private val statuses = LocalWhisperModel.entries.associateWith {
        MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
    }
    private var engine: LocalWhisperEngine? = null
    private var loadedModel: LocalWhisperModel? = null

    override fun status(model: LocalWhisperModel) = statuses.getValue(model).asStateFlow()

    override suspend fun downloadModel(model: LocalWhisperModel): Boolean = withContext(Dispatchers.IO) {
        if (!supported) return@withContext false
        val store = stores[model] ?: return@withContext false
        val downloadMutex = downloadMutexes.getValue(model)
        if (!downloadMutex.tryLock()) return@withContext false
        val modelStatus = statuses.getValue(model)
        try {
            val alreadyLoaded = mutex.withLock {
                (engine != null && loadedModel == model).also { loaded ->
                    if (!loaded) {
                        modelStatus.value = LocalSpeechModelStatus.Downloading(0, store.expectedSize)
                    }
                }
            }
            if (alreadyLoaded) return@withContext true
            if (store.isValid()) {
                mutex.withLock { modelStatus.value = cachedStatus(model) }
                return@withContext true
            }
            // Network IO never holds the inference lock; service changes may release the engine.
            download(model, store) { received, total ->
                modelStatus.value = LocalSpeechModelStatus.Downloading(received, total)
            }
            currentCoroutineContext().ensureActive()
            mutex.withLock { modelStatus.value = cachedStatus(model) }
            true
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                val valid = store.isValid()
                mutex.withLock {
                    modelStatus.value = when {
                        engine != null && loadedModel == model -> LocalSpeechModelStatus.Ready
                        valid -> LocalSpeechModelStatus.Downloaded
                        else -> LocalSpeechModelStatus.Missing
                    }
                }
            }
            throw error
        } catch (error: Exception) {
            mutex.withLock {
                modelStatus.value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
            }
            false
        } finally {
            downloadMutex.unlock()
        }
    }

    override suspend fun prepare(model: LocalWhisperModel): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!supported) return@withLock false
            val modelStatus = statuses.getValue(model)
            if (
                engine != null && loadedModel == model &&
                modelStatus.value == LocalSpeechModelStatus.Ready
            ) return@withLock true
            if (modelStatus.value is LocalSpeechModelStatus.Downloading) return@withLock false
            if (engine != null) closeEngine()
            val store = stores[model]
            if (store == null) {
                modelStatus.value = LocalSpeechModelStatus.Missing
                return@withLock false
            }
            modelStatus.value = LocalSpeechModelStatus.Preparing
            var cached = false
            try {
                if (!store.isValid()) {
                    modelStatus.value = LocalSpeechModelStatus.Missing
                    return@withLock false
                }
                cached = true
                currentCoroutineContext().ensureActive()
                modelStatus.value = LocalSpeechModelStatus.Preparing
                engine = createEngine(store.path)
                loadedModel = model
                currentCoroutineContext().ensureActive()
                modelStatus.value = LocalSpeechModelStatus.Ready
                true
            } catch (error: CancellationException) {
                closeEngine()
                modelStatus.value = if (cached) LocalSpeechModelStatus.Downloaded else LocalSpeechModelStatus.Missing
                throw error
            } catch (error: Exception) {
                preparationFailed(model, error)
            } catch (error: LinkageError) {
                preparationFailed(model, error)
            }
        }
    }

    private fun preparationFailed(model: LocalWhisperModel, error: Throwable): Boolean {
        closeEngine()
        statuses.getValue(model).value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
        return false
    }

    override suspend fun transcribe(
        model: LocalWhisperModel,
        wav: ByteArray,
        language: String,
    ): VoiceTranscriptionResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!supported) return@withLock VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
                val loaded = engine.takeIf {
                    loadedModel == model && statuses.getValue(model).value == LocalSpeechModelStatus.Ready
                }
                    ?: return@withLock VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_MODEL_NOT_READY)
                if (wav.size <= 44) return@withLock VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.NO_AUDIO)
                try {
                    val samples = whisperPcmSamples(wav)
                    currentCoroutineContext().ensureActive()
                    val text = loaded.transcribe(samples, language).trim()
                    // JNI inference is synchronous. Cancelled recordings must never publish stale text.
                    currentCoroutineContext().ensureActive()
                    if (text.isBlank()) VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.NO_AUDIO)
                    else VoiceTranscriptionResult.Success(text)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    VoiceTranscriptionResult.Failure(error.message.orEmpty(), VoiceTranscriptionFailureReason.LOCAL_RECOGNITION_FAILED)
                } catch (error: LinkageError) {
                    VoiceTranscriptionResult.Failure(error.message.orEmpty(), VoiceTranscriptionFailureReason.LOCAL_RECOGNITION_FAILED)
                }
            }
        }

    override suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) closeEngine()
        }
    }

    private fun cachedStatus(model: LocalWhisperModel): LocalSpeechModelStatus =
        if (engine != null && loadedModel == model) LocalSpeechModelStatus.Ready
        else LocalSpeechModelStatus.Downloaded

    private fun closeEngine() {
        val previous = engine
        val previousModel = loadedModel
        engine = null
        loadedModel = null
        if (previousModel != null && statuses.getValue(previousModel).value == LocalSpeechModelStatus.Ready) {
            statuses.getValue(previousModel).value = LocalSpeechModelStatus.Downloaded
        }
        previous?.close()
    }
}

private class WhisperJniEngine(model: Path) : LocalWhisperEngine {
    private val whisper = WhisperJNI().also {
        WhisperJNI.loadLibrary()
        WhisperJNI.setLibraryLogger(null)
    }
    private val context = checkNotNull(whisper.init(model, WhisperContextParams().apply { useGPU = false })) {
        "Could not load local Whisper model"
    }

    override fun transcribe(samples: FloatArray, language: String): String {
        val params = WhisperFullParams(WhisperSamplingStrategy.GREEDY).apply {
            nThreads = Runtime.getRuntime().availableProcessors().let { (it / 2).coerceIn(1, 6) }
            this.language = language.takeIf { it.isNotBlank() && it != "auto" } ?: "auto"
            if (language == "zh") initialPrompt = "以下是普通话的简体中文转写。"
            // The encoder otherwise pads even a short utterance to 30 seconds. Keep two
            // seconds of padding and at least 15.36 seconds of context for short clips.
            audioCtx = (((samples.size / 320 + 100 + 127) / 128) * 128).coerceIn(768, 1500)
            translate = false
            noContext = true
            noTimestamps = true
            printProgress = false
            printRealtime = false
            printTimestamps = false
            suppressNonSpeechTokens = true
        }
        check(whisper.full(context, params, samples, samples.size) == 0) { "Whisper transcription failed" }
        return (0 until whisper.fullNSegments(context)).joinToString("") { whisper.fullGetSegmentText(context, it) }
    }

    override fun close() = context.close()
}

internal fun whisperPcmSamples(wav: ByteArray): FloatArray {
    val target = AudioFormat(16_000f, 16, 1, true, false)
    val pcm = AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use { source ->
        AudioSystem.getAudioInputStream(target, source).use { it.readBytes() }
    }
    require(pcm.isNotEmpty() && pcm.size % 2 == 0) { "Invalid PCM audio" }
    return FloatArray(pcm.size / 2) { index ->
        val low = pcm[index * 2].toInt() and 0xff
        val high = pcm[index * 2 + 1].toInt()
        ((high shl 8) or low) / 32768f
    }
}
