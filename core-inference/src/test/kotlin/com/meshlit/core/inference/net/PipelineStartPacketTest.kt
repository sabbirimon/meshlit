package com.meshlit.core.inference.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-format tests for the Phase-2 activation-channel handshake.
 *
 * Coverage:
 *  - [PipelineStartPacket] round-trips a minimal request body
 *    (prompt + manifest sha + topology id).
 *  - [PipelineStartAck] round-trips both `ok=true` (with embedding
 *    dim) and `ok=false` (with reason) shapes.
 *  - `cancelToken` survives a round-trip when set, and defaults to
 *    null when omitted.
 *  - Default `protocolVersion` is `1`. A future peer sending v2
 *    would still parse cleanly on a v1 reader because unknown
 *    fields are ignored.
 */
class PipelineStartPacketTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `PipelineStartPacket round-trips`() {
        val original = PipelineStartPacket(
            prompt = "Explain pipeline parallelism in 50 words",
            manifestSha256 = "abcdef1234567890",
            topologyId = "topology-7e2c",
            cancelToken = "cancel-uuid-9f3a",
        )
        val body = json.encodeToString(PipelineStartPacket.serializer(), original)
        val parsed = json.decodeFromString(PipelineStartPacket.serializer(), body)
        assertEquals(original.prompt, parsed.prompt)
        assertEquals(original.manifestSha256, parsed.manifestSha256)
        assertEquals(original.topologyId, parsed.topologyId)
        assertEquals(original.cancelToken, parsed.cancelToken)
        assertEquals(PipelineStartPacket.PROTOCOL_VERSION, parsed.protocolVersion)
    }

    @Test
    fun `cancelToken defaults to null when omitted on the wire`() {
        // Simulate a client that didn't set a cancel token — the
        // field should land as null on the parsed object.
        val body = """
            {"protocolVersion":1,
             "prompt":"hi",
             "manifestSha256":"x",
             "topologyId":"t"}
        """.trimIndent()
        val parsed = json.decodeFromString(PipelineStartPacket.serializer(), body)
        assertNull(parsed.cancelToken)
    }

    @Test
    fun `protocolVersion defaults to 1`() {
        val minimal = PipelineStartPacket(prompt = "p", manifestSha256 = "h", topologyId = "t")
        assertEquals(1, minimal.protocolVersion)
        assertEquals(PipelineStartPacket.PROTOCOL_VERSION, minimal.protocolVersion)
    }

    @Test
    fun `PipelineStartAck ok with embedding dim round-trips`() {
        val ack = PipelineStartAck(ok = true, embeddingDim = 4096)
        val body = json.encodeToString(PipelineStartAck.serializer(), ack)
        val parsed = json.decodeFromString(PipelineStartAck.serializer(), body)
        assertTrue(parsed.ok)
        assertEquals(4096, parsed.embeddingDim)
        assertNull(parsed.reason)
    }

    @Test
    fun `PipelineStartAck failure round-trips`() {
        val ack = PipelineStartAck(ok = false, reason = "manifest_sha_mismatch")
        val body = json.encodeToString(PipelineStartAck.serializer(), ack)
        val parsed = json.decodeFromString(PipelineStartAck.serializer(), body)
        assertFalse(parsed.ok)
        assertEquals("manifest_sha_mismatch", parsed.reason)
        assertNull(parsed.embeddingDim)
    }

    @Test
    fun `unknown fields are tolerated`() {
        // A Phase-2.5 client might add new diagnostics. The v1
        // reader must not throw on unknown keys.
        val future = """
            {"ok":true,"embeddingDim":4096,"future_metric":"ok"}
        """.trimIndent()
        val parsed = json.decodeFromString(PipelineStartAck.serializer(), future)
        assertTrue(parsed.ok)
        assertEquals(4096, parsed.embeddingDim)
    }

    // -------- Phase 3 — async token batching knobs --------

    @Test
    fun `batchSize defaults to 1 preserving Phase 2 semantics`() {
        // The Phase 2 default of `batchSize = 1` means the
        // orchestrator never wraps a MicroBatch envelope around
        // traffic — each prompt flows through one activation
        // packet at a time. The default MUST stay at 1 so a
        // Phase 2 client (no batching knobs in the wire) continues
        // to work after a rolling upgrade.
        val minimal = PipelineStartPacket(
            prompt = "p",
            manifestSha256 = "h",
            topologyId = "t",
        )
        assertEquals(1, minimal.batchSize)
        assertEquals(PipelineStartPacket.DEFAULT_BATCH_SIZE, minimal.batchSize)
    }

    @Test
    fun `batchTimeoutMs defaults to 50ms`() {
        // 50 ms is the upper bound on how long a request waits
        // before the orchestrator flushes a partial batch. Anything
        // higher makes the caller feel laggy; anything lower
        // defeats the purpose of batching.
        val minimal = PipelineStartPacket(
            prompt = "p",
            manifestSha256 = "h",
            topologyId = "t",
        )
        assertEquals(50L, minimal.batchTimeoutMs)
        assertEquals(PipelineStartPacket.DEFAULT_BATCH_TIMEOUT_MS, minimal.batchTimeoutMs)
    }

    @Test
    fun `batchSize and batchTimeoutMs round-trip on the wire`() {
        val original = PipelineStartPacket(
            prompt = "Explain batching in 50 words",
            manifestSha256 = "abcdef1234567890",
            topologyId = "topology-batch",
            cancelToken = "cancel-uuid-1",
            batchSize = 8,
            batchTimeoutMs = 75L,
        )
        val body = json.encodeToString(PipelineStartPacket.serializer(), original)
        // Sanity: both fields appear in the wire JSON. A
        // regression here would silently drop the batching knob.
        assertTrue("batchSize missing from wire", body.contains("\"batchSize\":8"))
        assertTrue("batchTimeoutMs missing from wire", body.contains("\"batchTimeoutMs\":75"))

        val parsed = json.decodeFromString(PipelineStartPacket.serializer(), body)
        assertEquals(8, parsed.batchSize)
        assertEquals(75L, parsed.batchTimeoutMs)
        assertEquals(original.prompt, parsed.prompt)
    }

    @Test
    fun `Phase 2 wire without batching knobs decodes with defaults`() {
        // Backward compat: a Phase 2 client never sets
        // `batchSize` / `batchTimeoutMs`. The wire DTO's defaults
        // (1 / 50L) MUST apply so the receiver behaves identically
        // to a Phase 2 pipeline.
        val phase2Json = """
            {
              "protocolVersion": 1,
              "prompt": "hi",
              "manifestSha256": "x",
              "topologyId": "t",
              "cancelToken": "c"
            }
        """.trimIndent()
        val parsed = json.decodeFromString(PipelineStartPacket.serializer(), phase2Json)
        assertEquals(1, parsed.batchSize)
        assertEquals(50L, parsed.batchTimeoutMs)
        assertEquals("hi", parsed.prompt)
    }

    @Test
    fun `PipelineStartAck echoes resolvedBatchSize for Phase 3`() {
        // The FirstStage may downgrade the caller's batchSize
        // (e.g. if the manifest can't fit MAX_BATCH_SIZE KV
        // slots). The resolved value echoes back so the client
        // sizes its MicroBatch envelopes correctly.
        val ack = PipelineStartAck(
            ok = true,
            embeddingDim = 4096,
            resolvedBatchSize = 4,
            resolvedBatchTimeoutMs = 100L,
        )
        val body = json.encodeToString(PipelineStartAck.serializer(), ack)
        val parsed = json.decodeFromString(PipelineStartAck.serializer(), body)
        assertTrue(parsed.ok)
        assertEquals(4, parsed.resolvedBatchSize)
        assertEquals(100L, parsed.resolvedBatchTimeoutMs)
    }

    @Test
    fun `PipelineStartAck defaults batchSize to 1 for Phase 2 traffic`() {
        // A Phase 2 receiver fills `resolvedBatchSize = 1`
        // implicitly (the default). A Phase 3 client detects
        // `resolvedBatchSize == 1` and stays on Phase 2 semantics.
        val ack = PipelineStartAck(ok = true, embeddingDim = 4096)
        assertEquals(1, ack.resolvedBatchSize)
    }

    @Test
    fun `MAX_BATCH_SIZE is 16 to keep MicroBatch under transport cap`() {
        // 16 prompts × ~64 KB KV slice = ~1 MB per MicroBatch.
        // The RawTcpActivationChannel caps frames at 4 MB so we
        // have ~4× headroom for larger hidden states.
        assertEquals(16, PipelineStartPacket.MAX_BATCH_SIZE)
    }
}
