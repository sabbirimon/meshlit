package com.meshlit.ui.v2.screens

import com.meshlit.capability.CapabilityTier
import com.meshlit.core.bootstrap.BootstrapReport
import com.meshlit.core.bootstrap.BootstrapSnapshot
import com.meshlit.core.probe.HardwareCapability
import com.meshlit.core.probe.LiveHardwareMonitor
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.role.Role
import com.meshlit.core.role.RoleDecision

/**
 * Sealed `UiState` for the v2 `DeviceInfoScreen`.
 *
 * The DeviceInfo screen is the Hub-card → "Info" escape hatch. It
 * surfaces role, hardware, identity, bootstrap health, and
 * registered services — and exposes the actions the user can take
 * on those (copy node id, edit display name, force re-probe
 * hardware, force re-bootstrap).
 *
 * Reads from five sources:
 *  - `BootstrapSnapshotProvider` (snapshot, node id, flags, report)
 *  - `RoleManager.decision` (role name, confidence, reasons)
 *  - `HardwareProfilerRegistry.latest` (CPU/memory/etc.)
 *  - `ServiceRegistry.list` (services + their health)
 *  - `MeshlitApplication.displayName / localIpAddress / httpServerPort`
 *    (via the `viewModel.app` accessor)
 *
 * Compose-level commands: `onCopyNodeId`, `onDisplayNameChange`,
 * `onSaveDisplayName`, `onReprobeHardware`, `onForceRebootstrap`.
 * The screen renders a toast for each command's success/failure.
 */
sealed interface DeviceInfoUiState {

    /** Initial state — no bootstrap snapshot yet. The screen
     *  shows a single spinner and waits for the first emission. */
    object Loading : DeviceInfoUiState

    /**
     * All five sources have produced at least one value. The
     * `hardware` snapshot may be stale (older than
     * `timerMs` since last refresh); the screen shows a
     * "re-probe" pill in that case.
     */
    data class Ready(
        val nodeIdHex: String,
        val displayName: String,
        val defaultDisplayName: String,
        val localIpAddress: String,
        val httpServerPort: Int,
        val capabilityTier: CapabilityTier,
        val role: RoleDecision,
        val hardware: HardwareCapability?,
        val hardwareAgeMs: Long,
        val bootstrapSnapshot: BootstrapSnapshot,
        val services: List<ServiceDescriptor>,
        val isReprobing: Boolean = false,
        val isRebootstrapping: Boolean = false,
        val isLiveMonitor: Boolean = false,
        val cpuSeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
        val memorySeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
        val thermalSeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
        val batterySeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
        val networkSeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
        val diskSeries: List<LiveHardwareMonitor.SamplePoint> = emptyList(),
    ) : DeviceInfoUiState

    /** The bootstrap snapshot never appeared (the app is still
     *  coming up). The screen shows a retry affordance. */
    data class Failure(val message: String) : DeviceInfoUiState
}
