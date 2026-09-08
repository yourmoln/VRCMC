package com.vrcmc.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class ReadAloudSource { ORIGINAL, TRANSLATION, OUTGOING }

data class ReadAloudConfig(
    val enabled: Boolean = false,
    val source: ReadAloudSource = ReadAloudSource.ORIGINAL,
    val voice: String = "zh-CN-XiaoxiaoNeural",
    val outputDeviceId: String = "",
)

internal fun readAloudText(
    source: ReadAloudSource,
    original: String,
    translations: Map<String, String>,
    outputOrder: List<String>,
    outgoing: String,
): String = when (source) {
    ReadAloudSource.ORIGINAL -> original
    ReadAloudSource.TRANSLATION -> outputOrder.mapNotNull { translations[it]?.takeIf(String::isNotBlank) }
        .joinToString("\n")
    ReadAloudSource.OUTGOING -> outgoing
}.trim()

internal data class AudioOutputDevice(val id: String, val name: String)
internal expect fun availableAudioOutputDevices(): List<AudioOutputDevice>
internal expect fun supportsAudioOutputSelection(): Boolean

internal interface SpeechAudioPlayer {
    suspend fun play(mp3: ByteArray, outputDeviceId: String)
    fun stop()
}

internal expect fun createSpeechAudioPlayer(): SpeechAudioPlayer

internal class SpeechAudioPlaybackException(val what: Int, val extra: Int) :
    IllegalStateException("Audio playback failed: $what/$extra")

// One worker owns synthesis and playback. Reconfiguration cancels both and drops queued speech.
internal class ReadAloudController(
    private val scope: CoroutineScope,
    initialConfig: ReadAloudConfig,
    private val onError: (String) -> Unit,
    private val synthesize: suspend (String, String) -> ByteArray = EdgeTtsService::synthesize,
    private val player: SpeechAudioPlayer = createSpeechAudioPlayer(),
) {
    private data class Request(val text: String, val settings: ReadAloudConfig, val preview: Boolean = false)
    private var config = initialConfig
    private var queue: Channel<Request>? = null
    private var worker: Job? = null
    private val playbackMutex = Mutex()
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var previewVoice by mutableStateOf<String?>(null)
        private set

    fun configure(value: ReadAloudConfig) {
        if (config == value) return
        stop()
        config = value
    }

    fun enqueue(text: String, requestedConfig: ReadAloudConfig = config) {
        if (!config.enabled || config != requestedConfig || text.isBlank()) return
        enqueueRequest(Request(text, config))
    }

    fun preview(text: String, voice: String) {
        if (!config.enabled || text.isBlank()) return
        stop()
        enqueueRequest(Request(text, config.copy(voice = voice), preview = true))
    }

    fun stopPreview() {
        if (previewVoice != null) stop()
    }

    private fun enqueueRequest(request: Request) {
        failed = false
        val pending = queue ?: Channel<Request>(8).also { channel ->
            queue = channel
            worker = scope.launch {
                playbackMutex.withLock {
                    for (message in channel) {
                        busy = true
                        previewVoice = message.settings.voice.takeIf { message.preview }
                        var stage = "synthesis"
                        try {
                            val audio = withTimeout(60_000) { synthesize(message.text, message.settings.voice) }
                            stage = "playback"
                            withTimeout(180_000) { player.play(audio, message.settings.outputDeviceId) }
                        } catch (_: TimeoutCancellationException) {
                            currentCoroutineContext().ensureActive()
                            failed = true
                            onError("Edge TTS: $stage timed out")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            currentCoroutineContext().ensureActive()
                            failed = true
                            // Exception messages may contain speech text or token-bearing request URLs.
                            val types = generateSequence<Throwable>(error) { it.cause }.take(4)
                                .joinToString(" <- ") { it::class.simpleName ?: "Exception" }
                            val codes = (error as? SpeechAudioPlaybackException)
                                ?.let { " (${it.what}/${it.extra})" }.orEmpty()
                            onError("Edge TTS: $stage failed: $types$codes")
                        } finally {
                            if (queue === channel) {
                                busy = false
                                previewVoice = null
                            }
                        }
                    }
                }
            }
        }
        if (pending.trySend(request).isFailure) {
            failed = true
            onError("Edge TTS: speech queue is full")
        } else {
            busy = true
            if (request.preview) previewVoice = request.settings.voice
        }
    }

    fun stop() {
        queue?.cancel()
        queue = null
        worker?.cancel()
        worker = null
        player.stop()
        busy = false
        failed = false
        previewVoice = null
    }
}
