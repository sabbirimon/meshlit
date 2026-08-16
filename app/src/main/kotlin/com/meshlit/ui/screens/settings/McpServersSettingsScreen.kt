package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.settings.visibility.RowDescriptor
import com.meshlit.settings.visibility.SettingsVisibility
import com.meshlit.settings.visibility.Visibility
import kotlinx.coroutines.launch

/**
 * Phase 4.x — Settings menu rewrite.
 *
 * Renders a dedicated card per available MCP server, each
 * bound to the existing `*McpEnabledFlow` in `SettingsRepository`.
 *
 * Categories surfaced here:
 *  - General-purpose: Filesystem, In-App content, Remote control
 *  - Specialized:     Clipboard, Notifications, Device info, Web fetch
 *
 * The user can pick which MCPs they want by toggling each row.
 * Default-on for the general-purpose entries; specialized bundles
 * start off and require explicit opt-in (see BundledMcpServer.defaultOn).
 *
 * Visibility:
 *   SIMPLE   — the master toggles.
 *   ADVANCED — per-server "Open host" navigation (host config
 *              is intentional power-user territory).
 */
@Composable
fun McpServersSettingsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    onOpenHost: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val simpleAdvanced = remember { app.simpleAdvancedStore }
    val simple by simpleAdvanced.mode.collectAsState()
    val scope = rememberCoroutineScope()

    val remote by settings.remoteControlMcpEnabledFlow.collectAsState(initial = true)
    val filesystem by settings.filesystemMcpEnabledFlow.collectAsState(initial = true)
    val inApp by settings.inAppMcpEnabledFlow.collectAsState(initial = false)
    val clipboard by settings.clipboardMcpEnabledFlow.collectAsState(initial = false)
    val notifications by settings.notificationsMcpEnabledFlow.collectAsState(initial = false)
    val deviceInfo by settings.deviceInfoMcpEnabledFlow.collectAsState(initial = false)
    val webFetch by settings.webFetchMcpEnabledFlow.collectAsState(initial = false)

    val rows = buildList<RowDescriptor> {
        add(
            RowDescriptor(Visibility.SIMPLE) {
                HeaderRow(
                    title = "Bundled MCP servers",
                    subtitle = "Toggle each one independently. Disabled servers don't bind their listen port or show up in the agent's tool list.",
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                SectionLabel(text = "GENERAL PURPOSE")
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Storage,
                    name = "Filesystem",
                    subtitle = "files_list / files_read / files_write under the app sandbox. Sandboxed to filesDir.",
                    channelId = "filesystem",
                    enabled = filesystem,
                    onEnabledChange = { scope.launch { settings.setFilesystemMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Memory,
                    name = "In-App content",
                    subtitle = "Read notes / calendar / contacts / app files. Every tool is gated behind a per-resource permission prompt.",
                    channelId = "in_app",
                    enabled = inApp,
                    onEnabledChange = { scope.launch { settings.setInAppMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Hub,
                    name = "Remote control",
                    subtitle = "tap / swipe / screenshot the device via uiautomator-style tools. Opt-in by default — turning on requires the agent to drive the UI.",
                    channelId = "remote_control",
                    enabled = remote,
                    onEnabledChange = { scope.launch { settings.setRemoteControlMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                SectionLabel(text = "SPECIALIZED")
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.ContentCopy,
                    name = "Clipboard",
                    subtitle = "Read / write / clear the device clipboard, with a small recent-history ring.",
                    channelId = "clipboard",
                    enabled = clipboard,
                    onEnabledChange = { scope.launch { settings.setClipboardMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Notifications,
                    name = "Notifications",
                    subtitle = "Post / list / dismiss notifications via NotificationManager.",
                    channelId = "notifications",
                    enabled = notifications,
                    onEnabledChange = { scope.launch { settings.setNotificationsMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Info,
                    name = "Device info",
                    subtitle = "Inspect battery, network, and storage without granting broader permissions.",
                    channelId = "device_info",
                    enabled = deviceInfo,
                    onEnabledChange = { scope.launch { settings.setDeviceInfoMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.SIMPLE) {
                McpServerMasterRow(
                    icon = Icons.Outlined.Public,
                    name = "Web fetch",
                    subtitle = "Fetch a single URL over HTTP(S) and return the body as text. HTTPS only by default.",
                    channelId = "web_fetch",
                    enabled = webFetch,
                    onEnabledChange = { scope.launch { settings.setWebFetchMcpEnabled(it) } },
                )
            },
        )
        add(
            RowDescriptor(Visibility.ADVANCED) {
                com.meshlit.ui.components.RaNavRow(
                    leadingIcon = Icons.Outlined.Hub,
                    title = "Open host",
                    subtitle = "Configure port, auth token, and per-tool scopes for every enabled server in one place.",
                    onClick = onOpenHost,
                )
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SettingsVisibility.Render(rows = rows, simpleMode = simple)
        }
    }
}

@Composable
private fun McpServerMasterRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    subtitle: String,
    channelId: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingToggle(
            icon = icon,
            title = name,
            subtitle = "$subtitle · channel=$channelId",
            checked = enabled,
            onChange = onEnabledChange,
        )
    }
}
