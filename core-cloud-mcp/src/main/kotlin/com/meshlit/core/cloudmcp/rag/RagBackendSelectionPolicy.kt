package com.meshlit.core.cloudmcp.rag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One retrieval decision. Emitted by [RagBackendSelectionPolicy]
 * for every retrieval and surfaced as a chip on the UI so the
 * user always sees which backend answered.
 *
 * `null` `requestedBy` means the call happened under the
 * configured [RagMode] without an explicit user prompt.
 */
data class RagDecision(
    val mode: RagMode,
    val backend: RagBackend,
    val requestedBy: String? = null,
)

enum class RagBackend {
    Local,
    Remote,
    Hybrid,
}

/**
 * Emitted when [RagMode.Ask] is active and a retrieval is about
 * to happen. The UI surfaces a confirmation dialog and the user
 * either confirms (Local) or denies (skip retrieval).
 */
data class RagPermissionRequest(
    val prompt: String,
    val namespace: String?,
)

/**
 * Decides which backend to use for a retrieval. Pure logic —
 * no I/O — so it's trivially unit-testable.
 *
 * The policy has access to a `localCount` snapshot (number of
 * documents currently in the local store for the active
 * namespace) so [RagMode.Auto] can route local-first when
 * enough docs are on-device, and skip straight to remote when
 * the local store is empty / under-populated.
 */
class RagBackendSelectionPolicy {

    private val _decisions = MutableSharedFlow<RagDecision>(extraBufferCapacity = 16)
    val decisions: Flow<RagDecision> = _decisions.asSharedFlow()

    private val _permissionRequests = MutableSharedFlow<RagPermissionRequest>(extraBufferCapacity = 8)
    val permissionRequests: Flow<RagPermissionRequest> = _permissionRequests.asSharedFlow()

    /**
     * Resolve a retrieval. Returns the backend the agent loop
     * should query, or null if the user denied a permission
     * request.
     */
    suspend fun resolve(
        mode: RagMode,
        prompt: String,
        namespace: String?,
        localCount: Int,
    ): RagBackend? {
        return when (mode) {
            RagMode.Local -> {
                emit(mode, RagBackend.Local)
                RagBackend.Local
            }
            RagMode.Remote -> {
                emit(mode, RagBackend.Remote)
                RagBackend.Remote
            }
            RagMode.Auto -> {
                if (localCount >= AUTO_LOCAL_MIN_DOCS) {
                    emit(mode, RagBackend.Local)
                    RagBackend.Local
                } else {
                    emit(mode, RagBackend.Remote)
                    RagBackend.Remote
                }
            }
            RagMode.Ask -> {
                _permissionRequests.emit(
                    RagPermissionRequest(prompt = prompt, namespace = namespace),
                )
                // Caller awaits a user response. We don't know
                // yet what they'll pick — `resolve()` is split
                // into `confirmLocal` / `deny` helpers below
                // for the dialog flow.
                null
            }
        }
    }

    /** User confirmed a [RagPermissionRequest] — proceed with local. */
    suspend fun confirmLocal(request: RagPermissionRequest) {
        emit(RagMode.Ask, RagBackend.Local, requestedBy = "user")
    }

    /** User denied a [RagPermissionRequest] — skip retrieval. */
    suspend fun deny(request: RagPermissionRequest) {
        emit(RagMode.Ask, RagBackend.Local, requestedBy = "user-denied")
    }

    private suspend fun emit(
        mode: RagMode,
        backend: RagBackend,
        requestedBy: String? = null,
    ) {
        _decisions.emit(RagDecision(mode, backend, requestedBy))
    }

    companion object {
        /**
         * Minimum local document count to consider local retrieval
         * useful. Below this, [RagMode.Auto] routes straight to
         * remote so the user gets a real answer instead of an empty
         * hit. Tuned for a fresh install — the local store starts
         * empty.
         */
        const val AUTO_LOCAL_MIN_DOCS = 3
    }
}