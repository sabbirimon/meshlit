package com.meshlit.core.trust

import com.meshlit.core.common.ClusterRole
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * The three trust tiers defined in BUILD_GUIDE §0 principle 4 and
 * detail-sketched in `skills/cluster-trust-security/SKILL.md`.
 * Tiers are NOT a single global flag — a single device can carry
 * different effective trust depending on how it's currently connected.
 */
enum class TrustTier(val tag: String) {
    /** Same Wi-Fi, paired once. Low friction, still monitored. */
    LOCAL_TRUSTED("local_trusted"),

    /** Local, untrusted (a guest's phone joining temporarily). */
    LOCAL_SANDBOXED("local_sandboxed"),

    /** Reachable over WAN/cellular. Full TLS + signed tokens required. */
    WAN("wan");

    companion object {
        fun fromTag(tag: String): TrustTier? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * Per-device trust policy. Stored on each node; cached on peers after a
 * trust handshake. The combination `(nodeId, trustTier, allowedRoles)`
 * fully describes what this node is permitted to do for this peer.
 */
@Serializable
data class DeviceTrustPolicy(
    val nodeId: String,
    val trustTier: TrustTier,
    val allowedRoles: Set<String>,  // ClusterRole.tag set; serialized as strings for forward compat
    val tokenExpiryMs: Long? = null,  // null for LOCAL_TRUSTED's long-lived pairing
    val publicKeyFingerprint: String? = null,
) {
    /** Convenience: turn the role tags back into the enum set. */
    fun roles(): Set<ClusterRole> =
        allowedRoles.mapNotNull { ClusterRole.fromTag(it) }.toSet()

    fun isTokenExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        tokenExpiryMs?.let { it <= nowMs } == true
}

/**
 * The trust store interface. Each node implements this to remember
 * which peers it's paired with and what their effective tier is.
 * Concrete implementations: in-memory for tests, encrypted DataStore
 * for prod.
 */
interface TrustStore {
    fun policyFor(nodeId: NodeId): DeviceTrustPolicy?
    fun upsert(policy: DeviceTrustPolicy): MeshlitResult<Unit>
    fun revoke(nodeId: NodeId): MeshlitResult<Unit>
    fun list(): List<DeviceTrustPolicy>
}

/** Helper: the trust tier for a given (originator, peer) pair. */
fun effectiveTier(
    @Suppress("UNUSED_PARAMETER") originator: DeviceTrustPolicy,
    @Suppress("UNUSED_PARAMETER") peer: DeviceTrustPolicy,
): TrustTier {
    // The minimum of the two tiers applies. WAN > SANDBOXED > TRUSTED
    // in terms of strictness. Local-only flows stay local if both agree.
    return when {
        originator.trustTier == TrustTier.WAN || peer.trustTier == TrustTier.WAN -> TrustTier.WAN
        originator.trustTier == TrustTier.LOCAL_SANDBOXED || peer.trustTier == TrustTier.LOCAL_SANDBOXED -> TrustTier.LOCAL_SANDBOXED
        else -> TrustTier.LOCAL_TRUSTED
    }
}
