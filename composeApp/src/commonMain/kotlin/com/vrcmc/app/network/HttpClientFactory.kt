package com.vrcmc.app

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal expect fun createVrcmcHttpClient(
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient

internal expect fun createEdgeTtsHttpClient(
    block: HttpClientConfig<*>.() -> Unit = {},
): HttpClient
