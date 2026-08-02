package com.meshlit.inference

import com.meshlit.core.inference.net.MetricsSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide counters for inference traffic.
 *
 * Scope: in-memory only. Counters reset on process restart, which is
 * what users expect for a Grafana-style live dashboard — yesterday's
 * 200 failures are noise today.
 *
 * Threading:
 *  - The atomic counters are lock-free under contention; safe for the
 *    inference engine callback threads and the FGS.
 *  - The [snapshot] StateFlow is updated under a single `update` so
 *    readers always see a consistent view.
 *
 * Why a singleton and not passed around:
 *  - Counters span the inference engine (engine thread), the FGS
 *    (main thread), and the MiniRouter (coroutine). A singleton means
 *    no constructor fan-out.
 *  - The `MetricsScreen` reads from it via StateFlow — observers
 *    react automatically.
 *
 * Concurrency contract:
 *  - [recordJobStart] must be paired with [recordJobEnd]. We expose
 *    them as discrete events because the engine doesn't know the
 *    start time of a job; the router does.
 *  - [recordFailure] increments [failureTagCounts] for the given tag.
 *  - [recordTokens] accumulates into [totalTokensGenerated].
 */
class MetricsRegistry {

    /** Number of jobs currently waiting for a free coordinator slot. */
    private val _queueDepth = MutableStateFlow(0)
    val queueDepth: StateFlow<Int> = _queueDepth.asStateFlow()

    /** Cumulative jobs accepted since process start. */
    private val _totalJobs = AtomicLong(0)
    /** Cumulative jobs that finished successfully. */
    private val _successJobs = AtomicLong(0)
    /** Cumulative jobs that finished with a failure. */
    private val _failureJobs = AtomicLong(0)
    /** Total tokens streamed from `event: done` callbacks. */
    private val _totalTokens = AtomicLong(0)
    /** Weighted tokens/sec total — used to derive the rolling average. */
    private val _tokensPerSecondSum = AtomicLong(0)
    /** Number of times `recordTokens` was called with a non-zero rate. */
    private val _rateSamples = AtomicLong(0)

    /** Most recent 60 queue-depth readings, oldest first. Sampled at 1 Hz. */
    private val _sparkline = MutableStateFlow(List(SPARKLINE_BUCKETS) { 0 })
    val sparkline: StateFlow<List<Int>> = _sparkline.asStateFlow()

    private val _failureTagCounts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val failureTagCounts: StateFlow<Map<String, Long>> = _failureTagCounts.asStateFlow()

    /** When the process was started. Used for uptime in [snapshot]. */
    private val startedAtMs: Long = System.currentTimeMillis()

    /**
     * Mark the start of a new inference job. The router calls this
     * the moment it accepts a request from a peer. Returns a token
     * to pass to [recordJobEnd].
     */
    fun recordJobStart(): JobToken {
        _queueDepth.update { it + 1 }
        _totalJobs.incrementAndGet()
        return JobToken(System.currentTimeMillis())
    }

    /**
     * Mark the end of an inference job. Decrements the queue depth
     * and updates the success/failure counters based on [outcome].
     */
    fun recordJobEnd(token: JobToken, outcome: JobOutcome) {
        _queueDepth.update { it - 1 }
        when (outcome) {
            is JobOutcome.Success -> {
                _successJobs.incrementAndGet()
                _totalTokens.addAndGet(outcome.tokens.toLong())
                if (outcome.tokensPerSecond > 0f) {
                    // Store as integer millitokens/sec so AtomicLong works.
                    _tokensPerSecondSum.addAndGet((outcome.tokensPerSecond * 1000f).toLong())
                    _rateSamples.incrementAndGet()
                }
            }
            is JobOutcome.Failure -> {
                _failureJobs.incrementAndGet()
                _failureTagCounts.update { current ->
                    val next = HashMap(current)
                    next.merge(outcome.tag, 1L) { a, b -> a + b }
                    next
                }
            }
        }
        // Sample the queue depth into the sparkline. Cheap, lock-free
        // under contention because the list size is fixed.
        if (token.startedAtMs % SAMPLE_INTERVAL_MS == 0L) {
            pushSparkline(_queueDepth.value)
        }
    }

    /** Push [value] into the rolling sparkline ring buffer. */
    private fun pushSparkline(value: Int) {
        _sparkline.update { current ->
            val tail = current.drop(1) + value
            if (tail.size == SPARKLINE_BUCKETS) tail else current + value
        }
    }

    /**
     * Force the sparkline to advance regardless of the sample-interval
     * timing — useful from the periodic refresh so the chart isn't
     * stuck when traffic is light.
     */
    fun tickSparkline() {
        pushSparkline(_queueDepth.value)
    }

    /**
     * Snapshot of every counter. Cheap to call (no allocations).
     */
    fun snapshot(): MetricsSnapshot {
        val samples = _rateSamples.get()
        val avgRate = if (samples > 0) {
            _tokensPerSecondSum.get().toFloat() / 1000f / samples.toFloat()
        } else 0f
        val now = System.currentTimeMillis()
        return MetricsSnapshot(
            queueDepth = _queueDepth.value,
            totalJobs = _totalJobs.get(),
            successJobs = _successJobs.get(),
            failureTags = _failureTagCounts.value,
            totalTokensGenerated = _totalTokens.get(),
            avgTokensPerSecond = avgRate,
            uptimeSeconds = ((now - startedAtMs) / 1000L).coerceAtLeast(0L),
        )
    }

    /** Reset every counter. Exposed for tests; not used in production UI. */
    fun reset() {
        _queueDepth.value = 0
        _totalJobs.set(0)
        _successJobs.set(0)
        _failureJobs.set(0)
        _totalTokens.set(0)
        _tokensPerSecondSum.set(0)
        _rateSamples.set(0)
        _failureTagCounts.value = emptyMap()
        _sparkline.value = List(SPARKLINE_BUCKETS) { 0 }
    }

    /** Opaque token handed to [recordJobEnd]. Implements the
     *  [com.meshlit.core.inference.net.JobLifecycle.Token] interface
     *  so the server can hand it back to us without going through a
     *  wrapping layer. */
    data class JobToken(val startedAtMs: Long) : com.meshlit.core.inference.net.JobLifecycle.Token

    sealed class JobOutcome {
        data class Success(val tokens: Int, val tokensPerSecond: Float) : JobOutcome()
        data class Failure(val tag: String, val message: String) : JobOutcome()
    }

    companion object {
        /** 60 buckets × 1s = 60s of history. */
        const val SPARKLINE_BUCKETS = 60
        /** Sample the sparkline at most once per second per job. */
        private const val SAMPLE_INTERVAL_MS = 1000L
    }
}