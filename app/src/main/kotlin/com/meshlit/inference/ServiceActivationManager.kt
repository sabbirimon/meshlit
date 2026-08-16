package com.meshlit.inference

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.content.Intent

/**
 * Phase 4.x — `Commit 32: ServiceActivationManager + idle-kill`.
 *
 * Wraps the start/stop/intent dance around
 * [InferenceForegroundService]. The service is the long-lived
 * inference engine; without a manager, every caller (Jobs,
 * Agent, Coding, Setup wizard, drawer's Resume button, the
 * bundled-model auto-load at cold start) has to remember to
 * stop the service explicitly when they're done. They
 * usually forget, and the FGS leaks a notification forever.
 *
 * The `ServiceActivationManager` solves this by:
 *
 *   1. Tracking the last-activity timestamp (`recordActivity`).
 *   2. Running a periodic idle-kill coroutine that
 *      automatically `stopService()`s the FGS after
 *      [idleTimeoutMs] (default 10 minutes) of no activity.
 *   3. Exposing a `requestActivation(...)` entry point so
 *      screens don't have to know whether the FGS is already
 *      running — they just say "I need inference" and the
 *      manager handles `startService` idempotently.
 *   4. Exposing a `release(...)` API for callers that want
 *      to short-circuit the idle window (e.g. the drawer's
 *      "Stop" button).
 *
 * The manager is process-wide (one instance per app) and
 * lives on `MeshlitApplication` so the FGS itself can
 * re-stamp last-activity on every `coordinator.infer` / load.
 *
 * `@param idleTimeoutMs` how long the FGS may sit idle
 *   (no INFER, no LOAD, no UNLOAD) before being killed.
 *   Default 10 minutes; a chatty user never sees it.
 *   A user who sends one prompt and walks away gets the
 *   FGS cleaned up automatically.
 */
class ServiceActivationManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val idleTimeoutMs: Long = 10L * 60L * 1000L,
) {

    private val log = logger("ServiceActivationManager")
    private val mutex = Mutex()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _lastActivityMs = MutableStateFlow(System.currentTimeMillis())
    val lastActivityMs: StateFlow<Long> = _lastActivityMs.asStateFlow()

    private var idleKiller: Job? = null

    init {
        startIdleKiller()
    }

    /**
     * Stamp "now" as the last activity. Called by the FGS
     * on every INFER / LOAD / UNLOAD so the idle-kill
     * window restarts.
     */
    fun recordActivity() {
        _lastActivityMs.value = System.currentTimeMillis()
    }

    /**
     * Ensure the FGS is running. Idempotent — calling it
     * twice with the same path is a no-op. Starts the
     * service if it isn't already up; refreshes the
     * timestamp either way.
     */
    suspend fun requestActivation(reason: String = "unspecified") {
        mutex.withLock {
            // startForInference is the documented entry
            // point — it tolerates the service already
            // running (Android skips the redundant start).
            runCatching {
                InferenceForegroundService.startForInference(context)
            }.onFailure {
                log.warn(
                    "service_activation.start.fail",
                    "startForInference failed",
                    mapOf("reason" to reason, "error" to (it.message ?: "")),
                )
            }
            _active.value = true
            recordActivity()
        }
    }

    /**
     * Explicitly stop the FGS. Used by the drawer's "Stop"
     * button and by the idle-kill coroutine. Sets
     * [active] to false synchronously so the UI updates
     * immediately, then fires the actual `stopService`.
     */
    suspend fun release(reason: String = "unspecified") {
        mutex.withLock {
            runCatching {
                context.stopService(
                    Intent(context, InferenceForegroundService::class.java),
                )
            }.onFailure {
                log.warn(
                    "service_activation.stop.fail",
                    "stopService failed",
                    mapOf("reason" to reason, "error" to (it.message ?: "")),
                )
            }
            _active.value = false
        }
    }

    private fun startIdleKiller() {
        if (idleKiller?.isActive == true) return
        idleKiller = scope.launch {
            while (true) {
                delay(30_000L) // check every 30s
                val last = _lastActivityMs.value
                val idleFor = System.currentTimeMillis() - last
                if (_active.value && idleFor >= idleTimeoutMs) {
                    log.info(
                        "service_activation.idle_kill",
                        "FGS idle for >${idleTimeoutMs}ms — releasing",
                        mapOf("idle_for_ms" to idleFor.toString()),
                    )
                    release(reason = "idle_kill")
                }
            }
        }
    }

    fun cancel() {
        idleKiller?.cancel()
        idleKiller = null
    }
}
