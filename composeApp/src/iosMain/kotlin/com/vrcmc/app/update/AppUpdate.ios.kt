package com.vrcmc.app

actual fun appUpdateDownloadUrl(release: AppRelease): String? = null

actual suspend fun installAppUpdate(
    release: AppRelease,
    onProgress: (Float?) -> Unit,
): Result<Unit> =
    Result.failure(UnsupportedOperationException("Direct updates are not available on iOS"))
