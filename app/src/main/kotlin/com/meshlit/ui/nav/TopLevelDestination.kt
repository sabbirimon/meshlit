package com.meshlit.ui.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
 *
 * Phase 2.x — added four SDK-backed screens at the end of the nav:
 *
 *  - Voice     — STT/TTS/VAD via sherpa-onnx. Needs mic permission.
 *  - JSON      — structured output + tool calling against the LLM.
 *  - Catalog   — dynamic model registry served by `RunAnywhere.listModels`.
 *  - Vision    — image picker → VLM prompt. Currently surfaces a
 *                "backend not yet shipped" card until the VLM AAR lands.
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
    Settings("settings", R.string.screen_settings, Icons.Outlined.Settings),
    Voice("voice", R.string.screen_voice, Icons.Outlined.Mic),
    Structured("structured", R.string.screen_structured, Icons.Outlined.Code),
    Catalog("catalog", R.string.screen_catalog, Icons.Outlined.CloudDownload),
    Vision("vision", R.string.screen_vision, Icons.Outlined.Image);

    companion object {
        val all: List<TopLevelDestination> = entries.toList()
    }
}