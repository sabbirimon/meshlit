package com.meshlit.desktop
import java.util.prefs.Preferences
import java.util.UUID
import kotlin.test.*
class OnlineConnectionsTest {
    @Test fun savesOnlyValidatedAddressMetadataAndCanForget() {
        val node = Preferences.userRoot().node("meshlit-test-${UUID.randomUUID()}")
        try {
            val store = OnlineConnections(node)
            store.save("OpenRouter", "https://openrouter.ai/api/v1", HostProtocol.OPENAI, true)
            assertEquals("https://openrouter.ai/api/v1", OnlineConnections(node).load("OpenRouter")?.endpoint)
            assertFails { store.save("OpenRouter", "https://user:secret@openrouter.ai/api/v1", HostProtocol.OPENAI, true) }
            assertFalse(node.get("OpenRouter", "").contains("secret"))
            store.remove("OpenRouter"); assertNull(store.load("OpenRouter"))
        } finally { node.removeNode(); node.flush() }
    }
}
