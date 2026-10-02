package com.meshlit.core.agentmemory.acl

import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.MemoryVisibility
import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function tests for the ACL gate. Covers every combination
 * of (viewer-tier × atom-tier × visibility) so a regression in any
 * direction surfaces in CI.
 */
class MemoryAclTest {

    private fun atom(
        agentId: String = "agent.alice",
        teamId: String = "team.a",
        userId: String = "user.alice",
        tier: TrustTier = TrustTier.LOCAL_TRUSTED,
        visibility: MemoryVisibility = MemoryVisibility.TEAM,
    ) = MemoryAtom(
        id = "id-$agentId-${visibility.name}",
        layer = MemoryLayer.L1,
        text = "x",
        agentId = agentId,
        teamId = teamId,
        userId = userId,
        tier = tier,
        visibility = visibility,
    )

    @Test
    fun `self can always read own atoms regardless of tier`() {
        val a = atom(tier = TrustTier.WAN, visibility = MemoryVisibility.PRIVATE)
        assertTrue(
            "alice's own WAN atom must be readable by alice",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.alice",
                viewerUserId = "user.alice",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.LOCAL_SANDBOXED,
            ),
        )
    }

    @Test
    fun `WAN atom is invisible to LOCAL_SANDBOXED viewer`() {
        val a = atom(tier = TrustTier.WAN)
        assertFalse(
            "sandboxed viewer must not read WAN atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.LOCAL_SANDBOXED,
            ),
        )
    }

    @Test
    fun `LOCAL atom is invisible to WAN viewer`() {
        val a = atom(tier = TrustTier.LOCAL_TRUSTED, visibility = MemoryVisibility.LOCAL)
        assertFalse(
            "WAN viewer must not read a LOCAL atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.WAN,
            ),
        )
    }

    @Test
    fun `WAN viewer can read WAN atom with TEAM visibility`() {
        val a = atom(tier = TrustTier.WAN, visibility = MemoryVisibility.TEAM)
        assertTrue(
            "WAN peer must read same-team WAN atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.WAN,
            ),
        )
    }

    @Test
    fun `PRIVATE atom only readable by same user`() {
        val a = atom(visibility = MemoryVisibility.PRIVATE)
        assertFalse(
            "different user must not read PRIVATE atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.LOCAL_TRUSTED,
            ),
        )
        assertTrue(
            "same user must read PRIVATE atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.alice",
                viewerTeamId = "team.a",
                viewerTrustTier = TrustTier.LOCAL_TRUSTED,
            ),
        )
    }

    @Test
    fun `TEAM atom not readable across teams`() {
        val a = atom(visibility = MemoryVisibility.TEAM)
        assertFalse(
            "cross-team viewer must not read TEAM atom",
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.b",
                viewerTrustTier = TrustTier.LOCAL_TRUSTED,
            ),
        )
    }

    @Test
    fun `CLUSTER atom is universally readable once tier allows`() {
        val a = atom(visibility = MemoryVisibility.CLUSTER, tier = TrustTier.LOCAL_TRUSTED)
        assertTrue(
            MemoryAcl.canRead(
                atom = a,
                viewerAgentId = "agent.bob",
                viewerUserId = "user.bob",
                viewerTeamId = "team.b",
                viewerTrustTier = TrustTier.LOCAL_TRUSTED,
            ),
        )
    }

    @Test
    fun `filterReadable preserves input order`() {
        val alice = atom(agentId = "agent.alice", visibility = MemoryVisibility.TEAM)
        val bob = atom(agentId = "agent.bob", visibility = MemoryVisibility.PRIVATE)
        val carol = atom(agentId = "agent.carol", visibility = MemoryVisibility.CLUSTER)
        val filtered = MemoryAcl.filterReadable(
            atoms = listOf(alice, bob, carol),
            viewerAgentId = "agent.viewer",
            viewerUserId = "user.viewer",
            viewerTeamId = "team.a",
            viewerTrustTier = TrustTier.LOCAL_TRUSTED,
        )
        // bob's PRIVATE atom must drop; alice + carol must remain in input order.
        assertEquals(listOf("agent.alice", "agent.carol"), filtered.map { it.agentId })
    }
}