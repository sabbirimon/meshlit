package com.meshlit.core.inference.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.GpuBackend
import com.meshlit.core.trust.TrustTier
import kotlinx.serialization.Serializable

/**
 * Snapshot of a single device's cluster-relevant state.
 *
 *  - `peerId` — `"self"` for the local device; otherwise the IPv4
 *    of the remote peer as it appears in `PeerRegistry`.
 *  - `capabilityTier` — coarse classification (Lite / Mid / Full).
 *    The planner uses this to filter shard assignments.
 *  - `freeRamMb` / `freeDiskMb` — point-in-time snapshot, queried
 *    every 60 s by the periodic peer probe.
 *  - `hostedShardIds` — `modelId/shardId` strings already on the
 *    device. Drives the sticky-assignment rule in the planner.
 *  - `lastSeenMs` — `System.currentTimeMillis()` at last refresh;
 *    `Long.MAX_VALUE` for `self`.
 *  - `tier` — Phase 3 trust tier (LOCAL_TRUSTED / LOCAL_SANDBOXED /
 *    WAN). Defaults to `LOCAL_TRUSTED` for backward compatibility with
 *    pre-Phase-3 peers that don't include the field on the wire.
 *  - `gpuBackend` / `vramMb` / `isExternalGpu` — Phase 5 GPU layer.
 *    Defaults are conservative (`NONE`, 0, false) so older peers
 *    still parse on the wire and the planner treats them as CPU.
 *
 * The class is `@Serializable` so `/v1/capabilities` can return it
 * directly without a separate DTO. The wire size is ~180 bytes —
 * safe to keep on the hot path.
 */
@Serializable
data class PeerCapabilities(
    val peerId: String,
    val capabilityTier: CapabilityTier,
    val freeRamMb: Long,
    val freeDiskMb: Long,
    val hostedShardIds: Set<String>,
    val lastSeenMs: Long,
    val tier: TrustTier = TrustTier.LOCAL_TRUSTED,
    val gpuBackend: GpuBackend = GpuBackend.NONE,
    val vramMb: Long = 0L,
    val isExternalGpu: Boolean = false,
) {
    /** Stable id used by the planner's sticky-assignment rule. */
    fun shardKey(modelId: String, shardId: String): String = "$modelId/$shardId"

    /** Convenience: the peer has a working GPU backend. */
    val hasGpu: Boolean get() = gpuBackend != GpuBackend.NONE

    /** Convenience: this is an eGPU peer (e.g. USB4-attached RTX 4060). */
    val isEgpu: Boolean get() = hasGpu && isExternalGpu

    companion object {
        /** Empty capabilities for the uninitialised state. */
        fun empty(peerId: String, tier: CapabilityTier): PeerCapabilities =
            PeerCapabilities(
                peerId = peerId,
                capabilityTier = tier,
                freeRamMb = 0L,
                freeDiskMb = 0L,
                hostedShardIds = emptySet(),
                lastSeenMs = 0L,
            )
    }
}
