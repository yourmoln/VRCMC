package com.vrcmc.app

actual fun isAndroidApp(): Boolean = false

actual suspend fun installAppUpdate(
    release: AppRelease,
    onProgress: (Float?) -> Unit,
): Result<Unit> =
    Result.failure(UnsupportedOperationException("APK updates are only available on Android"))
