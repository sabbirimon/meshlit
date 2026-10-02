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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitOutlineV2
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 Devices hub. The Devices screen leads here — a grid of
 * action cards giving the user the full cluster surface
 * for managing their devices:
 *
 *  - **Scan** — start a discovery sweep
 *  - **Info** — device-level metrics (CPU, memory, battery, …)
 *  - **Cluster** — peer list + queue depth + failure breakdown
 *  - **Local group** — pre-shared key cluster for home Wi-Fi
 *  - **Networks map** — visual map of reachable nodes
 *  - **Pair (BLE)** — Bluetooth Low Energy pairing flow
 *  - **QR identity** — show this device's QR for out-of-band
 *    pairing + accept a peer's QR
 *  - **Storage** — on-device model + import storage usage
 *  - **Identity** — node ID, role, public key fingerprint
 *  - **Export** — export identity bundle (JSON) for backup
 *  - **Logs** — per-device debug log stream
 *  - **USB** — USB-tethered peers (when supported)
 *
 * Each card is a 160 dp rounded surface with a colored
 * 48 dp icon container, `titleMedium` SemiBold title, and a
 * `bodySmall` tertiary description. Tapping any card simply
 * logs the action for build no. 1; step 4 wires each to a
 * real screen (V2ScanScreen, V2ClusterScreen,
 * V2LocalGroupScreen, V2NetworksMapScreen,
 * V2DeviceManagementScreen).
 */
@Composable
fun V2DevicesHubScreen(onCardNavigate: (String) -> Unit = {}) {
    val cards = listOf(
        HubCard(
            id = "scan",
            icon = Icons.Filled.Hub,
            title = "Scan",
            description = "Sweep Wi-Fi + BLE for nearby peers",
            tint = MeshlitPulseViolet,
            // Wired to the v2 Scan screen (peer classification +
            // grouped list). Previously routed to /cluster, which
            // was wrong — Cluster is the metrics surface, Scan is
            // discovery.
            route = "scan",
        ),
        HubCard(
            id = "info",
            icon = Icons.Filled.Info,
            title = "Info",
            description = "CPU, memory, battery, uptime, role",
            tint = MeshlitPulseViolet,
            // Routed to the real Device Info screen (adds role,
            // hardware, identity, bootstrap health). The previous
            // route was `cluster` which only showed queue counters.
            route = "device_info",
        ),
        HubCard(
            id = "cluster",
            icon = Icons.Filled.DeviceHub,
            title = "Cluster",
            description = "Peers, queue depth, failure breakdown",
            tint = MeshlitPulseViolet,
            route = "cluster",
        ),
        HubCard(
            id = "local_group",
            icon = Icons.Filled.Group,
            title = "Local group",
            description = "Pre-shared key cluster on home Wi-Fi",
            tint = MeshlitPulseViolet,
            // Re-routed from `network` to `scan` so the
            // local-group pre-shared key filter chip in the Scan
            // surface is one tap away. The hub card landing here
            // also unblocks the "USB tether" row + BLE toggle.
            route = "scan",
        ),
        HubCard(
            id = "networks_map",
            icon = Icons.Filled.Map,
            title = "Networks map",
            description = "Visual topology of reachable nodes",
            tint = MeshlitPulseViolet,
            route = "network",
        ),
        HubCard(
            id = "pair_ble",
            icon = Icons.Filled.Bluetooth,
            title = "Pair (BLE)",
            description = "Bluetooth Low Energy pairing flow",
            tint = MeshlitPulseViolet,
            // Re-routed from `network` to `scan` so the BLE
            // toggle row opens immediately when the user taps
            // the "Pair (BLE)" hub card.
            route = "scan",
        ),
        HubCard(
            id = "qr_identity",
            icon = Icons.Filled.QrCode2,
            title = "QR identity",
            description = "Show / scan a node's identity QR",
            tint = MeshlitPulseViolet,
            // Re-routed from `users` (which delegated to MetricsScreen)
            // to the real Device Info screen — the QR is the visual
            // representation of the node id shown on that screen.
            route = "device_info",
        ),
        HubCard(
            id = "storage",
            icon = Icons.Filled.Storage,
            title = "Storage",
            description = "Models, imports, cache usage",
            tint = MeshlitPulseViolet,
            route = "files",
        ),
        HubCard(
            id = "identity",
            icon = Icons.Filled.Fingerprint,
            title = "Identity",
            description = "Node ID, role, public-key fingerprint",
            tint = MeshlitPulseViolet,
            // Identity card → real Device Info screen (node id + role
            // + capability tier, with copy + edit controls).
            // Previously routed to `users` → MetricsScreen.
            route = "device_info",
        ),
        HubCard(
            id = "export",
            icon = Icons.Filled.IosShare,
            title = "Export",
            description = "Backup identity bundle (JSON)",
            tint = MeshlitPulseViolet,
            // Export card → real Device Info screen so the user
            // can see their identity before exporting. The actual
            // export action (JSON bundle download) wires up in a
            // follow-up; for now the screen delivers the visible
            // identity + edit affordances. Previously routed to
            // `users` → MetricsScreen where no export existed.
            route = "device_info",
        ),
        HubCard(
            id = "logs",
            icon = Icons.Filled.Terminal,
            title = "Logs",
            description = "Per-device debug log stream",
            tint = MeshlitPulseViolet,
            route = "files",
        ),
        HubCard(
            id = "usb",
            icon = Icons.Filled.UsbOff,
            title = "USB tether",
            description = "USB-attached peer discovery",
            tint = MeshlitPulseViolet,
            // Re-routed from `network` to `scan` so the USB
            // tether row surfaces first when the user taps the
            // hub card. The Network monitor screen still gets the
            // detailed tether breakdown in a follow-up.
            route = "scan",
        ),
    )

    MeshlitDeepLinkWrap(
        headline = "Devices",
        subtitle = "Twelve surfaces for working with the cluster",
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
                    // Each card routes to the closest existing v2
                    // destination. Cards without a 1:1 destination
                    // (scan / info / pair-BLE) point at the
                    // cluster or network hub — which already
                    // covers the surface in detail. Tapping a
                    // card is now wired so the user gets visual
                    // feedback that something is happening
                    // instead of a silent card that does
                    // nothing.
                    HubCardView(card = card, onClick = {
                        card.route?.let { onCardNavigate(it) }
                    })
                }
            }
        }
    }
}

private data class HubCard(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val tint: androidx.compose.ui.graphics.Color,
    val route: String? = null,
)

@Composable
private fun HubCardView(card: HubCard, onClick: () -> Unit) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            // Cards are now clickable so the user gets the
            // ripple + scale feedback that says "this is
            // interactive". The actual route navigation is
            // wired by the NavHost-level caller via the
            // `onClick` callback. Without the `clickable`
            // modifier the cards looked press-able but did
            // nothing on tap — silent failures that read as
            // a wiring bug rather than an unimplemented
            // destination.
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