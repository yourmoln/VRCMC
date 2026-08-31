package com.vrcmc.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val japaneseDictionaryArchiveSize = 13_343_016L
internal const val japaneseDictionarySha256 =
    "24909fd751c0b439f7af5131b080eb65bc85062f0c4977ca4a71c76abe74e0b6"
internal const val japaneseDictionaryVersion = "0.9.0"
internal const val japaneseDictionaryArchiveName =
    "kuromoji-ipadic-$japaneseDictionaryVersion.jar"

internal sealed interface JapaneseDictionaryStatus {
    data object Missing : JapaneseDictionaryStatus

    data object Downloading : JapaneseDictionaryStatus

    data object Ready : JapaneseDictionaryStatus

    data class Failed(val detail: String) : JapaneseDictionaryStatus
}

private val japaneseDictionaryUrls =
    listOf(
        "https://repo.maven.apache.org/maven2/com/atilika/kuromoji/" +
            "kuromoji-ipadic/$japaneseDictionaryVersion/$japaneseDictionaryArchiveName",
        "https://repo1.maven.org/maven2/com/atilika/kuromoji/" +
            "kuromoji-ipadic/$japaneseDictionaryVersion/$japaneseDictionaryArchiveName",
    )

private val japaneseDictionaryHttpClient by lazy {
    createVrcmcHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 300_000
        }
    }
}

internal object JapaneseDictionaryManager {
    private val mutex = Mutex()

    var status: JapaneseDictionaryStatus by
        mutableStateOf(
            if (platformJapaneseDictionaryRequired()) {
                JapaneseDictionaryStatus.Missing
            } else {
                JapaneseDictionaryStatus.Ready
            }
        )
        private set

    val isReady: Boolean
        get() = status == JapaneseDictionaryStatus.Ready

    suspend fun ensureAvailable(): Boolean =
        mutex.withLock {
            if (!platformJapaneseDictionaryRequired()) {
                status = JapaneseDictionaryStatus.Ready
                return@withLock true
            }
            if (platformJapaneseDictionaryAvailable()) {
                status = JapaneseDictionaryStatus.Ready
                return@withLock true
            }

            status = JapaneseDictionaryStatus.Downloading
            var lastFailure: Throwable? = null
            for (url in japaneseDictionaryUrls) {
                try {
                    val response = japaneseDictionaryHttpClient.get(url)
                    check(response.status.isSuccess()) {
                        "Dictionary server returned HTTP ${response.status.value}"
                    }
                    platformCacheJapaneseDictionary(response.bodyAsChannel())
                    JapaneseRomanizer.clearCache()
                    status = JapaneseDictionaryStatus.Ready
                    return@withLock true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastFailure = error
                }
            }

            status =
                JapaneseDictionaryStatus.Failed(
                    lastFailure?.message?.take(300) ?: "Dictionary download failed"
                )
            false
        }
}

internal expect fun platformJapaneseDictionaryRequired(): Boolean

internal expect suspend fun platformJapaneseDictionaryAvailable(): Boolean

internal expect suspend fun platformCacheJapaneseDictionary(channel: ByteReadChannel)

internal expect fun platformJapaneseDictionaryCachePath(): String?
