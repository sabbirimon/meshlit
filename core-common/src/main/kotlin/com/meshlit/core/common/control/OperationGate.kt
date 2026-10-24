package com.meshlit.core.common.control

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable

@Serializable enum class ManagedFeature { INFERENCE, MODEL_TRANSFERS, CLUSTER, GATEWAY, CLOUD, SSH, BROWSER, CYBER, VM, TRAINING, MEDIA, RECOVERY, AUTOMATION, FILES, CRAWLER, HYPERL }
@Serializable data class OperationPolicy(val emergencyStopped: Boolean = false,
    val disabled: Set<ManagedFeature> = emptySet(), val agentDisabled: Set<ManagedFeature> = emptySet())
/** Admission and cancellation are independent of model output. Only the trusted human
 * configuration path may resume or enlarge permissions. A stop remains latched across restart. */
class OperationGate(initial: OperationPolicy = OperationPolicy(),
    allowedFeatures: Set<ManagedFeature> = ManagedFeature.entries.toSet(),
    private val persist: (OperationPolicy) -> Unit = {}) {
    // Build restrictions cannot be enlarged by a saved policy, an agent or Resume.
    private val buildFeatures = allowedFeatures.toSet()
    val supportedFeatures: Set<ManagedFeature> get() = buildFeatures.toSet()
    private val mutable = MutableStateFlow(initial)
    val policy = mutable.asStateFlow()
    private val active = mutableMapOf<Job, Pair<ManagedFeature, Boolean>>()
    @Synchronized fun requireAllowed(feature: ManagedFeature, agent: Boolean = false) {
        check(feature in buildFeatures) { "Unavailable in this build channel: ${feature.name}" }
        val p = mutable.value
        check(!p.emergencyStopped && feature !in p.disabled && (!agent || feature !in p.agentDisabled)) { "Operation stopped or disabled: ${feature.name}" }
    }
    @Synchronized fun setFeature(feature: ManagedFeature, enabled: Boolean, agentOnly: Boolean = false) {
        check(!enabled || feature in buildFeatures) { "Unavailable in this build channel: ${feature.name}" }
        val p = mutable.value
        val set = if (agentOnly) p.agentDisabled else p.disabled
        val updated = if (enabled) set - feature else set + feature
        val next = if (agentOnly) p.copy(agentDisabled = updated) else p.copy(disabled = updated)
        // Revocation is effective in memory even if persistent storage fails.
        if (!enabled) mutable.value = next
        if (!enabled) active.filterValues { it.first == feature && (!agentOnly || it.second) }.keys.toList().forEach { it.cancel(CancellationException("Feature revoked")) }
        persist(next); mutable.value = next
    }
    @Synchronized fun emergencyStop() {
        val next = mutable.value.copy(emergencyStopped = true)
        mutable.value = next
        active.keys.toList().forEach { it.cancel(CancellationException("Emergency stop")) }
        persist(next)
    }
    @Synchronized fun resumeHuman() { val next = mutable.value.copy(emergencyStopped = false); persist(next); mutable.value = next }
    suspend fun <T> run(feature: ManagedFeature, agent: Boolean = false, work: suspend () -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        synchronized(this@OperationGate) { requireAllowed(feature, agent); active[job] = feature to agent }
        try { work().also { currentCoroutineContext().ensureActive(); requireAllowed(feature, agent) } }
        finally { synchronized(this@OperationGate) { active.remove(job) } }
    }
}
