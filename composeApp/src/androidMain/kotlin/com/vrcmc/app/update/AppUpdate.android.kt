package com.vrcmc.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.measureTimedValue

actual fun isAndroidApp(): Boolean = true

private val mirrors = listOf(
    "", "https://ghfast.top/", "https://git.yylx.win/", "https://gh-proxy.com/",
    "https://ghfile.geekertao.top/", "https://gh-proxy.net/", "https://ghm.078465.xyz/",
    "https://gitproxy.127731.xyz/", "https://jiashu.1win.eu.org/", "https://github.tbedu.top/",
)

private fun mirrorUrl(prefix: String, source: String) = if (prefix.isEmpty()) source else prefix + source

actual suspend fun installAppUpdate(release: AppRelease): Result<Unit> = runCatching {
    val source = requireNotNull(release.apkUrl) { "此版本没有可用的 Android APK" }
    val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 8_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 8_000
        }
    }
    val ranked = withContext(Dispatchers.IO) {
        mirrors.mapNotNull { prefix ->
            runCatching {
                val timed = measureTimedValue {
                    client.head(mirrorUrl(prefix, source)) {
                        header(HttpHeaders.UserAgent, "VRCMC/${AppInfo.VERSION}")
                    }
                }
                if (timed.value.status.isSuccess()) prefix to timed.duration else null
            }.getOrNull()
        }.sortedBy { it.second }.map { it.first }
    }
    val bytes = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        for (prefix in ranked.ifEmpty { mirrors }) {
            try {
                val response = client.get(mirrorUrl(prefix, source))
                if (response.status.isSuccess()) return@withContext response.bodyAsBytes()
                failure = IllegalStateException("HTTP ${response.status.value}")
            } catch (t: Throwable) { failure = t }
        }
        throw failure ?: IllegalStateException("没有可用的下载源")
    }
    val context = requireNotNull(audioApplicationContext()).applicationContext
    val file = File(context.cacheDir, "VRCMC-${release.tagName}.apk")
    file.writeBytes(bytes)
    val archiveInfo = context.packageManager.getPackageArchiveInfo(file.path, 0)
    check(archiveInfo?.packageName == context.packageName) { "下载的 APK 包名不匹配" }
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
