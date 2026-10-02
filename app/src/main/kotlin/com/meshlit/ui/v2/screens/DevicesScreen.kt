package com.meshlit.ui.v2.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.v2.components.MeshlitLeadBar

/**
 * v2 Devices screen. Replaces the v1
 * `ui/screens/DevicesScreen.kt` (1,115 LOC) for the
 * `meshlitV2` build. Renders:
 *   - `MeshlitLeadBar("Devices", "Scope: <x>")` at the top
 *     (the "bold-lead content" pattern).
 *   - A `ScopePicker` row of `FilterChip`s (LOCAL / INTERNET /
 *     VPN / GROUP / CUSTOM) — duplicated from v1 for build no. 1,
 *     re-styled in step 4 wiring.
 *   - The endpoint list as a `LazyColumn` of
 *     `MeshlitEndpointCard` (placeholder for build no. 1).
 *   - A "Pairing identity" card using `MeshlitMark` (placeholder).
 *
 * State: `collectAsStateWithLifecycle()` on
 * `viewModel.uiState`. Shipped by the v2 plan's step 5 audit.
 *
 * For build no. 1 the screen renders just the lead bar + the
 * scope. The endpoint list and pairing card land in step 4
 * wiring when the v2 components are in place.
 */
@Composable
fun DevicesScreen(
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = viewModel(factory = DevicesViewModel.factory()),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val scopeLabel = (uiState as? DevicesUiState.Ready)?.scope?.name ?: "Local"
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxSize(),
    ) {
        MeshlitLeadBar(
            headline = "Devices",
            subtitle = "Scope: $scopeLabel",
        )
        when (val state = uiState) {
            DevicesUiState.Loading -> {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DevicesUiState.Failure -> {
                androidx.compose.foundation.layout.Box(
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
            is DevicesUiState.Ready -> {
                // The detailed scope picker + endpoint list + pairing
                // card land in step 4 wiring. For build no. 1, render
                // a compact summary so the screen ships with content.
                // The body is wrapped in a MeshlitSurfaceContainer
                // Surface (rounded, tonalElevation 2 dp) so the
                // headline + body text read against a clear card
                // background instead of bleeding into the
                // MeshlitLeadBar's lifted surface above. This is the
                // "text overlap gets a bg color" rule — text-only
                // screens on top of MeshlitInk get a card container.
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Surface(
                        color = MeshlitSurfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "${state.endpoints.size} endpoints",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MeshlitTextPrimaryV2,
                            )
                            Text(
                                text = "Active: ${state.activeEndpointId ?: "<none>"}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MeshlitTextPrimaryV2,
                            )
                        }
                    }
                }
            }
        }
    }
}
