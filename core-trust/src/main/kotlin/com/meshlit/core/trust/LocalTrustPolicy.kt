package com.meshlit.core.trust

import com.meshlit.core.common.NodeId
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton holding the local node's effective trust posture. Populated
 * once at app start (when the persistent [TrustStore] is loaded) and
 * read by `PeerCapabilities.self()` so the `/v1/capabilities` reply
 * carries the same tier a peer will see when it consults us.
 *
 * Pure JVM, no DI. Tests can swap the reference directly via [set].
 */
object LocalTrustPolicy {
    private val ref = AtomicReference<DeviceTrustPolicy?>(null)

    /** The local node's effective policy, or null if uninitialised. */
    fun current(): DeviceTrustPolicy? = ref.get()

    /** Convenience: the local node's effective tier, or [TrustTier.WAN] when unset. */
    fun currentTier(): TrustTier = ref.get()?.trustTier ?: TrustTier.WAN

    /**
     * Convenience: the local node's effective tier, or [fallback] when unset.
     * Used by callers that have a non-WAN default (e.g. the local
     * self-capabilities reply defaults to LOCAL_TRUSTED before the FGS
     * has populated the stable node id).
     */
    fun currentTierOr(fallback: TrustTier): TrustTier = ref.get()?.trustTier ?: fallback

    /** The local node id (stable across sessions). */
    fun currentNodeId(): NodeId? = ref.get()?.nodeId?.let { NodeId(it) }

    /** Update the local policy. Returns the previous value. */
    fun set(policy: DeviceTrustPolicy?): DeviceTrustPolicy? = ref.getAndSet(policy)

    /** Reset for tests. */
    fun reset() { ref.set(null) }
}
