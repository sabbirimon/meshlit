package com.meshlit.core.agentmemory.atom

import com.meshlit.core.trust.TrustTier
import kotlinx.serialization.Serializable

/**
 * The four storage layers defined in the TencentDB Agent Memory review
 * (see `/.puku-cli/plans/tencentdb-agent-memory-review.md` §6.1).
 *
 * Meshlit re-implements them locally rather than depending on the
 * upstream service — the layers are an architecture idea, not a
 * product feature. Each layer corresponds to a different
 * recall/lifecycle contract:
 *
 * - [L0] is the verbatim turn store. We never read from it on the
 *   recall path unless the caller asks for verbatim fallback.
 * - [L1] is the atom table. Most recall queries hit this layer.
 * - [L2] is the scenario block (project-scoped working context).
 * - [L3] is the persona / identity bootstrap.
 */
enum class MemoryLayer(val tag: String) {
    L0("l0"),
    L1("l1"),
    L2("l2"),
    L3("l3");

    companion object {
        fun fromTag(tag: String): MemoryLayer? = entries.firstOrNull { it.tag == tag }
    }
}

/**
 * A single extracted memory atom.
 *
 * The shape is deliberately small: text + provenance + ACL. Everything
 * else (embedding vectors, BM25 token caches) is derived at
 * read-time so the persisted surface stays portable across DataStore
 * migrations.
 *
 * @property id Stable identifier. ULID/UUID form so the atom can be
 *  dedup'd across L0 distillation passes.
 * @property layer Which layer this atom belongs to. The recall funnel
 *  uses this to pick the right budget slice.
 * @property text The atom's content. For L1 this is a single fact /
 *  preference / event / constraint; for L2 a paragraph; for L3 a
 *  long-lived identity fragment; for L0 the raw turn text.
 * @property scenarioId Optional project / session grouping for L2
 *  atoms. L1 atoms without a scenario are "always-on" persona.
 * @property agentId The agent that produced / owns the atom.
 * @property teamId The team the [agentId] belongs to. Two-team
 *  isolation is enforced by [MemoryAcl].
 * @property userId The user that owns the agent. ACL can also
 *  filter on this so a guest phone never reads the owner's
 *  private L3 profile.
 * @property tier The trust tier the atom was written at. ACL
 *  compares this against the requesting peer's tier.
 * @property visibility The sharing scope — see [MemoryVisibility].
 * @property createdAtMs Monotonic creation timestamp; used for the
 *  persona LRU cut + the audit log.
 */
@Serializable
data class MemoryAtom(
    val id: String,
    val layer: MemoryLayer,
    val text: String,
    val scenarioId: String? = null,
    val agentId: String,
    val teamId: String,
    val userId: String,
    val tier: TrustTier,
    val visibility: MemoryVisibility = MemoryVisibility.TEAM,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Sharing scope for a [MemoryAtom]. Mirrors the upstream
 * `(team, user, agent, visibility)` ACL axis but expressed as a
 * single field for query simplicity. The effective rule set is
 * enforced by [com.meshlit.core.agentmemory.acl.MemoryAcl].
 */
@Serializable
enum class MemoryVisibility {
    /** Visible to the same team only. */
    TEAM,
    /** Visible to a single user — never crosses device pairing. */
    PRIVATE,
    /** Visible cluster-wide. */
    CLUSTER,
    /** Visible to everyone in the local LAN. WAN agents never see. */
    LOCAL,
}