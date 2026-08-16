package com.meshlit.agent

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wires the pure-data [AgentCapabilityRegistry] to the persisted
 * SettingsRepository state. The registry is the single source of
 * truth for "what can the agent do right now?"; this holder
 * observes the SettingsRepository flows on a background scope and
 * updates the registry whenever a master toggle, a runtime
 * permission grant, or an allowlist entry changes.
 *
 * **Lifecycle:**
 *  - One instance per app, held by `MeshlitApplication`.
 *  - The holder starts collecting on construction; the underlying
 *    `CoroutineScope` lives for the lifetime of the process.
 *  - The holder also exposes `permissionGranted(capability)` —
 *    called by the activity when it sees a permission result so
 *    the registry reflects the system state without polling.
 */
class AgentCapabilityRegistryHolder(
    private val appContext: Context,
    private val settings: SettingsRepository,
) {
    val registry: AgentCapabilityRegistry = AgentCapabilityRegistry()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Initial sync — captures the current state on cold start
        // before any flow has emitted.
        for (cap in AgentCapability.entries) {
            val allowed = settings.agentCapabilityEnabledNow(cap.tag)
            val allowlist = settings.agentCapabilityAllowlistNow(cap.tag)
            val granted = isPermissionGranted(cap)
            registry.update(
                cap,
                AgentCapabilityRegistry.CapabilityState(
                    enabledByUser = allowed,
                    permissionGranted = granted,
                    allowlist = allowlist,
                ),
            )
        }

        // Subscribe to changes — combine the enabled-by-user flow
        // with the allowlist flow for one capability at a time so
        // each flow is independently reactive.
        for (cap in AgentCapability.entries) {
            scope.launch {
                combine(
                    settings.agentCapabilityEnabledFlow(cap.tag),
                    settings.agentCapabilityAllowlistFlow(cap.tag),
                ) { enabled, allowlist -> enabled to allowlist }
                    .collect { (enabled, allowlist) ->
                        registry.update(
                            cap,
                            AgentCapabilityRegistry.CapabilityState(
                                enabledByUser = enabled,
                                permissionGranted = isPermissionGranted(cap),
                                allowlist = allowlist,
                            ),
                        )
                    }
            }
        }
    }

    /**
     * Called by the activity after it handles a permission result
     * (either from the system prompt or after the user flips the
     * toggle in Settings). Re-reads the runtime permission and
     * pushes it into the registry.
     */
    fun refreshPermission(capability: AgentCapability) {
        val current = registry.get(capability)
        registry.update(
            capability,
            current.copy(permissionGranted = isPermissionGranted(capability)),
        )
    }

    private fun isPermissionGranted(capability: AgentCapability): Boolean {
        val perm = capability.permission ?: return true
        return ContextCompat.checkSelfPermission(appContext, perm) ==
            PackageManager.PERMISSION_GRANTED
    }
}