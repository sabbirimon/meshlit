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
    @Test fun embeddedNavigationHasExplicitOriginsAndRedactsCommonKeys() {
        assertEquals("https://example.com:443", webOrigin("https://example.com/login?code=private"))
        assertEquals("http://127.0.0.1:20128", webOrigin("http://127.0.0.1:20128/dashboard"))
        for(url in listOf("file:///etc/passwd", "javascript:alert(1)", "https://user:pass@example.com", "http://remote.example")) assertNull(webOrigin(url))
        assertFalse(dashboardText("API key: sk-examplePrivateKey123").contains("examplePrivate"))
        assertEquals(16000, dashboardText("a".repeat(20000)).length)
    }
    @Test fun closingDuringBrowserInitializationReleasesTheLateResource() {
        var releases = 0
        val slot = BrowserResourceSlot<AutoCloseable>()
        slot.close(); slot.register(AutoCloseable { releases++ }); slot.close()
        assertEquals(1, releases)
    }
}
