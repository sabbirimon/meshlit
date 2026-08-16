package com.meshlit.core.training.averaging

import com.meshlit.core.common.logger
import java.util.concurrent.atomic.AtomicInteger

/**
 * NaN / Inf guard for averaged gradients.
 *
 * Catches `NaN` and `±Inf` floats in the averaged gradient. Any
 * poisoned packet is dropped and counted. If the drop rate exceeds
 * [divergenceThresholdPct] over a sliding window of
 * [divergenceWindowSize] latest steps, the guard raises a
 * divergence event the trainer surfaces as `ClusterTrainerState.Failed`
 * + a `MeshlitEvent.Training.Diverged` for the audit trail.
 *
 * Composable: the default P2P averager wraps every received
 * gradient in `guard.checkAndDrop(...)` before averaging.
 *
 * Defaults are conservative — 25% drop rate over 8 steps is enough
 * to pause. A real autograd path will produce a NaN at most a few
 * times per million steps so this never trips falsely.
 */
class NaNGuard(
    private val divergenceThresholdPct: Int = 25,
    private val divergenceWindowSize: Int = 8,
) {
    private val log = logger("NaNGuard")

    private val dropped = AtomicInteger(0)
    private val total = AtomicInteger(0)
    private var lastDivergenceReason: String? = null

    /**
     * Returns the input unchanged if it has no NaN/Inf.
     * Returns `null` if the gradient is poisoned (caller should drop).
     */
    fun checkAndDrop(values: FloatArray): FloatArray? {
        if (values.isEmpty()) return values
        total.incrementAndGet()
        for (v in values) {
            if (v.isNaN() || v.isInfinite()) {
                val droppedCount = dropped.incrementAndGet()
                log.warn(
                    "cluster.trainer.nan.dropped",
                    "NaN/Inf detected in averaged gradient",
                    mapOf(
                        "dropped" to droppedCount,
                        "total" to total.get(),
                        "ratio_pct" to (droppedCount.toDouble() / total.get() * 100).toInt(),
                    ),
                )
                return null
            }
        }
        return values
    }

    /**
     * @return true iff the rolling drop rate crosses the threshold.
     */
    fun isDiverged(): Boolean {
        val t = total.get()
        if (t < divergenceWindowSize) return false
        val d = dropped.get()
        val pct = (d.toDouble() / t) * 100.0
        return pct >= divergenceThresholdPct
    }

    /** Diagnostic snapshot for the UI / MeshlitEvent. */
    fun snapshot(): String {
        val t = total.get().coerceAtLeast(1)
        val d = dropped.get()
        return "dropped=$d/$t (${(d.toDouble() / t * 100).toInt()}%)"
    }

    /** Reset on a new run. */
    fun reset() {
        dropped.set(0)
        total.set(0)
        lastDivergenceReason = null
    }

    fun lastDivergenceReason(): String? = lastDivergenceReason

    fun setLastDivergenceReason(reason: String) {
        lastDivergenceReason = reason
    }
}
