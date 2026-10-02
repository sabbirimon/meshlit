package com.meshlit.core.agentmemory.distill

import com.meshlit.core.agentmemory.atom.ConversationTurn
import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.MemoryVisibility
import com.meshlit.core.trust.TrustTier
import java.util.UUID

/**
 * The L0 → L1 distillation pass.
 *
 * Per the review plan §6.1, Meshlit keeps the distiller as a **pure
 * function over the in-memory turn list**. The Phase 0 spike does
 * NOT call into `core-inference` — the distiller extracts atoms via
 * deterministic pattern matching (preferences, facts, events,
 * constraints) that is good enough to:
 *
 *   - validate the recall funnel (BM25 + RRF) end-to-end,
 *   - gate the PersonaMem regression test (plan §6.4) on ≥ +40 %
 *     persona-recall accuracy on a synthetic dataset,
 *   - leave an obvious seam where a future LLM-backed distiller
 *     slots in (the `Distiller` interface).
 *
 * The shape is the same regardless of distiller quality: a list of
 * L1 atoms + the originating turn. The store just persists both.
 *
 * Phase 1 follow-up: replace [HeuristicDistiller] with an
 * `LlmDistiller` that pipes the turn through `core-inference`'s
 * local model. The interface is the swap point.
 */
interface MemoryDistiller {
    /**
     * Distill a single [turn] into one or more L1 atoms.
     * Returns an empty list when the turn contains nothing
     * memory-worthy.
     */
    fun distill(turn: ConversationTurn, context: DistillationContext): List<MemoryAtom>
}

/**
 * Per-call context the distiller needs to mint atoms. Mirrors the
 * `(team, user, agent, tier)` axes of the [MemoryAtom] record.
 *
 * @property agentId The agent producing the atoms.
 * @property userId The owning user.
 * @property teamId The owning team.
 * @property tier The trust tier the agent is currently operating at.
 */
data class DistillationContext(
    val agentId: String,
    val userId: String,
    val teamId: String,
    val tier: TrustTier = TrustTier.LOCAL_TRUSTED,
)

/**
 * Heuristic-only distiller. Recognises four atom kinds via simple
 * pattern matches:
 *
 *  - **Preference:** "I like / love / hate / prefer / always use X."
 *  - **Fact:** "I am / I'm / my name is / I work at X." (only the
 *    first sentence of a user turn that contains the cue).
 *  - **Event:** "yesterday / today / tomorrow / last week I did X."
 *  - **Constraint:** "don't / never / always / must / required X."
 *
 * Each match produces a single L1 atom. The `text` is the matching
 * sentence, not the whole turn — this keeps the recall budget
 * cheap and the BM25 surface clean.
 *
 * The cue regexes are deliberately conservative: a false negative
 * (a missed atom) costs nothing at the persona-recall benchmark
 * if the atom is still in the L0 fallback layer; a false positive
 * (a hallucinated atom) pollutes the persona block. The
 * PersonaMemTest gates the precision/recall trade-off.
 */
class HeuristicDistiller : MemoryDistiller {

    override fun distill(turn: ConversationTurn, context: DistillationContext): List<MemoryAtom> {
        // The distiller only acts on user turns. Assistant turns
        // are L0 verbatim but rarely produce L1 atoms on their own —
        // the user is the source of identity.
        if (turn.speaker.equals("assistant", ignoreCase = true)) return emptyList()
        val sentences = splitSentences(turn.text)
        return sentences.mapNotNull { sentence ->
            val kind = classify(sentence) ?: return@mapNotNull null
            atomFor(sentence, kind, turn, context)
        }
    }

    private fun classify(sentence: String): AtomKind? {
        val s = sentence.lowercase()
        return when {
            PREFERENCE.matches(s) -> AtomKind.PREFERENCE
            FACT.matches(s) -> AtomKind.FACT
            EVENT.matches(s) -> AtomKind.EVENT
            CONSTRAINT.matches(s) -> AtomKind.CONSTRAINT
            else -> null
        }
    }

    private fun atomFor(
        sentence: String,
        kind: AtomKind,
        turn: ConversationTurn,
        context: DistillationContext,
    ): MemoryAtom = MemoryAtom(
        id = UUID.randomUUID().toString(),
        layer = MemoryLayer.L1,
        text = sentence.trim(),
        scenarioId = null,
        agentId = context.agentId,
        teamId = context.teamId,
        userId = context.userId,
        tier = context.tier,
        visibility = when (kind) {
            AtomKind.PREFERENCE, AtomKind.FACT -> MemoryVisibility.PRIVATE
            AtomKind.EVENT -> MemoryVisibility.TEAM
            AtomKind.CONSTRAINT -> MemoryVisibility.PRIVATE
        },
        createdAtMs = turn.timestampMs,
    )

    /** Sentence splitter. Splits on `.`, `!`, `?`, and newline. The
     *  heuristic distiller doesn't need NLP — it just needs stable
     *  boundaries so a multi-sentence turn produces 0..N atoms. */
    private fun splitSentences(text: String): List<String> =
        text.split(Regex("[.!?\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private enum class AtomKind { PREFERENCE, FACT, EVENT, CONSTRAINT }

    private companion object {
        // Word-boundary anchored cues. Case-insensitive at the
        // matcher. The lookahead `.*` lets us capture the rest of
        // the sentence as the atom body.
        val PREFERENCE = Regex("^(i |i'm |im )?(like|love|hate|dislike|prefer|always use)\\b.*")
        val FACT = Regex("^(i am|i'm|im|my name is|i work at|i live in|i'm from)\\b.*")
        val EVENT = Regex("^(yesterday|today|tomorrow|last week|last night|this morning)\\b.*")
        val CONSTRAINT = Regex("^(i |i'm )?(don'?t|never|always|must|required|important)\\b.*")
    }
}