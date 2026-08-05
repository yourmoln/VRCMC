package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceEndpointTest {
    @Test
    fun usesDefaultPortsWhenPortsAreOmitted() {
        assertEquals(Device("192.168.1.10", 9000, 9001), parseDeviceEndpoint("192.168.1.10"))
    }

    @Test
    fun parsesReceiveIpSendFormatAndLegacyReceivePort() {
        assertEquals(
            Device("192.168.1.10", 9010, 9011),
            parseDeviceEndpoint("9010:192.168.1.10:9011"),
        )
        assertEquals(Device("192.168.1.10", 9010, 9001), parseDeviceEndpoint("192.168.1.10:9010"))
        assertEquals(Device("::1", 9010, 9011), parseDeviceEndpoint("9010:[::1]:9011"))
    }

    @Test
    fun rejectsInvalidPorts() {
        assertNull(parseDeviceEndpoint("192.168.1.10:not-a-port"))
        assertNull(parseDeviceEndpoint("70000:192.168.1.10:9001"))
        assertNull(parseDeviceEndpoint("9000:192.168.1.10:70000"))
    }
}
