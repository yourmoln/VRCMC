package com.vrcmc.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private var pendingPermissionAction: (() -> Unit)? = null

actual fun requestAudioPermissionIfNeeded(onGranted: () -> Unit): Boolean {
    val context = audioApplicationContext() ?: return false
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true
    pendingPermissionAction = onGranted
    (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 7001) }
    return false
}

internal fun handleAudioPermissionResult(granted: Boolean) {
    val action = pendingPermissionAction
    pendingPermissionAction = null
    if (granted) action?.invoke()
}
