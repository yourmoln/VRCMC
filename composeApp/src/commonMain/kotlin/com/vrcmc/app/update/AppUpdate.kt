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
import kotlinx.serialization.json.longOrNull

object AppInfo {
    const val VERSION = "2.0.0"
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
    val exeUrl: String? = null,
    val exeSize: Long? = null,
    val exeSha256: String? = null,
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

expect fun appUpdateDownloadUrl(release: AppRelease): String?

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

        val release = parseAppRelease(response.bodyAsText())
        UpdateCheckResult(release, isNewerVersion(release.tagName, AppInfo.VERSION))
    }

internal fun parseAppRelease(body: String): AppRelease {
    val releaseJson = updateJson.parseToJsonElement(body).jsonObject
    val assets = releaseJson["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
    val exeAssets = assets.filter {
        it["browser_download_url"]?.jsonPrimitive?.contentOrNull?.endsWith(".exe", ignoreCase = true) == true
    }
    // The release script publishes an Inno Setup installer; prefer it over other executables.
    val exe = exeAssets.firstOrNull {
        it["browser_download_url"]?.jsonPrimitive?.contentOrNull?.endsWith("-setup.exe", ignoreCase = true) == true
    } ?: exeAssets.firstOrNull()
    return AppRelease(
        tagName = releaseJson.getValue("tag_name").jsonPrimitive.content,
        name = releaseJson["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        body = releaseJson["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        htmlUrl = releaseJson.getValue("html_url").jsonPrimitive.content,
        apkUrl = assets.mapNotNull { it["browser_download_url"]?.jsonPrimitive?.contentOrNull }
            .firstOrNull { it.endsWith(".apk", ignoreCase = true) },
        exeUrl = exe?.get("browser_download_url")?.jsonPrimitive?.contentOrNull,
        exeSize = exe?.get("size")?.jsonPrimitive?.longOrNull,
        exeSha256 = exe?.get("digest")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':'),
    )
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
