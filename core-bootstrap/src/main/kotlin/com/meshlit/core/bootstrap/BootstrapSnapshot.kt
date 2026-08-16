package com.meshlit.core.bootstrap

/**
 * The frozen state returned by [BootstrapCoordinator.boot]. Captures
 * everything the rest of the app needs to know about the node's
 * identity and configuration, with no further async work required.
 *
 * @property nodeId The stable per-install node identity. Persisted
 *  on first boot, never re-minted across restarts — see Fix 4 in
 *  the build guide.
 * @property flags Every flag's current value at boot time.
 * @property role The role decided from the most-recent hardware
 *  snapshot, or `null` when no profiler ran yet.
 * @property report Per-phase timings + outcomes.
 */
data class BootstrapSnapshot(
    val nodeId: String,
    val flags: Map<String, Boolean>,
    val role: com.meshlit.core.role.RoleDecision? = null,
    val report: BootstrapReport,
)

/**
 * Recorded timings + outcomes for each phase the coordinator ran.
 *
 * @property entries One entry per phase that ran, in the order it
 *  ran. The first entry is always `Config`.
 */
data class BootstrapReport(
    val entries: List<Entry>,
) {
    data class Entry(
        val phase: BootstrapPhase,
        val outcome: Outcome,
        val durationMs: Long,
    )

    enum class Outcome { Ok, Skipped, Failed }

    val okPhases: List<BootstrapPhase> get() =
        entries.filter { it.outcome == Outcome.Ok }.map { it.phase }

    companion object {
        val Empty: BootstrapReport = BootstrapReport(emptyList())
    }
}
