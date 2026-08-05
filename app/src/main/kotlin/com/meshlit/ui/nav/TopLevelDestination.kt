package com.meshlit.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshlit.R

/**
 * The top-level destinations in the Meshlit app.
 *
 * The bottom bar shows all 9 user-facing entry points in display order:
 * Devices, Jobs, Voice, Agent, Models, Structured, Vision, Catalog,
 * Advanced. Each one has a dedicated tab — the user wants the model
 * categories (Voice / Chat / Vision / etc.) separated on the bar the
 * way the legacy build did, not bundled into the Advanced hub.
 *
 * The drawer (hamburger) still surfaces the power-user pages: Files,
 * Sessions, Cluster, Network, Users, Settings.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Devices("devices", R.string.screen_devices, Icons.Outlined.Devices),
    Jobs("jobs", R.string.screen_jobs, Icons.Outlined.GraphicEq),
    Agent("agent", R.string.screen_agent, Icons.Outlined.AutoAwesome),
    Models("models", R.string.screen_models, Icons.Outlined.Memory),
    Advanced("advanced", R.string.screen_advanced, Icons.Outlined.Tune),
    Files("files", R.string.screen_files, Icons.Outlined.Folder),
    Sessions("sessions", R.string.screen_sessions, Icons.Outlined.Terminal),
    Cluster("cluster", R.string.screen_cluster, Icons.Outlined.GridView),
    Network("network", R.string.screen_network, Icons.Outlined.Settings),
    Users("users", R.string.screen_users, Icons.Outlined.People),
    Settings("settings", R.string.screen_settings, Icons.Outlined.Settings),
    Voice("voice", R.string.screen_voice, Icons.Outlined.Mic),
    Structured("structured", R.string.screen_structured, Icons.Outlined.Code),
    Catalog("catalog", R.string.screen_catalog, Icons.Outlined.CloudDownload),
    Vision("vision", R.string.screen_vision, Icons.Outlined.Image),
    Cloud("cloud", R.string.screen_cloud, Icons.Outlined.Cloud);

    companion object {
        /**
         * Bottom-bar destinations in display order. The bar is
         * horizontally scrollable so all 9 fit even on narrow
         * phones. Voice / Structured / Vision / Catalog get their own
         * tabs so model categories stay separated like the legacy
         * build. Cloud lives in the drawer — 10 items in a bottom
         * bar is unusable; users reach it via swipe + tap, which
         * keeps all model categories visible without scroll.
         */
        val barItems: List<TopLevelDestination> = listOf(
            Devices, Jobs, Voice, Agent, Models, Structured, Vision, Catalog, Advanced,
        )
        /** Drawer-only destinations. */
        val drawerOnly: List<TopLevelDestination> = listOf(
            Files, Sessions, Cluster, Network, Users, Settings, Cloud,
        )
        /** Every destination — kept for callers that want the
         *  complete graph. */
        val all: List<TopLevelDestination> = entries.toList()
    }
}