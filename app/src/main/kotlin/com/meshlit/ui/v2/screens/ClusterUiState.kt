package com.meshlit.ui.v2.screens

import com.meshlit.core.registry.HealthState

/**
 * Sealed UiState for the v2 `ClusterScreen` (formerly the v1
 * `MetricsScreen` for the `/cluster` route). Aggregates the
 * three registry snapshots the v1 screen consumed inline
 * (`registry.queueDepth`, `registry.failureTagCounts`,
 * `registry.snapshot()` for peer health).
 */
sealed interface ClusterUiState {

    object Loading : ClusterUiState

    data class Ready(
        val queueDepth: Int,
        val activeTools: Int,
        val successCount: Long,
        val failureCount: Long,
        val tokensTotal: Long,
        val tokensPerSec: Float,
        val failureTags: Map<String, Long>,
        val peerHealth: Map<String, HealthState>,
    ) : ClusterUiState

    data class Failure(val message: String) : ClusterUiState
}