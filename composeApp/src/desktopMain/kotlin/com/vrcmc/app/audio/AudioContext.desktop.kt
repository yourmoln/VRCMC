package com.vrcmc.app

actual fun requestAudioPermissionIfNeeded(onGranted: () -> Unit): Boolean = true
