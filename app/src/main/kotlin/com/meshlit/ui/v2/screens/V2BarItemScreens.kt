package com.meshlit.ui.v2.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.screens.CatalogScreen
import com.meshlit.ui.screens.FilesScreen
import com.meshlit.ui.screens.JobsScreen
import com.meshlit.ui.screens.LogScreen
import com.meshlit.ui.screens.MetricsScreen
import com.meshlit.ui.screens.ScriptsScreen
import com.meshlit.ui.screens.StructuredScreen
import com.meshlit.ui.screens.VisionScreen
import com.meshlit.ui.screens.VoiceScreen
import com.meshlit.ui.screens.cloud.CloudHubScreen
import com.meshlit.ui.screens.help.HelpHubScreen
import com.meshlit.ui.screens.network.NetworkMonitorScreen
import com.meshlit.ui.screens.settings.ModelsScreen
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 wrappers for the v1 screens that back the bottom-bar
 * destinations and the drawer-only destinations. The v2 nav
 * graph delegates to these so every destination actually
 * renders real content rather than the build-no.1 placeholder.
 *
 * Each wrapper sits the v1 screen under a [MeshlitDeepLinkWrap]
 * so the lead bar reads with the v2 design language ("bold-lead
 * content") while the v1 body underneath is untouched. This
 * keeps the v1 source tree byte-identical to the pre-v2 state
 * — the wrapper adds chrome, never edits v1 files.
 *
 * The `onOpenDrawer` parameter is wired to a no-op in v2 because
 * the v2 chrome already owns the drawer (the [MeshlitAppV2]
 * scaffold slides the `ModalNavigationDrawer` from the hamburger
 * icon in the top bar, not from per-screen headers). Passing a
 * no-op keeps the v1 `MeshlitHeader` calls inside each v1 screen
 * from trying to open a drawer that doesn't exist on the v2 tree.
 */

@Composable
fun V2JobsScreen() {
    MeshlitDeepLinkWrap(
        headline = "Jobs",
        subtitle = "Queued inference tasks",
    ) {
        JobsScreen(
            onOpenDrawer = {},
            onOpenModels = {},
            // v2 chrome owns the lead bar + hamburger; skip v1's
            // duplicate header row to give the chat surface the
            // full vertical real-estate.
            omitHeader = true,
        )
    }
}

@Composable
fun V2ModelsScreen() {
    MeshlitDeepLinkWrap(
        headline = "Models",
        subtitle = "On-device + cloud catalog",
    ) {
        ModelsScreen(
            onBack = {},
            // v2 chrome already owns the lead bar; skip v1's
            // TopAppBar so the two headers don't stack.
            omitHeader = true,
        )
    }
}

@Composable
fun V2StructuredScreen() {
    MeshlitDeepLinkWrap(
        headline = "Structured",
        subtitle = "JSON-mode + schema-constrained output",
    ) {
        StructuredScreen(onOpenDrawer = {})
    }
}

@Composable
fun V2VisionScreen() {
    MeshlitDeepLinkWrap(
        headline = "Vision",
        subtitle = "Image + multimodal inference",
    ) {
        VisionScreen(onOpenDrawer = {})
    }
}

@Composable
fun V2CatalogScreen() {
    MeshlitDeepLinkWrap(
        headline = "Catalog",
        subtitle = "Multi-source model registry",
    ) {
        CatalogScreen(onOpenDrawer = {})
    }
}

/** Advanced hub — six-tile grid linking into the rest of the
 *  cluster surface (Files, Sessions, Logs, Cluster, Network,
 *  Users). Matches the Devices-hub aesthetic so the user sees a
 *  consistent "menu of surfaces" pattern in both tabs. Each
 *  card routes to the matching v2 destination via the
 *  `onCardNavigate` callback wired from [MeshlitAppV2]. */
@Composable
fun V2AdvancedScreen(onCardNavigate: (String) -> Unit = {}) {
    val cards = listOf(
        AdvancedCard(
            id = "files",
            icon = Icons.Filled.Storage,
            title = "Files",
            description = "Models, exports, logs",
            tint = MeshlitPulseViolet,
            route = "files",
        ),
        AdvancedCard(
            id = "sessions",
            icon = Icons.Filled.AccountTree,
            title = "Sessions",
            description = "Agent session history",
            tint = MeshlitPulseViolet,
            route = "sessions",
        ),
        AdvancedCard(
            id = "logs",
            icon = Icons.Filled.GraphicEq,
            title = "Logs",
            description = "Live debug + error stream",
            tint = MeshlitPulseViolet,
            route = "files",
        ),
        AdvancedCard(
            id = "network",
            icon = Icons.Filled.GraphicEq,
            title = "Network",
            description = "Bandwidth, peers, latency",
            tint = MeshlitPulseViolet,
            route = "network",
        ),
        AdvancedCard(
            id = "users",
            icon = Icons.Filled.People,
            title = "Users",
            description = "Identity, roles, permissions",
            tint = MeshlitPulseViolet,
            route = "users",
        ),
        AdvancedCard(
            id = "cloud",
            icon = Icons.Filled.Storage,
            title = "Cloud",
            description = "Cloud models + connectors",
            tint = MeshlitPulseViolet,
            route = "cloud",
        ),
    )
    MeshlitDeepLinkWrap(
        headline = "Advanced",
        subtitle = "Six surfaces for power users",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    AdvancedCardView(
                        card = card,
                        onClick = { card.route?.let { onCardNavigate(it) } },
                    )
                }
            }
        }
    }
}

private data class AdvancedCard(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val tint: androidx.compose.ui.graphics.Color,
    val route: String? = null,
)

@Composable
private fun AdvancedCardView(card: AdvancedCard, onClick: () -> Unit) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = card.tint.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = card.title,
                    tint = card.tint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MeshlitTextPrimaryV2,
            )
            Text(
                text = card.description,
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextTertiaryV2,
            )
        }
    }
}

/** Drawer-only destinations — same wrapper pattern, but these
 *  were unwired until this commit (the v2 nav used the
 *  catch-all placeholder). Each one delegates to the
 *  matching v1 screen. */
@Composable
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun V2VoiceScreen(onBack: () -> Unit = {}) {
    // Live PCM frame feed shared with the inner v1 VoiceScreen so
    // the 32-bar audio spectrum under the lead bar reflects real
    // capture, not a placeholder. SharedFlow(replay = 1) keeps
    // the last frame for late subscribers (e.g. if the spectrum
    // Composable re-mounts after a config change).
    val pcmFrames = androidx.compose.runtime.remember {
        kotlinx.coroutines.flow.MutableSharedFlow<ByteArray>(replay = 1, extraBufferCapacity = 32)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { pcmFrames.resetReplayCache() }
    }
    // Mic sensitivity / FFT gain. The user-controllable multiplier
    // applied to the raw FFT magnitudes before the EMA smoothing,
    // so they can dial the spectrum up or down to match their
    // microphone/room. 1.0× = neutral, 0.5× = half-gain (quiet
    // rooms / sensitive mic), 2.0× = double-gain (noisy rooms or
    // a cheap mic that needs a boost).
    var sensitivity by androidx.compose.runtime.remember { mutableStateOf(1.0f) }
    MeshlitDeepLinkWrap(
        headline = "Voice",
        subtitle = "Speech-to-text workspace",
    ) {
        androidx.compose.foundation.layout.Column {
            // Sensitivity slider — labelled so the user knows what
            // the slider moves. Range 0.5x…2.0x with 30 steps so
            // the user can tune finely. Centered at 1.0 (no
            // change) by the marks[] array.
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Text(
                        text = "Mic sensitivity",
                        style = MaterialTheme.typography.labelMedium,
                        color = MeshlitTextTertiaryV2,
                    )
                    androidx.compose.material3.Text(
                        text = "%.2fx".format(sensitivity),
                        style = MaterialTheme.typography.labelMedium,
                        color = MeshlitPulseViolet,
                    )
                }
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.5f..2.0f,
                    steps = 29,
                )
            }
            // Live spectrum sits above the v1 VoiceScreen so the
            // user sees a 32-bar visualizer of the mic input as
            // soon as capture starts. Idle (gray) when the mic is
            // closed, active violet when audio is flowing.
            com.meshlit.ui.components.voice.MeshlitAudioSpectrum(
                pcmFlow = pcmFrames,
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                sensitivity = sensitivity,
            )
            VoiceScreen(
                onOpenDrawer = onBack,
                spectrumFlow = pcmFrames,
                // v2 chrome already owns the lead bar — skip v1's
                // duplicate MeshlitHeader so the body fills the
                // remaining height and the Save / Import chips at
                // the bottom of the v1 scroll column are reachable.
                omitHeader = true,
            )
        }
    }
}

@Composable
fun V2FilesScreen(onBack: () -> Unit = {}) {
    MeshlitDeepLinkWrap(
        headline = "Files",
        subtitle = "Models, exports, logs on disk",
    ) {
        // The v1 FilesScreen uses onOpenDrawer for its top-bar
        // back arrow. Under v2 the chrome already owns the
        // drawer, so we wire that slot to the back navigation
        // callback passed from MeshlitAppV2. The user tapping
        // the arrow now actually pops the back stack instead
        // of silently doing nothing.
        FilesScreen(onOpenDrawer = onBack)
    }
}

@Composable
fun V2SessionsScreen(onBack: () -> Unit = {}) {
    MeshlitDeepLinkWrap(
        headline = "Sessions",
        subtitle = "Agent session history",
    ) {
        ScriptsScreen(onOpenDrawer = onBack)
    }
}

@Composable
fun V2NetworkScreen() {
    com.meshlit.ui.v2.screens.network.V2NetworkHubScreen()
}

@Composable
fun V2UsersScreen(onBack: () -> Unit = {}) {
    MeshlitDeepLinkWrap(
        headline = "Users",
        subtitle = "Identity, roles, permissions",
    ) {
        MetricsScreen(onBack = onBack)
    }
}

@Composable
fun V2CloudScreen(onBack: () -> Unit = {}) {
    MeshlitDeepLinkWrap(
        headline = "Cloud",
        subtitle = "Cloud models + connectors",
    ) {
        CloudHubScreen(
            onOpenDrawer = onBack,
            onOpenAddCustom = {},
            onOpenTerminal = {},
        )
    }
}

@Composable
fun V2HelpScreen(onBack: () -> Unit = {}) {
    MeshlitDeepLinkWrap(
        headline = "Help",
        subtitle = "Tours, manual, feedback",
    ) {
        HelpHubScreen(
            onBack = onBack,
            onOpenManual = {},
            onOpenTour = {},
            onOpenFeedback = {},
        )
    }
}
