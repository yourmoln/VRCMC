package com.vrcmc.app

data class Device(val address: String, val receivePort: Int = 9000, val sendPort: Int = 9001)

fun Device.displayEndpoint(): String =
    "$receivePort:${if (':' in address) "[$address]" else address}:$sendPort"
