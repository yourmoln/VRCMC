package com.vrcmc.app

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

// Custom provider endpoints may use certificates that Android does not know.
private val trustAllManager: X509TrustManager =
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

internal actual fun createVrcmcHttpClient(
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(CIO) {
        engine {
            https {
                trustManager = trustAllManager
            }
        }
        block()
    }
