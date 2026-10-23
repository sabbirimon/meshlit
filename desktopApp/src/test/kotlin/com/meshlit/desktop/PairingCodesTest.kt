package com.meshlit.desktop

import java.nio.file.Files
import kotlin.test.*

class PairingCodesTest {
    private val link="meshlit://pair?name=Phone&url=http%3A%2F%2F192.168.1.7%3A8080&node=550e8400-e29b-41d4-a716-446655440000&tier=LITE"

    @Test fun importsExportsAndSeparatesSavedCodes() {
        val directory=Files.createTempDirectory("meshlit-pairing-test")
        try {
            val code=DesktopPairingLinks.parse(link)
            assertFalse(code.temporary)
            assertEquals("http://192.168.1.7:8080",code.endpoint)
            val image=directory.resolve("code.png")
            DesktopPairingLinks.exportPng(link,image)
            assertEquals(link,DesktopPairingLinks.decodeImage(image))
            val store=DesktopPairingCodeStore(directory)
            store.save(listOf(code))
            assertEquals(listOf(code),store.load())
        } finally {
            Files.deleteIfExists(directory.resolve("code.png"))
            Files.deleteIfExists(directory.resolve("pairing-codes.json"))
            Files.delete(directory)
        }
    }

    @Test fun refusesExpiredAndNonPairingLinks() {
        assertFails { DesktopPairingLinks.parse("https://example.com") }
        assertFails { DesktopPairingLinks.parse("$link&exp=1") }
    }
}
