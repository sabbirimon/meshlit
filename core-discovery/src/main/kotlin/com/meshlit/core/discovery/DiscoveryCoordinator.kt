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

    fun bleStatus(): kotlinx.coroutines.flow.StateFlow<TransportStatus>? =
        initialTransports.filterIsInstance<BluetoothLeDiscoveryTransport>().firstOrNull()?.status

    fun refreshAvailability() {
        initialTransports.filterIsInstance<BluetoothLeDiscoveryTransport>().forEach { transport ->
            transport.refreshAvailability()
            val scope = currentScope; val self = currentSelf
            if (transport.status.value == TransportStatus.Stopped && transport in activeTransports && scope != null && self != null) {
                transportJobs.remove(transport.name)?.forEach { it.cancel() }
                startTransport(transport, scope, self)
            }
        }
    }

    fun start(scope: CoroutineScope, self: LocalPeerDescriptor) {
        if (currentScope != null) return
        currentScope = scope
        currentSelf = self
        activeTransports.forEach { transport -> startTransport(transport, scope, self) }
    }

    fun stop() {
        activeTransports.forEach { it.stop() }
        transportJobs.values.flatten().forEach { it.cancel() }
        transportJobs.clear()
        // Keep enabled/disabled transport configuration for the next scan.
        currentScope = null
        currentSelf = null
        _peers.value = emptyMap()
    }

    /**
     * Synchronously absorb a single advertisement. Public so tests
     * can exercise the dedup / evict logic without spinning up
     * transports, and so callers that bypass the transport layer
     * (e.g. a QR-paired peer) can feed in peers directly.
     *
     * Dedup rule: if the same nodeId is seen via two transports
     * (e.g. mDNS + BLE), the higher-priority transport wins
     * (LAN > NSD > BLE > RELAY > WAN > MANUAL; user-driven NFC,
     * QR-paste, and INVITE each have their own slots). Previously
     * this case silently dropped the new entry; Phase-1 pairing-
     * bug-fix logs the conflict and keeps the higher-priority
     * entry so the UI can show a "seen on Wi-Fi too" badge.
     */
    fun ingest(adv: PeerAdvertisement) {
        if (adv.nodeId == currentSelf?.nodeId || adv.nodeId.isBlank()) return
        if (adv.ttlSec <= 0) {
            _peers.update { peers ->
                if (peers[adv.nodeId]?.transport == adv.transport) peers - adv.nodeId else peers
            }
            return
        }
        _peers.update { current ->
            val existing = current[adv.nodeId]
            when {
                existing == null -> current + (adv.nodeId to adv)
                existing.transport == adv.transport -> current + (adv.nodeId to adv)
                // Cross-transport conflict. Pick the higher-
                // priority transport (lower ordinal) so a peer
                // reachable on both LAN and WAN shows up with
                // the LAN endpoint.
                priority(adv.transport) < priority(existing.transport) -> {
                    log.info(
                        "peer.merged",
                        "same nodeId via two transports; keeping higher-priority",
                        mapOf(
                            "node" to adv.nodeId.take(16),
                            "kept" to adv.transport,
                            "dropped" to existing.transport,
                        ),
                    )
                    current + (adv.nodeId to adv)
                }
                else -> {
                    log.info(
                        "peer.merged",
                        "same nodeId via two transports; keeping higher-priority",
                        mapOf(
                            "node" to adv.nodeId.take(16),
                            "kept" to existing.transport,
                            "dropped" to adv.transport,
                        ),
                    )
                    current
                }
            }
        }
    }

    /**
     * Static priority ordering for cross-transport dedup. Lower is
     * "better". LAN endpoints are surfaced over WAN-relayed ones
     * because they're typically lower-latency. A current NSD sighting
     * must replace a QR import so removing the app removes its live
     * radar dot. The QR remains in the saved device inventory. WAN-relayed
     * peers sit in the middle; manual entries are pinned at the bottom.
     */
    private fun priority(transport: String): Int = when (transport) {
        "lan" -> 0
        "nsd" -> 1
        "ble" -> 2
        "nfc" -> 3
        "qr" -> 3
        "invite" -> 3
        "relay" -> 3
        "wan" -> 4
        "manual" -> 5
        else -> 6
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
                currentJobs?.forEach { it.cancel() }
                _peers.update { peers -> peers.filterValues { it.transport != name } }
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
                disabledTransports.remove(transport)
                activeTransports.add(transport)
                if (scope != null && self != null) startTransport(transport, scope, self)
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
