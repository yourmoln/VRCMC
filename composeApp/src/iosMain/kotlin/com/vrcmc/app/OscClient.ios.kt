package com.vrcmc.app

// Native UDP transport is provided by the iOS host integration.
actual suspend fun sendChatboxOsc(address: String, text: String, port: Int): Boolean = false
