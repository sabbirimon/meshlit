package com.meshlit.core.inference.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 — `batchId` + `requestId` round-trip tests on
 * [ActivationPacket]. Backward compatibility: a Phase 2 packet
 * (without these fields) must still decode correctly because the
 * wire DTO carries default values.
 */
class ActivationPacketBatchIdTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun batchId_defaults_to_zero_for_single_prompt_traffic() {
        // Phase 2 callers never set batchId. The constructor's
        // default of 0L preserves Phase 2 semantics on the wire.
        val packet = ActivationPacket(
            stageIndex = 0,
            tokenIdx = 0L,
            positionInSequence = 0,
            layerEnd = 0,
            hiddenState = FloatArray(0),
            kvCacheKeys = ByteArray(0),
            kvCacheValues = ByteArray(0),
            finishedToken = 0,
            isFinished = false,
            crc32 = 0L,
        )
        assertEquals(0L, packet.batchId)
        assertEquals(0, packet.requestId)
    }

    @Test
    fun batchId_roundTrip_non_zero_values_survive() {
        val original = ActivationPacket(
            stageIndex = 1,
            tokenIdx = 99L,
            positionInSequence = 5,
            layerEnd = 12,
            hiddenState = floatArrayOf(0.5f, 1.5f),
            kvCacheKeys = ByteArray(4) { it.toByte() },
            kvCacheValues = ByteArray(4) { (it * 2).toByte() },
            finishedToken = 42,
            isFinished = true,
            crc32 = 0xDEADBEEFL,
            batchId = 12345L,
            requestId = 3,
        )

        val encoded = json.encodeToString(ActivationPacket.serializer(), original)
        // batchId must appear in the wire JSON — verify the wire
        // shape includes it (a regression here would silently
        // strip batchId and break batched routing).
        assertTrue("batchId not in wire JSON", encoded.contains("\"batchId\":12345"))
        assertTrue("requestId not in wire JSON", encoded.contains("\"requestId\":3"))

        val decoded = json.decodeFromString(ActivationPacket.serializer(), encoded)
        assertEquals(12345L, decoded.batchId)
        assertEquals(3, decoded.requestId)
        assertEquals(original.stageIndex, decoded.stageIndex)
        assertEquals(original.isFinished, decoded.isFinished)
    }

    @Test
    fun batchId_legacy_phase2_wire_decodes_with_defaults() {
        // Hand-rolled Phase 2 JSON (no batchId / requestId fields).
        // The Wire DTO carries default values (0L, 0) so this
        // round-trips cleanly. Each base64 segment decodes to a
        // multiple of 4 bytes so the float-array size check passes.
        val phase2Json = """
            {
              "packetVersion": 1,
              "stageIndex": 2,
              "tokenIdx": 17,
              "positionInSequence": 3,
              "layerEnd": 8,
              "hiddenStateBase64": "AAAAAA==",
              "kvCacheKeysBase64": "AAAAAA==",
              "kvCacheValuesBase64": "AAAAAA==",
              "finishedToken": 7,
              "isFinished": false,
              "crc32": 1234
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ActivationPacket.serializer(), phase2Json)
        assertEquals(2, decoded.stageIndex)
        assertEquals(17L, decoded.tokenIdx)
        // Defaults applied:
        assertEquals(0L, decoded.batchId)
        assertEquals(0, decoded.requestId)
    }

    @Test
    fun batchId_legacy_phase2_wire_with_unknown_fields_decodes() {
        // A future Phase 4 wire format may add new fields. The
        // Wire DTO is annotated with @JsonIgnoreUnknownKeys so the
        // Phase 3 decoder tolerates them without crashing.
        val futureJson = """
            {
              "packetVersion": 1,
              "stageIndex": 0,
              "tokenIdx": 0,
              "positionInSequence": 0,
              "layerEnd": 0,
              "hiddenStateBase64": "",
              "kvCacheKeysBase64": "",
              "kvCacheValuesBase64": "",
              "finishedToken": 0,
              "isFinished": false,
              "crc32": 0,
              "batchId": 7,
              "requestId": 2,
              "phase5Field": "ignored"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ActivationPacket.serializer(), futureJson)
        assertEquals(7L, decoded.batchId)
        assertEquals(2, decoded.requestId)
    }

    @Test
    fun equals_hashCode_include_batchId_and_requestId() {
        val a = ActivationPacket(
            stageIndex = 0,
            tokenIdx = 0L,
            positionInSequence = 0,
            layerEnd = 0,
            hiddenState = FloatArray(0),
            kvCacheKeys = ByteArray(0),
            kvCacheValues = ByteArray(0),
            finishedToken = 0,
            isFinished = false,
            crc32 = 0L,
            batchId = 1L,
            requestId = 0,
        )
        val b = a.copy(batchId = 2L)
        assertNotNull(b)
        assertFalse(
            "packets with different batchIds must not be equal",
            a == b,
        )

        val c = a.copy(requestId = 5)
        assertFalse(
            "packets with different requestIds must not be equal",
            a == c,
        )

        // Same values → equal.
        val d = a.copy()
        assertEquals(a, d)
        assertEquals(a.hashCode(), d.hashCode())
    }
}