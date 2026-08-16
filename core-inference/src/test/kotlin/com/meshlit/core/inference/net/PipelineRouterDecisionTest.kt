package com.meshlit.core.inference.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Phase 2 pipeline additions to the router
 * decision surface and request hints. Covers:
 *  - `Where.PIPELINE` is reachable from `RouterDecision.pipeline(...)`.
 *  - `RequestHints.parse` honours `pipeline=true`.
 *  - The header round-trips through `toHeaderValue` → `parse`.
 *  - Backward compatibility: legacy `role=brain,gpu=true` headers
 *    still parse with `pipeline=false`.
 */
class PipelineRouterDecisionTest {

    @Test
    fun `local factory builds LOCAL decision`() {
        val d = RouterDecision.local("reason-1")
        assertEquals(RouterDecision.Where.LOCAL, d.where)
        assertNull(d.peerBaseUrl)
        assertEquals("reason-1", d.reason)
        assertNull(d.pipeline)
    }

    @Test
    fun `forward factory builds FORWARD decision with peer URL`() {
        val d = RouterDecision.forward("http://10.0.0.2:8080", "scored")
        assertEquals(RouterDecision.Where.FORWARD, d.where)
        assertEquals("http://10.0.0.2:8080", d.peerBaseUrl)
        assertEquals("scored", d.reason)
        assertNull(d.pipeline)
    }

    @Test
    fun `pipeline factory builds PIPELINE decision with topology`() {
        val topology: com.meshlit.core.inference.PipelineTopology.Valid =
            com.meshlit.core.inference.PipelineTopology.Valid(
                manifest = com.meshlit.core.inference.net.ShardManifest(
                    modelId = "m",
                    modelSha256 = "sha",
                    totalLayers = 8,
                    hiddenDim = 64,
                    contextSize = 1024,
                    tokenizer = com.meshlit.core.inference.net.TokenizerRef(
                        type = "gguf-embedded",
                        offsetBytes = 0L,
                        lengthBytes = 32L,
                        sha256 = "tok",
                    ),
                    specialTokens = com.meshlit.core.inference.net.SpecialTokens(bos = 1, eos = 2),
                    kvCacheBytesPerToken = 1024L,
                    kvCacheBytesPerShard = 1024L * 1024L,
                    shards = listOf(
                        com.meshlit.core.inference.net.ShardSpec(
                            shardId = "shard-0",
                            layerStart = 0,
                            layerEnd = 8,
                            preferredCapabilityTier = com.meshlit.core.common.CapabilityTier.FULL,
                            estimatedRamMb = 256L,
                            stageRole = com.meshlit.core.inference.net.StageRole.FirstStage,
                        ),
                    ),
                ),
                assignments = emptyList(),
                topologyId = "tid",
            )
        val d = RouterDecision.pipeline(topology, "pipeline:3stages")
        assertEquals(RouterDecision.Where.PIPELINE, d.where)
        assertNull(d.peerBaseUrl)
        assertEquals("pipeline:3stages", d.reason)
        assertEquals(topology, d.pipeline)
    }

    @Test
    fun `RequestHints parse defaults pipeline to false`() {
        val h = RequestHints.parse(null)
        assertFalse(h.pipeline)
    }

    @Test
    fun `RequestHints parse honours pipeline=true`() {
        val h = RequestHints.parse("role=brain,gpu=true,pipeline=true")
        assertTrue(h.pipeline)
        assertTrue(h.needsGpu)
        assertEquals("brain", h.role)
    }

    @Test
    fun `RequestHints parse ignores pipeline=false`() {
        val h = RequestHints.parse("role=brain,pipeline=false")
        assertFalse(h.pipeline)
    }

    @Test
    fun `RequestHints toHeaderValue round-trips pipeline=true`() {
        val h = RequestHints(role = "brain", needsGpu = true, pipeline = true)
        val raw = h.toHeaderValue()
        assertTrue(raw.contains("pipeline=true"))
        val parsed = RequestHints.parse(raw)
        assertTrue(parsed.pipeline)
        assertTrue(parsed.needsGpu)
        assertEquals("brain", parsed.role)
    }

    @Test
    fun `RequestHints legacy header without pipeline still parses`() {
        // Pre-Phase-2 clients send only role=... ,gpu=... .
        // The parser must default pipeline=false so backward
        // compatibility holds.
        val h = RequestHints.parse("role=router,gpu=1")
        assertFalse(h.pipeline)
        assertTrue(h.needsGpu)
        assertEquals("router", h.role)
    }
}