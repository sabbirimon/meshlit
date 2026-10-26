package com.meshlit.desktop

import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.*

class DesktopNavigationTest {
    @Test fun dashboardNavigationRejectsCredentialBearingAndExecutableUrls() {
        for (bad in listOf("javascript:alert(1)", "file:///tmp/a", "http://example.com", "https://user:password@example.com", "https://example.com/?key=secret", "https://example.com/#secret", "http://localhost.evil.test")) {
            assertFails { dashboardUri(bad) }
        }
        assertEquals("127.0.0.1", dashboardUri("http://127.0.0.1:20128").host)
        assertEquals("openrouter.ai", dashboardUri("https://openrouter.ai/models").host)
    }
    @Test fun vendorDashboardAddressPersistsSeparatelyFromApiEndpoint() {
        val node = Preferences.userRoot().node("meshlit-test-${UUID.randomUUID()}")
        try {
            val addresses = VendorDashboardAddresses(node)
            addresses.save("OmniRoute", "https://gateway.example/dashboard")
            assertEquals("https://gateway.example/dashboard", VendorDashboardAddresses(node).load("OmniRoute", ""))
            assertFails { addresses.save("OmniRoute", "https://gateway.example?key=secret") }
            assertFalse(node.get("OmniRoute", "").contains("secret"))
        } finally { node.removeNode(); node.flush() }
    }
    @Test fun vscodeHandoffEncodesFolderAsData() {
        val folder = Files.createTempDirectory("meshlit project #&")
        try {
            val uri = vscodeFolderUri(folder)
            assertEquals("vscode", uri.scheme)
            assertEquals("file", uri.host)
            assertEquals(folder.toRealPath().toString(), uri.path)
            assertNull(uri.query); assertNull(uri.fragment)
            assertTrue(uri.toASCIIString().contains("%23"))
        } finally { Files.delete(folder) }
    }
}
