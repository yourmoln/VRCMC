package com.vrcmc.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Owned by the application, never by the API page or a selected recognition service.
// All commands are called on the UI thread, so repeated clicks share one download job.
internal class LocalSpeechModelController(
    private val scope: CoroutineScope,
    private val recognizer: LocalSpeechRecognizer = localSpeechRecognizer,
) {
    private val activeModel = MutableStateFlow<LocalSpeechModel?>(null)
    private val modelRevisions = MutableStateFlow<Map<LocalSpeechModel, Int>>(emptyMap())
    private val downloadJobs = mutableMapOf<LocalSpeechModel, Job>()

    init {
        scope.launch {
            combine(activeModel, modelRevisions) { model, revisions ->
                model?.let { it to (revisions[it] ?: 0) }
            }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active != null) {
                        try {
                            recognizer.prepare(active.first)
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { recognizer.release() }
                        }
                    }
                }
        }
    }

    fun updateConfig(config: VoiceInputConfig) {
        activeModel.value = config.localSpeechModel.takeIf {
            config.enabled && config.provider == VoiceInputProvider.LOCAL_MODEL
        }
    }

    fun downloadModel(model: LocalSpeechModel) {
        val previous = downloadJobs[model]
        if (previous != null && !previous.isCompleted && !previous.isCancelled) return
        downloadJobs[model] = scope.launch {
            // A retry may arrive while a cancelled job is finishing its file cleanup.
            previous?.join()
            if (recognizer.downloadModel(model)) {
                val revision = (modelRevisions.value[model] ?: 0) + 1
                modelRevisions.value = modelRevisions.value + (model to revision)
            }
        }
    }

    fun cancelDownload(model: LocalSpeechModel) {
        downloadJobs[model]?.cancel()
    }
}
