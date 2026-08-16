package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wire-format tests for the Phase-1 load-signal additions on
 * [HealthResponse]. We round-trip the JSON encoder/decoder
 * because the wire is the public contract and we want a
 * regression test the moment someone renames
 * `activeInferences` or `queueDepth`.
 *
 * Backward compatibility: a JSON object that omits the new fields
 * must decode as `activeInferences=0, queueDepth=0`. Older
 * peers / clients built before Phase 1 continue to parse.
 */
class HealthResponseLoadSignalTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `load-signal fields round-trip`() {
        val original = HealthResponse(
            status = "ok",
            engine = "llama.cpp",
            port = 8080,
            capabilityTier = CapabilityTier.FULL,
            activeInferences = 3,
            queueDepth = 1,
        )
        val body = json.encodeToString(HealthResponse.serializer(), original)
        val parsed = json.decodeFromString(HealthResponse.serializer(), body)
        assertEquals(3, parsed.activeInferences)
        assertEquals(1, parsed.queueDepth)
    }

    @Test
    fun `defaults are zero when fields are absent on the wire`() {
        // Simulate a pre-Phase-1 /v1/health reply — no
        // activeInferences, no queueDepth. The @Serializable
        // defaults should fill them in as 0.
        val legacy = """
            {"status":"ok","engine":"llama.cpp","port":8080}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), legacy)
        assertEquals(0, parsed.activeInferences)
        assertEquals(0, parsed.queueDepth)
    }

    @Test
    fun `unknown fields are ignored for forward-compat`() {
        // A future peer might add new fields; the Meshlit
        // decoder must not choke on them.
        val futureProof = """
            {"status":"ok","engine":"llama.cpp","port":8080,
             "activeInferences":2,"queueDepth":0,
             "future_field":"ignored"}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), futureProof)
        assertEquals(2, parsed.activeInferences)
        assertEquals(0, parsed.queueDepth)
    }
}
