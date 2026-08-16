package com.meshlit.core.role

import com.meshlit.core.probe.HardwareCapability

/**
 * Picks the best [Role] for a given [HardwareCapability] by scoring
 * every role and returning the highest scorer.
 *
 * Ties are broken by [Role] enum order (Idle < Brain < Tool <
 * Monitor < Relay) — this is deterministic across processes and
 * survives a cluster split without a separate tiebreaker signal.
 */
object RoleSuggestionEngine {

    fun suggest(capability: HardwareCapability): RoleDecision {
        val scored = Role.entries.associateWith { RolePolicy.score(capability, it) }
        val (bestRole, bestScore) = scored.maxBy { it.value }
        return RoleDecision(
            role = bestRole,
            confidence = bestScore,
            reasons = RolePolicy.explain(capability, bestRole),
            scores = scored,
        )
    }
}

/**
 * The decision returned by [RoleSuggestionEngine]. Includes every
 * role's score (not just the winner's) so the UI can show
 * "Brain=0.83, Tool=0.51, Monitor=0.40, Idle=0.05" instead of a
 * single number.
 */
data class RoleDecision(
    val role: Role,
    val confidence: Float,
    val reasons: List<String>,
    val scores: Map<Role, Float>,
)
