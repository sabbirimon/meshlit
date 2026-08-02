package com.meshlit.core.inference.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * One activation packet flowing down the pipeline for a single
 * generated token. The packet carries:
 *  - the hidden state produced by the source shard,
 *  - the KV-cache slice deltas the recipient needs to slot in,
 *  - the finished token id (set only by the LastStage),
 *  - a CRC32 over the whole payload so corruption mid-transport is
 *    caught before the next stage runs.
 *
 * Wire shape (v1, JSON only):
 *  - `hiddenState` / `kvCacheKeys` / `kvCacheValues` are base64.
 *  - `crc32` lets the receiver abort early on a bad packet.
 *  - Future binary path (WebRTC data channel) will use a parallel
 *    `ActivationPacketBinary` and pick at runtime.
 */
@Serializable(with = ActivationPacketSerializer::class)
data class ActivationPacket(
    val packetVersion: Int = 1,
    val stageIndex: Int,
    val tokenIdx: Long,
    val positionInSequence: Int,
    val layerEnd: Int,
    val hiddenState: FloatArray,
    val kvCacheKeys: ByteArray,
    val kvCacheValues: ByteArray,
    val finishedToken: Int,
    val isFinished: Boolean,
    val crc32: Long,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActivationPacket) return false
        return packetVersion == other.packetVersion &&
            stageIndex == other.stageIndex &&
            tokenIdx == other.tokenIdx &&
            positionInSequence == other.positionInSequence &&
            layerEnd == other.layerEnd &&
            hiddenState.contentEquals(other.hiddenState) &&
            kvCacheKeys.contentEquals(other.kvCacheKeys) &&
            kvCacheValues.contentEquals(other.kvCacheValues) &&
            finishedToken == other.finishedToken &&
            isFinished == other.isFinished &&
            crc32 == other.crc32
    }

    override fun hashCode(): Int {
        var result = packetVersion
        result = 31 * result + stageIndex
        result = 31 * result + tokenIdx.hashCode()
        result = 31 * result + positionInSequence
        result = 31 * result + layerEnd
        result = 31 * result + hiddenState.contentHashCode()
        result = 31 * result + kvCacheKeys.contentHashCode()
        result = 31 * result + kvCacheValues.contentHashCode()
        result = 31 * result + finishedToken
        result = 31 * result + isFinished.hashCode()
        result = 31 * result + crc32.hashCode()
        return result
    }
}

/**
 * Serializes [ActivationPacket]. JSON path uses base64 for the byte
 * arrays. We avoid the Kotlinx-generated `data class` serializer
 * because it can't encode/decode FloatArray/ByteArray via JSON
 * natively — we round-trip through a tiny wire DTO.
 */
object ActivationPacketSerializer : KSerializer<ActivationPacket> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ActivationPacket")

    override fun serialize(encoder: Encoder, value: ActivationPacket) {
        val json = encoder as? kotlinx.serialization.json.JsonEncoder
            ?: error("ActivationPacket supports JSON only (got ${encoder::class})")
        val wire = Wire(
            packetVersion = value.packetVersion,
            stageIndex = value.stageIndex,
            tokenIdx = value.tokenIdx,
            positionInSequence = value.positionInSequence,
            layerEnd = value.layerEnd,
            hiddenStateBase64 = b64Enc.encodeToString(value.hiddenState.toLittleEndianBytes()),
            kvCacheKeysBase64 = b64Enc.encodeToString(value.kvCacheKeys),
            kvCacheValuesBase64 = b64Enc.encodeToString(value.kvCacheValues),
            finishedToken = value.finishedToken,
            isFinished = value.isFinished,
            crc32 = value.crc32,
        )
        json.encodeJsonElement(kotlinx.serialization.json.Json.encodeToJsonElement(Wire.serializer(), wire))
    }

    override fun deserialize(decoder: Decoder): ActivationPacket {
        val json = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: error("ActivationPacket supports JSON only (got ${decoder::class})")
        val wire = kotlinx.serialization.json.Json.decodeFromJsonElement(Wire.serializer(), json.decodeJsonElement())
        val hsBytes = decode(wire.hiddenStateBase64)
        return ActivationPacket(
            packetVersion = wire.packetVersion,
            stageIndex = wire.stageIndex,
            tokenIdx = wire.tokenIdx,
            positionInSequence = wire.positionInSequence,
            layerEnd = wire.layerEnd,
            hiddenState = hsBytes.toFloatArrayLittleEndian(),
            kvCacheKeys = decode(wire.kvCacheKeysBase64),
            kvCacheValues = decode(wire.kvCacheValuesBase64),
            finishedToken = wire.finishedToken,
            isFinished = wire.isFinished,
            crc32 = wire.crc32,
        )
    }

    @Serializable
    private data class Wire(
        val packetVersion: Int = 1,
        val stageIndex: Int,
        val tokenIdx: Long,
        val positionInSequence: Int,
        val layerEnd: Int,
        val hiddenStateBase64: String,
        val kvCacheKeysBase64: String,
        val kvCacheValuesBase64: String,
        val finishedToken: Int,
        val isFinished: Boolean,
        val crc32: Long,
    )

    private val b64Enc = java.util.Base64.getEncoder()
    private val b64Dec = java.util.Base64.getDecoder()

    private fun decode(s: String): ByteArray = b64Dec.decode(s)

    /** Little-endian IEEE-754 byte view of a FloatArray. */
    private fun FloatArray.toLittleEndianBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val bits = java.lang.Float.floatToRawIntBits(this[i])
            out[i * 4]     = bits.toByte()
            out[i * 4 + 1] = (bits ushr 8).toByte()
            out[i * 4 + 2] = (bits ushr 16).toByte()
            out[i * 4 + 3] = (bits ushr 24).toByte()
        }
        return out
    }

    private fun ByteArray.toFloatArrayLittleEndian(): FloatArray {
        require(size % 4 == 0) { "hiddenState base64 length must be a multiple of 4" }
        val out = FloatArray(size / 4)
        for (i in out.indices) {
            val b0 = this[i * 4].toInt() and 0xff
            val b1 = this[i * 4 + 1].toInt() and 0xff
            val b2 = this[i * 4 + 2].toInt() and 0xff
            val b3 = this[i * 4 + 3].toInt() and 0xff
            val bits = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            out[i] = java.lang.Float.intBitsToFloat(bits)
        }
        return out
    }
}