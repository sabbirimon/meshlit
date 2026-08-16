package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.RingParticipant

/**
 * Strategy-agnostic gradient averaging contract. Every strategy
 * (P2P / DiLoCo / Accelerate) implements this. The
 * [com.meshlit.core.training.ClusterTrainer] selects an [Averager]
 * based on `DistributedConfig.strategy` and delegates the per-step
 * gradient averaging to it.
 *
 * Each strategy MAY use the same `GradRingPacket` wire envelope
 * (P2P and DiLoCo do; Accelerate does not — it delegates to the
 * desktop peer and becomes an OBSERVER).
 *
 * Implementations are independent coroutines — the trainer awaits
 * [average] once per step. The result is either an averaged
 * [FloatArray] or a typed failure that the trainer surfaces via
 * `ClusterTrainerState.Failed`.
 */
interface Averager {
    /** Stable identifier used in MeshlitEvent telemetry. */
    val kind: AveragerKind

    /**
     * Average [localGradient] with the rest of the ring's
     * contributions for [step]. The strategy may or may not need
     * [participants] (Accelerate ignores it; ring strategies use it
     * to compute the successor / predecessor).
     */
    suspend fun average(
        step: Long,
        localGradient: FloatArray,
        participants: List<RingParticipant>,
        cfg: DistributedConfig,
        localPeerId: String,
        localHost: String,
        localPort: Int,
    ): MeshlitResult<AveragedGradient>
}

/**
 * Output of an [Averager.average] call. Carries the averaged gradient
 * plus diagnostic data the UI can render (loss placeholder, magnitude,
 * source strategy, dropped-packet count).
 */
data class AveragedGradient(
    val step: Long,
    val values: FloatArray,
    val sourceKind: AveragerKind,
    /** Synthetic placeholder the UI displays as "loss" until real
     *  autograd lands. Mirrors the field that ClusterTrainer already
     *  populates. */
    val loss: Float,
    /** How many NaN/Inf packets the guards dropped this step.
     *  Zero for healthy runs; non-zero triggers the divergence
     *  threshold in NaNGuard. */
    val droppedPackets: Int = 0,
) {
    /** Two AveragedGradients are equal iff every field matches.
     *  FloatArray's default equals does the right thing here. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AveragedGradient) return false
        if (step != other.step) return false
        if (sourceKind != other.sourceKind) return false
        if (loss != other.loss) return false
        if (droppedPackets != other.droppedPackets) return false
        if (!values.contentEquals(other.values)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = step.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + sourceKind.hashCode()
        result = 31 * result + loss.hashCode()
        result = 31 * result + droppedPackets
        return result
    }
}

/**
 * Closed set of strategies. Wire format accepts unknown enum values
 * as `UNKNOWN` so a future strategy added on one peer doesn't break
 * older peers — they just see UNKNOWN and skip.
 */
enum class AveragerKind { P2P_RING, DILOCO, ACCELERATE, UNKNOWN }
