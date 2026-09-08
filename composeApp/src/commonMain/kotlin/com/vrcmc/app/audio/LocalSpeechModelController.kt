package com.vrcmc.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Owned by the application, never by the API page or a selected recognition service.
// All commands are called on the UI thread, so repeated clicks share one download job.
internal class LocalSpeechModelController(
    private val scope: CoroutineScope,
    private val recognizer: LocalSpeechRecognizer = localSpeechRecognizer,
) {
    private val enabled = MutableStateFlow(false)
    private val modelRevision = MutableStateFlow(0)
    private var downloadJob: Job? = null

    init {
        scope.launch {
            combine(enabled, modelRevision) { active, revision -> active to revision }
                .collectLatest { (active, _) ->
                    if (active) {
                        try {
                            recognizer.prepare()
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { recognizer.release() }
                        }
                    }
                }
        }
    }

    fun updateConfig(config: VoiceInputConfig) {
        enabled.value = config.enabled && config.provider == VoiceInputProvider.LOCAL_WHISPER
    }

    fun downloadModel() {
        val previous = downloadJob
        if (previous != null && !previous.isCompleted && !previous.isCancelled) return
        downloadJob = scope.launch {
            // A retry may arrive while a cancelled job is finishing its file cleanup.
            previous?.join()
            if (recognizer.downloadModel()) modelRevision.value++
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }
}
