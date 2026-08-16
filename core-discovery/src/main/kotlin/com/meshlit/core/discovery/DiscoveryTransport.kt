package com.meshlit.core.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
 */
abstract class DiscoveryTransport(open val name: String) {

    private val _advertisements = MutableSharedFlow<PeerAdvertisement>(replay = 0, extraBufferCapacity = 64)
    val advertisements: SharedFlow<PeerAdvertisement> = _advertisements.asSharedFlow()

    /** Start emitting. Implementations should be idempotent. */
    abstract fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job

    /** Stop emitting. Implementations should release system resources. */
    abstract fun stop()

    /** Test-only / coordinator-only helper for forwarding an externally-discovered advertisement. */
    protected suspend fun emit(adv: PeerAdvertisement) {
        _advertisements.emit(adv)
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
)
