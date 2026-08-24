package com.vrcmc.app

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

internal actual fun createVrcmcHttpClient(
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient { block() }
