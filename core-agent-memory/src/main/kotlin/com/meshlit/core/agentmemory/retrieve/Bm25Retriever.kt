package com.meshlit.core.agentmemory.retrieve

import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.ScoredAtom
import kotlin.math.ln

/**
 * Pure-Kotlin BM25 ranker over [MemoryAtom] text. Used as the first
 * retrieval source in the funnel — the review plan §6.2 calls for
 * "BM25 + RRF first, vector later".
 *
 * BM25 parameters:
 *  - k1 = 1.5 — saturates term-frequency contribution earlier than
 *    the classic 1.2; the corpus is small (≤ a few hundred atoms per
 *    device) so a steeper curve rewards distinct matches.
 *  - b = 0.75 — standard default. Mid-weight on document-length
 *    normalization, which matters because L2 scenario blocks are
 *    paragraphs and L1 atoms are sentences.
 *
 * The retriever is **stateless**: every call recomputes the IDF
 * table from scratch. The corpus is small enough that an incremental
 * index would be more complexity than payoff for the Phase 0 spike.
 * The Phase 1 vector-retrieval follow-up will add an inverted index
 * alongside.
 */
class Bm25Retriever(
    private val k1: Float = 1.5f,
    private val b: Float = 0.75f,
) {

    /**
     * Rank [atoms] against [query]. Returns the top-K (or fewer)
     * scored atoms in descending score order. `topK = 0` returns
     * every candidate.
     */
    fun rank(query: String, atoms: List<MemoryAtom>, topK: Int = 0): List<ScoredAtom> {
        if (atoms.isEmpty()) return emptyList()
        val queryTerms = tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()
        val docs = atoms.map { it.text to tokenize(it.text) }
        val dl = docs.map { (_, toks) -> toks.size }
        val avgDl = dl.average().toFloat()
        val idf = inverseDocumentFrequency(queryTerms, docs.map { it.second })
        val scored = atoms.indices.map { i ->
            val (text, docTerms) = docs[i]
            val score = bm25Score(queryTerms, docTerms, idf, dl[i], avgDl)
            ScoredAtom(atom = atoms[i], score = score) to text
        }
        // Return 0-score atoms out of the list — they're noise and
        // the budget cut is cheaper without them.
        val filtered = scored
            .filter { (sa, _) -> sa.score > 0f }
            .sortedByDescending { (sa, _) -> sa.score }
            .map { (sa, _) -> sa }
        return if (topK > 0) filtered.take(topK) else filtered
    }

    /**
     * Reciprocal-rank-fusion score against a single peer ranker.
     * `k` is the RRF constant — 60 is the literature default.
     *
     * The Phase 0 spike has only BM25, so this function is currently
     * unused. It's here so the vector retriever (Phase 1) can fuse
     * without a refactor.
     */
    fun rrf(rankBm25: List<ScoredAtom>, rankOther: List<ScoredAtom>, k: Int = 60): List<ScoredAtom> {
        val scores = HashMap<String, Float>()
        rankBm25.forEachIndexed { idx, sa ->
            scores[sa.atom.id] = (scores[sa.atom.id] ?: 0f) + 1f / (k + idx + 1)
        }
        rankOther.forEachIndexed { idx, sa ->
            scores[sa.atom.id] = (scores[sa.atom.id] ?: 0f) + 1f / (k + idx + 1)
        }
        val atoms = HashMap<String, MemoryAtom>()
        rankBm25.forEach { atoms[it.atom.id] = it.atom }
        rankOther.forEach { atoms[it.atom.id] = it.atom }
        return atoms.values.map { atom ->
            ScoredAtom(atom = atom, score = scores[atom.id] ?: 0f)
        }.sortedByDescending { it.score }
    }

    // ----- internals ----------------------------------------------------

    private fun bm25Score(
        queryTerms: List<String>,
        docTerms: List<String>,
        idf: Map<String, Float>,
        docLen: Int,
        avgDl: Float,
    ): Float {
        if (avgDl <= 0f) return 0f
        val tf = termFrequency(queryTerms, docTerms)
        var score = 0f
        for (term in queryTerms) {
            val f = tf[term] ?: 0
            if (f == 0) continue
            val idfTerm = idf[term] ?: 0f
            val numerator = f * k1
            val denominator = f + k1 * (1f - b + b * (docLen / avgDl))
            score += idfTerm * (numerator / denominator)
        }
        return score.coerceAtLeast(0f)
    }

    private fun termFrequency(query: List<String>, doc: List<String>): Map<String, Int> {
        val tf = HashMap<String, Int>(doc.size)
        for (tok in doc) if (tok in query) tf[tok] = (tf[tok] ?: 0) + 1
        return tf
    }

    private fun inverseDocumentFrequency(
        queryTerms: List<String>,
        docs: List<List<String>>,
    ): Map<String, Float> {
        val n = docs.size.toFloat()
        val result = HashMap<String, Float>(queryTerms.size)
        for (term in queryTerms.toSet()) {
            val containing = docs.count { term in it }.toFloat()
            // Smoothed IDF — never negative. If every doc contains
            // the term the score is 0 (which still ranks atoms at
            // their term-frequency relative position).
            result[term] = ln(1f + (n - containing + 0.5f) / (containing + 0.5f))
        }
        return result
    }

    companion object {
        /**
         * Tokeniser — case-fold + split on non-letter/digit. ASCII
         * only is fine for the persona-recall benchmark; the
         * Phase 1 vector retriever can swap in a Unicode-aware
         * tokenizer (ICU / java.text.BreakIterator) without
         * touching the ranker.
         */
        fun tokenize(text: String): List<String> =
            text.lowercase()
                .split(Regex("[^\\p{L}\\p{Nd}]+"))
                .filter { it.isNotBlank() }
    }
}