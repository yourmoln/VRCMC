package com.vrcmc.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

actual fun appUpdateDownloadUrl(release: AppRelease): String? = release.apkUrl

actual suspend fun installAppUpdate(
    release: AppRelease,
    onProgress: (Float?) -> Unit,
): Result<Unit> = runCatching {
    val source = requireNotNull(release.apkUrl) { "This release has no Android APK" }
    onProgress(null)
    val downloadUrl = findFastestAppUpdateUrl(source)

    val context = requireNotNull(audioApplicationContext()).applicationContext
    val fileName = "VRCMC-${release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.apk"
    val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
    target.delete()
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request = DownloadManager.Request(Uri.parse(downloadUrl))
        .setTitle("VRCMC ${release.tagName}")
        .setDescription("VRCMC update")
        .setMimeType(APK_MIME_TYPE)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
    val downloadId = manager.enqueue(request)
    context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
        .putLong(DOWNLOAD_ID, downloadId)
        .putString(DOWNLOAD_FILE, target.path)
        .apply()
    awaitDownload(manager, downloadId, onProgress)
    verifyApk(context, target)
    openInstaller(context, target)
}

private suspend fun awaitDownload(
    manager: DownloadManager,
    downloadId: Long,
    onProgress: (Float?) -> Unit,
) {
    while (true) {
        val state = withContext(Dispatchers.IO) {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                check(cursor.moveToFirst()) { "Download task disappeared" }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                Triple(status, downloaded, total)
            }
        }
        if (state.third > 0) onProgress((state.second.toFloat() / state.third).coerceIn(0f, 1f))
        else onProgress(null)
        when (state.first) {
            DownloadManager.STATUS_SUCCESSFUL -> return
            DownloadManager.STATUS_FAILED -> error("Download failed")
        }
        delay(500)
    }
}

private fun verifyApk(context: Context, apk: File) {
    check(apk.isFile && apk.length() > 0) { "Downloaded APK is empty" }
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
    }
    val archive = context.packageManager.getPackageArchiveInfo(apk.path, flags)
    check(archive?.packageName == context.packageName) { "Downloaded APK package does not match" }
    val installed = context.packageManager.getPackageInfo(context.packageName, flags)
    check(signatures(archive).contentDeepEquals(signatures(installed))) {
        "Downloaded APK signature does not match"
    }
}

private fun signatures(info: android.content.pm.PackageInfo): Array<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }.toTypedArray()
    } else {
        @Suppress("DEPRECATION") info.signatures.orEmpty().map { it.toByteArray() }.toTypedArray()
    }

private fun openInstaller(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, APK_MIME_TYPE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    })
}

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_NOTIFICATION_CLICKED) return
        val preferences = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val expectedId = preferences.getLong(DOWNLOAD_ID, -1L)
        val clickedIds = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
        if (expectedId < 0 || clickedIds?.contains(expectedId) != true) return
        val apk = preferences.getString(DOWNLOAD_FILE, null)?.let(::File) ?: return
        runCatching {
            verifyApk(context, apk)
            openInstaller(context, apk)
        }
    }
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val UPDATE_PREFS = "vrcmc_update"
private const val DOWNLOAD_ID = "download_id"
private const val DOWNLOAD_FILE = "download_file"
