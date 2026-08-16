package com.meshlit.core.cluster

import com.meshlit.core.common.logger
import com.meshlit.core.discovery.beacon.BeaconEmitter
import com.meshlit.core.discovery.beacon.BeaconEnvelope
import com.meshlit.core.discovery.beacon.ResourceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * **Phase Hivemind-1 — KubeScheduler**
 *
 * A Kubernetes-style control loop that runs every [tickIntervalMs]
 * and re-evaluates which peer in the cluster should be the Host.
 * Sits on top of [ClusterCoordinator] (which provides the
 * membership state and deterministic election algorithm); the
 * scheduler contributes the multi-dimensional scoring + hysteresis
 * + handover protocol.
 *
 * Lifecycle:
 *  1. [start] launches a coroutine that ticks every 10 s.
 *  2. Each tick reads the latest [com.meshlit.core.discovery.beacon.ResourceSnapshot]
 *     + [com.meshlit.core.inference.cluster.PeerCapabilities] for
 *     every peer and calls [KubeScoring.scoreAll].
 *  3. If the highest-scoring peer differs from the current
 *     [ClusterState.hostOfRecord] by more than [KubeScoring.YIELD_THRESHOLD],
 *     [requestHandover] is called.
 *  4. [requestHandover] emits a [BeaconEnvelope.Takeover] via
 *     [beacon], then waits up to 5 s for a [BeaconEnvelope.YieldAck]
 *     via [onYieldAck]. On accept → current host steps down, new
 *     host steps up. On reject → stay.
 *
 * Hot-swap hooks:
 *  - [ClusterCoordinator.events] carries [ClusterEvent.PeerAdded]
 *    and [ClusterEvent.PeerRemoved]. The scheduler subscribes via
 *    [observeClusterEvents] so a peer churn triggers an immediate
 *    tick instead of waiting for the next 10 s slot.
 *  - [forceYield] is exposed so the UI's "Re-elect now" button
 *    bypasses the hysteresis gate.
 *
 * Future-proofing: the [peerCapabilityProvider] /
 * [peerHealthProvider] callbacks accept any source, so when the
 * Mac/Windows/Linux apps ship, they plug in via the same peer
 * registry without scheduler changes.
 */
class KubeScheduler(
    private val scope: CoroutineScope,
    private val coordinator: ClusterCoordinator,
    private val beacon: BeaconEmitter,
    /** Self-snapshot provider (battery, thermal, charging, cores). */
    private val selfSnapshotProvider: () -> ResourceSnapshot,
    /** Returns the per-peer [KubeScoring.Inputs] keyed by nodeId. */
    private val peerInputsProvider: () -> Map<String, KubeScoring.Inputs>,
    private val tickIntervalMs: Long = 10_000L,
    private val handoverAckTimeoutMs: Long = 5_000L,
    private val handoverRetryCount: Int = 1,
) {

    private val log = logger("KubeScheduler")

    private val _state = MutableStateFlow(KubeState())
    val state: StateFlow<KubeState> = _state.asStateFlow()

    /** Handover requests that are in flight. Each entry is removed
     *  when the corresponding [BeaconEnvelope.YieldAck] arrives or
     *  the timeout fires. */
    private val _handoverEvents = MutableSharedFlow<HandoverEvent>(extraBufferCapacity = 32)
    val handoverEvents: SharedFlow<HandoverEvent> = _handoverEvents.asSharedFlow()

    /** Mutable handover map: `handoffToken → HandoverRequest`. */
    private val pendingHandover = HashMap<String, HandoverRequest>()
    private var tickJob: Job? = null
    private var observeJob: Job? = null

    data class KubeState(
        val hostOfRecord: String? = null,
        val hostScore: Double = 0.0,
        val eligibleHosts: List<KubeScoring.ScoreBreakdown> = emptyList(),
        val workerCount: Int = 0,
        val lastTickMs: Long = 0L,
        val lastHandoverMs: Long = 0L,
        val pendingHandover: HandoverRequest? = null,
        val selfScore: KubeScoring.ScoreBreakdown? = null,
    )

    data class HandoverRequest(
        val fromNodeId: String,
        val toNodeId: String,
        val scoreDelta: Double,
        val requestedAtMs: Long,
        val handoffToken: String,
        val attempt: Int = 0,
    )

    sealed interface HandoverEvent {
        val request: HandoverRequest

        data class Requested(override val request: HandoverRequest) : HandoverEvent
        data class Acked(override val request: HandoverRequest, val accepted: Boolean, val errorCode: String?) : HandoverEvent
        data class TimedOut(override val request: HandoverRequest) : HandoverEvent
    }

    fun start() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                runCatching { tick() }
                    .onFailure {
                        log.warn("kube.tick.fail", "tick threw", mapOf("err" to (it.message ?: "")))
                    }
                delay(tickIntervalMs)
            }
        }
        observeJob = scope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    is ClusterEvent.PeerAdded -> {
                        log.info("kube.peer.added", "churn trigger", mapOf(
                            "nodeId" to event.nodeId,
                            "score" to event.initialScore,
                        ))
                        // Trigger immediate tick so the new peer is
                        // scored within the next 100 ms instead of
                        // waiting for the next tick slot.
                        tick()
                    }
                    is ClusterEvent.PeerRemoved -> {
                        log.info("kube.peer.removed", "churn trigger", mapOf(
                            "nodeId" to event.nodeId,
                            "reason" to event.reason.name,
                        ))
                        // Immediate re-tick. If the removed peer
                        // was the host, the new ranking will pick
                        // a replacement (or null if no eligible
                        // host remains).
                        tick()
                    }
                    is ClusterEvent.YieldReceived -> {
                        val token = pendingHandover.entries
                            .firstOrNull { it.value.fromNodeId == event.fromNodeId }
                            ?.key
                        if (token != null) {
                            val req = pendingHandover.remove(token) ?: return@collect
                            _handoverEvents.tryEmit(HandoverEvent.Acked(req, accepted = true, errorCode = null))
                        }
                    }
                    is ClusterEvent.HostChanged -> {
                        // Update internal mirror so we don't
                        // re-trigger for the same host.
                        _state.value = _state.value.copy(
                            hostOfRecord = event.to,
                            lastHandoverMs = if (event.from.isNotBlank()) System.currentTimeMillis() else _state.value.lastHandoverMs,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        tickJob?.cancel(); tickJob = null
        observeJob?.cancel(); observeJob = null
    }

    /**
     * Force a re-evaluation NOW. Bypasses the 10 s tick interval.
     * Also clears the hysteresis gate: a re-tick can promote a
     * peer with a smaller score delta than [KubeScoring.YIELD_THRESHOLD].
     * Used by the UI's "Re-elect now" button.
     */
    suspend fun forceYield() {
        tick(ignoreHysteresis = true)
    }

    /**
     * Inbound yield-ack handler. Called from the beacon receiver
     * when a peer responds with [BeaconEnvelope.YieldAck]. The
     * scheduler matches the `handoffToken` to its in-flight map
     * and either confirms the handover (caller steps down + new
     * host promotes) or logs the rejection.
     */
    fun onYieldAck(ack: BeaconEnvelope.YieldAck) {
        val req = pendingHandover.remove(ack.handoffToken) ?: return
        if (ack.accepted) {
            log.info("kube.yield.acked", "handover accepted", mapOf(
                "from" to req.fromNodeId,
                "to" to req.toNodeId,
                "delta" to req.scoreDelta,
            ))
            _handoverEvents.tryEmit(HandoverEvent.Acked(req, accepted = true, errorCode = null))
            coordinator.setForcedHost(ack.toNodeId)
        } else {
            log.warn("kube.yield.rejected", "handover rejected", mapOf(
                "from" to req.fromNodeId,
                "to" to req.toNodeId,
                "code" to (ack.errorCode ?: "unknown"),
            ))
            _handoverEvents.tryEmit(HandoverEvent.Acked(req, accepted = false, errorCode = ack.errorCode))
        }
    }

    /** Build the inputs for a fresh tick. Combines the self snapshot
     *  with the per-peer inputs map. */
    private fun collectInputs(): List<KubeScoring.Inputs> {
        val perPeer = peerInputsProvider()
        return perPeer.values.toList()
    }

    /**
     * Run one control-loop iteration. The full algorithm:
     *  1. Score every peer.
     *  2. Find the best-scoring peer.
     *  3. Find the current host's score.
     *  4. Apply hysteresis: if `best != current` AND `delta > YIELD_THRESHOLD`,
     *     request handover. If `current` has fallen below [KubeScoring.HOST_FLOOR]
     *     AND a replacement exists, request handover regardless of delta.
     *  5. Update [state].
     */
    suspend fun tick(ignoreHysteresis: Boolean = false) {
        val inputs = collectInputs()
        val scored = KubeScoring.scoreAll(inputs)
        val currentHost = coordinator.state.value.hostOfRecord
        val best = scored.firstOrNull()
        val currentBreakdown = scored.firstOrNull { it.nodeId == currentHost }
        val currentScore = currentBreakdown?.total ?: 0.0
        val selfBreakdown = scored.firstOrNull { it.nodeId == coordinator.state.value.selfNodeId }
        val workerCount = scored.count { it.workerEligible }
        val now = System.currentTimeMillis()

        if (best == null) {
            // No peers at all. Stay where we are.
            _state.value = KubeState(
                hostOfRecord = currentHost,
                eligibleHosts = emptyList(),
                workerCount = 0,
                lastTickMs = now,
                selfScore = selfBreakdown,
            )
            return
        }

        val shouldHandover: Boolean
        val delta: Double
        when {
            best.nodeId == currentHost -> {
                shouldHandover = false; delta = 0.0
            }
            best.hostEligible && currentBreakdown == null -> {
                // No current host (cluster boot); promote best if it's eligible.
                shouldHandover = true; delta = best.total
            }
            best.hostEligible && (ignoreHysteresis || (best.total - currentScore) > KubeScoring.YIELD_THRESHOLD) -> {
                shouldHandover = true; delta = best.total - currentScore
            }
            currentBreakdown != null && currentScore < KubeScoring.HOST_FLOOR && best.hostEligible -> {
                // Current host has fallen below floor; replace regardless of delta.
                shouldHandover = true; delta = best.total - currentScore
            }
            else -> {
                shouldHandover = false; delta = 0.0
            }
        }

        val newHost = if (best.hostEligible) best.nodeId else currentHost
        _state.value = KubeState(
            hostOfRecord = newHost,
            hostScore = best.total,
            eligibleHosts = scored,
            workerCount = workerCount,
            lastTickMs = now,
            lastHandoverMs = if (shouldHandover) now else _state.value.lastHandoverMs,
            selfScore = selfBreakdown,
            pendingHandover = if (shouldHandover) {
                HandoverRequest(
                    fromNodeId = currentHost.orEmpty(),
                    toNodeId = best.nodeId,
                    scoreDelta = delta,
                    requestedAtMs = now,
                    handoffToken = UUID.randomUUID().toString(),
                    attempt = 0,
                )
            } else _state.value.pendingHandover,
        )

        if (shouldHandover) {
            val req = _state.value.pendingHandover ?: return
            requestHandover(req)
        }
    }

    private suspend fun requestHandover(req: HandoverRequest) {
        if (req.fromNodeId.isBlank()) {
            // No current host — just promote.
            log.info("kube.promote", "no current host, promoting", mapOf("to" to req.toNodeId))
            coordinator.setForcedHost(req.toNodeId)
            pendingHandover[req.handoffToken] = req
            _handoverEvents.tryEmit(HandoverEvent.Requested(req))
            // Wait briefly for ack; if none, assume the new host
            // accepted by virtue of being the only eligible peer.
            scope.launch {
                delay(handoverAckTimeoutMs)
                if (pendingHandover.remove(req.handoffToken) != null) {
                    log.info("kube.promote.ack.timeout", "no ack, assuming accepted", mapOf("to" to req.toNodeId))
                }
            }
            return
        }
        pendingHandover[req.handoffToken] = req
        _handoverEvents.tryEmit(HandoverEvent.Requested(req))
        log.info("kube.handover.request", "requesting handover", mapOf(
            "from" to req.fromNodeId,
            "to" to req.toNodeId,
            "delta" to req.scoreDelta,
            "attempt" to req.attempt,
        ))
        beacon.emitTakeover(
            toNodeId = req.toNodeId,
            handoffToken = req.handoffToken,
            scores = _state.value.eligibleHosts.associate { it.nodeId to it.total },
        )
        // Wait for ack with timeout + retry.
        scope.launch {
            delay(handoverAckTimeoutMs)
            val stillPending = pendingHandover.remove(req.handoffToken)
            if (stillPending != null) {
                if (stillPending.attempt < handoverRetryCount) {
                    val retried = stillPending.copy(attempt = stillPending.attempt + 1)
                    pendingHandover[retried.handoffToken] = retried
                    log.warn("kube.handover.retry", "retrying", mapOf(
                        "to" to req.toNodeId,
                        "attempt" to retried.attempt,
                    ))
                    requestHandover(retried)
                } else {
                    log.warn("kube.handover.failed", "all retries exhausted", mapOf(
                        "to" to req.toNodeId,
                    ))
                    _handoverEvents.tryEmit(HandoverEvent.TimedOut(stillPending))
                }
            }
        }
    }
}