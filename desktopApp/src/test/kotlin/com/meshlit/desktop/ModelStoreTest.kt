package com.meshlit.desktop
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*
class ModelStoreTest {
    @Test fun recordsFullHashWithoutCopyAndRejectsCorruption(): Unit = runBlocking {
        val directory = Files.createTempDirectory("meshlit-model-test")
        val weight = directory.resolve("model.gguf")
        try {
            Files.write(weight, byteArrayOf(71,71,85,70) + ByteArray(12))
            val item = inspectModel(weight) { _, _ -> }
            val store = ModelStore(directory); store.save(listOf(item)); assertEquals(listOf(item), store.load())
            assertEquals(16, Files.size(weight))
            assertFails { store.save(listOf(item.copy(sha256 = "bad"))) }
            store.save(emptyList()); assertTrue(Files.exists(weight))
            Files.writeString(directory.resolve("models.json"), "{bad"); assertFails { store.load() }
        } finally { Files.deleteIfExists(directory.resolve("models.json")); Files.delete(weight); Files.delete(directory) }
    }
}
