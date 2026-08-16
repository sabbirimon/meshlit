package com.meshlit.ui.screens.library

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
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.glow

/**
 * Stitch-parity Card Library — grid of category cards leading into
 * the major Meshlit subsystems (clustering, models, network, agents,
 * speech, vision, security, performance).
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/CardLibrary.tsx`.
 */
@Composable
fun MeshlitV2CardLibraryScreen(palette: StitchPalette = StitchPalette.DARK) {
    val cards = listOf(
        LibCard("Cluster",     "Federated topology view",        Icons.Outlined.Hub,         MeshlitDesignPalette.iridescentStart, "12 nodes · 4 online"),
        LibCard("Models",      "GGUF / ONNX catalog",            Icons.Outlined.Storage,     MeshlitDesignPalette.iridescentIndigo, "5 installed · 2 cached"),
        LibCard("Network",     "PCAP + HTTP/SSE router",         Icons.Outlined.Wifi,        MeshlitDesignPalette.iridescentEnd, "8080 open · 5 peers"),
        LibCard("Agent",       "Ghosty reasoning loop",          Icons.Outlined.AutoAwesome, MeshlitDesignPalette.Dark.textAmber, "5/5 steps"),
        LibCard("Speech",      "Whisper STT · Kokoro TTS",       Icons.Outlined.Sensors,     MeshlitDesignPalette.iridescentPink, "VAD armed"),
        LibCard("Vision",      "MobileVLM-3B workbench",         Icons.Outlined.Layers,      MeshlitDesignPalette.iridescentStart, "bbox detector"),
        LibCard("Performance", "Thermal + RAM governors",        Icons.Outlined.Speed,       MeshlitDesignPalette.iridescentIndigo, "3.4 GB free"),
        LibCard("Trust Tiers", "LAN · WAN · Temporary",          Icons.Outlined.Shield,      MeshlitDesignPalette.iridescentEnd, "LAN mode"),
        LibCard("MCP Tools",   "JSON-RPC 2.0 server",            Icons.Outlined.AccountTree, MeshlitDesignPalette.Dark.textAmber, "7 tools"),
        LibCard("Power",       "Battery / charger state",        Icons.Outlined.Bolt,        MeshlitDesignPalette.iridescentPink, "82% battery"),
        LibCard("Cloud Hub",   "External MCP providers",         Icons.Outlined.Cloud,       MeshlitDesignPalette.iridescentStart, "Idle"),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "Card Library",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Browse every Meshlit subsystem · tap to open",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(items = cards, key = { it.title }) { c -> CardTile(c, palette) }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

private data class LibCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val meta: String,
)

@Composable
private fun CardTile(card: LibCard, palette: StitchPalette) {
    MeshlitGlassCard(
        palette = palette,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .glow(color = card.accent.copy(alpha = 0.45f), radius = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(card.accent.copy(alpha = 0.20f))
                    .border(1.dp, card.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = card.icon,
                    contentDescription = null,
                    tint = card.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = card.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(card.accent),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
