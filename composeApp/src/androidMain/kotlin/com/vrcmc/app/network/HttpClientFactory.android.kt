package com.vrcmc.app

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
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

// Edge TTS uses Android's TLS stack, proxy selection and connection recovery through OkHttp.
internal actual fun createEdgeTtsHttpClient(
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient =
    HttpClient(OkHttp) {
        engine {
            config {
                addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    // Ktor 3.2.x otherwise discards non-401 WebSocket handshake responses.
                    if (response.code != 101 && response.request.url.encodedPath.endsWith("/edge/v1")) {
                        val failure = EdgeTtsHttpException(response.code, response.header("Date"))
                        response.close()
                        throw failure
                    }
                    response
                }
            }
        }
        block()
    }
