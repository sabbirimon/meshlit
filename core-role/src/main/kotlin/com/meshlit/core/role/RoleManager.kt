package com.meshlit.core.role

import com.meshlit.core.common.logger
import com.meshlit.core.probe.HardwareCapability
import com.meshlit.core.probe.HardwareProfilerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Maintains the live role decision for a single node. Subscribes to
 * a [HardwareProfilerRegistry] and re-runs [RoleSuggestionEngine]
 * whenever the capability snapshot changes.
 *
 * Exposes a [StateFlow<RoleDecision>] so the cluster UI / router can
 * react without polling.
 */
class RoleManager(
    private val profiler: HardwareProfilerRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val log = logger("RoleManager")

    private val state = MutableStateFlow<RoleDecision>(
        RoleDecision(
            role = Role.Idle,
            confidence = RolePolicy.scoreIdle(),
            reasons = listOf("no capability snapshot yet"),
            scores = Role.entries.associateWith { if (it == Role.Idle) RolePolicy.scoreIdle() else 0f },
        ),
    )

    /** Live role decision. */
    val decision: StateFlow<RoleDecision> = state.asStateFlow()

    /** Subscribe to the profiler and re-suggest on every new
     *  snapshot. Safe to call once. */
    fun start() {
        // The profiler doesn't expose a flow directly; we call it
        // once on start and emit. A future revision can subscribe
        // to a periodic re-profile channel.
        scope.launchCoroutine { initialRefresh() }
    }

    private suspend fun initialRefresh() {
        when (val res = profiler.profileAll()) {
            is com.meshlit.core.common.MeshlitResult.Success ->
                updateWith(res.value)
            is com.meshlit.core.common.MeshlitResult.Failure ->
                log.warn("role.profile_fail", "initial profile failed: ${res.error.tag}")
        }
    }

    /** Push a new capability snapshot through the policy. Used by
     *  tests and by the bootstrap coordinator when it has a
     *  snapshot in hand. */
    fun updateWith(capability: HardwareCapability) {
        val decision = RoleSuggestionEngine.suggest(capability)
        state.value = decision
        log.info(
            "role.suggest",
            "role re-suggested",
            mapOf(
                "role" to decision.role.name,
                "confidence" to "%.2f".format(decision.confidence),
                "reasons" to decision.reasons.joinToString(";"),
            ),
        )
    }
}

// Kotlin idiom for `scope.launch { ... }` without pulling in the
// extension at the call site. Kept in this file so the file's
// coroutine imports stay local.
private fun CoroutineScope.launchCoroutine(block: suspend () -> Unit) {
    kotlinx.coroutines.launch(this.coroutineContext + kotlinx.coroutines.Dispatchers.Default) {
        block()
    }
}
