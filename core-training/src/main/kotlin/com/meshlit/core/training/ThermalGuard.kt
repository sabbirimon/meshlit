package com.meshlit.core.training

import com.meshlit.core.common.logger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thermal guard. Default-enabled.
 *
 * Subscribes to the same `core-power` `PowerSnapshot` stream that the
 * AutoPilot (R-15) consumes. When the device's CPU temperature
 * crosses `THROTTLE_RATIO * maxTempC` the local step rate is halved.
 * When it crosses `PAUSE_RATIO * maxTempC` the run is paused.
 *
 * The throttle / pause decision is exposed as a `stepRateFactor` so
 * the trainer can apply it (no global state mutation). The values
 * are conservative — phones throttle hard at 80% of TJmax, so we
 * back off before the OS does it for us.
 *
 * Wire: emits status via the existing `MeshlitEvent` surface so the
 * audit trail (R-22) records every throttle / pause.
 */
class ThermalGuard(
    private val enabled: Boolean = true,
    private val maxTempCSupplier: () -> Float? = { null },
    private val currentTempCSupplier: () -> Float? = { null },
    private val onEvent: (ThermalEvent) -> Unit = {},
) {
    private val log = logger("ThermalGuard")

    private val lastThrottleAtMs = AtomicLong(0L)

    /**
     * Returns the rate factor to apply to the next step. 1.0 = normally,
     * 0.5 = halve, 0.0 = pause.
     */
    fun stepRateFactor(): Float {
        if (!enabled) return 1.0f
        val maxT = maxTempCSupplier() ?: return 1.0f
        val curT = currentTempCSupplier() ?: return 1.0f
        val ratio = curT / maxT
        return when {
            ratio >= PAUSE_RATIO -> {
                onEvent(ThermalEvent.Paused(curT, maxT))
                0.0f
            }
            ratio >= THROTTLE_RATIO -> {
                onEvent(ThermalEvent.Throttled(curT, maxT))
                lastThrottleAtMs.set(System.currentTimeMillis())
                0.5f
            }
            else -> 1.0f
        }
    }

    /** True iff the device is currently throttled. */
    fun isThrottling(): Boolean {
        if (!enabled) return false
        val maxT = maxTempCSupplier() ?: return false
        val curT = currentTempCSupplier() ?: return false
        return (curT / maxT) >= THROTTLE_RATIO
    }

    /** True iff the device is currently paused due to thermal. */
    fun isPaused(): Boolean {
        if (!enabled) return false
        val maxT = maxTempCSupplier() ?: return false
        val curT = currentTempCSupplier() ?: return false
        return (curT / maxT) >= PAUSE_RATIO
    }

    sealed class ThermalEvent {
        data class Throttled(val currentC: Float, val maxC: Float) : ThermalEvent()
        data class Paused(val currentC: Float, val maxC: Float) : ThermalEvent()
    }

    companion object {
        /** Phone throttles hard at ~80% of TJmax. We back off slightly earlier. */
        const val THROTTLE_RATIO: Float = 0.75f
        const val PAUSE_RATIO: Float = 0.90f
    }
}
