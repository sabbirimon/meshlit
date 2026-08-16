package com.meshlit.core.training.ring

import kotlinx.serialization.Serializable
import java.util.Base64

/**
 * One ring participant in a Swarm-Peers / DiLoCo / Accelerate
 * gradient-averaging topology. The same envelope is used across
 * strategies so the trainer can swap the [Averager] without
 * rewriting the wire layer.
 *
 * @property peerId stable Meshlit node id (`meshlit-<hostOrIp>-<port>`).
 * @property host bindable host (IPv4 literal or hostname).
 * @property port port the Swarm-Peers / DiLoCo peer is listening on.
 * @property role where this peer sits in the ring. The default
 *   topology is a single clockwise ring; DiLoCo adds an "outer"
 *   role that points at the desktop peer.
 * @property shardKeys which model shards this peer holds. The
 *   trainer uses the intersection of all participants' shard keys
 *   to pick the gradient slice to average.
 */
@Serializable
data class RingParticipant(
    val peerId: String,
    val host: String,
    val port: Int,
    val role: RingRole = RingRole.INNER,
    val shardKeys: List<String> = emptyList(),
)

/**
 * Closed set of roles in a ring topology. `OBSERVER` is what a
 * phone assumes when it can compute gradients but cannot complete
 * a ring hop (e.g. it has no Swarm-Peers transport available) —
 * it contributes its local gradient but does not forward.
 */
@Serializable
enum class RingRole { INNER, OUTER, OBSERVER }

/**
 * Per-step gradient packet that rides over the Swarm-Peers /
 * DiLoCo UDP transport. The wire format is a chunked,
 * base64-encoded byte array so receivers under 64 KB MTU can
 * still rebuild multi-megabyte gradient slices without truncation.
 *
 * The trainer's monotonic `step` counter lets receivers drop
 * late packets; `hopsRemaining` is the ring's TTL so a packet
 * can't loop forever after a participant drops out.
 *
 * @property peerId originating peer (NOT the immediate predecessor
 *   — this is the chain root, so MeshlitEvent telemetry can
 *   attribute the contribution).
 * @property chunkIdx / [totalChunks] rebuilds one gradient slice
 *   when the float array is too big for a single UDP datagram.
 * @property dataBase64 base64-encoded raw bytes (little-endian
 *   IEEE 754 float32).
 * @property hopsRemaining decremented at every hop. When it
 *   reaches zero the receiver stops forwarding and finalises.
 * @property ttlMs wall-clock deadline so a stalled ring cannot
 *   leak packets.
 */
@Serializable
data class GradRingPacket(
    val step: Long,
    val peerId: String,
    val chunkIdx: Int = 0,
    val totalChunks: Int = 1,
    val dataBase64: String = "",
    val hopsRemaining: Int = 1,
    val ttlMs: Long = 0L,
    val sourcePeerId: String = peerId,
    val shardKey: String? = null,
) {
    fun values(): FloatArray {
        if (dataBase64.isEmpty()) return FloatArray(0)
        val bytes = runCatching { Base64.getDecoder().decode(dataBase64) }.getOrNull()
            ?: return FloatArray(0)
        if (bytes.size % 4 != 0) return FloatArray(0)
        val out = FloatArray(bytes.size / 4)
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (i in out.indices) out[i] = bb.getFloat(i * 4)
        return out
    }
}

/**
 * Snapshot of the ring at a given trainer step. The
 * [P2pRingAverager] rebuilds one of these on every `average()`
 * call so it can fall back to a stale topology when a peer
 * drops mid-step without leaking the dropped peer's contribution.
 */
@Serializable
data class RingTopology(
    val step: Long,
    val rank0PeerId: String,
    val participants: List<RingParticipant>,
    val successorPeerId: String,
) {
    fun successor(peerId: String): RingParticipant? =
        participants.firstOrNull { it.peerId == successorPeerId }
            ?: participants.firstOrNull { it.peerId == peerId } // self-loop fallback
}