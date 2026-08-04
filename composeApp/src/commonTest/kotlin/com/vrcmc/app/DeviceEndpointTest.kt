package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceEndpointTest {
    @Test
    fun usesPort9000WhenPortIsOmitted() {
        assertEquals(Device("192.168.1.10", 9000), parseDeviceEndpoint("192.168.1.10"))
    }

    @Test
    fun keepsAnExplicitPort() {
        assertEquals(Device("192.168.1.10", 9010), parseDeviceEndpoint("192.168.1.10:9010"))
        assertEquals(Device("::1", 9001), parseDeviceEndpoint("[::1]:9001"))
    }

    @Test
    fun rejectsInvalidPorts() {
        assertNull(parseDeviceEndpoint("192.168.1.10:not-a-port"))
        assertNull(parseDeviceEndpoint("192.168.1.10:70000"))
    }
}
