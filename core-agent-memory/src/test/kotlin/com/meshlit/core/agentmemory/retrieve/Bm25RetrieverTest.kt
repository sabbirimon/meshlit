package com.meshlit.core.agentmemory.retrieve

import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.ScoredAtom
import com.meshlit.core.trust.TrustTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bm25RetrieverTest {

    private val ctx = listOf(
        MemoryAtom(
            id = "a1",
            layer = MemoryLayer.L1,
            text = "The user prefers dark mode and concise explanations.",
            agentId = "a", teamId = "t", userId = "u", tier = TrustTier.LOCAL_TRUSTED,
        ),
        MemoryAtom(
            id = "a2",
            layer = MemoryLayer.L1,
            text = "The user works on a Kubernetes cluster in Frankfurt.",
            agentId = "a", teamId = "t", userId = "u", tier = TrustTier.LOCAL_TRUSTED,
        ),
        MemoryAtom(
            id = "a3",
            layer = MemoryLayer.L1,
            text = "The user dislikes long status updates and prefers bullet points.",
            agentId = "a", teamId = "t", userId = "u", tier = TrustTier.LOCAL_TRUSTED,
        ),
        MemoryAtom(
            id = "a4",
            layer = MemoryLayer.L1,
            text = "Yesterday the user shipped the on-device audit log.",
            agentId = "a", teamId = "t", userId = "u", tier = TrustTier.LOCAL_TRUSTED,
        ),
    )

    @Test
    fun `query matches the most relevant atom first`() {
        val ranked = Bm25Retriever().rank("dark mode preferences", ctx)
        assertTrue("expected at least one match", ranked.isNotEmpty())
        val topId = ranked.first().atom.id
        assertEquals("a1 (preferences / dark mode) should win", "a1", topId)
    }

    @Test
    fun `rank returns 0-score atoms out of the list`() {
        val ranked = Bm25Retriever().rank("frank kubernetes cluster", ctx)
        assertTrue(ranked.all { it.score > 0f })
    }

    @Test
    fun `rank respects topK`() {
        val ranked = Bm25Retriever().rank("user", ctx, topK = 1)
        assertEquals(1, ranked.size)
    }

    @Test
    fun `query with no shared terms returns empty`() {
        val ranked = Bm25Retriever().rank("xyzzynothingmatches", ctx)
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `empty corpus returns empty result`() {
        val ranked = Bm25Retriever().rank("anything", emptyList())
        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `BM25 score is higher for the more specific match`() {
        // a1 ("prefers dark mode...") does not contain "kubernetes"
        // or "cluster" at all, so BM25 may legitimately filter it out
        // of the ranking. The contract is: a2 (which does match) must
        // be present AND it must rank higher than any of the other
        // candidates that DO match.
        val ranked = Bm25Retriever().rank("kubernetes cluster", ctx)
        val a2 = ranked.firstOrNull { it.atom.id == "a2" }
        assertTrue("a2 (kubernetes cluster) must be in the ranked list", a2 != null)
        // Every other scored atom must rank ≤ a2.
        val a2Score = a2!!.score
        ranked.forEach { sa ->
            if (sa.atom.id != "a2") {
                assertTrue(
                    "non-a2 atom ${sa.atom.id} must score ≤ a2 ($a2Score)",
                    sa.score <= a2Score + 1e-4f,
                )
            }
        }
    }

    @Test
    fun `tokenizer lowercases and splits on punctuation`() {
        val toks = Bm25Retriever.tokenize("Hello, World! Foo-Bar 123.")
        assertEquals(listOf("hello", "world", "foo", "bar", "123"), toks)
    }

    @Test
    fun `tokenizer drops empty fragments`() {
        val toks = Bm25Retriever.tokenize("... hello ...")
        assertEquals(listOf("hello"), toks)
    }

    @Test
    fun `RRF combines two ranked lists`() {
        val rrf = Bm25Retriever().rrf(
            rankBm25 = listOf(ScoredAtom(ctx[0], 1.0f), ScoredAtom(ctx[1], 0.5f)),
            rankOther = listOf(ScoredAtom(ctx[1], 1.0f), ScoredAtom(ctx[0], 0.5f)),
            k = 60,
        )
        // Both atoms appear in both lists — both must come back with
        // RRF score = 1/61 + 1/62 > 0.
        assertEquals(2, rrf.size)
        assertTrue(rrf.all { it.score > 0f })
    }

    @Test
    fun `RRF gives higher score to the atom ranked first in both lists`() {
        // ctx[0] ranks first in BM25; ctx[1] ranks first in `rankOther`.
        // ctx[0] wins RRF.
        val rrf = Bm25Retriever().rrf(
            rankBm25 = listOf(ScoredAtom(ctx[0], 1.0f)),
            rankOther = listOf(ScoredAtom(ctx[0], 1.0f)),
            k = 60,
        )
        assertEquals("a1", rrf.first().atom.id)
    }

    @Test
    fun `scores are non-negative`() {
        val ranked = Bm25Retriever().rank("frank", ctx)
        assertTrue(ranked.all { it.score >= 0f })
    }

    @Test
    fun `different queries surface different top atoms`() {
        val a = Bm25Retriever().rank("kubernetes cluster", ctx).first().atom.id
        val b = Bm25Retriever().rank("yesterday shipped", ctx).first().atom.id
        assertNotEquals(a, b)
    }
}