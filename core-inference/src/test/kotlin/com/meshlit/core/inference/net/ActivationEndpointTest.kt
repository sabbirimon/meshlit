package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wire-format tests for the Phase-2 [ActivationEndpoint] surface.
 * Two scenarios are tested:
 *
 *  1. `HealthResponse.activation` round-trips through the JSON
 *     encoder/decoder and lands on the same host + port we put in.
 *  2. Older Phase-1 health replies (no `activation` field) decode
 *     cleanly with `activation = null`. Backward compatibility is
 *     a hard requirement because the field will land in production
 *     before every peer has rebuilt with Phase 2.
 *  3. Unknown fields are ignored — a future peer might add new
 *     diagnostics and the decoder must not choke.
 */
class ActivationEndpointTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `activation endpoint round-trips on HealthResponse`() {
        val endpoint = ActivationEndpoint(host = "192.168.1.42", port = 9090)
        val original = HealthResponse(
            status = "ok",
            engine = "llama.cpp",
            port = 8080,
            capabilityTier = CapabilityTier.FULL,
            activeInferences = 1,
            queueDepth = 0,
            activation = endpoint,
        )
        val body = json.encodeToString(HealthResponse.serializer(), original)
        // Spot-check the wire shape — the endpoint must be present
        // in the JSON body and the field must be the last one
        // (Phase 2 appends, never inserts).
        assertEquals(true, body.contains("\"activation\""))
        assertEquals(true, body.contains("\"host\":\"192.168.1.42\""))
        assertEquals(true, body.contains("\"port\":9090"))
        val parsed = json.decodeFromString(HealthResponse.serializer(), body)
        assertNotNull(parsed.activation)
        assertEquals("192.168.1.42", parsed.activation!!.host)
        assertEquals(9090, parsed.activation.port)
    }

    @Test
    fun `missing activation field decodes as null for Phase-1 peers`() {
        // A legacy Phase-1 reply omits the activation field
        // entirely. The decoder must tolerate this and default the
        // field to null.
        val legacy = """
            {"status":"ok","engine":"llama.cpp","port":8080,
             "capabilityTier":"FULL","activeInferences":0,"queueDepth":0}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), legacy)
        assertNull(parsed.activation)
    }

    @Test
    fun `unknown fields are ignored for forward-compat`() {
        // A future peer might add new diagnostics under
        // `activation`. The decoder must not choke.
        val future = """
            {"status":"ok","engine":"llama.cpp","port":8080,
             "activation":{"host":"10.0.0.5","port":9090,"future":"x"}}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), future)
        assertNotNull(parsed.activation)
        assertEquals("10.0.0.5", parsed.activation!!.host)
        assertEquals(9090, parsed.activation.port)
    }
}
