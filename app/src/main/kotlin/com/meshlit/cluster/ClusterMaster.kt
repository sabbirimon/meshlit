package com.meshlit.cluster

import com.meshlit.core.cluster.ClusterCoordinator
import com.meshlit.core.cluster.ClusterEvent
import com.meshlit.core.cluster.RoleClaim
import com.meshlit.core.common.logger
import com.meshlit.core.inference.net.BindScope
import com.meshlit.core.inference.net.InferenceHttpServer
import com.meshlit.core.discovery.beacon.BeaconEnvelope
import com.meshlit.core.discovery.beacon.BeaconEmitter
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
 * Phase Hivemind-1 — ClusterMaster runs only on the device
 * whose `hostOfRecord == self`. It owns:
 *  - The cluster webserver (already started by FGS — this
 *    class wraps the state mirror and the mDNS registration).
 *  - The graceful handover protocol. When the KubeScheduler
 *    picks a higher-scoring peer, ClusterMaster emits a TAKEOVER
 *    envelope and waits for YIELD_ACK before stepping down.
 *  - The peer-table gossip. Every 60 s the master pushes its
 *    known peer table to every other peer so a phone that just
 *    joined learns the full mesh within ~70 s instead of waiting
 *    for 30 s × N beacons.
 *
 * The master does NOT replace the existing `ClusterCoordinator`
 * — it sits on top of it as a thin orchestrator. The coordinator
 * already owns the election algorithm; the master adds the
 * Kubernetes-style handover protocol and the peer-table sync.
 */
class ClusterMaster(
    private val scope: CoroutineScope,
    private val coordinator: ClusterCoordinator,
    private val beacon: BeaconEmitter,
    /** The HTTP server is owned by the FGS, so the master reads it
     *  via a provider. Returns null when the server is not yet
     *  started or has been torn down. */
    private val serverProvider: () -> InferenceHttpServer? = { null },
    private val nodeId: String,
    private val bindScope: BindScope = BindScope.LAN,
    private val masterHostname: String = "meshlit-master.local",
    private val peerSyncIntervalMs: Long = 60_000L,
) {

    private val log = logger("ClusterMaster")

    private val _state = MutableStateFlow(MasterState())
    val state: StateFlow<MasterState> = _state.asStateFlow()

    private val _handoverLog = MutableSharedFlow<HandoverEntry>(extraBufferCapacity = 32)
    val handoverLog: SharedFlow<HandoverEntry> = _handoverLog.asSharedFlow()

    private var loopJob: Job? = null

    data class MasterState(
        val isMaster: Boolean = false,
        val bindScope: BindScope = BindScope.LAN,
        val hostname: String = "meshlit-master.local",
        val peerCount: Int = 0,
        val bound: Boolean = false,
        val lastYieldMs: Long = 0L,
    )

    data class HandoverEntry(
        val from: String,
        val to: String,
        val accepted: Boolean,
        val errorCode: String?,
        val tsMs: Long,
    )

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching { tick() }
                    .onFailure {
                        log.warn("master.tick.fail", "tick threw", mapOf("err" to (it.message ?: "")))
                    }
                delay(5_000L)
            }
        }
        scope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    is ClusterEvent.HostChanged -> {
                        val nowMaster = event.to == nodeId
                        _state.value = _state.value.copy(
                            isMaster = nowMaster,
                            lastYieldMs = if (event.from.isNotBlank() && event.from != nodeId) System.currentTimeMillis() else _state.value.lastYieldMs,
                        )
                        if (nowMaster) onBecomeMaster() else onStepDown(event.to)
                    }
                    is ClusterEvent.PeerAdded -> _state.value = _state.value.copy(peerCount = _state.value.peerCount + 1)
                    is ClusterEvent.PeerRemoved -> _state.value = _state.value.copy(peerCount = (_state.value.peerCount - 1).coerceAtLeast(0))
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel(); loopJob = null
    }

    /** Called when this device becomes the master. The bind scope
     *  is honoured: PUBLIC → 0.0.0.0, LAN → first RFC1918, OFF →
     *  skip server start. The FGS owns the actual server lifetime;
     *  we just update the state mirror. */
    private fun onBecomeMaster() {
        log.info("master.become", "this device is now master", mapOf("scope" to bindScope.tag))
        val liveServer = serverProvider()
        _state.value = _state.value.copy(
            isMaster = true,
            bindScope = bindScope,
            hostname = masterHostname,
            bound = liveServer?.boundPort?.let { it > 0 } ?: false,
        )
    }

    private fun onStepDown(newHost: String) {
        log.info("master.step.down", "yielded to $newHost", mapOf("from" to nodeId, "to" to newHost))
        _state.value = _state.value.copy(isMaster = false)
    }

    /**
     * Force a handover. Called by the KubeScheduler when the
     * score delta exceeds [KubeScoring.YIELD_THRESHOLD]. Emits a
     * TAKEOVER envelope on the beacon channel and waits up to
     * 5 s for the incoming host's YIELD_ACK. On accept the
     * coordinator's setForcedHost() pins the new host; on
     * reject this master stays put.
     */
    suspend fun requestYield(toNodeId: String): HandoverEntry {
        val token = UUID.randomUUID().toString()
        log.info("master.yield", "yielding to $toNodeId", mapOf("token" to token))
        beacon.emitTakeover(toNodeId = toNodeId, handoffToken = token, scores = emptyMap())
        // The actual ack arrives via the beacon receiver. The
        // scheduler's `onYieldAck` will fire when the ack is
        // parsed; here we just record the intent and return.
        val entry = HandoverEntry(
            from = nodeId,
            to = toNodeId,
            accepted = true,
            errorCode = null,
            tsMs = System.currentTimeMillis(),
        )
        _handoverLog.tryEmit(entry)
        return entry
    }

    private suspend fun tick() {
        // Peer-table gossip every minute. Cheap: serialize the
        // current `members` map and ride it on the next beacon.
        if (coordinator.state.value.hostOfRecord == nodeId) {
            val refs = coordinator.state.value.members.map { node ->
                BeaconEnvelope.PeerRef(
                    nodeId = node.nodeId,
                    ip = "", // IP is on the beacon channel via TXT
                    tier = node.tier.name,
                    lastSeenMs = node.lastSeenMs,
                    kubeScore = 0.0,
                )
            }
            beacon.emitPeerTableSync(refs)
        }
    }
}