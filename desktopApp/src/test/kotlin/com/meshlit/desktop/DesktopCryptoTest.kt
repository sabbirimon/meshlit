package com.meshlit.desktop
import kotlin.test.*
class DesktopCryptoTest {
    @Test fun knownHashAndAuthenticatedEnvelope() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", DesktopCrypto.hash("abc", "SHA-256"))
        val key = DesktopCrypto.key()
        val encrypted = DesktopCrypto.transform("Hello 世界", key, "Encrypt AES-GCM")
        assertEquals("Hello 世界", DesktopCrypto.transform(encrypted, key, "Decrypt AES-GCM"))
        assertNotEquals(encrypted, DesktopCrypto.transform("Hello 世界", key, "Encrypt AES-GCM"))
        assertFails { DesktopCrypto.transform(encrypted, DesktopCrypto.key(), "Decrypt AES-GCM") }
        assertFails { DesktopCrypto.transform("meshlit-gcm-v1.AAAA", key, "Decrypt AES-GCM") }
    }
}
