package com.meshlit.core.training

import com.meshlit.core.common.logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stub LoRA trainer used by the v0 synthetic averaging pipeline.
 *
 * The v0 ring-averaging path (`StrategyDispatcher` + `P2pRingAverager`
 * + `DiLoCoAverager` + `AccelerateDelegateAverager`) does not yet
 * have a real autograd back-end on the phone. Each step we ship a
 * deterministic FloatArray as the "local gradient" so the ring
 * averaging can be exercised end-to-end:
 *
 *  - The receiver averages the synthetic gradient with its own
 *    synthetic gradient via `averageTwo`.
 *  - The averaged result is fed back into [applyGradient] which
 *    increments a running counter and logs the magnitude. This
 *    lets the UI show "step 12 magnitude −0.42" without needing
 *    a real optimiser.
 *
 * When Meshlit graduates from v0 to v1 (real autograd), the only
 * change here is the body of [computeLocalGradient] + [applyGradient];
 * the public contract stays the same so the ring layer does not
 * need to be rewritten.
 *
 * Thread model: the trainer is bound to a single
 * `ClusterTrainerRegistry` instance and serialises step / apply
 * calls behind the mutex so the synthetic gradient never tears
 * between threads.
 */
class LocalLoraTrainer(
    private val paramCount: Int = 1024,
) {
    private val log = logger("LocalLoraTrainer")
    private val mutex = Mutex()

    private var stepCount: Long = 0L
    private var lastMagnitude: Float = 0f

    /**
     * Synthetic per-step gradient. The values are deterministic
     * in `step` and `seed` so two phones independently running
     * the same `step` produce identical gradients — which is what
     * makes the ring "average" convergence observable.
     */
    suspend fun computeLocalGradient(
        step: Long,
        loraRank: Int,
        seed: Long,
    ): FloatArray = mutex.withLock {
        // size mirrors the trainer's lora-rank param slice — the
        // real path uses `paramCount = (loraRank * 2 * hiddenDim)`.
        // We keep it small here so the v0 ring tests don't allocate
        // 50 MB per step.
        val size = maxOf(64, loraRank * 8)
        val out = FloatArray(size)
        var s = (seed xor step) and 0xFFFFFFFFL
        for (i in out.indices) {
            s = (s * 6364136223846793005L + 1442695040888963407L) and 0xFFFFFFFFL
            // map to [-0.5, 0.5]
            out[i] = ((s.toInt() and 0xFFFF) / 65535f) - 0.5f
        }
        out
    }

    /**
     * Apply the averaged gradient. In v0 this just records the
     * magnitude and increments the step counter — there's no
     * optimiser state to mutate. The signature stays the same so
     * the v1 back-end can drop in without touching the dispatcher.
     */
    suspend fun applyGradient(averaged: FloatArray): Unit = mutex.withLock {
        stepCount += 1
        lastMagnitude = if (averaged.isEmpty()) 0f else averaged.sum()
        log.info(
            "cluster.trainer.local.apply",
            "applied averaged gradient",
            mapOf(
                "step" to stepCount.toString(),
                "mag" to lastMagnitude.toString(),
                "size" to averaged.size.toString(),
            ),
        )
    }

    fun snapshot(): Snapshot = Snapshot(stepCount, lastMagnitude, paramCount)

    data class Snapshot(val stepCount: Long, val lastMagnitude: Float, val paramCount: Int)
}