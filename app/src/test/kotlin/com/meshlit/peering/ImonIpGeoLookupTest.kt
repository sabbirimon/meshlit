package com.meshlit.peering

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImonIpGeoLookupTest {
    @Test fun onlyPublicIpLiteralsCanBeLookedUp() {
        listOf("127.0.0.1","192.168.1.20","10.0.0.2","100.64.0.1","169.254.2.1",
            "::1","fc00::1","fe80::1","example.com","1.2.3.4/path").forEach {
            assertFalse("Expected private or invalid: $it", ImonIpGeoLookup.isPublicLiteral(it))
        }
        assertTrue(ImonIpGeoLookup.isPublicLiteral("8.8.8.8"))
        assertTrue(ImonIpGeoLookup.isPublicLiteral("2606:4700:4700::1111"))
    }
}
