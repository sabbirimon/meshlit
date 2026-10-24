package com.meshlit.desktop

import java.nio.file.Files
import kotlin.test.*

class LocalEngineTest {
    @Test fun rejectsInvalidModelAndWrongStarterInsteadOfStarting() {
        val directory = Files.createTempDirectory("meshlit-model-contract-")
        try {
            val invalid = directory.resolve("invalid.gguf")
            Files.write(invalid, ByteArray(64))
            assertFailsWith<IllegalArgumentException> { validateModel(invalid, false) }
            val headerOnly = directory.resolve("small.gguf")
            Files.write(headerOnly, byteArrayOf(71, 71, 85, 70) + ByteArray(60))
            assertFailsWith<IllegalArgumentException> { validateModel(headerOnly, true) }
            var cancellationChecked = false
            validateModel(headerOnly, false) { cancellationChecked = true }
            assertTrue(cancellationChecked)
        } finally { Files.list(directory).use { it.forEach(Files::delete) }; Files.delete(directory) }
    }
    @Test fun missingEngineDoesNotConnectToAnotherServer() {
        val directory = Files.createTempDirectory("meshlit-missing-engine-")
        try {
            LocalEngine(directory, false).use { engine ->
                assertFalse(engine.available())
                assertFailsWith<IllegalStateException> { engine.start() }
                engine.stop(); engine.stop()
            }
        } finally { Files.delete(directory) }
    }
}
