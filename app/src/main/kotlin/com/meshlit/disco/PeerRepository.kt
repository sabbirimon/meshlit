package com.meshlit.disco

import com.meshlit.core.common.NodeId
import com.meshlit.core.discovery.DiscoveryCoordinator
import com.meshlit.core.discovery.LocalPeerDescriptor
import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.trust.TrustStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Public face of the v2 Scan screen + hub's "Scan" card.
 *
 * Owns the [DiscoveryCoordinator]'s start/stop and exposes a
 * [uiState] with classified, grouped peers so the screen can
 * render without re-deriving categories on every recomposition.
 *
 * Why a repository over reading `coordinator.peers` directly:
 *  - The screen wants [ClassifiedPeer], not [PeerAdvertisement] —
 *    classification needs the host's own subnet prefixes, which
 *    are local to this class.
 *  - The hub's "Scan" card wants a count by category; grouping
 *    once in the repo is cheaper than recomputing at three
 *    callers.
 *  - Stopping on dispose (`onCleared()`) is mandatory — leaving
 *    mDNS listeners running after the user closes Scan leaks
 *    battery and broadcasts the local node id.
 *
 * Wiring: Koin single in [com.meshlit.disco.discoModule] + a
 * `stateIn`-backed `uiState` keyed on the repository's own
 * application scope.
 *
 * Scan screen extension surface:
 *  - [forget] — revoke a peer's trust fingerprint and evict them
 *    from the coordinator's peer map. Surfaced from the Scan row's
 *    "Forget this peer" action.
 *  - [setTransportEnabled] — toggle BLE / mDNS off at runtime.
 *    Drives the Scan screen's transport switch row.
 *  - [ScanUiState.usbTether] — `null` until the host's
 *    `ConnectivityManager` reports a USB-NCM interface. The Scan
 *    screen renders a coral row at the top of the peer list when
 *    this is non-null.
 */
class PeerRepository(
    private val coordinator: DiscoveryCoordinator,
    private val localIPPrefixProvider: () -> List<String>,
    private val clusterFingerprintsProvider: () -> Set<String>,
    private val trustStore: TrustStore? = null,
    private val initialTransports: Set<String> = setOf("nsd", "ble"),
    private val usbTetherProvider: () -> UsbTetherInfo? = { null },
) {

    private val _scanning = MutableStateFlow(false)
    /** True while mDNS listeners are active. */
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _transportsEnabled = MutableStateFlow(initialTransports)

    /**
     * `null` until [start] is called. From then on, emits
     * [ScanUiState] for every change to the peer map, the
     * scanning flag, the local IP / cluster fingerprint
     * providers, or the transport toggle state.
     */
    @Volatile
    private var uiStateFlow: StateFlow<ScanUiState>? = null

    val uiState: StateFlow<ScanUiState>
        get() = uiStateFlow
            ?: error("PeerRepository.start() must be called before reading uiState")

    private var started = false

    /**
     * Begin discovery. Idempotent — calling twice is a no-op so
     * the screen + hub calling simultaneously doesn't double-listen.
     *
     * [scope] is the application scope (or any long-lived
     * coroutine context). The coordinator's listener jobs are
     * scoped to it. [self] is the local node identity other
     * peers will see.
     */
    fun start(scope: CoroutineScope, self: LocalPeerDescriptor) {
        if (started) return
        started = true
        coordinator.start(scope, self)
        _scanning.value = true
        // Reconcile the coordinator's initial transport list with
        // our own enabled-set. If the caller disabled BLE in the
        // UI before the screen mounted, drop it from the
        // coordinator before subscriptions attach so we don't
        // race with a fresh advertisement.
        initialTransports.let { enabled ->
            coordinator.transports.map { it.name }.forEach { name ->
                if (name !in enabled) {
                    coordinator.setTransportEnabled(name, enabled = false)
                }
            }
        }
        // Build the stateIn-backed flow once. combine() on a
        // StateFlow is cheap; the snapshot only re-derives when
        // peers map, scanning flag, transport toggle, or USB
        // tether state changes.
        uiStateFlow = combine(
            coordinator.peers,
            _scanning,
            _transportsEnabled,
        ) { peers, scanning, transports ->
            val ips = localIPPrefixProvider()
            val fps = clusterFingerprintsProvider()
            val classified = peers.values
                .map { classify(it, ips, fps) }
                .sortedWith(
                    compareBy(
                        { it.category.ordinal },
                        { it.advertisement.host },
                        { it.advertisement.port },
                    ),
                )
            val grouped = PeerCategory.entries.associateWith { cat ->
                classified.filter { it.category == cat }
            }
            ScanUiState(
                scanning = scanning,
                grouped = grouped,
                total = classified.size,
                localIps = ips,
                usbTether = usbTetherProvider(),
                transportsEnabled = transports,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ScanUiState(
                scanning = false,
                grouped = PeerCategory.entries.associateWith { emptyList() },
                total = 0,
                localIps = localIPPrefixProvider(),
                usbTether = usbTetherProvider(),
                transportsEnabled = initialTransports,
            ),
        )
    }

    fun stop() {
        if (!started) return
        started = false
        coordinator.stop()
        _scanning.value = false
    }

    /**
     * Inject a peer that came from a QR-pairing or invite-link,
     * bypassing the mDNS path. Routes through the same dedup
     * machinery so the rest of the system sees it identically.
     */
    fun ingest(adv: PeerAdvertisement) = coordinator.ingest(adv)

    /**
     * Revoke this peer's trust fingerprint and evict them from the
     * peer's `peers` map. Both steps are idempotent — re-calling
     * with the same id is a no-op so the UI can retry without
     * surfaces crashing. Logs at info level for traceability.
     */
    fun forget(nodeId: String) {
        trustStore?.revoke(NodeId(nodeId))
        coordinator.evict(nodeId)
    }

    /**
     * Toggle a transport on/off at runtime via the underlying
     * [DiscoveryCoordinator.setTransportEnabled]. Unknown names are
     * silently ignored so a no-longer-registered transport doesn't
     * crash the UI.
     */
    fun setTransportEnabled(name: String, enabled: Boolean) {
        coordinator.setTransportEnabled(name, enabled)
        _transportsEnabled.value = if (enabled) {
            _transportsEnabled.value + name
        } else {
            _transportsEnabled.value - name
        }
    }
}

/**
 * Snapshot of the repo for the screen + any other consumers.
 * [grouped] is keyed by category; an empty list means "no peers
 * in this bucket right now" (not "category missing from data
 * class").
 *
 * Surface added in Phase 0.5:
 *  - [usbTether]: when a USB-NCM / RNDIS tether comes up, the
 *    repository exposes it via a small row at the top of the Scan
 *    list — coral accent, "USB tether" + peer IP + MTU + Mbps.
 *  - [transportsEnabled]: which discovery transports are
 *    currently engaged. Drives the Scan screen's transport toggle
 *    row.
 */
data class ScanUiState(
    val scanning: Boolean,
    val grouped: Map<PeerCategory, List<ClassifiedPeer>>,
    val total: Int,
    val localIps: List<String>,
    val usbTether: UsbTetherInfo? = null,
    val transportsEnabled: Set<String> = setOf("nsd", "ble"),
)

/**
 * Lightweight record of a USB-attached peer rendered at the top
 * of the Scan list. Populated by the host's `ConnectivityManager`
 * callback; `null` when no USB tether is up.
 *
 * Fields are deliberately minimal — the rest of the host's
 * networking state lives in the Network monitor screen.
 */
data class UsbTetherInfo(
    val host: String,
    val mtu: Int,
    val linkMbps: Int,
)