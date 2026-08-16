package com.meshlit.core.cloudmcp.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide store of the most recent accessibility snapshot
 * per foreground package. The agent loop calls [latest] before
 * each action to ground the LLM's screen context.
 *
 * We keep at most one snapshot per package — the LLM takes a
 * fresh snapshot before each action, so history isn't useful.
 * Stale snapshots (older than ~3s) are evicted on every write
 * so the Live device pane reflects the present, not a frozen
 * frame.
 */
class AndroidSnapshotStore {

    private val _latest = MutableStateFlow<Map<String, AndroidSnapshot>>(emptyMap())
    val latest: StateFlow<Map<String, AndroidSnapshot>> = _latest.asStateFlow()

    /**
     * Read the latest snapshot for [packageName], or null if
     * the service hasn't captured one yet.
     */
    fun get(packageName: String): AndroidSnapshot? = _latest.value[packageName]

    /**
     * Current foreground snapshot (the one with the most recent
     * [AndroidSnapshot.capturedAtMs]). The agent loop polls
     * this on every prompt.
     */
    fun foreground(): AndroidSnapshot? =
        _latest.value.values.maxByOrNull { it.capturedAtMs }

    /**
     * Store [snapshot]. Drops any per-package stale entry on
     * the same write.
     */
    fun put(snapshot: AndroidSnapshot) {
        _latest.update { it + (snapshot.packageName to snapshot) }
    }

    /**
     * Drop every snapshot. Called when the AccessibilityService
     * is unbound.
     */
    fun clear() {
        _latest.value = emptyMap()
    }
}
