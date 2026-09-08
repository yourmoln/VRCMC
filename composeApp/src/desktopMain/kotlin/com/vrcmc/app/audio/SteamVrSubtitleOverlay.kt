package com.vrcmc.app

import androidx.compose.runtime.*
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class SteamVrOverlayStatus { CONNECTED, WAITING_FOR_CONTROLLER, UNAVAILABLE }

internal data class SteamVrOverlayContent(
    val status: String,
    val captions: List<SystemAudioCaption>,
    val dark: Boolean,
)

internal data class SteamVrOverlayFrame(
    val position: SteamVrOverlayPosition,
    val opacityPercent: Int,
    val content: SteamVrOverlayContent,
    val scalePercent: Int = 100,
)

internal interface SteamVrOverlayBackend : AutoCloseable {
    fun update(frame: SteamVrOverlayFrame): SteamVrOverlayStatus
}

// OpenVR owns process-global interfaces. A rapidly reopened window must wait until
// the preceding session has released them, including any native call in progress.
private val steamVrSessionMutex = Mutex()

@Composable
internal fun rememberSteamVrSubtitleOverlay(
    config: SystemAudioLanguageConfig,
    content: SteamVrOverlayContent,
    onError: (String) -> Unit,
): SteamVrOverlayStatus? {
    val frame = SteamVrOverlayFrame(config.steamVrOverlayPosition, config.opacityPercent, content, config.steamVrOverlayScalePercent)
    val frames = remember { MutableStateFlow(frame) }
    val currentOnError by rememberUpdatedState(onError)
    var status by remember { mutableStateOf<SteamVrOverlayStatus?>(null) }
    SideEffect { frames.value = frame }
    LaunchedEffect(config.steamVrOverlayEnabled) {
        status = null
        if (!config.steamVrOverlayEnabled) return@LaunchedEffect
        val uiScope = this
        steamVrSessionMutex.withLock {
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "VRCMC-SteamVR-overlay").apply { isDaemon = true }
            }.asCoroutineDispatcher().use { dispatcher ->
                withContext(dispatcher) {
                    runSteamVrOverlay(
                        frames = frames,
                        open = { OpenVrSubtitleOverlay() },
                        onStatus = { value -> uiScope.launch { status = value } },
                        onError = { message -> uiScope.launch { currentOnError(message) } },
                    )
                }
            }
        }
    }
    return status.takeIf { config.steamVrOverlayEnabled }
}

internal suspend fun runSteamVrOverlay(
    frames: StateFlow<SteamVrOverlayFrame>,
    open: () -> SteamVrOverlayBackend,
    onStatus: (SteamVrOverlayStatus) -> Unit,
    onError: (String) -> Unit,
    retryMillis: Long = 5_000,
    refreshMillis: Long = 100,
) {
    var lastError: String? = null
    var lastStatus: SteamVrOverlayStatus? = null
    fun report(value: SteamVrOverlayStatus) {
        if (lastStatus != value) {
            lastStatus = value
            onStatus(value)
        }
    }
    while (currentCoroutineContext().isActive) {
        try {
            open().use { overlay ->
                while (currentCoroutineContext().isActive) {
                    report(overlay.update(frames.value))
                    lastError = null
                    // SteamVR tracks the attached device at compositor speed. Poll only
                    // for subtitles, changed settings, controller roles and shutdown events.
                    delay(refreshMillis)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (error !is Exception && error !is LinkageError) throw error
            report(SteamVrOverlayStatus.UNAVAILABLE)
            val message = "SteamVR overlay: ${error::class.simpleName}: ${error.message.orEmpty()}"
            if (lastError != message) {
                lastError = message
                onError(message)
            }
            delay(retryMillis)
        }
    }
}
