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
    DesktopLocalSpeechRecognizer(LocalWhisperModelStore(localWhisperModelPath()))
}

private fun localWhisperModelPath(): Path =
    System.getProperty("vrcmc.whisperModel.path")?.takeIf(String::isNotBlank)?.let(Path::of)
        ?: (System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let { Path.of(it, "VRCMC") }
            ?: Path.of(System.getProperty("user.home"), ".vrcmc"))
            .resolve("models").resolve("whisper-small").resolve("ggml-small-q5_1.bin")

internal fun localWhisperSupported(): Boolean =
    Platform.isWindows() && Platform.is64Bit() && !Platform.isARM() &&
        // The bundled Windows JNI binary requires AVX2; reject unsupported CPUs before loading it.
        Kernel32.INSTANCE.IsProcessorFeaturePresent(40)

internal interface LocalWhisperEngine : AutoCloseable {
    fun transcribe(samples: FloatArray, language: String): String
}

internal class DesktopLocalSpeechRecognizer(
    private val store: LocalWhisperModelStore,
    override val supported: Boolean = localWhisperSupported(),
    private val download: suspend (LocalWhisperModelStore, (Long, Long) -> Unit) -> Unit = ::downloadWhisperModel,
    private val createEngine: (Path) -> LocalWhisperEngine = ::WhisperJniEngine,
) : LocalSpeechRecognizer {
    // Loading, inference and disposal share a lock: microphone and system audio may overlap.
    private val mutex = Mutex()
    private val downloadMutex = Mutex()
    private val mutableStatus = MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
    override val status = mutableStatus.asStateFlow()
    private var engine: LocalWhisperEngine? = null

    override suspend fun downloadModel(): Boolean = withContext(Dispatchers.IO) {
        if (!supported || !downloadMutex.tryLock()) return@withContext false
        try {
            mutex.withLock {
                if (engine != null) return@withContext true
                mutableStatus.value = LocalSpeechModelStatus.Downloading(0, whisperModelSize)
                if (store.isValid()) {
                    mutableStatus.value = LocalSpeechModelStatus.Downloaded
                    return@withContext true
                }
            }
            // Network IO never holds the inference lock; service changes may release the engine.
            download(store) { received, total ->
                mutableStatus.value = LocalSpeechModelStatus.Downloading(received, total)
            }
            currentCoroutineContext().ensureActive()
            mutex.withLock { mutableStatus.value = LocalSpeechModelStatus.Downloaded }
            true
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    mutableStatus.value = if (store.isValid()) LocalSpeechModelStatus.Downloaded
                        else LocalSpeechModelStatus.Missing
                }
            }
            throw error
        } catch (error: Exception) {
            mutex.withLock {
                mutableStatus.value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
            }
            false
        } finally {
            downloadMutex.unlock()
        }
    }

    override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!supported) return@withLock false
            if (mutableStatus.value == LocalSpeechModelStatus.Ready) return@withLock true
            if (mutableStatus.value is LocalSpeechModelStatus.Downloading) return@withLock false
            mutableStatus.value = LocalSpeechModelStatus.Preparing
            var cached = false
            try {
                if (!store.isValid()) {
                    mutableStatus.value = LocalSpeechModelStatus.Missing
                    return@withLock false
                }
                cached = true
                currentCoroutineContext().ensureActive()
                mutableStatus.value = LocalSpeechModelStatus.Preparing
                engine = createEngine(store.path)
                currentCoroutineContext().ensureActive()
                mutableStatus.value = LocalSpeechModelStatus.Ready
                true
            } catch (error: CancellationException) {
                closeEngine()
                mutableStatus.value = if (cached) LocalSpeechModelStatus.Downloaded else LocalSpeechModelStatus.Missing
                throw error
            } catch (error: Exception) {
                preparationFailed(error)
            } catch (error: LinkageError) {
                preparationFailed(error)
            }
        }
    }

    private fun preparationFailed(error: Throwable): Boolean {
        closeEngine()
        mutableStatus.value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
        return false
    }

    override suspend fun transcribe(wav: ByteArray, language: String): VoiceTranscriptionResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!supported) return@withLock VoiceTranscriptionResult.Failure(reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED)
                val loaded = engine.takeIf { mutableStatus.value == LocalSpeechModelStatus.Ready }
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
            if (engine != null) {
                closeEngine()
                mutableStatus.value = LocalSpeechModelStatus.Downloaded
            }
        }
    }

    private fun closeEngine() {
        val previous = engine
        engine = null
        previous?.close()
    }
}

private class WhisperJniEngine(model: Path) : LocalWhisperEngine {
    private val whisper = WhisperJNI().also {
        WhisperJNI.loadLibrary()
        WhisperJNI.setLibraryLogger(null)
    }
    private val context = checkNotNull(whisper.init(model, WhisperContextParams().apply { useGPU = false })) {
        "Could not load Whisper Small"
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
