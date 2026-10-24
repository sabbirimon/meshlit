package com.meshlit.devices

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class EndpointAddressTest {
    @Test fun formatsIpv6AndIpv4Endpoints() {
        assertEquals("http://192.168.1.2:8080", endpointUrl("192.168.1.2", 8080))
        assertEquals("https://[2001:db8::12]:8443", endpointUrl("2001:db8::12", 8443, true))
        assertEquals("[2001:db8::12]", URI(endpointUrl("[2001:db8::12]", 8080)).host)
        assertEquals("http://[fe80::1%25wlan0]:8080", endpointUrl("[fe80::1%25wlan0]", 8080))
    }
}
