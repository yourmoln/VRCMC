package com.vrcmc.app

import java.security.MessageDigest

internal actual fun speechSha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)
