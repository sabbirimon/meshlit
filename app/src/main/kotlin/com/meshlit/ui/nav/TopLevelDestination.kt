package com.meshlit.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import com.meshlit.R

/**
 * The top-level destinations in the Meshlit app. Phase 0 ships all
 * nine as empty-state stubs — subsequent phases fill them in.
 *
 * Agent is the "Claude-Code-like" surface: full chat, code generation,
 * autopilot (model-iterates-on-its-own), and a system prompt tuned
 * for code/agentic work. Slots in between Jobs and Models since
 * it's a more interactive counterpart to the prompt box on Jobs.
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
    Files("files", R.string.screen_files, Icons.Outlined.Folder),
    Sessions("sessions", R.string.screen_sessions, Icons.Outlined.Terminal),
    Cluster("cluster", R.string.screen_cluster, Icons.Outlined.GridView),
    Network("network", R.string.screen_network, Icons.Outlined.Settings),
    Users("users", R.string.screen_users, Icons.Outlined.People),
    Settings("settings", R.string.screen_settings, Icons.Outlined.Settings);

    companion object {
        val all: List<TopLevelDestination> = entries.toList()
    }
}