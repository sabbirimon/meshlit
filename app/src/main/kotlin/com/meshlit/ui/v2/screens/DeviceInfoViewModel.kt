package com.meshlit.ui.v2.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.bootstrap.BootstrapSnapshotProvider
import com.meshlit.core.bootstrap.BootstrapCoordinator
import com.meshlit.core.bootstrap.BootstrapSnapshot
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.probe.HardwareProfilerRegistry
import com.meshlit.core.probe.LiveHardwareMonitor
import com.meshlit.core.registry.ServiceRegistry
import com.meshlit.core.role.RoleManager
import com.meshlit.di.koinInject
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Backs the v2 `DeviceInfoScreen`.
 *
 * Merges five StateFlows into a single `DeviceInfoUiState`:
 *  - `BootstrapSnapshotProvider.snapshotFlow` — node id, flags, report
 *  - `RoleManager.decision` — role + confidence + reasons
 *  - `HardwareProfilerRegistry.latest` — capability snapshot
 *  - `ServiceRegistry.list` — registered services
 *  - `SettingsRepository.displayNameFlow` — user-editable name
 *
 * The merge is hot: whenever any source emits a new value, the
 * `Ready` state is rebuilt in one pass. The `isReprobing` /
 * `isRebootstrapping` flags are toggled by command events so the
 * UI can disable the corresponding buttons while they're running.
 *
 * Commands:
 *  - [onCopyNodeId]           — returns the node id for the screen to feed
 *                               to the clipboard. No state change.
 *  - [onDisplayNameChange]    — local-only draft (kept in the screen's
 *                               `TextField` state); the VM doesn't track
 *                               the draft.
 *  - [onSaveDisplayName]      — persists the trimmed value via
 *                               `SettingsRepository.setDisplayName`.
 *  - [onReprobeHardware]      — re-runs the profiler suite. The
 *                               resulting `HardwareCapability` flows
 *                               into `latest` and triggers another
 *                               state rebuild.
 *  - [onForceRebootstrap]     — re-runs `BootstrapCoordinator.boot()`
 *                               and re-publishes the snapshot. The
 *                               snapshot flow then triggers another
 *                               state rebuild.
 *
 * The five-listener `combine` runs on `viewModelScope` so its
 * lifetime is tied to the screen. When the user pops back, the
 * merge stops automatically.
 */
class DeviceInfoViewModel(
    private val env: DeviceInfoEnvironment = koinInject(),
    private val snapshotProvider: BootstrapSnapshotProvider = koinInject(),
    private val roleManager: RoleManager = koinInject(),
    private val profiler: HardwareProfilerRegistry = koinInject(),
    private val liveMonitor: LiveHardwareMonitor = koinInject(),
    private val registry: ServiceRegistry = koinInject(),
    private val settings: SettingsRepository = koinInject(),
    private val bootstrapCoordinator: BootstrapCoordinator = koinInject(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeviceInfoUiState>(DeviceInfoUiState.Loading)
    val uiState: StateFlow<DeviceInfoUiState> = _uiState

    private val _reprobing = MutableStateFlow(false)
    private val _rebootstrapping = MutableStateFlow(false)

    init {
        // Start the Castro-style real-time monitor. It feeds
        // `liveMonitor.current` (which the merge below reads) so
        // the UI sees 1 Hz refreshes of CPU / memory / thermal /
        // battery / network without any further plumbing. The
        // monitor keeps rolling buffers for sparklines; the
        // combine() pulls them in as `LiveSeries`.
        liveMonitor.start()

        // The combine() builder caps at 5 flows per overload. We
        // first fold the 5 read-only sources into one tuple, then
        // fold that with the 6-axis rolling-buffers + live-running
        // flag, then finally with `_reprobing` / `_rebootstrapping`.
        // `liveMonitor.current` is layered on top of
        // `profiler.latest` — when the monitor is running it
        // emits on every tick, otherwise `profiler.latest`
        // carries the last manual re-probe.
        val sources = combine(
            snapshotProvider.snapshotFlow(),
            roleManager.decision,
            liveMonitor.current,
            registry.list(),
            settings.displayNameFlow,
        ) { snapshot, decision, hardware, services, displayOverride ->
            SourcesState(
                snapshot = snapshot,
                decision = decision,
                hardware = hardware,
                services = services,
                displayOverride = displayOverride,
            )
        }
        val liveState = combine(
            liveMonitor.running,
            liveMonitor.cpuSeries,
            liveMonitor.memorySeries,
            liveMonitor.thermalSeries,
            liveMonitor.batterySeries,
        ) { running, cpu, mem, therm, batt ->
            LiveState(running, cpu, mem, therm, batt, emptyList(), emptyList())
        }
        // combine() of 5 inputs is the max — wire the network
        // series through a second fold, then the disk series
        // through a third fold, so the ready state includes all 6.
        val liveStateWithNetwork = combine(
            liveState,
            liveMonitor.networkSeries,
        ) { base, net -> base.copy(network = net) }
        val liveStateWithDisk = combine(
            liveStateWithNetwork,
            liveMonitor.diskSeries,
        ) { base, disk -> base.copy(disk = disk) }
        // `combine` of two flows (the source tuple + the
        // `_reprobing` flag) is well-typed and easy to read.
        combine(sources, liveStateWithDisk, _reprobing, _rebootstrapping) { state, live, reprobing, rebootstrapping ->
            if (state.snapshot == null) {
                DeviceInfoUiState.Failure(
                    message = "Bootstrap snapshot not published yet",
                )
            } else {
                val snapshot = state.snapshot
                val defaultDisplayName = env.deviceInfo.displayName
                val effectiveDisplayName =
                    state.displayOverride.ifBlank { defaultDisplayName }
                val hardware = state.hardware
                val hardwareAgeMs = if (hardware == null) Long.MAX_VALUE
                    else System.currentTimeMillis() - hardware.timestampMs
                DeviceInfoUiState.Ready(
                    nodeIdHex = env.nodeIdHex.ifBlank { snapshot.nodeId },
                    displayName = effectiveDisplayName,
                    defaultDisplayName = defaultDisplayName,
                    localIpAddress = env.localIpAddress,
                    httpServerPort = env.httpServerPort,
                    capabilityTier = env.capabilityTier,
                    role = state.decision,
                    hardware = hardware,
                    hardwareAgeMs = hardwareAgeMs,
                    bootstrapSnapshot = snapshot,
                    services = state.services,
                    isReprobing = reprobing,
                    isRebootstrapping = rebootstrapping,
                    isLiveMonitor = live.running,
                    cpuSeries = live.cpu,
                    memorySeries = live.memory,
                    thermalSeries = live.thermal,
                    batterySeries = live.battery,
                    networkSeries = live.network,
                    diskSeries = live.disk,
                )
            }
        }.onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    private data class LiveState(
        val running: Boolean,
        val cpu: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
        val memory: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
        val thermal: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
        val battery: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
        val network: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
        val disk: List<com.meshlit.core.probe.LiveHardwareMonitor.SamplePoint>,
    )

    /** Intermediate holder for the 5 read-only sources so we can
     *  `combine` them into a single `Flow` and then re-combine
     *  with the two `MutableStateFlow` flags. */
    private data class SourcesState(
        val snapshot: BootstrapSnapshot?,
        val decision: com.meshlit.core.role.RoleDecision,
        val hardware: com.meshlit.core.probe.HardwareCapability?,
        val services: List<com.meshlit.core.registry.ServiceDescriptor>,
        val displayOverride: String,
    )

    /**
     * Returns the node id for the screen to feed to the clipboard.
     * Pulled synchronously from the current `Ready` state so the
     * screen doesn't have to manage a separate string.
     */
    fun nodeIdForCopy(): String =
        (_uiState.value as? DeviceInfoUiState.Ready)?.nodeIdHex ?: ""

    /**
     * Persist a new display name. Blank values clear the override
     * and fall back to the auto-derived default. The screen should
     * fire a toast on completion.
     */
    fun onSaveDisplayName(value: String) {
        viewModelScope.launch {
            settings.setDisplayName(value)
        }
    }

    /**
     * Re-run the profiler suite and refresh the hardware card.
     * The `isReprobing` flag is cleared automatically when the
     * `profiler.latest` flow emits the new snapshot (it carries
     * a fresh `timestampMs` so the merge picks it up).
     */
    fun onReprobeHardware() {
        if (_reprobing.value) return
        _reprobing.value = true
        viewModelScope.launch {
            try {
                profiler.reprobe()
            } catch (t: Throwable) {
                android.util.Log.w("MeshlitDeviceInfo", "reprobe failed", t)
            } finally {
                _reprobing.value = false
            }
        }
    }

    /**
     * Toggle the Castro-style live monitor. The monitor ticks
     * once per second by default; stopping it clears the periodic
     * sampler (the sparkline buffers are preserved). When the
     * monitor is restarted, the existing buffers stay in place
     * unless the user explicitly calls [LiveHardwareMonitor.reset].
     */
    fun onToggleLiveMonitor() {
        if (liveMonitor.running.value) liveMonitor.stop()
        else liveMonitor.start()
    }

    /**
     * Re-run the entire bootstrap sequence. Re-publishes the
     * snapshot via `BootstrapSnapshotProvider`. The merge picks up
     * the new snapshot and rebuilds the state.
     */
    fun onForceRebootstrap() {
        if (_rebootstrapping.value) return
        _rebootstrapping.value = true
        viewModelScope.launch {
            try {
                when (val res = bootstrapCoordinator.boot()) {
                    is MeshlitResult.Success -> {
                        snapshotProvider.publish(res.value)
                        env.setStableNodeId(res.value.nodeId)
                    }
                    is MeshlitResult.Failure -> {
                        android.util.Log.w(
                            "MeshlitDeviceInfo",
                            "force reboot failed: ${res.error.tag}",
                        )
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.w("MeshlitDeviceInfo", "force reboot threw", t)
            } finally {
                _rebootstrapping.value = false
            }
        }
    }

    override fun onCleared() {
        // Stop the live sampling loop so we don't burn battery
        // while the screen is off-stage. The `viewModelScope` is
        // about to be cancelled by the framework; stopping the
        // monitor here is a belt-and-braces measure so the
        // interval ticker dies before scope cancellation can
        // race with the next sample.
        liveMonitor.stop()
        super.onCleared()
    }

    companion object {
        fun factory(): androidx.lifecycle.ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DeviceInfoViewModel(
                        env = koinInject(),
                        snapshotProvider = koinInject(),
                        roleManager = koinInject(),
                        profiler = koinInject(),
                        liveMonitor = koinInject(),
                        registry = koinInject(),
                        settings = koinInject(),
                        bootstrapCoordinator = koinInject(),
                    )
                }
            }
    }
}
