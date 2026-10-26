package com.vrcmc.app

import java.nio.file.Path
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

internal interface LocalSpeechEngine : AutoCloseable {
    fun transcribe(samples: FloatArray, language: String): String
}

internal class JvmLocalSpeechRecognizer(
    private val stores: Map<LocalSpeechModel, LocalSpeechModelStore>,
    override val supported: Boolean = true,
    private val download: suspend (
        LocalSpeechModel,
        LocalSpeechModelStore,
        (Long, Long) -> Unit,
    ) -> Unit = ::downloadSpeechModel,
    private val createEngine: (LocalSpeechModel, Path) -> LocalSpeechEngine,
) : LocalSpeechRecognizer {
    internal constructor(
        store: LocalSpeechModelStore,
        supported: Boolean = true,
        download: suspend (LocalSpeechModelStore, (Long, Long) -> Unit) -> Unit = { cache, progress ->
            downloadSpeechModel(LocalSpeechModel.SENSE_VOICE_SMALL_INT8, cache, progress)
        },
        createEngine: (LocalSpeechModel, Path) -> LocalSpeechEngine,
    ) : this(
        stores = mapOf(LocalSpeechModel.SENSE_VOICE_SMALL_INT8 to store),
        supported = supported,
        download = { _, cache, progress -> download(cache, progress) },
        createEngine = createEngine,
    )

    // Loading, inference and disposal share a lock: microphone and system audio may overlap.
    private val mutex = Mutex()
    private val downloadMutexes = LocalSpeechModel.entries.associateWith { Mutex() }
    private val statuses = LocalSpeechModel.entries.associateWith {
        MutableStateFlow<LocalSpeechModelStatus>(LocalSpeechModelStatus.Missing)
    }
    private var engine: LocalSpeechEngine? = null
    private var loadedModel: LocalSpeechModel? = null

    override val supportedModels: Set<LocalSpeechModel>
        get() = if (supported) stores.keys else emptySet()

    override fun status(model: LocalSpeechModel) = statuses.getValue(model).asStateFlow()

    override suspend fun downloadModel(model: LocalSpeechModel): Boolean = withContext(Dispatchers.IO) {
        if (!supported) return@withContext false
        val store = stores[model] ?: return@withContext false
        val downloadMutex = downloadMutexes.getValue(model)
        if (!downloadMutex.tryLock()) return@withContext false
        val modelStatus = statuses.getValue(model)
        try {
            val alreadyLoaded = mutex.withLock {
                (engine != null && loadedModel == model).also { loaded ->
                    if (!loaded) {
                        modelStatus.value = LocalSpeechModelStatus.Downloading(0, store.expectedTotalSize)
                    }
                }
            }
            if (alreadyLoaded) return@withContext true
            if (store.isComplete()) {
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
                val valid = store.isComplete()
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

    override suspend fun deleteModel(model: LocalSpeechModel): Boolean = withContext(Dispatchers.IO) {
        if (!supported) return@withContext false
        val store = stores[model] ?: return@withContext false
        mutex.withLock {
            if (statuses.getValue(model).value is LocalSpeechModelStatus.Downloading) {
                return@withLock false
            }
            if (engine != null && loadedModel == model) closeEngine()
            val status = statuses.getValue(model)
            status.value = LocalSpeechModelStatus.Deleting
            try {
                store.delete()
                status.value = LocalSpeechModelStatus.Missing
                true
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    status.value = if (store.isComplete()) {
                        LocalSpeechModelStatus.Downloaded
                    } else {
                        LocalSpeechModelStatus.Missing
                    }
                }
                throw error
            } catch (error: Exception) {
                status.value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
                false
            } catch (error: LinkageError) {
                status.value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
                false
            }
        }
    }

    override suspend fun prepare(model: LocalSpeechModel): Boolean = withContext(Dispatchers.IO) {
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
                if (!store.isComplete()) {
                    modelStatus.value = LocalSpeechModelStatus.Missing
                    return@withLock false
                }
                cached = true
                currentCoroutineContext().ensureActive()
                engine = createEngine(model, store.path)
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

    private fun preparationFailed(model: LocalSpeechModel, error: Throwable): Boolean {
        closeEngine()
        statuses.getValue(model).value = LocalSpeechModelStatus.Failed(error.message?.take(300).orEmpty())
        return false
    }

    override suspend fun transcribe(
        model: LocalSpeechModel,
        wav: ByteArray,
        language: String,
    ): VoiceTranscriptionResult =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!supported) {
                    return@withLock VoiceTranscriptionResult.Failure(
                        reason = VoiceTranscriptionFailureReason.LOCAL_UNSUPPORTED,
                    )
                }
                val loaded = engine.takeIf {
                    loadedModel == model && statuses.getValue(model).value == LocalSpeechModelStatus.Ready
                } ?: return@withLock VoiceTranscriptionResult.Failure(
                    reason = VoiceTranscriptionFailureReason.LOCAL_MODEL_NOT_READY,
                )
                if (wav.size <= 44) {
                    return@withLock VoiceTranscriptionResult.Failure(
                        reason = VoiceTranscriptionFailureReason.NO_AUDIO,
                    )
                }
                try {
                    val samples = speechPcmSamples(wav)
                    currentCoroutineContext().ensureActive()
                    val text = loaded.transcribe(samples, language).trim()
                    currentCoroutineContext().ensureActive()
                    if (text.isBlank()) VoiceTranscriptionResult.Failure(
                        reason = VoiceTranscriptionFailureReason.NO_AUDIO,
                    ) else VoiceTranscriptionResult.Success(text)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    VoiceTranscriptionResult.Failure(
                        error.message.orEmpty(),
                        VoiceTranscriptionFailureReason.LOCAL_RECOGNITION_FAILED,
                    )
                } catch (error: LinkageError) {
                    VoiceTranscriptionResult.Failure(
                        error.message.orEmpty(),
                        VoiceTranscriptionFailureReason.LOCAL_RECOGNITION_FAILED,
                    )
                }
            }
        }

    override suspend fun release() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (engine != null) closeEngine()
        }
    }

    private fun cachedStatus(model: LocalSpeechModel): LocalSpeechModelStatus =
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
