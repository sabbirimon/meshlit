package com.meshlit.core.agentmemory.acl

import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryVisibility
import com.meshlit.core.trust.TrustTier

/**
 * Per-call ACL gate for [MemoryAtom]. Mirrors the upstream Tencent
 * design (review plan §6.3) but stays on-device — the
 * `(team, user, agent, visibility)` tuple is folded into [MemoryAtom]
 * itself, and the gate runs as a pure function over the snapshot.
 *
 * Phase 0 keeps storage local to each device. Cluster-wide ACL
 * enforcement is a Phase 1 follow-up — the gossip protocol will
 * carry the (team, user, agent, visibility) tuple over the wire and
 * the same [MemoryAcl] gate runs on the receiver.
 *
 * Rules (apply in order, fail fast):
 *
 * 1. **Trust tier:** the viewer's [TrustTier] must be at least as
 *    strict as the atom's tier. WAN agents may read WAN atoms; a
 *    LOCAL_SANDBOXED peer may NOT read a WAN atom because doing so
 *    would lift sandboxing. Same in reverse — a LOCAL_TRUSTED
 *    device may read WAN atoms (they're a strict superset).
 * 2. **Visibility scope:**
 *    - [MemoryVisibility.PRIVATE] — same userId only.
 *    - [MemoryVisibility.TEAM] — same teamId only.
 *    - [MemoryVisibility.LOCAL] — viewer's tier is one of the local
 *      tiers (LOCAL_TRUSTED / LOCAL_SANDBOXED).
 *    - [MemoryVisibility.CLUSTER] — always visible (assumes the
 *      store already filtered out the WAN-only atoms in step 1).
 * 3. **Self-visibility:** a viewer may always read their own atoms.
 */
object MemoryAcl {

    /**
     * Returns `true` iff [viewer] is allowed to read [atom].
     *
     * @param viewerAgentId The agent making the recall request.
     * @param viewerUserId The user owning the [viewerAgentId].
     * @param viewerTeamId The team owning the [viewerUserId].
     * @param viewerTrustTier The trust tier of the connection to
     *  the calling peer (NOT the device's own tier — that's the
     *  [MemoryAtom.tier] field).
     */
    fun canRead(
        atom: MemoryAtom,
        viewerAgentId: String,
        viewerUserId: String,
        viewerTeamId: String,
        viewerTrustTier: TrustTier,
    ): Boolean {
        if (atom.agentId == viewerAgentId) return true
        if (!tierAllows(viewerTrustTier, atom.tier)) return false
        return when (atom.visibility) {
            MemoryVisibility.PRIVATE -> atom.userId == viewerUserId
            MemoryVisibility.TEAM -> atom.teamId == viewerTeamId
            MemoryVisibility.LOCAL -> viewerTrustTier != TrustTier.WAN
            MemoryVisibility.CLUSTER -> true
        }
    }

    /** Filter [atoms] down to those the viewer can read. The output
     *  preserves input order so the recall layer's scoring is not
     *  perturbed. */
    fun filterReadable(
        atoms: Iterable<MemoryAtom>,
        viewerAgentId: String,
        viewerUserId: String,
        viewerTeamId: String,
        viewerTrustTier: TrustTier,
    ): List<MemoryAtom> = atoms.filter {
        canRead(it, viewerAgentId, viewerUserId, viewerTeamId, viewerTrustTier)
    }

    /** Trust-tier check lifted out for unit-testing. Returns `true`
     *  when [viewer] can read atoms authored at [author]. The rule:
     *  a stricter tier can always read a looser atom, but a
     *  looser-tier peer may not read a strict-tier atom. */
    private fun tierAllows(viewer: TrustTier, author: TrustTier): Boolean {
        val strictness = mapOf(
            TrustTier.LOCAL_TRUSTED to 0,
            TrustTier.LOCAL_SANDBOXED to 1,
            TrustTier.WAN to 2,
        )
        // Read direction: a WAN atom (strictness 2) is only readable
        // by a WAN or stricter viewer (strictness ≥ 2). We use ≥ for
        // the strictness ordering so the same function is reusable
        // for "may this viewer also write?" — both directions need
        // the same threshold.
        return strictness.getValue(viewer) >= strictness.getValue(author)
    }
}