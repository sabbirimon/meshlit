package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.ui.v2.components.MeshlitLeadBar

/**
 * v2 Cluster screen (replaces the v1 `MetricsScreen.kt` for
 * the `/cluster` route in the `meshlitV2` build).
 *
 * Renders:
 *   - `MeshlitLeadBar("Cluster", "<queue> jobs · <active> active")`
 *   - A 2-column `LazyVerticalGrid` of `StatCard` placeholders
 *     (queue, success, failure, tokens, tokens/sec)
 *   - `FailureBreakdownCard` (placeholder for build no. 1)
 *   - `PeerHealthSection` (placeholder for build no. 1)
 *
 * State: `collectAsStateWithLifecycle()` on `viewModel.uiState`.
 * The v1 screen polled the registry inline in a `LaunchedEffect`;
 * the v2 build hoists the polling into `ClusterViewModel` so the
 * composable layer is pure.
 */
@Composable
fun ClusterScreen(
    modifier: Modifier = Modifier,
    viewModel: ClusterViewModel = viewModel(factory = ClusterViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val subtitle = (uiState as? ClusterUiState.Ready)?.let {
        "${it.queueDepth} jobs · ${it.activeTools} active"
    } ?: "Cluster registry"

    Column(modifier = modifier.fillMaxSize()) {
        MeshlitLeadBar(
            headline = "Cluster",
            subtitle = subtitle,
        )
        when (val state = uiState) {
            ClusterUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is ClusterUiState.Failure -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is ClusterUiState.Ready -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${state.tokensTotal} tokens @ ${"%.1f".format(state.tokensPerSec)} t/s",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${state.successCount} ok · ${state.failureCount} failed",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${state.peerHealth.size} peers",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}