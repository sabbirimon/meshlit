package com.meshlit.feature.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshlit.feature.advanced.screens.BenchmarksScreen
import com.meshlit.feature.advanced.screens.CloudProvidersScreen
import com.meshlit.feature.advanced.screens.DiarizationScreen
import com.meshlit.feature.advanced.screens.DocumentOcrScreen
import com.meshlit.feature.advanced.screens.DocumentWorkbenchScreen
import com.meshlit.feature.advanced.screens.GhostySettingsScreen
import com.meshlit.feature.advanced.screens.GpuPanelScreen
import com.meshlit.feature.advanced.screens.ImageGenerationScreen
import com.meshlit.feature.advanced.screens.McpSettingsScreen
import com.meshlit.feature.advanced.screens.ReadAloudScreen
import com.meshlit.feature.advanced.screens.SegmentationScreen
import com.meshlit.feature.advanced.screens.SettingsScreen
import com.meshlit.feature.advanced.screens.SolutionsScreen
import com.meshlit.feature.advanced.screens.StorageScreen
import com.meshlit.feature.advanced.screens.TranscriptionScreen
import com.meshlit.feature.advanced.screens.VisionWorkbenchScreen
import com.meshlit.feature.advanced.screens.VoiceActivityScreen
import com.meshlit.feature.advanced.screens.WebToolsScreen

/**
 * Internal nav host for the Advanced hub. Renders the hub screen
 * and one destination per [AdvancedDestination] entry.
 *
 * The hub is exposed via `route = "advanced"`. Each leaf has a
 * stable route under `advanced/<slug>` matching the destination's
 * [AdvancedDestination.route].
 */
@Composable
fun AdvancedNavHost(
    accent: androidx.compose.ui.graphics.Color,
    accentDim: androidx.compose.ui.graphics.Color,
    host: McpHost = LocalMcpHost,
    ghostyHost: GhostyHost = LocalGhostyHost,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = "advanced") {
        composable("advanced") {
            AdvancedScreen(
                accent = accent,
                accentDim = accentDim,
                onNavigate = { dest -> navController.navigate(dest.route) },
            )
        }
        composable(AdvancedDestination.Diarization.route) {
            DiarizationScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.ReadAloud.route) {
            ReadAloudScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.Transcription.route) {
            TranscriptionScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.VoiceActivity.route) {
            VoiceActivityScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.WebTools.route) {
            WebToolsScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.Solutions.route) {
            SolutionsScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.CloudProviders.route) {
            CloudProvidersScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.Benchmarks.route) {
            BenchmarksScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.GpuPanel.route) {
            GpuPanelScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.Settings.route) {
            SettingsScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.DocumentWorkbench.route) {
            DocumentWorkbenchScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.VisionWorkbench.route) {
            VisionWorkbenchScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.DocumentOcr.route) {
            DocumentOcrScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.Segmentation.route) {
            SegmentationScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.ImageGeneration.route) {
            ImageGenerationScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
        composable(AdvancedDestination.GhostySettings.route) {
            GhostySettingsScreen(
                accent = accent,
                accentDim = accentDim,
                onBack = { navController.popBackStack() },
                onEnabledChange = { ghostyHost.setEnabled(it) },
            )
        }
        composable(AdvancedDestination.McpSettings.route) {
            val state by host.state.collectAsState()
            val servers by host.userServers.collectAsState()
            McpSettingsScreen(
                accent = accent,
                accentDim = accentDim,
                onBack = { navController.popBackStack() },
                serverRunning = state.isRunning,
                boundHost = state.boundHost,
                boundPort = state.boundPort,
                onToggleServer = { on ->
                    if (on) host.start() else host.stop()
                },
                userServers = servers,
                onAddServer = { name, command -> host.addServer(name, command) },
                onRemoveServer = { id -> host.removeServer(id) },
            )
        }
        composable(AdvancedDestination.Storage.route) {
            StorageScreen(accent, accentDim, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Tiny host interface the MCP settings screen needs. The real
 * binding lives in `:app`; the default implementation falls back
 * to no-ops so the Advanced hub can be previewed in isolation.
 */
interface McpHost {
    val state: kotlinx.coroutines.flow.StateFlow<McpHostState>
    val userServers: kotlinx.coroutines.flow.StateFlow<List<String>>
    fun start()
    fun stop()
    fun addServer(name: String, command: String)
    fun removeServer(id: String)
}

data class McpHostState(
    val isRunning: Boolean = false,
    val boundHost: String? = null,
    val boundPort: Int? = null,
)

private object NoopMcpHost : McpHost {
    override val state = kotlinx.coroutines.flow.MutableStateFlow(McpHostState())
    override val userServers = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    override fun start() = Unit
    override fun stop() = Unit
    override fun addServer(name: String, command: String) = Unit
    override fun removeServer(id: String) = Unit
}

/** Default MCP host — defaults to no-op; `:app` overrides it. */
val LocalMcpHost: McpHost = NoopMcpHost

interface GhostyHost {
    fun setEnabled(enabled: Boolean)
}

private object NoopGhostyHost : GhostyHost {
    override fun setEnabled(enabled: Boolean) = Unit
}

val LocalGhostyHost: GhostyHost = NoopGhostyHost