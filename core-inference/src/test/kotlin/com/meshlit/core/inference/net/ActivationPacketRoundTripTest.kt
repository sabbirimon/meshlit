package com.meshlit.core.inference.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip + wire-shape tests for [ActivationPacket]. Keeps the
 * serializer honest about FloatArray/ByteArray conversion without
 * needing the full Android runtime.
 */
class ActivationPacketRoundTripTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun activationPacket_roundTrip_preservesFloatAndByteArrays() {
        val original = ActivationPacket(
            packetVersion = 1,
            stageIndex = 0,
            tokenIdx = 42L,
            positionInSequence = 7,
            layerEnd = 10,
            hiddenState = floatArrayOf(1.0f, -2.5f, 3.14159f, 0.0f),
            kvCacheKeys = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05),
            kvCacheValues = byteArrayOf(0x10, 0x20, 0x30),
            finishedToken = 99,
            isFinished = false,
            crc32 = 0xCAFEBABEL,
        )

        val encoded = json.encodeToString(ActivationPacket.serializer(), original)
        // Body must be valid JSON containing base64 segments.
        assertTrue("missing base64 hiddenState", encoded.contains("\"hiddenStateBase64\":"))
        assertTrue("missing base64 kvCacheKeys", encoded.contains("\"kvCacheKeysBase64\":"))
        assertTrue("missing base64 kvCacheValues", encoded.contains("\"kvCacheValuesBase64\":"))

        val decoded = json.decodeFromString(ActivationPacket.serializer(), encoded)
        assertEquals(original.packetVersion, decoded.packetVersion)
        assertEquals(original.stageIndex, decoded.stageIndex)
        assertEquals(original.tokenIdx, decoded.tokenIdx)
        assertEquals(original.positionInSequence, decoded.positionInSequence)
        assertEquals(original.layerEnd, decoded.layerEnd)
        assertArrayEquals(original.hiddenState, decoded.hiddenState, 0.0001f)
        assertArrayEquals(original.kvCacheKeys, decoded.kvCacheKeys)
        assertArrayEquals(original.kvCacheValues, decoded.kvCacheValues)
        assertEquals(original.finishedToken, decoded.finishedToken)
        assertEquals(original.isFinished, decoded.isFinished)
        assertEquals(original.crc32, decoded.crc32)
    }

    @Test
    fun activationPacket_littleEndianFloatEncoding() {
        // 1.0f IEEE-754 little-endian: 00 00 80 3F.
        val packet = ActivationPacket(
            stageIndex = 0,
            tokenIdx = 0L,
            positionInSequence = 0,
            layerEnd = 0,
            hiddenState = floatArrayOf(1.0f),
            kvCacheKeys = ByteArray(0),
            kvCacheValues = ByteArray(0),
            finishedToken = 0,
            isFinished = false,
            crc32 = 0L,
        )
        val encoded = json.encodeToString(ActivationPacket.serializer(), packet)
        val decoded = json.decodeFromString(ActivationPacket.serializer(), encoded)
        assertEquals(1.0f, decoded.hiddenState[0], 0.0001f)
    }
}
