package com.meshlit.core.training.averaging

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.training.LocalLoraTrainer
import com.meshlit.core.training.ThermalGuard
import com.meshlit.core.training.config.DistributedConfig
import com.meshlit.core.training.ring.RingParticipant

/**
 * The strategy dispatcher. Picks the right [Averager] for the run
 * based on `DistributedConfig.strategy`, and unifies the per-step
 * flow:
 *
 *  1. Apply the [ThermalGuard] step-rate factor.
 *  2. Compute the local gradient via [LocalLoraTrainer].
 *  3. Delegate to the chosen [Averager].
 *  4. Run the result through [NaNGuard].
 *  5. Apply the averaged gradient via the local trainer.
 *
 * This is the single seam between the existing `ClusterTrainer`
 * and the new strategy layer. The dispatcher changes
 * `ClusterTrainer.launch` to call `dispatcher.step(...)` instead
 * of doing the ring logic inline.
 *
 * Important: the dispatcher is stateless across calls — the averager
 * may carry state (DiLoCoAverager's outerState), but the dispatcher
 * holds only the references to the injected collaborators.
 */
class StrategyDispatcher(
    private val localLoraTrainer: LocalLoraTrainer,
    private val thermalGuard: ThermalGuard,
    private val nanGuard: NaNGuard,
    private val averager: Averager,
) {
    private val log = logger("StrategyDispatcher")

    /**
     * Run a single training step. Returns the averaged gradient
     * (or a typed failure). The trainer calls this once per step.
     */
    suspend fun step(
        step: Long,
        cfg: DistributedConfig,
        participants: List<RingParticipant>,
        localPeerId: String,
        localHost: String,
        localPort: Int,
    ): MeshlitResult<AveragedGradient> {
        // 1. Thermal guard.
        val rateFactor = thermalGuard.stepRateFactor()
        if (rateFactor <= 0.0f) {
            return MeshlitResult.Failure(
                MeshlitError.Resource("cluster.trainer.thermal.paused")
            )
        }

        // 2. Local gradient (synthetic in v0).
        val localGradient = localLoraTrainer.computeLocalGradient(
            step = step,
            loraRank = cfg.sharding.keepLastN,  // placeholder; real path uses config.loraRank
            seed = step,
        )

        // 3. Delegate to the averager.
        val averaged = averager.average(
            step = step,
            localGradient = localGradient,
            participants = participants,
            cfg = cfg,
            localPeerId = localPeerId,
            localHost = localHost,
            localPort = localPort,
        )

        // 4. NaN guard.
        if (averaged is MeshlitResult.Success) {
            val guarded = nanGuard.checkAndDrop(averaged.value.values)
            if (guarded == null) {
                nanGuard.setLastDivergenceReason("dispatcher.step.received_nan")
                log.warn(
                    "cluster.trainer.dispatcher.nan",
                    "averager returned NaN/Inf gradient",
                    mapOf("step" to step),
                )
                return MeshlitResult.Failure(
                    MeshlitError.Invalid("cluster.trainer.dispatcher.nan:${nanGuard.snapshot()}")
                )
            }
            // 5. Apply.
            localLoraTrainer.applyGradient(guarded)
            return MeshlitResult.Success(
                averaged.value.copy(values = guarded)
            )
        }

        return averaged
    }

    fun averagerKind(): AveragerKind = averager.kind
}
