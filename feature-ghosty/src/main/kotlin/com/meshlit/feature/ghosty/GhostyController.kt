package com.meshlit.feature.ghosty

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide Ghosty controller. Maps the persistent
 * [GhostyConfigStore] flag onto a runtime "is the bubble actually
 * showing right now" state.
 *
 * The controller is deliberately small — the rendering and the
 * foreground-service plumbing live in [GhostyOverlayService]. The
 * controller's job is to flip a single boolean and emit a state
 * flow so settings UI and the service agree about the bubble's
 * current state.
 */
class GhostyController(
    private val store: GhostyConfigStore,
) {
    private val mutex = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Bring the bubble up. Idempotent — a second call while
     *  already running is a no-op. */
    suspend fun enable() {
        mutex.withLock {
            if (_running.value) return@withLock
            store.update { it.copy(enabled = true) }
            _running.value = true
        }
    }

    /** Tear the bubble down. Idempotent. */
    suspend fun disable() {
        mutex.withLock {
            if (!_running.value) return@withLock
            store.update { it.copy(enabled = false) }
            _running.value = false
        }
    }

    /** Toggle convenience used from Settings → Ghosty. */
    suspend fun setEnabled(enabled: Boolean) {
        if (enabled) enable() else disable()
    }
}