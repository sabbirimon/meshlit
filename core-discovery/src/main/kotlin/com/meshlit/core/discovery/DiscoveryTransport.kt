package com.meshlit.core.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A pluggable transport that surfaces nearby Meshlit peers. Concrete
 * implementations include [NsdDiscoveryTransport] (mDNS / DNS-SD),
 * Wi-Fi Aware, and Wi-Fi Direct — see the [name] discriminator.
 *
 * Each transport emits [PeerAdvertisement]s via the [advertisements]
 * flow. [start] begins discovery and registration; [stop] ends both.
 * Implementations are responsible for deduplication within their own
 * TTL window — the [DiscoveryCoordinator] merges dedup across
 * transports.
 *
 * [status] surfaces a typed reason the transport may be inactive
 * (missing permission, no adapter, etc.) so the UI can show a real
 * error badge instead of a silent no-op.
 */
abstract class DiscoveryTransport(open val name: String) {

    private val _advertisements = MutableSharedFlow<PeerAdvertisement>(replay = 0, extraBufferCapacity = 64)
    val advertisements: SharedFlow<PeerAdvertisement> = _advertisements.asSharedFlow()

    private val _status = MutableStateFlow<TransportStatus>(TransportStatus.Starting)
    /** Live status of this transport. Updated as the OS / permission
     *  state changes. */
    val status: StateFlow<TransportStatus> = _status.asStateFlow()

    /** Start emitting. Implementations should be idempotent. */
    abstract fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job

    /** Stop emitting. Implementations should release system resources. */
    abstract fun stop()

    /**
     * Test-only / coordinator-only helper for forwarding an externally-discovered advertisement.
     */
    protected suspend fun emit(adv: PeerAdvertisement) {
        _advertisements.emit(adv)
    }

    /**
     * Test-only / transport-internal helper for updating [status].
     * Subclasses call this when they hit a real condition
     * (permission denied, adapter missing, scan failed) so the UI
     * can render an accurate badge.
     */
    protected fun setStatus(status: TransportStatus) {
        _status.value = status
    }
}

/**
 * What the local node advertises. [port] is the HTTP/SSE server port
 * (typically `8080`). [tierTag] is the trust posture the local node
 * is willing to claim; receivers should still verify against their
 * own [com.meshlit.core.trust.TrustStore].
 */
data class LocalPeerDescriptor(
    val nodeId: String,
    val host: String,
    val port: Int,
    val tierTag: String,
    val fingerprint: String,
    val displayName: String = "",
    val manufacturer: String = "",
    val model: String = "",
)

/**
 * Typed status a [DiscoveryTransport] can be in. Each value carries
 * the reason the transport is (or isn't) producing advertisements;
 * the UI maps this to a badge / banner without further string
 * inference.
 */
sealed class TransportStatus {
    /** The transport has been constructed but `start()` has not
     *  completed yet. */
    data object Starting : TransportStatus()
    /** `start()` ran without error and the transport is producing
     *  advertisements (or is trying to). */
    data object Active : TransportStatus()
    /** The OS did not grant the runtime permission the transport
     *  needs. [permission] is the Android `Manifest.permission.*`
     *  string so the UI can route the user to a "Grant permission"
     *  button. */
    data class MissingPermission(val permission: String) : TransportStatus()
    /** The platform feature (BluetoothAdapter, NsdManager, etc.)
     *  is unavailable on this device — typically because the
     *  hardware is missing or airplane mode disabled the radio. */
    data class Unavailable(val reason: String) : TransportStatus()
    /** The transport's native API returned an error. [code] is
     *  whatever the platform-specific callback gave us; [message]
     *  is a human-readable summary. */
    data class Error(val code: Int, val message: String) : TransportStatus()
    /** `stop()` was called. */
    data object Stopped : TransportStatus()
}
