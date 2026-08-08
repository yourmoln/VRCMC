package com.vrcmc.app

data class AudioInputDevice(val id: String, val name: String)

expect fun availableAudioInputDevices(): List<AudioInputDevice>

expect fun isDesktopAudioPlatform(): Boolean
