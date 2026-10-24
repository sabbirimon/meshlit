package com.meshlit.desktop
import java.nio.file.Files
import java.util.UUID
import kotlin.test.*
class NodeStoreTest {
    @Test fun preservesAddressesAndRejectsUnsafeEdits() {
        val directory = Files.createTempDirectory("meshlit-nodes-test")
        try {
            val store = NodeStore(directory)
            val node = SavedNode(UUID.randomUUID().toString(), "Phone", DeviceCategory.PHONE, "https://example.org:8443/v1", HostProtocol.OPENAI)
            store.save(listOf(node)); assertEquals(listOf(node), store.load())
            val previous = Files.readString(directory.resolve("nodes.json"))
            assertFalse(previous.contains("token"))
            assertFails { store.save(listOf(node.copy(endpoint = "http://192.168.1.10/v1"))) }
            assertFails { store.save(listOf(node, node)) }
            assertEquals(previous, Files.readString(directory.resolve("nodes.json")))
            store.save(emptyList()); assertTrue(store.load().isEmpty())
            Files.writeString(directory.resolve("nodes.json"), "{broken"); assertFails { store.load() }
        } finally { Files.deleteIfExists(directory.resolve("nodes.json")); Files.delete(directory) }
    }
}
