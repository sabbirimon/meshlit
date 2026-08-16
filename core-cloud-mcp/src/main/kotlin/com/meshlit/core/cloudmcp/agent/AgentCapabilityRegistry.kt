package com.meshlit.core.cloudmcp.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Runtime state for every [AgentCapability] the user has toggled on.
 *
 * This is the single source of truth for "what can the agent do
 * right now?". The agent loop reads [enabledCapabilities] before
 * deciding which tools to advertise to the LLM; a tool is only
 * registered when:
 *
 *  1. The user has flipped its master toggle in Settings
 *     (`SettingsRepository.agentCapabilityEnabled(capability)`).
 *  2. The runtime permission (if any) has been granted.
 *  3. For high-risk capabilities (`Sms`, `Storage`), the
 *     per-target allowlist (e.g. SMS recipients) has at least one
 *     entry the agent is allowed to act on.
 *
 * **Why this layer exists:**
 * The agent loop never asks the user for permission directly — it
 * asks the registry. The registry either accepts the call (because
 * everything is permitted) or returns an `agent.permission-denied`
 * tool result. The agent loop never has to know which Android API
 * it routed through, only that the gate was lifted.
 *
 * **Why this is in `:core-cloud-mcp/agent/` (not `core-cloud-mcp`):**
 * So the Android-system-binding code (`Activity.startActivity`,
 * `SmsManager`, `MediaRecorder`) lives in the app module. The
 * registry stays a pure-data holder so it can be unit-tested
 * without any Android framework.
 */
class AgentCapabilityRegistry {
    private val _state = MutableStateFlow<Map<AgentCapability, CapabilityState>>(emptyMap())
    val state: StateFlow<Map<AgentCapability, CapabilityState>> = _state.asStateFlow()

    /**
     * Per-capability state. `permissionGranted` is fed by the
     * platform (the app checks the permission before each call);
     * `enabledByUser` comes from `SettingsRepository`; `allowlist`
     * is the per-target set (SMS recipients, storage root URIs).
     */
    data class CapabilityState(
        val enabledByUser: Boolean = false,
        val permissionGranted: Boolean = false,
        val allowlist: Set<String> = emptySet(),
    )

    /**
     * Update one capability's state. Called by `MeshlitApplication`
     * whenever the user flips a toggle, grants a permission, or
     * adds an allowlist entry.
     */
    fun update(capability: AgentCapability, state: CapabilityState) {
        _state.update { it + (capability to state) }
    }

    /**
     * Read one capability's state, defaulting to "off / ungranted"
     * for capabilities the user has never touched.
     */
    fun get(capability: AgentCapability): CapabilityState =
        _state.value[capability] ?: CapabilityState()

    /**
     * List the capabilities that are *currently usable* — both the
     * user toggle and the runtime permission are satisfied. The
     * agent loop calls this when it builds the `tools[]` payload
     * for the next LLM request.
     */
    fun enabledCapabilities(): List<AgentCapability> = _state.value
        .filterValues { it.enabledByUser && it.permissionGranted }
        .keys
        .toList()

    /**
     * One-shot decision: would a tool call to [target] (e.g. an SMS
     * recipient number, or a storage URI) be permitted right now?
     *
     * `target == null` → caller doesn't have a per-action target
     * (location, camera, network-state). Allowed iff the capability
     * is on + permission is granted.
     *
     * `target != null` → additionally checks the per-target
     * allowlist. If the capability has no allowlist concept (e.g.
     * location), the target is informational and ignored.
     */
    fun isAllowed(capability: AgentCapability, target: String? = null): Boolean {
        val s = get(capability)
        if (!s.enabledByUser) return false
        if (capability.permission != null && !s.permissionGranted) return false
        if (target != null && s.allowlist.isNotEmpty() && !s.allowlist.contains(target)) {
            return false
        }
        return true
    }
}