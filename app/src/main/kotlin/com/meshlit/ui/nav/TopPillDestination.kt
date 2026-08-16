package com.meshlit.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewComfy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshlit.R

/**
 * Stitch-parity destinations for the **horizontal pill nav** rendered
 * above the main content.
 *
 * Mirrors the layout in
 * `stitch/meshlit---federated-edge-ai-cluster/src/App.tsx` — 13
 * horizontally-scrolling pills with icon + label, gradient highlight
 * when selected, no back stack, no drawer parent.
 *
 * Order matters: this is the display order on the pill bar.
 * Each entry maps to an existing [TopLevelDestination] route so
 * tapping a pill navigates the same NavHost. We keep the routes
 * pointing at the canonical destinations rather than introducing a
 * new navigation graph — the design changes the affordance, not
 * the routing graph.
 */
enum class TopPillDestination(
    val target: TopLevelDestination,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val routeOverride: String? = null,
) {
    Dashboard(TopLevelDestination.Devices, R.string.screen_devices, Icons.Outlined.MonitorHeart, routeOverride = "v2/dashboard"),
    Nodes(TopLevelDestination.Cluster, R.string.screen_cluster, Icons.Outlined.GridView, routeOverride = "v2/nodes"),
    Console(TopLevelDestination.Jobs, R.string.screen_jobs, Icons.Outlined.Terminal, routeOverride = "v2/console"),
    Agent(TopLevelDestination.Agent, R.string.screen_agent, Icons.Outlined.SmartToy, routeOverride = "v2/agent"),
    Mcp(TopLevelDestination.Servers, R.string.screen_mcp_servers, Icons.Outlined.BuildCircle, routeOverride = "v2/mcp"),
    Models(TopLevelDestination.Models, R.string.screen_models, Icons.Outlined.Download, routeOverride = "v2/models"),
    Monitoring(TopLevelDestination.Network, R.string.screen_network, Icons.Outlined.Radio, routeOverride = "v2/network"),
    Speech(TopLevelDestination.Voice, R.string.screen_voice, Icons.Outlined.Mic, routeOverride = "v2/speech"),
    Vision(TopLevelDestination.Vision, R.string.screen_vision, Icons.Outlined.Visibility, routeOverride = "v2/vision"),
    Jobs(TopLevelDestination.Jobs, R.string.screen_jobs, Icons.Outlined.ViewAgenda, routeOverride = "v2/jobs"),
    UiKit(TopLevelDestination.Advanced, R.string.screen_advanced, Icons.Outlined.Layers, routeOverride = "v2/uikit"),
    Library(TopLevelDestination.Advanced, R.string.screen_advanced, Icons.Outlined.ViewComfy, routeOverride = "v2/library"),
    Settings(TopLevelDestination.Settings, R.string.screen_settings, Icons.Outlined.Settings, routeOverride = "v2/settings"),
    ;

    /** Route used for both pill highlighting (currentRoute == route)
     *  and as the navigation target on tap. */
    val route: String get() = routeOverride ?: target.route

    companion object {
        /** Display order on the pill bar. */
        val ordered: List<TopPillDestination> = entries.toList()
    }
}