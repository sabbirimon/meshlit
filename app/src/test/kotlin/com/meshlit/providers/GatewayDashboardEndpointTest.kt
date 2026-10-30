package com.meshlit.providers

import org.junit.Assert.*
import org.junit.Test

class GatewayDashboardEndpointTest {
    @Test fun exactReviewedHttpsOriginAllowsItsOwnDashboardAndApiOnly() {
        val address=GatewayDashboardEndpoint.parse("https://owner.example:8443/dashboard")
        assertTrue(address.allowsNavigation("https://owner.example:8443/api/providers?view=all"))
        assertTrue(address.allowsNavigation("https://owner.example:8443/dashboard#models"))
        for(url in listOf("https://owner.example/dashboard","https://owner.example.evil.test:8443/","http://owner.example:8443/",
            "https://attacker@owner.example:8443/","file:///etc/passwd","javascript:alert(1)","data:text/html,test")) assertFalse(url,address.allowsNavigation(url))
    }
    @Test fun normalHttpsDefaultPortAndLiteralLoopbackTunnelWork() {
        assertTrue(GatewayDashboardEndpoint.parse("https://owner.example/").allowsNavigation("https://owner.example:443/api"))
        assertTrue(GatewayDashboardEndpoint.parse("http://127.0.0.1:20128/").allowsNavigation("http://127.0.0.1:20128/dashboard"))
        assertTrue(GatewayDashboardEndpoint.parse("http://[::1]:20128/").allowsNavigation("http://[::1]:20128/api/models"))
    }
    @Test fun urlSecretsAmbiguousHttpAndInvalidPortsAreNeverSaved() {
        for(value in listOf("http://192.168.1.2:20128/","http://localhost:20128/","http://127.1:20128/","http://2130706433/",
            "https://owner.example/?token=private","https://owner.example/#token","https://user:private@owner.example/",
            "https://owner.example:0/","https://owner.example:65536/","https://owner.example\\@other.example/")) {
            assertThrows(value,IllegalArgumentException::class.java) {GatewayDashboardEndpoint.parse(value)}
        }
    }
}
