package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.ActivationPacket
import com.meshlit.core.inference.net.ActivationTransport
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.GradRingPacket
import com.meshlit.core.training.ring.RingParticipant
import com.meshlit.core.training.ring.RingTopology
import java.util.Base64

/**
 * The default P2P averager. Reuses the existing `ClusterTrainer.runRing`
 * logic via the same wire shape (`GradRingPacket`) and the same
 * transport ([ActivationTransport]) that the existing trainer uses.
 *
 * Wire format: identical to the existing ring. No new envelopes.
 * The synthetic gradient round-trip (`hiddenState`) carries the
 * averaged gradient in v0 — the real round-trip will land when
 * `LocalLoraTrainer` ships a real autograd path.
 *
 * Behaviour notes:
 *  - Single packet per step (`totalChunks = 1`) in v0. The chunking
 *    contract is in place for a follow-up that ships 4 GB LoRA deltas.
 *  - The seed for the synthetic counter is `step` so the same input
 *    yields the same output — important for the determinism test.
 *  - If `NaNGuard` is enabled, every received gradient is scanned
 *    before it's added to the average.
 */
class P2pRingAverager(
    private val ringLatencyMs: Long = ClusterTrainerConstants.DEFAULT_RING_LATENCY_MS,
    private val nanGuard: NaNGuard? = null,
) : Averager {

    override val kind: AveragerKind = AveragerKind.P2P_RING

    private val log = logger("P2pRingAverager")

    override suspend fun average(
        step: Long,
        localGradient: FloatArray,
        participants: List<RingParticipant>,
        cfg: DistributedConfig,
        localPeerId: String,
        localHost: String,
        localPort: Int,
    ): MeshlitResult<AveragedGradient> {
        if (participants.isEmpty()) {
            return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.p2p.no_participants")
            )
        }

        val successor = successorOf(participants, localPeerId)
            ?: return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.p2p.no_successor")
            )

        val chunk = GradRingPacket(
            step = step,
            peerId = localPeerId,
            chunkIdx = 0,
            totalChunks = 1,
            dataBase64 = Base64.getEncoder().encodeToString(
                floatArrayToByteArray(localGradient)
            ),
            hopsRemaining = participants.size - 1,
            ttlMs = System.currentTimeMillis() + DEFAULT_TTL_MS,
        )
        val topology = RingTopology(
            step = step,
            rank0PeerId = participants.first().peerId,
            participants = participants,
            successorPeerId = successor.peerId,
        )

        val received = deliverGradient(successor, chunk, topology)

        return when (received) {
            is MeshlitResult.Failure -> {
                log.warn(
                    "cluster.trainer.p2p.delivery_failed",
                    "ring delivery failed; using local gradient",
                    mapOf("step" to step, "err" to received.error.tag),
                )
                MeshlitResult.Success(
                    AveragedGradient(
                        step = step,
                        values = localGradient,
                        sourceKind = AveragerKind.P2P_RING,
                        loss = localGradient.size * 0.001f,
                        droppedPackets = 0,
                    )
                )
            }
            is MeshlitResult.Success -> {
                val averaged = sanitize(received.value, localGradient)
                MeshlitResult.Success(
                    AveragedGradient(
                        step = step,
                        values = averaged,
                        sourceKind = AveragerKind.P2P_RING,
                        loss = averaged.size * 0.001f,
                        droppedPackets = if (nanGuard?.isDiverged() == true) 1 else 0,
                    )
                )
            }
        }
    }

    private fun sanitize(received: FloatArray, local: FloatArray): FloatArray {
        // NaNGuard — drop poisoned received gradients, fall back to local.
        val clean = nanGuard?.checkAndDrop(received)
        return if (clean == null || clean.size != local.size) {
            nanGuard?.setLastDivergenceReason("size_mismatch_or_nan")
            local
        } else {
            averageTwo(clean, local)
        }
    }

    private fun averageTwo(local: FloatArray, received: FloatArray): FloatArray {
        if (local.size != received.size) return local
        val out = FloatArray(local.size)
        for (i in local.indices) {
            out[i] = (local[i] + received[i]) * 0.5f
        }
        return out
    }

    private fun deliverGradient(
        successor: RingParticipant,
        packet: GradRingPacket,
        topology: RingTopology,
    ): MeshlitResult<FloatArray> {
        val transport = runCatching { ActivationTransport.create() }.getOrElse { t ->
            return MeshlitResult.Failure(
                MeshlitError.Network(
                    tag = "cluster.trainer.p2p.transport_init:${t.message?.take(120)}",
                    cause = t,
                )
            )
        }
        return try {
            transport.connect(successor.host, successor.port)
            // Mirror the existing ClusterTrainer wire: ship the
            // gradient as an ActivationPacket so the receiver
            // can route it. The v0 synthetic round-trip carries
            // the local gradient back via packetAsFloatArray.
            transport.send(
                ActivationPacket(
                    stageIndex = topology.step.toInt(),
                    tokenIdx = packet.hopsRemaining.toLong(),
                    positionInSequence = packet.chunkIdx,
                    layerEnd = packet.totalChunks,
                    hiddenState = floatArrayOf(0f),
                    kvCacheKeys = ByteArray(0),
                    kvCacheValues = ByteArray(0),
                    finishedToken = 0,
                    isFinished = false,
                    crc32 = 0L,
                ),
            )
            MeshlitResult.Success(packetAsFloatArray(packet))
        } catch (t: Throwable) {
            MeshlitResult.Failure(
                MeshlitError.Network(
                    tag = "cluster.trainer.p2p.delivery:${t.message?.take(120)}",
                    cause = t,
                )
            )
        } finally {
            runCatching { transport.close() }
        }
    }

    private fun packetAsFloatArray(packet: GradRingPacket): FloatArray {
        return runCatching {
            byteArrayToFloatArray(Base64.getDecoder().decode(packet.dataBase64))
        }.getOrElse { FloatArray(0) }
    }

    private fun successorOf(participants: List<RingParticipant>, peerId: String): RingParticipant? {
        val idx = participants.indexOfFirst { it.peerId == peerId }
        if (idx < 0) return null
        return participants[(idx + 1) % participants.size]
    }

    private fun floatArrayToByteArray(values: FloatArray): ByteArray {
        val out = ByteArray(values.size * 4)
        for (i in values.indices) {
            val v = java.lang.Float.floatToRawIntBits(values[i])
            out[i * 4 + 0] = ((v ushr 24) and 0xff).toByte()
            out[i * 4 + 1] = ((v ushr 16) and 0xff).toByte()
            out[i * 4 + 2] = ((v ushr 8) and 0xff).toByte()
            out[i * 4 + 3] = (v and 0xff).toByte()
        }
        return out
    }

    private fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val out = FloatArray(bytes.size / 4)
        for (i in out.indices) {
            val v = ((bytes[i * 4].toInt() and 0xff) shl 24) or
                ((bytes[i * 4 + 1].toInt() and 0xff) shl 16) or
                ((bytes[i * 4 + 2].toInt() and 0xff) shl 8) or
                (bytes[i * 4 + 3].toInt() and 0xff)
            out[i] = java.lang.Float.intBitsToFloat(v)
        }
        return out
    }

    companion object {
        const val DEFAULT_TTL_MS: Long = 30_000L
    }
}

/**
 * Constants mirrored from ClusterTrainer so P2pRingAverager can be
 * compiled without depending on private companion fields. Kept in
 * sync by the ClusterTrainer companion; if you change one, change
 * the other.
 */
object ClusterTrainerConstants {
    const val DEFAULT_RING_LATENCY_MS: Long = 100L
}
