package com.meshlit.harness

import org.junit.Assert.*
import org.junit.Test

class HarnessEndpointTest {
    @Test fun loginCredentialIsSeparateFromSavedAddress() {
        val address = HarnessEndpoint.parse("https://harness.example/ui/?token=abcdefghijklmnop")
        assertEquals("https://harness.example/ui/", address.endpoint)
        assertEquals("https://harness.example", address.origin)
        assertTrue(address.launchUrl.endsWith("token=abcdefghijklmnop"))
    }
    @Test fun localTunnelAndTlsAccepted() {
        listOf("https://192.168.1.2/", "http://127.0.0.1:3080/", "http://[::1]:3080/").forEach { HarnessEndpoint.parse(it) }
    }
    @Test fun rejectsNetworkHttpAndAmbiguousSecrets() {
        listOf("http://192.168.1.2/", "http://localhost/", "file:///tmp/test", "https://user:pass@host/",
            "https://host/#token=x", "https://host/?token=short", "https://host/?key=abcdefghijklmnop",
            "https://host/?token=abcdefghijklmnop&token=abcdefghijklmnop", "https://host/../", "https://host/%2f/", "https://host/api", "https://host:0/").forEach {
            assertThrows(Exception::class.java) { HarnessEndpoint.parse(it) }
        }
    }
    @Test fun navigationCannotSwitchOriginOrDowngrade() {
        val address = HarnessEndpoint.parse("https://host/ui/")
        assertTrue(address.allowsNavigation("https://host/ui/api"))
        assertTrue(address.allowsNavigation("https://host:443/ui/"))
        listOf("http://host/ui/", "https://host:8443/ui/", "https://other/ui/", "https://host.evil/", "https://user@host/", "intent://host/", "file:///tmp/").forEach {
            assertFalse(address.allowsNavigation(it))
        }
    }
}
