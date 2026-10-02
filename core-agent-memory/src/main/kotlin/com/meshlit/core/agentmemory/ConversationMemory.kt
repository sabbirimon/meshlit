package com.meshlit.core.agentmemory

import com.meshlit.core.agentmemory.atom.ConversationTurn
import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.RecallBudget
import com.meshlit.core.agentmemory.atom.RecallResult
import com.meshlit.core.agentmemory.atom.ScoredAtom
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.flow.Flow

/**
 * The on-device agent memory surface.
 *
 * Borrowed from the (rejected) TencentDB Agent Memory architecture —
 * see `/.puku-cli/plans/tencentdb-agent-memory-review.md` §6.1–6.4
 * for the design rationale. The interface is the only public API:
 * production impls (DataStore-backed) and the in-memory test impl
 * are interchangeable.
 *
 * Threading: every method is suspending or returns a [Flow]. The
 * store is single-process (no cluster-wide gossip in Phase 0); the
 * Phase 1 follow-up may add a gossip-protocol layer on top.
 *
 * **No cloud calls. No Tencent SDK. No remote service.** Every byte
 * of agent memory stays on the device or on the user's cluster.
 */
interface ConversationMemory {

    /**
     * Append a single raw [ConversationTurn]. The store is responsible
     * for emitting the corresponding L0 atom. L1 distillation runs in
     * the background — see [observeDistillation].
     */
    suspend fun appendTurn(turn: ConversationTurn)

    /**
     * Append a pre-formed atom. The distiller never calls this — it's
     * the entry point for callers that want to write a directly
     * authored L2 scenario block or L3 persona fragment.
     */
    suspend fun putAtom(atom: MemoryAtom)

    /**
     * Read-side recall. Returns scored atoms honouring [budget] and
     * the caller's [viewer] (ACL gate).
     * Layer ordering follows the review's funnel:
     *   1. Always-prepend the L3 persona block (capped small).
     *   2. Run BM25 + RRF over L1 atoms matching [query].
     *   3. Drop to L0 verbatim only when [RecallBudget.includeL0].
     */
    suspend fun recall(
        query: String,
        viewer: MemoryViewer,
        budget: RecallBudget = RecallBudget(),
        layers: Set<MemoryLayer> = setOf(MemoryLayer.L1, MemoryLayer.L3),
    ): RecallResult

    /**
     * Reactive flow of newly-distilled atoms. Agents that want to
     * keep a side-cache in sync subscribe here. Cold flow: replays
     * the full atom list on collect, then emits only changes.
     */
    fun observeAtoms(layer: MemoryLayer? = null): Flow<List<MemoryAtom>>

    /**
     * Reactive flow of distillation events. Emits every L0 turn as
     * it becomes one or more L1 atoms. Useful for telemetry and the
     * "what just got remembered" debug screen.
     */
    fun observeDistillation(): Flow<DistillationEvent>

    /** Snapshot the entire atom store. Used by the bootstrap audit
     *  log line + the Settings → Memory inspector screen. */
    suspend fun snapshot(): MemorySnapshot

    /** Wipe a single atom. Used by the user-driven "forget" action. */
    suspend fun forget(atomId: String)

    /** Bulk wipe by scenario. Used by "close project" → drop the
     *  scenario's L2 block + any related L1 atoms. */
    suspend fun forgetScenario(scenarioId: String)
}

/**
 * Identifies the caller of a recall operation. The ACL gate uses
 * every field — the viewer's team and user MUST be supplied
 * explicitly so cross-team isolation is enforced even when the
 * store's own default context disagrees with the caller.
 */
data class MemoryViewer(
    val agentId: String,
    val userId: String,
    val teamId: String,
    val trustTier: TrustTier,
)

/**
 * A snapshot of the atom store at a point in time. Counts per layer
 * are cheap to compute; the full atom list is only included when the
 * caller asks for it.
 */
data class MemorySnapshot(
    val atoms: List<MemoryAtom>,
    val counts: Map<MemoryLayer, Int>,
) {
    companion object {
        fun fromAtoms(atoms: List<MemoryAtom>): MemorySnapshot {
            val counts: Map<MemoryLayer, Int> = atoms.groupingBy { it.layer }.eachCount()
            return MemorySnapshot(atoms, counts)
        }
    }
}

/** Emitted by [ConversationMemory.observeDistillation]. */
sealed interface DistillationEvent {
    val turn: ConversationTurn
    val timestampMs: Long

    data class Started(
        override val turn: ConversationTurn,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : DistillationEvent

    data class AtomsProduced(
        override val turn: ConversationTurn,
        val atoms: List<MemoryAtom>,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : DistillationEvent

    data class Failed(
        override val turn: ConversationTurn,
        val cause: Throwable,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : DistillationEvent
}

/** Convenience overload for callers that already have a [ScoredAtom]
 *  list. Sums the [ScoredAtom.score] values into a single relevance
 *  metric; useful for the "how confident is this recall?" UI tile. */
fun List<ScoredAtom>.relevance(): Float =
    if (isEmpty()) 0f else sumOf { it.score.toDouble() }.toFloat() / size