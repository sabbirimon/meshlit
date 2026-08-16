package com.meshlit.inference

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local load signal exposed on `/v1/health` and consulted by
 * [PeerLoadScorer] when picking a peer to forward to.
 *
 * The tracker is incremented on `coordinator.infer` start and
 * decremented on finish (or fail). The queue depth is the count of
 * callers currently waiting on the coordinator's infer mutex —
 * typically 0-1 on a single-actor Meshlit cluster.
 *
 * Backed by a [MutableStateFlow] so the router can subscribe and
 * recompute scores whenever the load changes. The flow also feeds
 * the [HealthEnricher] that publishes the load on `/v1/health`.
 *
 * Threading: every method is a single atomic read-then-write on
 * the [MutableStateFlow], so concurrent calls from the FGS scope
 * are safe. The counters are clamped at zero so a missed
 * `finish()` cannot lead to a negative counter that would
 * artificially lower the load-signal penalty.
 *
 * Lifecycle: the tracker is constructed once in `FGS.onCreate` and
 * survives the lifetime of the service. Counter drift across
 * process restarts is irrelevant (the process starts at zero).
 */
class LocalLoadTracker {

    private val _state = MutableStateFlow(LocalLoad())
    val state: StateFlow<LocalLoad> = _state.asStateFlow()

    /**
     * Mark an inference as started. Increments
     * [LocalLoad.activeInferences]. Idempotent under coroutines: each
     * `start()` must be paired with exactly one `finish()` or
     * `fail(reason)`.
     */
    fun start() {
        _state.value = _state.value.copy(
            activeInferences = _state.value.activeInferences + 1,
        )
    }

    /**
     * Mark an inference as successfully finished. Decrements
     * [LocalLoad.activeInferences] (clamped at zero) and increments
     * [LocalLoad.successCount]. Call this from the engine's success
     * path inside a `finally` block so a thrown exception still
     * cleans up the counter via [fail].
     */
    fun finish() {
        _state.value = _state.value.copy(
            activeInferences = (_state.value.activeInferences - 1).coerceAtLeast(0),
            successCount = _state.value.successCount + 1,
        )
    }

    /**
     * Mark an inference as failed. Decrements
     * [LocalLoad.activeInferences] (clamped at zero) and increments
     * [LocalLoad.failureCount]. The reason is currently not stored on
     * the load signal — the engine's own failure-tags counter in
     * [MetricsRegistry] is the canonical source.
     */
    fun fail(reason: String) {
        _state.value = _state.value.copy(
            activeInferences = (_state.value.activeInferences - 1).coerceAtLeast(0),
            failureCount = _state.value.failureCount + 1,
        )
    }

    /**
     * Mark a caller as enqueued on the coordinator's infer mutex.
     * Called by the FGS right before suspending on the mutex.
     */
    fun enqueue() {
        _state.value = _state.value.copy(
            queueDepth = _state.value.queueDepth + 1,
        )
    }

    /**
     * Mark a previously-enqueued caller as dequeued. Counter is
     * clamped at zero so a missed `enqueue()` cannot lead to a
     * negative count.
     */
    fun dequeue() {
        _state.value = _state.value.copy(
            queueDepth = (_state.value.queueDepth - 1).coerceAtLeast(0),
        )
    }

    /**
     * Snapshot of the local load. Atomic via [MutableStateFlow] —
     * the FGS publishes this on `/v1/health` so peers can route
     * load away from a busy node.
     */
    data class LocalLoad(
        val activeInferences: Int = 0,
        val queueDepth: Int = 0,
        val successCount: Long = 0L,
        val failureCount: Long = 0L,
    )
}
