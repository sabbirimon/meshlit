package com.meshlit.core.discovery

import com.meshlit.core.common.NodeId
import com.meshlit.core.trust.TrustTier
import kotlinx.serialization.Serializable

/**
 * One mDNS / Wi-Fi-Aware / Wi-Fi-Direct advertisement. Carries
 * enough metadata for the receiving device to:
 *  - decide whether to trust the peer (via [tier]),
 *  - route traffic to the right port ([port]),
 *  - verify the peer hasn't been impersonated (via [fingerprint]
 *    matching the local node's stored `DeviceTrustPolicy.publicKeyFingerprint`).
 *
 * The TTL field is informational — discovery transports are expected
 * to age out their own entries.
 */
@Serializable
data class PeerAdvertisement(
    val nodeId: String,
    val host: String,
    val port: Int,
    val tier: String,
    val fingerprint: String,
    val ttlSec: Int = 60,
    val transport: String = "nsd",
) {
    fun trustTierOrDefault(): TrustTier = TrustTier.fromTag(tier) ?: TrustTier.LOCAL_SANDBOXED
    fun nodeIdTyped(): NodeId = NodeId(nodeId)
}
