package com.vrcmc.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkTest {
    @Test
    fun prefersCommonPrivateLanAddress() {
        assertEquals(
            "192.168.1.42",
            preferredLocalIpv4Address(listOf("10.0.0.5", "127.0.0.1", "192.168.1.42")),
        )
    }

    @Test
    fun rejectsLoopbackAndLinkLocalAddresses() {
        assertNull(preferredLocalIpv4Address(listOf("127.0.0.1", "169.254.2.3", "not-an-ip")))
    }

    @Test
    fun createsLanScanTargetsWithoutOwnAddressOrNetworkEdges() {
        val targets = localIpv4ScanTargets(listOf(" 192.168.12.42 "))

        assertTrue("192.168.12.1" in targets)
        assertTrue("192.168.12.254" in targets)
        assertFalse("192.168.12.42" in targets)
        assertFalse("192.168.12.0" in targets)
        assertFalse("192.168.12.255" in targets)
    }
}
