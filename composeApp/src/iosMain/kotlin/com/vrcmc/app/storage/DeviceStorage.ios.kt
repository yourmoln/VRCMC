package com.vrcmc.app

import cnames.structs.__CFData
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSUserDefaults
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private val defaults
    get() = NSUserDefaults.standardUserDefaults

actual fun loadStoredDevices(): List<Device> =
    (defaults.stringForKey("devices") ?: "").split(';').mapNotNull { value ->
        val parts = value.split('|')
        when (parts.firstOrNull()) {
            "v3" ->
                parts
                    .getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Device(
                            it,
                            parts.getOrNull(2)?.toIntOrNull() ?: 9000,
                            parts.getOrNull(3)?.toIntOrNull() ?: 9001,
                        )
                    }
            "v2" ->
                parts
                    .getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Device(it, parts.getOrNull(2)?.toIntOrNull() ?: 9000) }
            else -> parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { Device(it) }
        }
    }

actual fun saveStoredDevices(devices: List<Device>, activeAddress: String) {
    defaults.setObject(
        devices.joinToString(";") { "v3|${it.address}|${it.receivePort}|${it.sendPort}" },
        forKey = "devices",
    )
    defaults.setObject(activeAddress, forKey = "active")
}

actual fun loadStoredActiveAddress(): String = defaults.stringForKey("active") ?: ""

actual fun loadStoredTranslationSettings(): String =
    defaults.stringForKey("translationSettings") ?: ""

actual fun saveStoredTranslationSettings(value: String) {
    defaults.setObject(value, forKey = "translationSettings")
}

@OptIn(ExperimentalForeignApi::class)
actual fun loadStoredTranslationSecrets(): String {
    val query = keychainQuery() ?: return ""
    return try {
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CPointerVar<out kotlinx.cinterop.CPointed>>()
            result.value = null
            if (SecItemCopyMatching(query, result.ptr) != errSecSuccess) return@memScoped ""
            val value = result.value ?: return@memScoped ""
            try {
                if (CFGetTypeID(value) != platform.CoreFoundation.CFDataGetTypeID())
                    return@memScoped ""
                val data = value.reinterpret<__CFData>()
                val size = CFDataGetLength(data).toInt()
                val bytes = CFDataGetBytePtr(data) ?: return@memScoped ""
                ByteArray(size) { index -> bytes[index].toByte() }.decodeToString()
            } finally {
                CFRelease(value)
            }
        }
    } finally {
        CFRelease(query)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun saveStoredTranslationSecrets(value: String) {
    val query = keychainQuery() ?: return
    try {
        if (value.isBlank() || value == "{}") {
            SecItemDelete(query)
            return
        }
        val encoded = value.encodeToByteArray()
        val data =
            encoded.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), encoded.size.convert())
            } ?: return
        try {
            val update = mutableDictionary() ?: return
            try {
                CFDictionarySetValue(update, kSecValueData, data)
                if (SecItemUpdate(query, update) == errSecItemNotFound) {
                    CFDictionarySetValue(query, kSecValueData, data)
                    CFDictionarySetValue(
                        query,
                        kSecAttrAccessible,
                        kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    )
                    SecItemAdd(query, null)
                }
            } finally {
                CFRelease(update)
            }
        } finally {
            CFRelease(data)
        }
    } finally {
        CFRelease(query)
    }
}

actual fun loadStoredChatHistory(): String = defaults.stringForKey("chatHistory") ?: ""

actual fun saveStoredChatHistory(value: String) {
    defaults.setObject(value, forKey = "chatHistory")
}

actual fun loadStoredSimultaneousInterpretationEnabled(): Boolean =
    defaults.boolForKey("simultaneousInterpretation")

actual fun saveStoredSimultaneousInterpretationEnabled(value: Boolean) {
    defaults.setBool(value, forKey = "simultaneousInterpretation")
}

actual fun loadStoredAlwaysInterpretationEnabled(): Boolean =
    defaults.boolForKey("alwaysInterpretation")

actual fun saveStoredAlwaysInterpretationEnabled(value: Boolean) {
    defaults.setBool(value, forKey = "alwaysInterpretation")
}

actual fun loadStoredAlwaysInterpretationDelayMillis(): Int =
    defaults.integerForKey("alwaysInterpretationDelayMillis").toInt().takeIf { it > 0 } ?: 2_000

actual fun saveStoredAlwaysInterpretationDelayMillis(value: Int) {
    defaults.setInteger(value.toLong(), forKey = "alwaysInterpretationDelayMillis")
}

@OptIn(ExperimentalForeignApi::class)
private fun keychainQuery() =
    mutableDictionary()?.also { query ->
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        val service =
            CFStringCreateWithCString(null, "io.github.vrcmteam.vrcmcs", kCFStringEncodingUTF8)
        val account = CFStringCreateWithCString(null, "translation-secrets", kCFStringEncodingUTF8)
        try {
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
        } finally {
            service?.let(::CFRelease)
            account?.let(::CFRelease)
        }
    }

@OptIn(ExperimentalForeignApi::class)
private fun mutableDictionary() = memScoped {
    CFDictionaryCreateMutable(
        null,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )
}
