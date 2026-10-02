package com.meshlit.core.agentmemory.store

import com.meshlit.core.agentmemory.ConversationMemory
import com.meshlit.core.agentmemory.DistillationEvent
import com.meshlit.core.agentmemory.MemorySnapshot
import com.meshlit.core.agentmemory.MemoryViewer
import com.meshlit.core.agentmemory.acl.MemoryAcl
import com.meshlit.core.agentmemory.atom.ConversationTurn
import com.meshlit.core.agentmemory.atom.MemoryAtom
import com.meshlit.core.agentmemory.atom.MemoryLayer
import com.meshlit.core.agentmemory.atom.RecallBudget
import com.meshlit.core.agentmemory.atom.RecallResult
import com.meshlit.core.agentmemory.atom.ScoredAtom
import com.meshlit.core.agentmemory.distill.DistillationContext
import com.meshlit.core.agentmemory.distill.MemoryDistiller
import com.meshlit.core.agentmemory.retrieve.Bm25Retriever
import com.meshlit.core.common.logger
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * In-memory [ConversationMemory] implementation.
 *
 * Used by:
 *  - tests (the Phase 0 spike has no DataStore round-trip),
 *  - the preview / inspector UI in dev builds,
 *  - the unit-test scaffold for `core-orchestration`.
 *
 * Production wiring is the [DataStore-backed] variant (Phase 0.5
 * follow-up; see review plan §6). The interface surface is identical
 * so the call sites don't change.
 *
 * Threading: every mutation goes through [mutex]. Reads are
 * non-suspending but take the mutex briefly so the snapshot is
 * consistent.
 */
class InMemoryConversationMemory(
    private val distiller: MemoryDistiller,
    private val retriever: Bm25Retriever = Bm25Retriever(),
    /**
     * ACL context for atoms this store writes on behalf of the user.
     * In tests, callers pass a custom context to vary team/user ids.
     */
    private val defaultContext: DistillationContext = DistillationContext(
        agentId = "agent.local",
        userId = "user.local",
        teamId = "team.local",
    ),
) : ConversationMemory {

    private val log = logger("InMemoryConversationMemory")
    private val mutex = Mutex()

    /** L0 raw turns, in arrival order. */
    private val turns = mutableListOf<ConversationTurn>()

    /** Atom store, keyed by [MemoryAtom.id]. */
    private val atoms = mutableMapOf<String, MemoryAtom>()

    /** Snapshot Flow — emits the current atom list on every change. */
    private val changes = MutableStateFlow<List<MemoryAtom>>(emptyList())

    /** Distillation events Flow. Replays nothing on collect; emits
     *  only changes. */
    private val events = MutableSharedFlow<DistillationEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    override suspend fun appendTurn(turn: ConversationTurn) {
        mutex.withLock {
            turns.add(turn)
            val l0 = l0AtomFor(turn)
            atoms[l0.id] = l0
        }
        publishChange()
        // Run the distiller outside the mutex so the lock window is
        // bounded; the L1 atoms land via the same mutex.
        val ctx = defaultContext
        events.tryEmit(DistillationEvent.Started(turn))
        val produced = runCatching { distiller.distill(turn, ctx) }
        produced.fold(
            onSuccess = { emitted ->
                mutex.withLock {
                    emitted.forEach { atoms[it.id] = it }
                }
                publishChange()
                events.tryEmit(DistillationEvent.AtomsProduced(turn, emitted))
                log.info(
                    "core.memory.distilled",
                    "turn distilled",
                    mapOf(
                        "atoms" to atoms.size,
                        "speaker" to turn.speaker,
                    ),
                )
            },
            onFailure = { t ->
                events.tryEmit(DistillationEvent.Failed(turn, t))
                log.error("core.memory.distill_fail", "distiller threw", t)
            },
        )
    }

    override suspend fun putAtom(atom: MemoryAtom) {
        mutex.withLock { atoms[atom.id] = atom }
        publishChange()
    }

    override suspend fun recall(
        query: String,
        viewer: MemoryViewer,
        budget: RecallBudget,
        layers: Set<MemoryLayer>,
    ): RecallResult {
        val started = System.currentTimeMillis()
        // Honour the budget — bail out at the deadline if the BM25
        // sweep hasn't finished. The retriever is fast on the
        // typical corpus (~200 atoms) so the timeout rarely fires,
        // but it's the contract.
        val raw = withTimeoutOrNull(budget.timeoutMs) {
            mutex.withLock {
                val candidates = atoms.values.filter { it.layer in layers }
                val readable = MemoryAcl.filterReadable(
                    candidates,
                    viewerAgentId = viewer.agentId,
                    viewerUserId = viewer.userId,
                    viewerTeamId = viewer.teamId,
                    viewerTrustTier = viewer.trustTier,
                )
                readable
            }
        } ?: emptyList()
        // BM25 over the readable slice. We score outside the mutex
        // because the retriever is pure-Kotlin and cheap.
        val scored = retriever.rank(query, raw, topK = budget.maxAtoms)
        val layeredHits = raw.groupingBy { it.layer }.eachCount()
        // Char-budget cut — drop the tail atoms (lowest-scoring) until
        // the total char count fits.
        val (truncated, packed) = packBudget(scored, budget)
        val elapsed = System.currentTimeMillis() - started
        return RecallResult(
            atoms = packed,
            layerHits = layeredHits,
            elapsedMs = elapsed,
            truncated = truncated,
        )
    }

    override fun observeAtoms(layer: MemoryLayer?): Flow<List<MemoryAtom>> =
        changes.asStateFlow().map { snap ->
            if (layer == null) snap else snap.filter { it.layer == layer }
        }

    override fun observeDistillation(): Flow<DistillationEvent> = events.asSharedFlow()

    override suspend fun snapshot(): MemorySnapshot = mutex.withLock {
        MemorySnapshot.fromAtoms(atoms.values.toList())
    }

    override suspend fun forget(atomId: String) {
        mutex.withLock { atoms.remove(atomId) }
        publishChange()
    }

    override suspend fun forgetScenario(scenarioId: String) {
        mutex.withLock {
            val ids = atoms.entries
                .filter { (_, atom) -> atom.scenarioId == scenarioId }
                .map { it.key }
            ids.forEach { atoms.remove(it) }
        }
        publishChange()
    }

    /** Synchronous accessor for tests. NOT on the public interface. */
    fun rawAtoms(): List<MemoryAtom> = atoms.values.toList()

    /** Synchronous accessor for tests. NOT on the public interface. */
    fun rawTurns(): List<ConversationTurn> = turns.toList()

    /** Filter the [changes] flow to a specific [layer]. Convenience
     *  overload for the common "L1 only" subscribe case. */
    fun l1Changes(): Flow<List<MemoryAtom>> = changes.asStateFlow()
        .map { snap -> snap.filter { it.layer == MemoryLayer.L1 } }

    // ----- internals ----------------------------------------------------

    private fun publishChange() {
        // Snapshot outside the mutex so collectors don't see a
        // half-mutated map.
        changes.value = atoms.values.toList()
    }

    private fun l0AtomFor(turn: ConversationTurn): MemoryAtom = MemoryAtom(
        id = UUID.randomUUID().toString(),
        layer = MemoryLayer.L0,
        text = "[${turn.speaker}] ${turn.text}",
        scenarioId = null,
        agentId = defaultContext.agentId,
        teamId = defaultContext.teamId,
        userId = defaultContext.userId,
        tier = defaultContext.tier,
        createdAtMs = turn.timestampMs,
    )

    private fun packBudget(
        scored: List<ScoredAtom>,
        budget: RecallBudget,
    ): Pair<Boolean, List<ScoredAtom>> {
        var total = 0
        val out = ArrayList<ScoredAtom>(scored.size)
        var truncated = false
        for (sa in scored) {
            val len = sa.atom.text.length
            // Always include the first atom — empty result is worse
            // than a slightly-too-long one.
            if (out.isNotEmpty() && total + len > budget.maxChars) {
                truncated = true
                break
            }
            out.add(sa)
            total += len
        }
        return truncated to out
    }

    /** Convenience constructor for tests + the preview UI. */
    companion object {
        fun withDefaults(): InMemoryConversationMemory =
            InMemoryConversationMemory(
                distiller = com.meshlit.core.agentmemory.distill.HeuristicDistiller(),
            )
    }
}