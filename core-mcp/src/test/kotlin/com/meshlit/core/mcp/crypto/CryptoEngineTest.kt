package com.meshlit.core.mcp.crypto
import org.junit.Assert.*
import org.junit.Test
class CryptoEngineTest {
    @Test fun standardHashAndRfc4231Hmac() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", CryptoEngine.run("sha256", "abc"))
        val expected = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        assertEquals(expected, CryptoEngine.run("hmac_sha256", "Hi There", "0b".repeat(20)))
        assertEquals("true", CryptoEngine.run("hmac_verify", "Hi There", "0b".repeat(20), expectedHex = expected))
        assertEquals("false", CryptoEngine.run("hmac_verify", "Changed", "0b".repeat(20), expectedHex = expected))
    }
    @Test fun authenticatedUnicodeRoundTripFreshNoncesAndTamperRejection() {
        val key = CryptoEngine.run("random_key"); assertEquals(64, key.length)
        val first = CryptoEngine.run("encrypt", "Hello 世界", key, "recipient-one")
        val second = CryptoEngine.run("encrypt", "Hello 世界", key, "recipient-one")
        assertNotEquals(first, second)
        assertEquals("Hello 世界", CryptoEngine.run("decrypt", first, key, "recipient-one"))
        assertThrows(javax.crypto.AEADBadTagException::class.java) { CryptoEngine.run("decrypt", first, key, "recipient-two") }
        val changed = first.dropLast(1) + if (first.last() == '0') '1' else '0'
        assertThrows(javax.crypto.AEADBadTagException::class.java) { CryptoEngine.run("decrypt", changed, key, "recipient-one") }
        assertThrows(IllegalArgumentException::class.java) { CryptoEngine.run("encrypt", "data", "00".repeat(16)) }
        assertThrows(IllegalArgumentException::class.java) { CryptoEngine.run("sha256", "x".repeat(65537)) }
    }
}
