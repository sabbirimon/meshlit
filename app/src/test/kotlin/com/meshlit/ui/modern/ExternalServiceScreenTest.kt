package com.meshlit.ui.modern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExternalServiceScreenTest {
    @Test fun localAndVpnHttpAreAccepted() {
        assertEquals("http://192.168.1.20:8080/",validateExternalServiceUrl("http://192.168.1.20:8080/"))
        assertEquals("http://100.100.1.2/",validateExternalServiceUrl("http://100.100.1.2/"))
        assertEquals("http://printer.local/",validateExternalServiceUrl("http://printer.local/"))
    }

    @Test fun publicHttpAndEmbeddedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { validateExternalServiceUrl("http://example.com/") }
        assertThrows(IllegalArgumentException::class.java) { validateExternalServiceUrl("https://user:secret@example.com/") }
        assertThrows(IllegalArgumentException::class.java) { validateExternalServiceUrl("https://example.com/?token=secret") }
        assertEquals("https://example.com/",validateExternalServiceUrl("https://example.com/"))
    }
}
