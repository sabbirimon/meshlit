package com.meshlit.core.discovery

import com.meshlit.core.common.logger
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
    private val initialTransports: List<DiscoveryTransport>,
) {

    private val log = logger("DiscoveryCoordinator")
    private val _peers = MutableStateFlow<Map<String, PeerAdvertisement>>(emptyMap())

    /** Best-known peer per nodeId. Updated as advertisements arrive. */
    val peers: StateFlow<Map<String, PeerAdvertisement>> = _peers.asStateFlow()

    private val activeTransports: MutableList<DiscoveryTransport> = initialTransports.toMutableList()
    private val disabledTransports: MutableList<DiscoveryTransport> = mutableListOf()
    private val transportJobs: MutableMap<String, List<Job>> = mutableMapOf()
    private var currentScope: CoroutineScope? = null
    private var currentSelf: LocalPeerDescriptor? = null

    /** Snapshot of currently-active transports. Read-only. */
    val transports: List<DiscoveryTransport> get() = activeTransports.toList()

    fun start(scope: CoroutineScope, self: LocalPeerDescriptor) {
        if (transportJobs.isNotEmpty()) return
        currentScope = scope
        currentSelf = self
        activeTransports.forEach { transport -> startTransport(transport, scope, self) }
    }

    fun stop() {
        activeTransports.forEach { it.stop() }
        transportJobs.values.flatten().forEach { it.cancel() }
        transportJobs.clear()
        activeTransports.clear()
        disabledTransports.clear()
        currentScope = null
        currentSelf = null
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

    /**
     * Remove a peer by nodeId. Mirrors the internal `ttlSec <= 0`
     * eviction path so a "Forget this peer" UI action and a
     * transport-driven expiry produce the same observable effect on
     * [peers]. Safe to call when the peer is absent.
     */
    fun evict(nodeId: String) {
        _peers.update { it - nodeId }
    }

    /**
     * Toggle a transport by [name] on or off at runtime. When
     * enabled, the transport is started under the [CoroutineScope]
     * passed to the most recent [start] call (and begins
     * `advertisements` collect into [peers]); when disabled, the
     * transport's [DiscoveryTransport.stop] is called and its
     * subscription job is cancelled. Toggling a name that is not
     * in [initialTransports] is a silent no-op — the
     * `PeerRepository` injects the BLE transport at construction
     * time so the toggle is meaningful.
     *
     * The seam exists for the v2 Scan screen's BLE toggle (see
     * `V2ScanScreen`). Toggling BLE off is cheap and does not
     * require a Bluetooth permission prompt; enabling BLE again
     * resumes the existing transport.
     */
    fun setTransportEnabled(name: String, enabled: Boolean) {
        val activeMatch = activeTransports.firstOrNull { it.name == name }
        val disabledMatch = disabledTransports.firstOrNull { it.name == name }
        val transport = activeMatch ?: disabledMatch ?: run {
            log.warn(
                "coordinator.transport.unknown",
                "transport not present",
                mapOf("name" to name),
            )
            return
        }
        val currentJobs = transportJobs[name]
        when {
            enabled && activeMatch != null -> {
                log.info(
                    "coordinator.transport.alreadyEnabled",
                    "transport already enabled",
                    mapOf("name" to name),
                )
                return
            }
            !enabled && activeMatch == null -> {
                log.info(
                    "coordinator.transport.alreadyDisabled",
                    "transport already disabled",
                    mapOf("name" to name),
                )
                return
            }
            !enabled -> {
                transport.stop()
                currentJobs!!.forEach { it.cancel() }
                transportJobs.remove(name)
                activeTransports.remove(transport)
                disabledTransports.add(transport)
                log.info(
                    "coordinator.transport.disabled",
                    "transport disabled",
                    mapOf("name" to name),
                )
            }
            else -> {
                // Enable path: re-add and start under the
                // remembered scope + descriptor. Defensive — if
                // start() was never called we can't resume, so we
                // log + bail.
                val scope = currentScope
                val self = currentSelf
                if (scope == null || self == null) {
                    log.warn(
                        "coordinator.transport.enableBeforeStart",
                        "cannot enable before start()",
                        mapOf("name" to name),
                    )
                    return
                }
                disabledTransports.remove(transport)
                activeTransports.add(transport)
                startTransport(transport, scope, self)
                log.info(
                    "coordinator.transport.enabled",
                    "transport enabled",
                    mapOf("name" to name),
                )
            }
        }
    }

    private fun startTransport(
        transport: DiscoveryTransport,
        scope: CoroutineScope,
        self: LocalPeerDescriptor,
    ) {
        val collectJob = scope.launch {
            transport.advertisements.collect { adv -> ingest(adv) }
        }
        val transportJob = transport.start(scope, self)
        transportJobs[transport.name] = listOf(collectJob, transportJob)
    }

    companion object {
        /** Convenience: a coordinator with no transports (test-friendly). */
        val Empty: DiscoveryCoordinator = DiscoveryCoordinator(emptyList())
    }
}