package com.meshlit.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the validation rules in [PairingPayload.decode].
 *
 * The decoder is the front door of the pairing surface — a stray
 * QR or a typo must NOT pollute the local peer list. Each test
 * pins one of the rules added in the Phase-1 pairing-bugfix
 * sweep.
 */
class PairingPayloadTest {

    private val validNodeId = "550e8400-e29b-41d4-a716-446655440000"
    private val validBaseUrl = "http://192.168.1.10:8080"
    private val validUri =
        "meshlit://pair?name=Test&url=$validBaseUrl&node=$validNodeId&tier=MID"

    @Test
    fun `valid v1 URI decodes cleanly`() {
        val p = PairingPayload.decode(validUri)
        assertEquals("Test", p.nodeName)
        assertEquals(validBaseUrl, p.baseUrl)
        assertEquals(validNodeId, p.nodeId)
        assertEquals("MID", p.capabilityTier)
    }

    @Test
    fun `valid JSON payload decodes cleanly`() {
        val json = """
            {"nodeName":"Bob","baseUrl":"https://bob.example.com:8443",
             "nodeId":"$validNodeId","capabilityTier":"LITE"}
        """.trimIndent()
        val p = PairingPayload.decode(json)
        assertEquals("Bob", p.nodeName)
        assertEquals("https://bob.example.com:8443", p.baseUrl)
    }

    @Test
    fun `missing baseUrl is rejected`() {
        val bad = "meshlit://pair?name=Test&node=$validNodeId&tier=MID"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        assertTrue(e.message!!.contains("missing endpoint URL"))
    }

    @Test
    fun `non-http URL is rejected`() {
        val bad = "meshlit://pair?name=Test&url=ftp://nope&node=$validNodeId&tier=MID"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        assertTrue(e.message!!.contains("scheme"))
    }

    @Test
    fun `URL without host is rejected`() {
        // `http://` (scheme + `://` + nothing) — `java.net.URI`
        // rejects this at parse time, and our decoder
        // surfaces a typed rejection reason so the UI can
        // show a clear banner instead of crashing.
        val bad = "meshlit://pair?name=Test&url=http://&node=$validNodeId&tier=MID"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        val msg = e.message ?: ""
        assertTrue(
            "expected a URI/host rejection, got: $msg",
            msg.contains("host", ignoreCase = true) ||
                msg.contains("authority", ignoreCase = true) ||
                msg.contains("malformed", ignoreCase = true) ||
                msg.contains("not a valid URI", ignoreCase = true),
        )
    }

    @Test
    fun `non-meshlit scheme is rejected with typed reason`() {
        val bad = "https://example.com/pair?name=Test&url=$validBaseUrl&node=$validNodeId&tier=MID"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        assertTrue(
            "expected a clear 'not a Meshlit pairing payload' reason, got: ${e.message}",
            e.message!!.contains("not a Meshlit pairing payload"),
        )
    }

    @Test
    fun `meshlit-join URI is rejected with a hint to use the Join Cluster screen`() {
        val bad = "meshlit://join?v=1&node=$validNodeId&relay=http://relay.example.com"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        assertTrue(e.message!!.contains("Join Cluster"))
    }

    @Test
    fun `empty nodeId is accepted (legacy v1 QRs without node)`() {
        val p = PairingPayload.decode("meshlit://pair?name=Test&url=$validBaseUrl&tier=MID")
        assertEquals("", p.nodeId)
    }

    @Test
    fun `64-char hex fingerprint as nodeId is accepted`() {
        val fp = "a".repeat(64)
        val p = PairingPayload.decode("meshlit://pair?name=Test&url=$validBaseUrl&node=$fp&tier=MID")
        assertEquals(fp, p.nodeId)
    }

    @Test
    fun `recovery phrase cannot be published as nodeId`() {
        val phrase = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima"
        val encoded = java.net.URLEncoder.encode(phrase, "UTF-8")
        assertThrows(IllegalArgumentException::class.java) { PairingPayload.decode("meshlit://pair?name=Test&url=$validBaseUrl&node=$encoded&tier=MID") }
    }

    @Test
    fun `unrecognised nodeId shape is rejected`() {
        val bad = "meshlit://pair?name=Test&url=$validBaseUrl&node=💩&tier=MID"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        assertTrue(e.message!!.contains("nodeId"))
    }

    @Test
    fun `blank input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode("   ")
        }
    }

    @Test
    fun `encode round-trips through decode`() {
        val original = PairingPayload(
            nodeName = "Bob & Alice",
            baseUrl = "http://192.168.0.5:8080",
            nodeId = validNodeId,
            capabilityTier = "MID",
        )
        val encoded = original.encode()
        val decoded = PairingPayload.decode(encoded)
        assertEquals(original.nodeName, decoded.nodeName)
        assertEquals(original.baseUrl, decoded.baseUrl)
        assertEquals(original.nodeId, decoded.nodeId)
        assertEquals(original.capabilityTier, decoded.capabilityTier)
    }

    @Test
    fun `decode failure reason is non-blank and surfaced for UI`() {
        val bad = "not even a uri"
        val e = assertThrows(IllegalArgumentException::class.java) {
            PairingPayload.decode(bad)
        }
        // The UI shows this message in the typed banner; it must
        // be specific enough that the user can act on it.
        assertNotNull(e.message)
        assertTrue(e.message!!.isNotBlank())
    }

    @Test
    fun `time limited QR round trips and expired QR is rejected`() {
        val temporary = PairingPayload("Phone", validBaseUrl, validNodeId, "MID",
            expiresAtEpochMs = System.currentTimeMillis() + 5 * 60 * 1000L)
        assertEquals(temporary.expiresAtEpochMs, PairingPayload.decode(temporary.encode()).expiresAtEpochMs)
        val expired = temporary.copy(expiresAtEpochMs = System.currentTimeMillis() - 1)
        assertThrows(IllegalArgumentException::class.java) { PairingPayload.decode(expired.encode()) }
        assertThrows(IllegalArgumentException::class.java) { PairingPayload.decode(temporary.encode().replace(Regex("exp=\\d+"), "exp=oops")) }
    }
}
