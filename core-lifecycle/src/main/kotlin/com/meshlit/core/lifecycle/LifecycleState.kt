package com.meshlit.core.lifecycle

/**
 * Lifecycle state of a single [ManagedService] inside
 * [ServiceLifecycleController]. Modelled after the sealed-state
 * pattern that `MeshlitServerController` uses (see
 * `core-mcp/.../MeshlitServerController.kt`) — same shape on
 * purpose so the existing Settings UI can render either controller's
 * state without a fork.
 */
sealed class LifecycleState {
    /** Not started (or fully stopped). */
    data object Idle : LifecycleState()

    /** A start is in flight. */
    data object Starting : LifecycleState()

    /** A stop is in flight. */
    data object Stopping : LifecycleState()

    /** Running and serving. */
    data object Running : LifecycleState()

    /** Last start or stop returned a failure. */
    data class Error(val reason: String) : LifecycleState()
}
