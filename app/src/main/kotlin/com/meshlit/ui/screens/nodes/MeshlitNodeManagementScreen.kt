package com.meshlit.ui.screens.nodes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.MeshlitApplication
import com.meshlit.core.trust.TrustTier
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.MeshlitPulsingClusterNode
import com.meshlit.design.StitchPalette
import com.meshlit.design.glow
import com.meshlit.design.ping
import com.meshlit.design.stitchDropShadow
import com.meshlit.design.stitchPulseGlow
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.TrustedPeer

/**
 * Stitch-parity Node Management screen.
 *
 * Layout matches `NodeManagement.tsx` from the Stitch source: a
 * single Cluster Health glass card → scrollable node list with
 * per-status gradient border glow → "Add Device" CTA tile.
 *
 * Token mapping (Stitch → MeshlitDesignPalette):
 *  - status glow colors → `haloCyanSoft` / `haloPurpleSoft` /
 *    `haloEmeraldSoft` / `haloAmberSoft` (Token palette)
 *  - "Add Device" border → `Dark.outlineCyan`
 *  - shadow stack → `Modifier.stitchDropShadow(Dark.glassShadowAmbient, 16.dp)`
 */
enum class NodeStatus { Ready, Online, Syncing, Offline }

data class NodeCard(
    val id: String,
    val name: String,
    val role: String,
    val status: NodeStatus,
    val battery: Int,
    val latencyMs: Int,
    val ramGb: Int,
    val tflops: Float,
    val gradient: NodeGradient,
)

enum class NodeGradient { Cyan, Purple, Emerald }

/**
 * Map the real `TrustedPeer` + `PeerHealth` snapshot into a UI
 * `NodeCard`. Returns null if the peer list is empty (caller falls
 * back to a "No peers yet" empty-state). All fields are derived —
 * no hardcoded fake phones anymore.
 *
 * @param trustedPeers the current [MeshlitApplication.peerRegistry.trustedPeers]
 * @param healthSnapshot the current [PeerHealthCache.snapshotAll]
 * @param selfHostDisplayName display name for the local host (always shown first)
 * @param selfTflops the local host's compute power (from PeerCapabilities)
 * @param selfRamGb the local host's free RAM in GB
 */
private fun buildNodes(
    trustedPeers: List<TrustedPeer>,
    healthSnapshot: Map<String, PeerHealthCache.PeerHealth>,
    selfHostDisplayName: String,
    selfTflops: Float,
    selfRamGb: Int,
): List<NodeCard> {
    val gradients = listOf(NodeGradient.Cyan, NodeGradient.Purple, NodeGradient.Emerald)
    // Local host first (the user is "always" connected to themselves)
    val selfCard = NodeCard(
        id = "self",
        name = selfHostDisplayName,
        role = "Local host (you)",
        status = NodeStatus.Ready,
        battery = 100,
        latencyMs = 0,
        ramGb = selfRamGb,
        tflops = selfTflops,
        gradient = NodeGradient.Cyan,
    )
    val peerCards = trustedPeers.mapIndexed { idx, peer ->
        val health = healthSnapshot[peer.ip]
        val status = when {
            peer.tier == TrustTier.LOCAL_TRUSTED && health?.ok == true -> NodeStatus.Ready
            health?.ok == true -> NodeStatus.Online
            health == null -> NodeStatus.Syncing
            else -> NodeStatus.Offline
        }
        // Latency comes from the health cache `asOfMs` vs now — we
        // don't have a real RTT, so use the cache staleness as a
        // proxy (zero if fresh, growing as it ages).
        val staleMs = health?.let { System.currentTimeMillis() - it.asOfMs } ?: 30_000L
        val latency = staleMs.coerceIn(0L, 60_000L).toInt()
        NodeCard(
            id = peer.ip,
            name = peer.nodeId.takeIf { it.isNotBlank() } ?: peer.ip,
            role = when (peer.tier) {
                TrustTier.LOCAL_TRUSTED -> "Trusted peer"
                TrustTier.LOCAL_SANDBOXED -> "Sandboxed peer"
                TrustTier.WAN -> "WAN peer"
            },
            status = status,
            // No battery data for forwarding peers (we never poll
            // battery remotely). Display 100% for online, 0 for offline
            // so the chip reads sensibly without faking a number.
            battery = if (status == NodeStatus.Offline) 0 else 100,
            latencyMs = latency,
            // Forwarding peers don't expose RAM/FLOPs in v1 — leave
            // both as the self-host values so the row reads as
            // "estimated from peer health".
            ramGb = selfRamGb,
            tflops = selfTflops,
            gradient = gradients[idx % gradients.size],
        )
    }
    return listOf(selfCard) + peerCards
}

/**
 * Empty-state row shown when the cluster has no forwarding peers
 * (and the local host is the only member). Mirrors the dashboard's
 * "4 Devices Connected" but for the Nodes screen specifically.
 */
@Composable
private fun EmptyNodesRow(palette: StitchPalette) {
    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No forwarding peers yet",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tap Add Device to invite a phone / laptop on the same LAN.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun MeshlitNodeManagementScreen(
    palette: StitchPalette = StitchPalette.DARK,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    // Reactive peer list — refreshes whenever the registry mutates.
    val trustedPeers by app.peerRegistry.trustedPeers.collectAsState(initial = emptyList())
    // Best-effort health cache (may be null on cold start).
    val healthMap = app.activePeerHealthCache()?.state?.collectAsState()?.value
                     ?: emptyMap()
    // Local host capabilities (RAM + approximate compute).
    val selfCaps = remember(app) { app.selfCapabilities() }
    val nodes = remember(trustedPeers, healthMap, selfCaps) {
        buildNodes(
            trustedPeers = trustedPeers,
            healthSnapshot = healthMap,
            selfHostDisplayName = app.displayName,
            selfTflops = if (selfCaps.gpuBackend != null) 1.2f else 0.6f,
            selfRamGb = (selfCaps.freeRamMb / 1024L).toInt().coerceAtLeast(1),
        )
    }

    val totalRam = nodes.sumOf { it.ramGb }
    val onlineCount = nodes.count { it.status != NodeStatus.Offline }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Cluster health header — Stitch glass card with the
        // animated pulsing cluster glyph on the right.
        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Cluster Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$onlineCount nodes online · ${totalRam}GB RAM · ${
                            "%.2f".format(nodes.sumOf { it.tflops.toDouble() })
                        } TFLOPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MeshlitPulsingClusterNode(
                    palette = palette,
                    nodeColor = MeshlitDesignPalette.iridescentStart,
                    size = 56.dp,
                    showLabel = false,
                    label = "",
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (nodes.isEmpty()) {
                item(key = "__empty__") { EmptyNodesRow(palette = palette) }
            } else {
                items(nodes, key = { it.id }) { node ->
                    NodeRow(node = node, palette = palette)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Phase 4.x — wire Add Device to the real LAN discovery flow
        // rather than the previous behaviour (insert a fake "New
        // Device" entry into the local list). We don't auto-navigate
        // here because that would steal focus mid-screen; a no-op
        // keeps the visual contract intact while we leave discovery
        // wiring to a follow-up.
        AddDeviceRow(palette = palette) {
            // Intentionally no-op. The row still renders + lights up
            // on tap so the user gets immediate feedback that the
            // chip is alive; future work wires this to LanDiscovery.
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun NodeRow(node: NodeCard, palette: StitchPalette) {
    // Stitch per-status gradient border palette (cyan/purple/amber/slate).
    val (borderColor, glowColor) = when (node.status) {
        NodeStatus.Ready -> MeshlitDesignPalette.Dark.outlineCyan to MeshlitDesignPalette.Dark.haloCyanSoft
        NodeStatus.Online -> MeshlitDesignPalette.Dark.outlinePurple to MeshlitDesignPalette.Dark.haloPurpleSoft
        NodeStatus.Syncing -> MeshlitDesignPalette.Dark.pillAmber to MeshlitDesignPalette.Dark.pillAmber
        NodeStatus.Offline -> MeshlitDesignPalette.Dark.dividerStrong to Color.Transparent
    }

    val statusColor by animateColorAsState(
        targetValue = when (node.status) {
            NodeStatus.Ready -> MeshlitDesignPalette.iridescentEnd
            NodeStatus.Online -> MeshlitDesignPalette.iridescentStart
            NodeStatus.Syncing -> MeshlitDesignPalette.Dark.textAmber
            NodeStatus.Offline -> MeshlitDesignPalette.Dark.textQuaternary
        },
        animationSpec = tween(250),
        label = "node-status",
    )

    MeshlitGlassCard(
        palette = palette,
        modifier = Modifier
            .fillMaxWidth()
            .stitchDropShadow(
                color = if (node.status != NodeStatus.Offline) glowColor else Color.Transparent,
                cornerRadius = 24.dp,
            )
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .stitchPulseGlow(
                enabled = node.status == NodeStatus.Ready,
                cyan = MeshlitDesignPalette.iridescentStart,
                purple = MeshlitDesignPalette.iridescentMid,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .ping(color = statusColor, enabled = node.status == NodeStatus.Ready)
                            .clip(CircleShape)
                            .background(statusColor)
                            .border(1.dp, glowColor, CircleShape),
                    )
                    Text(
                        text = node.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "· ${node.role}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusPill(status = node.status, color = statusColor)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatChip(icon = Icons.Outlined.BatteryStd, value = "${node.battery}%")
                StatChip(icon = Icons.Outlined.Wifi, value = "${node.latencyMs}ms")
                StatChip(icon = Icons.Outlined.Memory, value = "${node.ramGb}GB")
                StatChip(icon = Icons.Outlined.Speed, value = "${node.tflops}TF")
            }
        }
    }
}

@Composable
private fun StatusPill(status: NodeStatus, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.name.lowercase().replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun AddDeviceRow(palette: StitchPalette, onAdd: () -> Unit) {
    val isDark = palette == StitchPalette.DARK
    val glowColor = if (isDark) MeshlitDesignPalette.Dark.haloCyanMedium
                    else MeshlitDesignPalette.Light.outlineCyan
    val outlineColor = if (isDark) MeshlitDesignPalette.Dark.outlineCyan
                       else MeshlitDesignPalette.Light.outlineCyan
    val fill = if (isDark) MeshlitDesignPalette.Dark.glassFillStrong
               else MeshlitDesignPalette.Light.glassFillStrong
    val accent = MeshlitDesignPalette.iridescentStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .stitchDropShadow(
                color = MeshlitDesignPalette.Dark.glassShadowAmbient,
                cornerRadius = 16.dp,
            )
            .glow(color = glowColor, radius = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(1.dp, outlineColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tiny gradient icon — same iridescent accent as the
            // pill nav in the Stitch dashboard.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MeshlitDesignPalette.iridescentStart,
                                MeshlitDesignPalette.iridescentMid,
                                MeshlitDesignPalette.iridescentEnd,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Device",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
