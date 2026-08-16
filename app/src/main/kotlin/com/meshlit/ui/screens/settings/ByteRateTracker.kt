package com.meshlit.ui.screens.settings

import android.os.SystemClock

/**
 * Lightweight moving-average byte-rate tracker for download rows.
 *
 * The download coroutine calls [update] every progress tick with
 * the cumulative MB (or any monotonically increasing integer)
 * reported so far. The tracker remembers the last few samples and
 * exposes the smoothed bytes-per-second via [bytesPerSecond].
 *
 * The moving-average window is intentionally tiny (3 samples) so
 * the rate display reacts within ~1 s of the user's tap and then
 * settles quickly. The unit tests for this class live next to it.
 */
class ByteRateTracker {
    private data class Sample(val cumulative: Long, val elapsedMs: Long)

    /** Monotonic clock — survives wall-clock changes. */
    private val startMs: Long = SystemClock.elapsedRealtime()

    private val samples: ArrayDeque<Sample> = ArrayDeque()

    /**
     * Record a new sample. `cumulative` is the running total of
     * bytes (or MB) reported by the upstream downloader. Negative
     * values are clamped to zero so a buggy source can't poison
     * the average.
     */
    fun update(cumulative: Long) {
        val safe = if (cumulative < 0L) 0L else cumulative
        samples.addLast(Sample(safe, SystemClock.elapsedRealtime() - startMs))
        // Keep the trailing window small — three samples is enough
        // for the UI to feel "live" without flickering on every
        // progress event.
        while (samples.size > 3) samples.removeFirst()
    }

    /**
     * Smoothed bytes-per-second over the tracked window. Returns
     * 0 when there's only one sample (no delta yet) or when the
     * window is empty.
     */
    fun bytesPerSecond(): Double {
        if (samples.size < 2) return 0.0
        val first = samples.first()
        val last = samples.last()
        val deltaBytes = last.cumulative - first.cumulative
        val deltaMs = last.elapsedMs - first.elapsedMs
        if (deltaMs <= 0L) return 0.0
        // Caller passes either raw bytes (1.0 multiplier) or MB
        // (1_048_576 multiplier). We treat the unit as bytes
        // (multiplier=1) and let the caller pre-multiply.
        return deltaBytes.toDouble() * 1000.0 / deltaMs.toDouble()
    }
}
