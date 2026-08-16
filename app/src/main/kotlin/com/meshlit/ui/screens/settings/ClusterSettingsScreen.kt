package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
 * Phase 4.x — Settings menu from-scratch rewrite.
 *
 * Cluster & Network — was a stub (`SettingRow`s with empty
 * `onClick`). Now every row binds a DataStore key and ships a
 * real Save/Set control.
 *
 * Visibility tier mapping:
 *   SIMPLE   — cluster name, discovery mode chip, Tailscale
 *              master toggle.
 *   ADVANCED — Tailscale auth key, WireGuard config path,
 *              WAN relay URL, raw firewall ports.
 */
@Composable
fun ClusterSettingsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val simpleAdvanced = app.simpleAdvancedStore
    val simple by simpleAdvanced.mode.collectAsState()
    val scope = rememberCoroutineScope()

    val name by settings.clusterNameFlow.collectAsState(initial = "mesh-cluster")
    val discovery by settings.clusterDiscoveryFlow.collectAsState(initial = "NSD")
    val tailscaleOn by settings.clusterTailscaleEnabledFlow.collectAsState(initial = false)
    val tailscaleKey by settings.clusterTailscaleAuthKeyFlow.collectAsState(initial = "")
    val wgPath by settings.clusterWireguardConfigPathFlow.collectAsState(initial = "")
    val relayUrl by settings.clusterRelayUrlFlow.collectAsState(initial = "https://relay.meshlit.dev")
    val firewallPorts by settings.clusterFirewallPortsFlow.collectAsState(initial = "8080,8443,9443")

    val rows = buildList<RowDescriptor> {
        add(RowDescriptor(Visibility.SIMPLE) {
            HeaderRow(
                title = "Cluster identity",
                subtitle = "Name visible to peers. Stored locally; not broadcast until you join a discovery group.",
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            TextRow(
                title = "Cluster name",
                subtitle = "Public name. Defaults to mesh-cluster.",
                placeholder = "mesh-cluster",
                onCommit = { v -> scope.launch { settings.setClusterName(v) } },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            ChipRow(
                title = "Discovery mode",
                subtitle = "NSD = local DNS-SD; LAN = broadcast; WFD = Wi-Fi Direct; TAILSCALE = tunnel; WG = WireGuard; RELAY = Meshlit relay.",
                options = listOf("NSD", "LAN", "WFD", "TAILSCALE", "WG", "RELAY"),
                selected = discovery,
                onSelect = { scope.launch { settings.setClusterDiscovery(it) } },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            SettingToggle(
                icon = Icons.Outlined.Hub,
                title = "Tailscale enabled",
                subtitle = "When on, Meshlit joins the auth-key's tailnet on cold start.",
                checked = tailscaleOn,
                onChange = { scope.launch { settings.setClusterTailscaleEnabled(it) } },
            )
        })

        add(RowDescriptor(Visibility.ADVANCED) {
            HeaderRow(
                title = "Advanced transports",
                subtitle = "Per-protocol keys, paths, and tunables. Hidden in Simple mode.",
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            TextRow(
                title = "Tailscale auth key",
                subtitle = "tskey-auth-…  Visible only to the local process; sent to the FGS over IPC.",
                placeholder = "tskey-auth-XXXXXXXX",
                onCommit = { v -> scope.launch { settings.setClusterTailscaleAuthKey(v) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            TextRow(
                title = "WireGuard config path",
                subtitle = "Path to a `.conf` Meshlit reads on cold start.",
                placeholder = "/sdcard/Download/meshlit-wg.conf",
                onCommit = { v -> scope.launch { settings.setClusterWireguardConfigPath(v) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            TextRow(
                title = "WAN relay URL",
                subtitle = "Meshlit relay used when local discovery fails.",
                placeholder = "https://relay.meshlit.dev",
                onCommit = { v -> scope.launch { settings.setClusterRelayUrl(v) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            TextRow(
                title = "Firewall ports (CSV)",
                subtitle = "Comma-separated ports the cluster exposes. 8080,8443,9443 by default.",
                placeholder = "8080,8443,9443",
                onCommit = { v -> scope.launch { settings.setClusterFirewallPorts(v) } },
            )
        })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(16.dp)),
    ) {
        item {
            SettingsVisibility.Render(rows = rows, simpleMode = simple)
        }
    }
}