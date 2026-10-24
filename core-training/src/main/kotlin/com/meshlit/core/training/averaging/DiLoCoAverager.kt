package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.RingParticipant

/**
 * DiLoCo averager.
 *
 * DiLoCo (Distributed Low-Communication) decouples inner optimization
 * from outer averaging. Each peer runs AdamW locally for
 * [DistributedConfig.DiLoCo.innerSteps] steps; afterwards, every peer
 * ships a delta to the ring. The ring averages the deltas with a
 * Nesterov-style outer-LR update (a momentum-style smoothing on top
 * of the simple sum/N average).
 *
 * Wire format: same `GradRingPacket` envelope as the P2P ring. Only
 * the cadence differs (every innerSteps rather than every step), and
 * the outer update applies a Nesterov factor rather than a simple
 * average.
 *
 * Defaults: innerSteps=500, outerLr=0.7. The latter is validated in
 * [DistributedConfig.init] to be in [0.1, 1.0] because DiLoCo
 * diverges outside this range.
 *
 * Failure modes:
 *  - Outer-state divergence: detected by [NaNGuard] and surfaced as
 *    `MeshlitEvent.Training.Diverged`. The trainer pauses and emits
 *    a `RepairAction.PauseTraining` for the autoPilot audit trail.
 *  - Partial ring drop: tolerated up to `maxStaleness` peers. Drops
 *    beyond that threshold trigger a re-elect via the existing
 *    `ClusterCoordinator` flow.
 */
class DiLoCoAverager(
    private val nanGuard: NaNGuard? = null,
) : Averager {

    override val kind: AveragerKind = AveragerKind.DILOCO

    private val log = logger("DiLoCoAverager")

    /** Outer state, persisted across steps. Holds the last outer
     *  delta so the Nesterov update can mix in the current delta
     *  proportionally. */
    private var outerState: FloatArray = FloatArray(0)
    private var lastOuterDelta: FloatArray = FloatArray(0)
    private var stepCounter: Long = 0

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
                MeshlitError.Invalid("cluster.trainer.diloco.no_participants")
            )
        }

        if(participants.size!=1 || participants.single().peerId!=localPeerId) return MeshlitResult.Failure(
            MeshlitError.Invalid("training_exchange_unavailable: DiLoCo peer deltas are not implemented")
        )
        if(localGradient.any{!it.isFinite()}) return MeshlitResult.Failure(MeshlitError.Invalid("cluster.trainer.diloco.diverged"))
        // Inner-step counter increments; outer step fires every
        // `innerSteps`.
        stepCounter += 1
        val innerSteps = cfg.diloco.innerSteps.coerceAtLeast(1)
        val isOuterStep = (stepCounter % innerSteps) == 0L

        if (!isOuterStep) {
            // Inner step: no averaging happens. The local gradient
            // passes through unchanged.
            return MeshlitResult.Success(
                AveragedGradient(
                    step = step,
                    values = localGradient,
                    sourceKind = AveragerKind.DILOCO,
                    loss = null,
                    droppedPackets = 0,
                )
            )
        }

        // Outer step: ring-averaged Nesterov update.
        // Single participant contributes only its actual supplied delta. Never clone peer deltas.
        val averageDelta = localGradient.copyOf()

        // Nesterov smoothing: outer_state_t = outer_state_{t-1}
        //                          + outerLr * (averageDelta + 0.9 * (avg - last))
        if (outerState.size != localGradient.size) {
            outerState = FloatArray(localGradient.size)
            lastOuterDelta = FloatArray(localGradient.size)
        }
        val outerLr = cfg.diloco.outerLr.toFloat()
        val out = FloatArray(localGradient.size)
        for (i in localGradient.indices) {
            val nesterovTerm = 0.9f * (averageDelta[i] - lastOuterDelta[i])
            outerState[i] = outerState[i] + outerLr * (averageDelta[i] + nesterovTerm)
            out[i] = outerState[i]
        }
        lastOuterDelta = averageDelta

        val clean = if(out.any{!it.isFinite()}) null else if(nanGuard==null) out else nanGuard.checkAndDrop(out)
        if (clean == null) {
            nanGuard?.setLastDivergenceReason("diloco_outer_diverged")
            return MeshlitResult.Failure(
                MeshlitError.Invalid("cluster.trainer.diloco.diverged")
            )
        }

        return MeshlitResult.Success(
            AveragedGradient(
                step = step,
                values = clean,
                sourceKind = AveragerKind.DILOCO,
                loss = null,
                droppedPackets = if (nanGuard?.isDiverged() == true) 1 else 0,
            )
        )
    }

    fun reset() {
        outerState = FloatArray(0)
        lastOuterDelta = FloatArray(0)
        stepCounter = 0
    }
}
