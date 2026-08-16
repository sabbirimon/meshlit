package com.meshlit.core.discovery

import com.meshlit.core.common.logger
import com.meshlit.core.common.NodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Merges multiple [DiscoveryTransport]s into a single dedup-by-nodeId
 * state flow. Each transport emits [PeerAdvertisement]s as it hears
 * them; the coordinator keys on `nodeId` and keeps the freshest
 * entry per peer.
 *
 * TTL handling: the coordinator does not track wall-clock TTL — each
 * transport is responsible for honouring its own [PeerAdvertisement.ttlSec].
 * When a transport's underlying listener drops a peer (e.g. NSD
 * `onServiceLost`), the transport emits an "expiry" via a separate
 * [PeerAdvertisement] with `ttlSec = 0` and the coordinator evicts.
 *
 * Pure JVM-testable: the coordinator never touches Android. The
 * `callbackBridge` field is a hook for tests to push fake
 * advertisements into the coordinator's view.
 */
class DiscoveryCoordinator(
    private val transports: List<DiscoveryTransport>,
) {

    private val log = logger("DiscoveryCoordinator")
    private val _peers = MutableStateFlow<Map<String, PeerAdvertisement>>(emptyMap())

    /** Best-known peer per nodeId. Updated as advertisements arrive. */
    val peers: StateFlow<Map<String, PeerAdvertisement>> = _peers.asStateFlow()

    private var startedJobs: List<Job> = emptyList()

    fun start(scope: CoroutineScope, self: LocalPeerDescriptor) {
        if (startedJobs.isNotEmpty()) return
        startedJobs = transports.flatMap { transport ->
            val collectJob = scope.launch {
                transport.advertisements.collect { adv -> ingest(adv) }
            }
            val transportJob = transport.start(scope, self)
            listOf(collectJob, transportJob)
        }
    }

    fun stop() {
        transports.forEach { it.stop() }
        startedJobs.forEach { it.cancel() }
        startedJobs = emptyList()
        _peers.value = emptyMap()
    }

    /**
     * Synchronously absorb a single advertisement. Public so tests
     * can exercise the dedup / evict logic without spinning up
     * transports, and so callers that bypass the transport layer
     * (e.g. a QR-paired peer) can feed in peers directly.
     */
    fun ingest(adv: PeerAdvertisement) {
        if (adv.ttlSec <= 0) {
            _peers.update { it - adv.nodeId }
            return
        }
        _peers.update { current ->
            val existing = current[adv.nodeId]
            if (existing == null || existing.transport == adv.transport) {
                current + (adv.nodeId to adv)
            } else {
                current
            }
        }
    }

    companion object {
        /** Convenience: a coordinator with no transports (test-friendly). */
        val Empty: DiscoveryCoordinator = DiscoveryCoordinator(emptyList())
    }
}
