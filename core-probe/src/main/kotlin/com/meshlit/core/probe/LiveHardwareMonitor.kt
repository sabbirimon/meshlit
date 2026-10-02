package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Castro-style real-time device monitor. Runs a periodic sampling
 * loop that re-probes every axis (`cpu`, `memory`, `thermal`,
 * `battery`, `network`, `npu`) and publishes:
 *
 *  - a hot `current: StateFlow<HardwareCapability?>` for the "live"
 *    numeric reads the UI binds to,
 *  - per-axis `series: StateFlow<List<SamplePoint>>` rolling buffers
 *    that feed the sparklines (default 60 samples = ~60 s at the
 *    1000 ms tick rate).
 *
 * Sampling is driven by [start] on the supplied `scope`; [stop]
 * cancels the loop. The registry's [HardwareProfilerRegistry.latest]
 * is updated as a side-effect so any view model that already
 * observes the registry continues to work — this monitor layers on
 * top of the registry, not in place of it.
 *
 * Defaults:
 *  - `tickIntervalMs = 1000` (1 Hz, the Castro default).
 *  - `rollingWindow = 60` samples.
 *
 * Power behaviour:
 *  - Tick interval is fixed; callers should pause via [stop] when
 *    the host screen is off-screen to spare battery. The Device
 *    Info screen drives this through `DisposableEffect`.
 *  - A single failing profiler does not abort the loop — the
 *    registry already folds a failing axis into a `ProfileSample(
 *    score = 0f, rawValue = "")` so the next tick recovers
 *    cleanly.
 */
open class LiveHardwareMonitor(
    private val profiler: HardwareProfilerRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val tickIntervalMs: Long = 1_000L,
    private val rollingWindow: Int = 60,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val diskBaseline: DiskIoBaseline = DiskIoBaseline(),
) {

    private val log = logger("LiveHardwareMonitor")

    private val _current = MutableStateFlow<HardwareCapability?>(null)
    val current: StateFlow<HardwareCapability?> = _current.asStateFlow()

    private val _cpuSeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val cpuSeries: StateFlow<List<SamplePoint>> = _cpuSeries.asStateFlow()

    private val _memorySeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val memorySeries: StateFlow<List<SamplePoint>> = _memorySeries.asStateFlow()

    private val _thermalSeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val thermalSeries: StateFlow<List<SamplePoint>> = _thermalSeries.asStateFlow()

    private val _batterySeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val batterySeries: StateFlow<List<SamplePoint>> = _batterySeries.asStateFlow()

    private val _networkSeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val networkSeries: StateFlow<List<SamplePoint>> = _networkSeries.asStateFlow()

    private val _diskSeries = MutableStateFlow<List<SamplePoint>>(emptyList())
    val diskSeries: StateFlow<List<SamplePoint>> = _diskSeries.asStateFlow()

    private val _rawReadout = MutableStateFlow<RawReadout?>(null)
    val rawReadout: StateFlow<RawReadout?> = _rawReadout.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var loop: Job? = null

    /**
     * Begin the periodic sampling loop. Idempotent — calling
     * [start] while already running is a no-op. The loop runs
     * until [stop] is called or the [scope] is cancelled.
     */
    fun start() {
        if (_running.value) return
        _running.value = true
        loop = scope.launch {
            log.info(
                "monitor.start",
                "live hardware monitor started",
                mapOf("tickMs" to tickIntervalMs, "window" to rollingWindow),
            )
            // First sample immediately so the UI doesn't blink
            // through 1 s of empty state on entry.
            sampleOnce()
            while (true) {
                delay(tickIntervalMs)
                sampleOnce()
            }
        }
    }

    /**
     * Cancel the sampling loop. The next [start] restarts it
     * from scratch (rolling buffers are preserved, not cleared —
     * call [reset] explicitly if the caller wants a clean
     * window).
     */
    fun stop() {
        if (!_running.value) return
        _running.value = false
        loop?.cancel()
        loop = null
        log.info("monitor.stop", "live hardware monitor stopped")
    }

    /** Wipe the rolling buffers (used when starting a fresh
     *  recording session). */
    fun reset() {
        _cpuSeries.value = emptyList()
        _memorySeries.value = emptyList()
        _thermalSeries.value = emptyList()
        _batterySeries.value = emptyList()
        _networkSeries.value = emptyList()
        _current.value = null
    }

    private suspend fun sampleOnce() {
        val now = clock()
        val res = profiler.profileAll()
        when (res) {
            is MeshlitResult.Success -> {
                val cap = res.value
                _current.value = cap
                pushSample(_cpuSeries, cap.cpu, now)
                pushSample(_memorySeries, cap.memory, now)
                pushSample(_thermalSeries, cap.thermal, now)
                pushSample(_batterySeries, cap.battery, now)
                pushSample(_networkSeries, cap.network, now)
                // Sample the parallel disk-IO baseline (independent
                // of the profiler registry) so the Resource Monitor
                // chart can render disk throughput even though no
                // DiskProfiler is registered.
                val (diskPct, diskRaw) = diskBaseline.sample()
                pushSample(_diskSeries, ProfileSample(score = diskPct, rawValue = diskRaw), now)
                // Build the raw-readout struct the screen binds to
                // for "Current" badges on each Resource Monitor
                // chart. Keeps the live values + the formatted
                // raw strings in a single value so the UI can
                // render in one pass.
                _rawReadout.value = RawReadout(
                    cpuPct = cap.cpu.score ?: 0f,
                    cpuRaw = cap.cpu.rawValue,
                    memoryPct = cap.memory.score ?: 0f,
                    memoryRaw = cap.memory.rawValue,
                    thermalPct = cap.thermal.score ?: 0f,
                    thermalRaw = cap.thermal.rawValue,
                    batteryPct = cap.battery.score ?: 0f,
                    batteryRaw = cap.battery.rawValue,
                    networkPct = cap.network.score ?: 0f,
                    networkRaw = cap.network.rawValue,
                    diskPct = diskPct,
                    diskRaw = diskRaw,
                    timestampMs = now,
                )
            }
            is MeshlitResult.Failure -> {
                log.warn(
                    "monitor.sample.fail",
                    "live sample failed: ${res.error.tag}",
                )
            }
        }
    }

    private fun pushSample(series: MutableStateFlow<List<SamplePoint>>, sample: ProfileSample, now: Long) {
        val score = sample.score ?: 0f
        series.update { prev ->
            val next = prev + SamplePoint(timestampMs = now, value = score, raw = sample.rawValue)
            if (next.size <= rollingWindow) next else next.takeLast(rollingWindow)
        }
    }

    /**
     * Single-observation point on a rolling sparkline series.
     *
     * `value` is the `0f..1f` normalised score; `raw` is the
     * profile's raw string (e.g. "4096" for memory in MB or
     * "50" for battery percent) so sparkline tooltips can show
     * the underlying number.
     */
    data class SamplePoint(
        val timestampMs: Long,
        val value: Float,
        val raw: String,
    )

    /**
     * Snapshot of every metric the Resource Monitor chart row
     * binds to. Built once per tick so the UI can render the
     * "current" badges + chart values without re-deriving them.
     *
     * `*Pct` is normalised 0..1f (matching the rest of the
     * profiler contract); `*Raw` is the human-friendly string
     * for tooltips + the badge label.
     */
    data class RawReadout(
        val cpuPct: Float,
        val cpuRaw: String,
        val memoryPct: Float,
        val memoryRaw: String,
        val thermalPct: Float,
        val thermalRaw: String,
        val batteryPct: Float,
        val batteryRaw: String,
        val networkPct: Float,
        val networkRaw: String,
        val diskPct: Float,
        val diskRaw: String,
        val timestampMs: Long,
    )
}
