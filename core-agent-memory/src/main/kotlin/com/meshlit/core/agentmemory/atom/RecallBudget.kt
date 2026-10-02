package com.meshlit.core.agentmemory.atom

/**
 * Budget for a single [com.meshlit.core.agentmemory.ConversationMemory.recall]
 * call. The retrieval layer MUST honour every field — the user prompt
 * will blow past the context window if any one is dropped.
 *
 * Defaults are tuned for the Phase 0 agent runtime (3B SmolLM2 on
 * a mid-spec device, ~2 K-token effective prompt).
 *
 * @property maxAtoms Hard cap on atoms returned per layer slice.
 *  L1 recall defaults to 32, L2/L3 persona to 4, L0 verbatim to 8.
 * @property maxChars Hard cap on the total character count across
 *  every returned atom. The retrieval loop truncates the tail
 *  (lowest-scoring atoms) once this is exceeded.
 * @property timeoutMs Upper bound on the recall latency. The
 *  retrieval loop returns whatever it has at this deadline rather
 *  than blocking the prompt path.
 * @property includeL0 Whether to drop down to the verbatim L0 layer.
 *  Off by default — L0 is the expensive fallback the agent only
 *  reaches for when L1 retrieval misses.
 */
data class RecallBudget(
    val maxAtoms: Int = 32,
    val maxChars: Int = 4_000,
    val timeoutMs: Long = 250L,
    val includeL0: Boolean = false,
) {
    init {
        require(maxAtoms in 1..512) { "maxAtoms out of range: $maxAtoms" }
        require(maxChars in 64..65_536) { "maxChars out of range: $maxChars" }
        require(timeoutMs in 1..10_000L) { "timeoutMs out of range: $timeoutMs" }
    }
}

/**
 * Result of a recall call.
 *
 * @property atoms The selected atoms, ordered by descending score
 *  (highest first). Each atom carries its score in [scored].
 * @property layerHits The number of candidates surfaced per layer.
 *  Surfaced in telemetry so the recall funnel can be tuned.
 * @property elapsedMs Wall-clock duration of the recall call.
 * @property truncated Whether the budget (chars / atoms / timeout)
 *  cut the candidate list short.
 */
data class RecallResult(
    val atoms: List<ScoredAtom>,
    val layerHits: Map<MemoryLayer, Int>,
    val elapsedMs: Long,
    val truncated: Boolean,
)

/** A scored atom — the score is in [0, 1] (higher = better). */
data class ScoredAtom(
    val atom: MemoryAtom,
    val score: Float,
)