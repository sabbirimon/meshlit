package com.meshlit.core.inference.net

import com.meshlit.core.common.CapabilityTier
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wire-format tests for the [MetricsSnapshot] load-signal additions
 * on [HealthResponse]. The wire is the public contract, so we
 * round-trip the JSON encoder/decoder to catch regressions the
 * moment someone renames a field or drops a default.
 *
 * Backward compatibility: a JSON object that omits `metrics` must
 * decode as `metrics = null`. A JSON object that omits individual
 * counters must default to zero. Older peers / clients built before
 * Phase 1 continue to parse.
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
            metrics = MetricsSnapshot(
                queueDepth = 1,
                totalJobs = 7,
                totalTokensGenerated = 42L,
                avgTokensPerSecond = 12.5f,
                uptimeSeconds = 600L,
            ),
        )
        val body = json.encodeToString(HealthResponse.serializer(), original)
        val parsed = json.decodeFromString(HealthResponse.serializer(), body)
        assertEquals(1, parsed.metrics?.queueDepth)
        assertEquals(7L, parsed.metrics?.totalJobs)
        assertEquals(42L, parsed.metrics?.totalTokensGenerated)
        assertEquals(12.5f, parsed.metrics?.avgTokensPerSecond)
        assertEquals(600L, parsed.metrics?.uptimeSeconds)
    }

    @Test
    fun `defaults are null when metrics is absent on the wire`() {
        // Simulate a pre-Phase-1 /v1/health reply — no metrics
        // block. The @Serializable defaults should fill it in as null.
        val legacy = """
            {"status":"ok","engine":"llama.cpp","port":8080}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), legacy)
        assertEquals(null, parsed.metrics)
    }

    @Test
    fun `metrics defaults to zero when individual counters are absent on the wire`() {
        // A Phase 2 /v1/health reply with a metrics block but no
        // individual counters. The @Serializable defaults should
        // fill them in as zero.
        val partial = """
            {"status":"ok","engine":"llama.cpp","port":8080,
             "metrics":{"queueDepth":3}}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), partial)
        assertEquals(3, parsed.metrics?.queueDepth)
        assertEquals(0L, parsed.metrics?.totalJobs)
        assertEquals(0L, parsed.metrics?.totalTokensGenerated)
        assertEquals(0f, parsed.metrics?.avgTokensPerSecond)
        assertEquals(0L, parsed.metrics?.uptimeSeconds)
    }

    @Test
    fun `unknown fields are ignored for forward-compat`() {
        // A future peer might add new fields; the Meshlit
        // decoder must not choke on them.
        val futureProof = """
            {"status":"ok","engine":"llama.cpp","port":8080,
             "metrics":{"queueDepth":0,"totalJobs":4},
             "future_field":"ignored"}
        """.trimIndent()
        val parsed = json.decodeFromString(HealthResponse.serializer(), futureProof)
        assertEquals(0, parsed.metrics?.queueDepth)
        assertEquals(4L, parsed.metrics?.totalJobs)
    }
}
