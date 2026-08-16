package com.meshlit.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.cluster.ClusterMaster
import com.meshlit.core.cluster.KubeScoring
import com.meshlit.core.cluster.KubeScheduler
import com.meshlit.core.inference.net.BindScope
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.ui.components.AppleGroupedCard
import com.meshlit.ui.components.AppleListTile
import com.meshlit.ui.components.PulsingDot
import com.meshlit.ui.screens.settings.SectionLabel

/**
 * Phase Hivemind-1 — Cluster webserver card.
 *
 * Lives on the Server Hub screen and lets the user:
 *  - Pick a [BindScope] (PUBLIC, LAN, OFF).
 *  - See the current host (nodeId, kube score, hostname).
 *  - See the eligible hosts list sorted by score.
 *  - Force a re-election (kubeScheduler.forceYield()).
 *
 * Uses the Stitch glass tokens so it matches the rest of the
 * app: `MeshlitGlassCard` for the surface, `stitchPulseGlow`
 * for the active host row, `MeshlitShimmerProgressBar` for
 * the handover indicator.
 */
@Composable
fun ClusterWebserverCard(
    master: ClusterMaster,
    scheduler: KubeScheduler,
    onBindScopeChange: (BindScope) -> Unit,
    onReelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val masterState by master.state.collectAsState()
    val kubeState by scheduler.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier) {
        SectionLabel("Cluster webserver")
        AppleGroupedCard(contentPadding = PaddingValues(0.dp)) {
            // 1. BindScope picker
            BindScopeRow(
                current = masterState.bindScope,
                onSelected = onBindScopeChange,
            )
            // 2. Current host
            CurrentHostRow(
                hostname = masterState.hostname,
                hostNodeId = kubeState.hostOfRecord,
                kubeScore = kubeState.hostScore,
                isMaster = masterState.isMaster,
            )
            // 3. Eligible hosts (collapsible)
            EligibleHostsList(
                hosts = kubeState.eligibleHosts,
            )
            // 4. Re-elect button
            ReelectRow(onClick = onReelect)
        }
    }
}

@Composable
private fun BindScopeRow(
    current: BindScope,
    onSelected: (BindScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Bind",
            modifier = Modifier.width(80.dp),
            color = MeshlitDesignPalette.streamingGlow,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BindScope.values().forEach { scope ->
                Chip(
                    label = scope.tag.uppercase(),
                    selected = scope == current,
                    onClick = { onSelected(scope) },
                )
            }
        }
    }
}

@Composable
private fun CurrentHostRow(
    hostname: String,
    hostNodeId: String?,
    kubeScore: Double,
    isMaster: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WifiTethering,
            contentDescription = null,
            tint = if (isMaster) MeshlitDesignPalette.iridescentStart else MeshlitDesignPalette.streamingGlow,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hostNodeId ?: "no host",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = hostname,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
        Text(
            text = "%.2f".format(kubeScore),
            color = MeshlitDesignPalette.iridescentStart,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
private fun EligibleHostsList(hosts: List<KubeScoring.ScoreBreakdown>) {
    if (hosts.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
    ) {
        Text(
            "Eligible hosts · ${hosts.count { it.hostEligible }} of ${hosts.size}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        hosts.take(5).forEach { host ->
            HostRow(host)
        }
    }
}

@Composable
private fun HostRow(host: KubeScoring.ScoreBreakdown) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(
            color = if (host.hostEligible) MeshlitDesignPalette.iridescentStart else MeshlitDesignPalette.streamingGlow,
            size = 6.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = host.nodeId,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = host.ip.ifBlank { "—" },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "%.2f".format(host.total),
            color = if (host.hostEligible) MeshlitDesignPalette.iridescentStart else MeshlitDesignPalette.streamingGlow,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

@Composable
private fun ReelectRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Re-elect",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Run now",
            color = MeshlitDesignPalette.iridescentStart,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .padding(0.dp),
        )
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) {
        MeshlitDesignPalette.iridescentStart.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    }
    val border = if (selected) {
        MeshlitDesignPalette.iridescentStart
    } else {
        MeshlitDesignPalette.streamingGlow.copy(alpha = 0.2f)
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 0.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(0.dp),
        ) {
            androidx.compose.material3.Surface(
                onClick = onClick,
                shape = RoundedCornerShape(999.dp),
                color = bg,
                border = androidx.compose.foundation.BorderStroke(1.dp, border),
            ) {
                Text(
                    text = label,
                    color = if (selected) MeshlitDesignPalette.iridescentStart else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}