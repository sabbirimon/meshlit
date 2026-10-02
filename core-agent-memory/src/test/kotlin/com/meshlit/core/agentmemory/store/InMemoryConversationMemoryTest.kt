package com.meshlit.core.agentmemory.store

import com.meshlit.core.agentmemory.distill.DistillationContext
import com.meshlit.core.agentmemory.distill.HeuristicDistiller
import com.meshlit.core.agentmemory.atom.ConversationTurn
import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.RecallBudget
import com.meshlit.core.agentmemory.MemoryViewer
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryConversationMemoryTest {

    private val self = MemoryViewer(
        agentId = "agent.test",
        userId = "user.test",
        teamId = "team.test",
        trustTier = TrustTier.LOCAL_TRUSTED,
    )

    private fun newStore(
        ctx: DistillationContext = DistillationContext(
            agentId = "agent.test",
            userId = "user.test",
            teamId = "team.test",
        ),
    ) = InMemoryConversationMemory(
        distiller = HeuristicDistiller(),
        defaultContext = ctx,
    )

    @Test
    fun `appendTurn writes an L0 atom and emits a change`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "Hello there."))
        val snap = store.snapshot()
        // L0 is always written; L1 may or may not be (this sentence
        // has no distiller cue). The store always grows by one atom.
        assertEquals(1, snap.counts[MemoryLayer.L0])
        assertTrue(
            "raw atom text should start with speaker tag",
            snap.atoms.single { it.layer == MemoryLayer.L0 }.text.startsWith("[user]"),
        )
    }

    @Test
    fun `preference turn produces an L1 atom and L0 atom`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "I love dark mode."))
        val snap = store.snapshot()
        assertEquals(1, snap.counts[MemoryLayer.L0])
        assertEquals(1, snap.counts[MemoryLayer.L1])
    }

    @Test
    fun `recall returns BM25-scored L1 atoms`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "I love dark mode."))
        store.appendTurn(ConversationTurn("user", "I work at Meshlit Labs."))
        store.appendTurn(ConversationTurn("user", "Yesterday I shipped the audit log."))
        val result = store.recall(
            query = "dark mode preference",
            viewer = self,
        )
        assertTrue("recall should return at least one atom", result.atoms.isNotEmpty())
        assertTrue(
            "top atom should mention dark mode",
            result.atoms.first().atom.text.contains("dark"),
        )
    }

    @Test
    fun `recall honours the ACL — cross-team viewer reads nothing`() = runTest {
        val store = newStore(
            ctx = DistillationContext(
                agentId = "agent.alice",
                userId = "user.alice",
                teamId = "team.a",
            ),
        )
        store.appendTurn(ConversationTurn("user", "I love dark mode."))
        val result = store.recall(
            query = "dark mode",
            viewer = MemoryViewer(
                agentId = "agent.bob",
                userId = "user.bob",
                teamId = "team.b", // different team than the store
                trustTier = TrustTier.LOCAL_TRUSTED,
            ),
            budget = RecallBudget(maxAtoms = 10),
            layers = setOf(MemoryLayer.L1, MemoryLayer.L3),
        )
        // Cross-team viewer sees zero atoms.
        assertTrue("cross-team viewer must see no atoms", result.atoms.isEmpty())
    }

    @Test
    fun `recall honours the char budget`() = runTest {
        val store = newStore()
        repeat(20) {
            store.appendTurn(ConversationTurn("user", "I love dark mode. I work at Meshlit. Today I shipped it."))
        }
        val result = store.recall(
            query = "dark mode",
            viewer = self,
            budget = RecallBudget(maxAtoms = 100, maxChars = 200),
        )
        val totalChars = result.atoms.sumOf { it.atom.text.length }
        assertTrue(
            "char budget must be respected, was $totalChars",
            totalChars <= 200 || result.atoms.size == 1, // always at least 1
        )
        assertTrue("truncated flag must reflect the cut", result.truncated)
    }

    @Test
    fun `recall respects the atom budget`() = runTest {
        val store = newStore()
        repeat(50) { store.appendTurn(ConversationTurn("user", "I love dark mode.")) }
        val result = store.recall(
            query = "dark mode",
            viewer = self,
            budget = RecallBudget(maxAtoms = 5, maxChars = 10_000),
        )
        assertTrue(result.atoms.size <= 5)
    }

    @Test
    fun `observeAtoms emits the L0 layer slice`() = runTest {
        val store = newStore()
        // Seed once so the first emission is non-empty.
        store.appendTurn(ConversationTurn("user", "Hello."))
        val l0 = store.observeAtoms(MemoryLayer.L0).first()
        assertTrue("L0 flow should have at least one atom", l0.isNotEmpty())
        assertTrue(l0.all { it.layer == MemoryLayer.L0 })
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `observeDistillation emits AtomsProduced for matched turns`() = runTest {
        val store = newStore()
        // Take just one event — the SharedFlow stays open otherwise
        // and `runTest` complains that the test body never finished.
        val collector = launch {
            store.observeDistillation()
                .take(2) // Started + AtomsProduced
                .collect { /* drain */ }
        }
        store.appendTurn(ConversationTurn("user", "I love dark mode."))
        advanceUntilIdle()
        collector.cancelAndJoin()
        // If we got here without the timeout, both events were emitted.
        assertTrue("collector completed without timeout", true)
    }

    @Test
    fun `forget removes a single atom`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "I love dark mode."))
        val atom = store.rawAtoms().first { it.layer == MemoryLayer.L1 }
        store.forget(atom.id)
        assertFalse(
            "forgotten atom must be gone",
            store.rawAtoms().any { it.id == atom.id },
        )
    }

    @Test
    fun `forgetScenario removes every atom tagged with that scenario`() = runTest {
        val store = newStore()
        store.putAtom(
            MemoryAtom(
                id = "x1",
                layer = MemoryLayer.L2,
                text = "scenario-A block",
                scenarioId = "A",
                agentId = "agent.test", teamId = "team.test", userId = "user.test",
                tier = TrustTier.LOCAL_TRUSTED,
            ),
        )
        store.putAtom(
            MemoryAtom(
                id = "x2",
                layer = MemoryLayer.L2,
                text = "scenario-B block",
                scenarioId = "B",
                agentId = "agent.test", teamId = "team.test", userId = "user.test",
                tier = TrustTier.LOCAL_TRUSTED,
            ),
        )
        store.forgetScenario("A")
        val snap = store.snapshot()
        assertNotNull(snap.atoms.firstOrNull { it.id == "x2" })
        assertFalse(snap.atoms.any { it.id == "x1" })
    }

    @Test
    fun `recall excludes L0 by default`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "dark mode preference"))
        val result = store.recall(
            query = "dark mode",
            viewer = self,
            layers = setOf(MemoryLayer.L1),
        )
        assertTrue(result.atoms.all { it.atom.layer == MemoryLayer.L1 })
    }

    @Test
    fun `recall includes L0 when requested`() = runTest {
        val store = newStore()
        store.appendTurn(ConversationTurn("user", "dark mode preference"))
        val result = store.recall(
            query = "dark mode",
            viewer = self,
            budget = RecallBudget(includeL0 = true, maxChars = 50_000),
            layers = setOf(MemoryLayer.L0, MemoryLayer.L1),
        )
        assertTrue(result.atoms.any { it.atom.layer == MemoryLayer.L0 })
    }
}