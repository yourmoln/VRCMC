package com.vrcmc.app

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray

object AppInfo {
    const val VERSION = "1.1.3"
    const val REPOSITORY_URL = "https://github.com/yourmoln/VRCMC"
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/yourmoln/VRCMC/releases/latest"
}

data class AppRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
)

data class UpdateCheckResult(
    val release: AppRelease,
    val updateAvailable: Boolean,
)

private val updateHttpClient = createVrcmcHttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 8_000
        socketTimeoutMillis = 15_000
    }
}
private val updateJson = Json { ignoreUnknownKeys = true }

private val releaseApiUrls = listOf(
    AppInfo.LATEST_RELEASE_API_URL,
    "https://ghfast.top/${AppInfo.LATEST_RELEASE_API_URL}",
    "https://gh-proxy.com/${AppInfo.LATEST_RELEASE_API_URL}",
)

expect fun isAndroidApp(): Boolean

expect suspend fun installAppUpdate(
    release: AppRelease,
    onProgress: (Float?) -> Unit,
): Result<Unit>

suspend fun checkForAppUpdate(): Result<UpdateCheckResult> =
    runCatching {
        var lastError: Throwable? = null
        val response = releaseApiUrls.firstNotNullOfOrNull { url ->
            runCatching {
                updateHttpClient.get(url) {
                    header(HttpHeaders.Accept, "application/vnd.github+json")
                    header(HttpHeaders.UserAgent, "VRCMC/${AppInfo.VERSION}")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }.also { response ->
                    if (!response.status.isSuccess()) {
                        lastError = IllegalStateException("GitHub returned HTTP ${response.status.value}")
                        return@runCatching null
                    }
                }
            }.onFailure { lastError = it }.getOrNull()
        } ?: throw (lastError ?: IllegalStateException("Unable to reach GitHub release service"))

        val releaseJson = updateJson.parseToJsonElement(response.bodyAsText()).jsonObject
        val tagName = releaseJson.getValue("tag_name").jsonPrimitive.content
        val release =
            AppRelease(
                tagName = tagName,
                name = releaseJson["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                body = releaseJson["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                htmlUrl = releaseJson.getValue("html_url").jsonPrimitive.content,
                apkUrl = releaseJson["assets"]?.jsonArray
                    ?.mapNotNull { asset -> asset.jsonObject["browser_download_url"]?.jsonPrimitive?.contentOrNull }
                    ?.firstOrNull { it.endsWith(".apk", ignoreCase = true) },
            )
        UpdateCheckResult(release, isNewerVersion(tagName, AppInfo.VERSION))
    }

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateParts = versionParts(candidate) ?: return false
    val currentParts = versionParts(current) ?: return false
    val partCount = maxOf(candidateParts.size, currentParts.size)
    repeat(partCount) { index ->
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun versionParts(value: String): List<Int>? =
    Regex("""\d+(?:\.\d+)*""")
        .find(value)
        ?.value
        ?.split('.')
        ?.map { it.toIntOrNull() ?: return null }
