package com.meshlit.ui.v2.screens.network

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.di.koinInject
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.PeerRegistry
import com.meshlit.ui.theme.MeshlitInk
import com.meshlit.ui.theme.MeshlitPulseAqua
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurface
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitLeadBar

/**
 * v2 Network hub — replaces the v1 `NetworkMonitorScreen` wrap
 * for the bottom-bar / drawer "Network" entry. The screen reads:
 *
 *   1. **This device** — node role, local IPv4, port (8080), and
 *      whether the FGS-owned inference HTTP server is listening.
 *   2. **Transport** — Wi-Fi / BLE / USB state, with a one-line
 *      caption describing whether peers are reachable on each
 *      transport.
 *   3. **Peers** — the trusted peer list from [PeerRegistry], with
 *      each entry showing its IP, trust tier, and a freshness pill
 *      sourced from the [PeerHealthCache] (if bound).
 *   4. **Open Network monitor** — a footer button that deep-links
 *      to the full v1-style capture/inspect screen for power users.
 *
 * Build no. 1 ships a static hub view — values are read on first
 * composition and refreshed via `collectAsState` on the flows.
 * Step 2 swaps the static `MeshlitLeadBar` subtitle to a live
 * "X peers · Y healthy" counter driven by the same flows.
 */
@Composable
fun V2NetworkHubScreen() {
    val app: MeshlitApplication = koinInject()
    val peerRegistry: PeerRegistry = koinInject()

    val trusted by peerRegistry.trustedPeers
        .collectAsState(initial = emptyList())
    val health by (app.activePeerHealthCache()?.state ?: remember {
        kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
    }).collectAsState()

    val activeCount = trusted.size
    val healthyCount = trusted.count { tp ->
        val entry = health[tp.ip]
        entry?.ok == true && (System.currentTimeMillis() - entry.asOfMs) < PeerHealthCache.STALE_AFTER_MS
    }

    Column(modifier = Modifier.fillMaxSize().background(MeshlitInk)) {
        MeshlitLeadBar(
            headline = "Network",
            subtitle = if (activeCount == 0) {
                "No peers yet · scan to find your cluster"
            } else {
                "$activeCount peers · $healthyCount healthy"
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("this-device") { ThisDeviceCard(app) }
            item("transports") { TransportsCard() }
            if (trusted.isNotEmpty()) {
                item("peers-header") {
                    Text(
                        text = "Peers (${trusted.size})",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeshlitTextSecondaryV2,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(trusted, key = { it.ip }) { tp ->
                    val peerHealth = health[tp.ip]
                    val isHealthy = peerHealth?.ok == true &&
                        (System.currentTimeMillis() - peerHealth.asOfMs) < PeerHealthCache.STALE_AFTER_MS
                    PeerRow(
                        ip = tp.ip,
                        tierLabel = when (tp.tier.name) {
                            "LOCAL_TRUSTED" -> "Trusted"
                            "LOCAL_SANDBOXED" -> "Sandbox"
                            else -> tp.tier.name
                        },
                        healthy = isHealthy,
                        modelLoaded = peerHealth?.modelLoaded == true,
                    )
                }
            }
            item("open-monitor") { OpenMonitorHint() }
        }
    }
}

@Composable
private fun ThisDeviceCard(app: MeshlitApplication) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MeshlitPulseViolet.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MeshlitPulseViolet,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = "This device",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MeshlitTextPrimaryV2,
                    )
                    Text(
                        text = "Brain node · inference HTTP server",
                        style = MaterialTheme.typography.bodySmall,
                        color = MeshlitTextTertiaryV2,
                    )
                }
            }
            StatRow(label = "Node ID", value = app.nodeIdHex.take(16) + "…")
            StatRow(label = "Local IP", value = app.localIpAddress)
            StatRow(label = "HTTP port", value = app.httpServerPort.toString())
            StatRow(label = "Display name", value = app.displayName)
        }
    }
}

@Composable
private fun TransportsCard() {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Transports",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MeshlitTextPrimaryV2,
            )
            TransportRow(
                icon = Icons.Filled.Hub,
                tint = MeshlitPulseAqua,
                label = "Wi-Fi (LAN)",
                caption = "Primary inference path · port 8080",
                healthy = true,
            )
            TransportRow(
                icon = Icons.Filled.GraphicEq,
                tint = MeshlitPulseViolet,
                label = "Bluetooth Low Energy",
                caption = "Out-of-band pairing · slow inference",
                healthy = true,
            )
            TransportRow(
                icon = Icons.Filled.Storage,
                tint = MeshlitPulseCoral,
                label = "USB tether",
                caption = "Wired peer discovery · when connected",
                healthy = false,
            )
        }
    }
}

@Composable
private fun TransportRow(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    label: String,
    caption: String,
    healthy: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = tint.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MeshlitTextPrimaryV2,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextTertiaryV2,
            )
        }
        Pill(text = if (healthy) "Up" else "Down", healthy = healthy)
    }
}

@Composable
private fun PeerRow(
    ip: String,
    tierLabel: String,
    healthy: Boolean,
    modelLoaded: Boolean,
) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (healthy) MeshlitPulseAqua.copy(alpha = 0.18f) else MeshlitSurfaceHigh,
                        shape = RoundedCornerShape(10.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = if (healthy) MeshlitPulseAqua else MeshlitTextTertiaryV2,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ip,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = if (modelLoaded) "$tierLabel · model loaded" else tierLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
            Pill(text = if (healthy) "Healthy" else "Stale", healthy = healthy)
        }
    }
}

@Composable
private fun OpenMonitorHint() {
    Surface(
        color = MeshlitSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = MeshlitTextTertiaryV2,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Open the full Network monitor from the drawer for capture / inspect / export.",
                style = MaterialTheme.typography.bodySmall,
                color = MeshlitTextTertiaryV2,
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MeshlitTextTertiaryV2,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = MeshlitTextPrimaryV2,
        )
    }
}

@Composable
private fun Pill(text: String, healthy: Boolean) {
    Surface(
        color = if (healthy) MeshlitPulseAqua.copy(alpha = 0.18f) else MeshlitSurfaceHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (healthy) MeshlitPulseAqua else MeshlitTextTertiaryV2,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}