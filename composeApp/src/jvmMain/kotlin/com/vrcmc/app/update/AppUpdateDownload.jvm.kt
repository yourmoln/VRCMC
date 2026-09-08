package com.vrcmc.app

import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val appUpdateMirrors = listOf(
    "", "https://ghfast.top/", "https://git.yylx.win/", "https://gh-proxy.com/",
    "https://ghfile.geekertao.top/", "https://gh-proxy.net/", "https://ghm.078465.xyz/",
    "https://gitproxy.127731.xyz/", "https://jiashu.1win.eu.org/", "https://github.tbedu.top/",
)

internal suspend fun findFastestAppUpdateUrl(source: String): String {
    val client = createVrcmcHttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 8_000
        }
    }
    return try {
        coroutineScope {
            val winners = Channel<String>(Channel.UNLIMITED)
            appUpdateMirrors.forEach { prefix ->
                launch(Dispatchers.IO) {
                    val url = prefix + source
                    try {
                        if (client.head(url) {
                                header(HttpHeaders.UserAgent, "VRCMC/${AppInfo.VERSION}")
                            }.status.isSuccess()) {
                            winners.send(url)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Other sources can still succeed.
                    }
                }
            }
            val winner = withTimeoutOrNull(8_000) { winners.receive() }
            coroutineContext.cancelChildren()
            checkNotNull(winner) { "No available download source" }
        }
    } finally {
        client.close()
    }
}
