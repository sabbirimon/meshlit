package com.meshlit.core.inference.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 — round-trip + back-compat tests for [MicroBatch],
 * [MicroBatchEntry], [MicroBatchReply], and [MicroBatchReplyEntry].
 *
 * Covers:
 *  - `MicroBatch` round-trips with multiple entries
 *  - entries preserve order through encode/decode
 *  - `MicroBatchEntry.prompt` defaults to "" on subsequent batches
 *  - `MicroBatchReply` echoes batchId and demuxes per-request entries
 *  - `WireFrameKind` constants are stable strings
 */
class MicroBatchTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun microBatch_roundTrip_preserves_entries_and_order() {
        val original = MicroBatch(
            batchId = 42L,
            entries = listOf(
                MicroBatchEntry(
                    requestId = 0,
                    prompt = "Hello",
                    position = 0,
                    hiddenStateBase64 = "AAAA",
                    kvCacheKeysBase64 = "AAAA",
                    kvCacheValuesBase64 = "AAAA",
                ),
                MicroBatchEntry(
                    requestId = 1,
                    prompt = "World",
                    position = 0,
                    hiddenStateBase64 = "AAAB",
                    kvCacheKeysBase64 = "AAAB",
                    kvCacheValuesBase64 = "AAAB",
                ),
                MicroBatchEntry(
                    requestId = 2,
                    prompt = "Pipeline",
                    position = 0,
                    hiddenStateBase64 = "AAAC",
                    kvCacheKeysBase64 = "AAAC",
                    kvCacheValuesBase64 = "AAAC",
                    isFinished = true,
                    finishedToken = 99,
                ),
            ),
            isLastBatch = false,
            flushedAtMs = 1_700_000_000_000L,
        )

        val encoded = json.encodeToString(MicroBatch.serializer(), original)
        val decoded = json.decodeFromString(MicroBatch.serializer(), encoded)

        assertEquals(original.batchId, decoded.batchId)
        assertEquals(original.isLastBatch, decoded.isLastBatch)
        assertEquals(original.flushedAtMs, decoded.flushedAtMs)
        assertEquals(3, decoded.entries.size)

        // Order preserved through round-trip.
        assertEquals(0, decoded.entries[0].requestId)
        assertEquals("Hello", decoded.entries[0].prompt)
        assertEquals(1, decoded.entries[1].requestId)
        assertEquals("World", decoded.entries[1].prompt)
        assertEquals(2, decoded.entries[2].requestId)
        assertEquals(true, decoded.entries[2].isFinished)
        assertEquals(99, decoded.entries[2].finishedToken)
    }

    @Test
    fun microBatch_entry_prompt_defaults_to_empty_on_subsequent_batches() {
        // The Phase 3 contract: only the first batch for a request
        // carries the prompt. Subsequent batches drop it. The wire
        // DTO's `prompt: String = ""` default makes the encoder
        // emit "prompt":"" — receivers reuse the previously-seen
        // prompt for that request id.
        val entry = MicroBatchEntry(
            requestId = 7,
            position = 12,
            hiddenStateBase64 = "AAAA",
            kvCacheKeysBase64 = "AAAA",
            kvCacheValuesBase64 = "AAAA",
        )
        assertEquals("", entry.prompt)
        assertEquals(-1, entry.finishedToken)
        assertFalse(entry.isFinished)

        val encoded = json.encodeToString(MicroBatchEntry.serializer(), entry)
        val decoded = json.decodeFromString(MicroBatchEntry.serializer(), encoded)
        assertEquals("", decoded.prompt)
        assertEquals(-1, decoded.finishedToken)
    }

    @Test
    fun microBatchReply_roundTrip_demuxes_per_request() {
        val reply = MicroBatchReply(
            batchId = 99L,
            entries = listOf(
                MicroBatchReplyEntry(
                    requestId = 0,
                    finishedToken = 101,
                    isFinished = false,
                    hiddenStateBase64 = "AAAA",
                    kvCacheKeysBase64 = "AAAA",
                    kvCacheValuesBase64 = "AAAA",
                ),
                MicroBatchReplyEntry(
                    requestId = 1,
                    finishedToken = -1,
                    isFinished = true,
                    hiddenStateBase64 = "AAAB",
                    kvCacheKeysBase64 = "AAAB",
                    kvCacheValuesBase64 = "AAAB",
                ),
            ),
            isLastReply = true,
            flushedAtMs = 1_700_000_001_000L,
        )

        val encoded = json.encodeToString(MicroBatchReply.serializer(), reply)
        val decoded = json.decodeFromString(MicroBatchReply.serializer(), encoded)

        assertEquals(99L, decoded.batchId)
        assertTrue(decoded.isLastReply)
        assertEquals(2, decoded.entries.size)

        // Per-request demux: entry index 0 → request 0.
        assertEquals(0, decoded.entries[0].requestId)
        assertEquals(101, decoded.entries[0].finishedToken)
        assertFalse(decoded.entries[0].isFinished)

        // Per-request demux: entry index 1 → request 1 (finished).
        assertEquals(1, decoded.entries[1].requestId)
        assertEquals(-1, decoded.entries[1].finishedToken)
        assertTrue(decoded.entries[1].isFinished)
    }

    @Test
    fun microBatch_empty_entries_roundTrips_as_empty_list() {
        // Edge case: a flush deadline fires when no requests are
        // in flight. The receiver must not crash on an empty list.
        val batch = MicroBatch(
            batchId = 0L,
            entries = emptyList(),
            isLastBatch = false,
        )
        val encoded = json.encodeToString(MicroBatch.serializer(), batch)
        val decoded = json.decodeFromString(MicroBatch.serializer(), encoded)
        assertEquals(0L, decoded.batchId)
        assertEquals(0, decoded.entries.size)
        assertFalse(decoded.isLastBatch)
    }

    @Test
    fun wireFrameKind_constants_are_stable() {
        // These strings appear on the wire — a typo here would
        // silently break frame routing across a rolling upgrade.
        // Lock them down so any refactor has to consciously
        // rename them.
        assertEquals("activation_packet", WireFrameKind.ACTIVATION_PACKET_KIND)
        assertEquals("micro_batch", WireFrameKind.MICRO_BATCH_KIND)
        assertEquals("pipeline_start", WireFrameKind.PIPELINE_START_KIND)
        assertEquals("pipeline_ack", WireFrameKind.PIPELINE_ACK_KIND)
        assertEquals("micro_batch_reply", WireFrameKind.MICRO_BATCH_REPLY_KIND)
    }

    @Test
    fun microBatch_isLastBatch_roundTrip_preserved() {
        val batch = MicroBatch(
            batchId = 7L,
            entries = listOf(
                MicroBatchEntry(
                    requestId = 0,
                    prompt = "shutdown",
                    position = 99,
                    hiddenStateBase64 = "AAAA",
                    kvCacheKeysBase64 = "AAAA",
                    kvCacheValuesBase64 = "AAAA",
                ),
            ),
            isLastBatch = true,
            flushedAtMs = 1_700_000_002_000L,
        )
        val encoded = json.encodeToString(MicroBatch.serializer(), batch)
        // Sanity: the wire carries the boolean explicitly.
        assertTrue("isLastBatch missing from wire JSON", encoded.contains("\"isLastBatch\":true"))
        val decoded = json.decodeFromString(MicroBatch.serializer(), encoded)
        assertTrue(decoded.isLastBatch)
        assertEquals(1, decoded.entries.size)
    }

    @Test
    fun microBatchEntry_finishedToken_negative_one_means_no_token() {
        // Contract: `finishedToken = -1` is the sentinel for "no
        // token yet" — receivers should not emit a token event
        // for this entry. The default value guards against
        // uninitialised fields.
        val entry = MicroBatchEntry(
            requestId = 0,
            position = 0,
            hiddenStateBase64 = "",
            kvCacheKeysBase64 = "",
            kvCacheValuesBase64 = "",
        )
        assertEquals(-1, entry.finishedToken)
        val encoded = json.encodeToString(MicroBatchEntry.serializer(), entry)
        // Default `encodeDefaults = true` serialises the -1.
        assertTrue("finishedToken=-1 not in wire JSON", encoded.contains("\"finishedToken\":-1"))
    }

    @Test
    fun microBatch_unknown_fields_are_tolerated() {
        // Phase 4 may add new fields. Phase 3 receivers ignore
        // them, so the decoder must use `ignoreUnknownKeys = true`.
        val futureJson = """
            {
              "batchId": 1,
              "entries": [],
              "isLastBatch": false,
              "flushedAtMs": 0,
              "phase4field": "ignored"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(MicroBatch.serializer(), futureJson)
        assertNotNull(decoded)
        assertEquals(1L, decoded.batchId)
    }
}