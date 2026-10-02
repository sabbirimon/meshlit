package com.meshlit.core.agentmemory.distill

import com.meshlit.core.agentmemory.atom.ConversationTurn
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.MemoryVisibility
import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDistillerTest {

    private val ctx = DistillationContext(
        agentId = "agent.test",
        userId = "user.test",
        teamId = "team.test",
        tier = TrustTier.LOCAL_TRUSTED,
    )

    private val distiller = HeuristicDistiller()

    @Test
    fun `preference cue produces a PRIVATE L1 atom`() {
        val turn = ConversationTurn("user", "I love dark mode.")
        val atoms = distiller.distill(turn, ctx)
        assertEquals(1, atoms.size)
        val a = atoms.single()
        assertEquals(MemoryLayer.L1, a.layer)
        assertEquals(MemoryVisibility.PRIVATE, a.visibility)
        assertTrue(a.text.contains("dark mode"))
    }

    @Test
    fun `fact cue produces an L1 atom`() {
        val turn = ConversationTurn("user", "I work at Meshlit Labs.")
        val atoms = distiller.distill(turn, ctx)
        assertEquals(1, atoms.size)
        assertTrue(atoms.single().text.contains("Meshlit"))
    }

    @Test
    fun `event cue produces a TEAM-visible L1 atom`() {
        val turn = ConversationTurn("user", "Yesterday I shipped the audit log.")
        val atoms = distiller.distill(turn, ctx)
        assertEquals(1, atoms.size)
        assertEquals(MemoryVisibility.TEAM, atoms.single().visibility)
    }

    @Test
    fun `constraint cue produces an L1 atom`() {
        val turn = ConversationTurn("user", "Never ship without running tests.")
        val atoms = distiller.distill(turn, ctx)
        assertEquals(1, atoms.size)
        assertTrue(atoms.single().text.startsWith("Never"))
    }

    @Test
    fun `multi-sentence turn produces one atom per matched sentence`() {
        val turn = ConversationTurn(
            "user",
            "I love dark mode. Today I tried the new build.",
        )
        val atoms = distiller.distill(turn, ctx)
        // Both sentences match (PREFERENCE + EVENT). The third trailing
        // empty fragment is dropped by splitSentences.
        assertEquals(2, atoms.size)
        assertTrue(atoms.any { it.text.contains("dark mode") })
        assertTrue(atoms.any { it.text.contains("tried the new build") })
    }

    @Test
    fun `unmatched sentence produces no atom`() {
        val turn = ConversationTurn("user", "Random sentence with no cue.")
        val atoms = distiller.distill(turn, ctx)
        assertTrue("unmatched sentence must yield zero atoms", atoms.isEmpty())
    }

    @Test
    fun `assistant turns are ignored`() {
        val turn = ConversationTurn("assistant", "I love dark mode.")
        val atoms = distiller.distill(turn, ctx)
        assertTrue("assistant turns must not produce L1 atoms", atoms.isEmpty())
    }

    @Test
    fun `atom ids are unique across a turn batch`() {
        val turn = ConversationTurn(
            "user",
            "I love dark mode. I work at Meshlit. Today I shipped it. Never ship without tests.",
        )
        val atoms = distiller.distill(turn, ctx)
        assertEquals(4, atoms.size)
        val ids = atoms.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `atom carries the context agent team user and tier`() {
        val turn = ConversationTurn("user", "I love dark mode.")
        val atoms = distiller.distill(turn, ctx)
        val a = atoms.single()
        assertEquals(ctx.agentId, a.agentId)
        assertEquals(ctx.teamId, a.teamId)
        assertEquals(ctx.userId, a.userId)
        assertEquals(ctx.tier, a.tier)
    }

    @Test
    fun `custom distiller swaps in via the interface`() {
        val turn = ConversationTurn("user", "anything")
        val fixed = object : MemoryDistiller {
            override fun distill(
                turn: ConversationTurn,
                context: DistillationContext,
            ) = listOf(
                com.meshlit.core.agentmemory.atom.MemoryAtom(
                    id = "fixed-id",
                    layer = MemoryLayer.L1,
                    text = "synthetic",
                    agentId = context.agentId,
                    teamId = context.teamId,
                    userId = context.userId,
                    tier = context.tier,
                ),
            )
        }
        val atoms = fixed.distill(turn, ctx)
        assertNotNull(atoms.singleOrNull { it.id == "fixed-id" })
    }
}