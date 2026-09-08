package com.vrcmc.app

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
internal actual fun speechSha256(data: ByteArray): ByteArray =
    ByteArray(CC_SHA256_DIGEST_LENGTH).also { digest ->
        data.usePinned { input ->
            digest.usePinned { output ->
                CC_SHA256(if (data.isEmpty()) null else input.addressOf(0), data.size.toUInt(), output.addressOf(0).reinterpret())
            }
        }
    }
