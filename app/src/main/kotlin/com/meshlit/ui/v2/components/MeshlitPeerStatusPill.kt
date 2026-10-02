package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meshlit.inference.PeerHealthCache
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitTextTertiaryV2

/**
 * v2 peer-status pill. Mirrors the v1 `PeerHealthSection`'s
 * status indicator but uses the v2 surface vocabulary
 * (16 dp radius pill, 10 dp horizontal padding, bodySmall label
 * in the accent colour).
 *
 * Per the plan's §4 ClusterScreen bullet: `MeshlitPeerStatusPill
 * (memberState, accent)`. The shared `PeerHealthCache.PeerHealth`
 * shape is reused — `ok = true` lights the violet accent,
 * `ok = false` falls back to tertiary. No cluster-extension
 * function needed; the colour choice is local to the pill.
 */
@Composable
fun MeshlitPeerStatusPill(
    peerHealth: PeerHealthCache.PeerHealth,
    modifier: Modifier = Modifier,
) {
    val accent: Color = if (peerHealth.ok) MeshlitPulseViolet else MeshlitTextTertiaryV2
    val label: String = when {
        peerHealth.ok && peerHealth.modelLoaded -> "ready"
        peerHealth.ok -> "online"
        else -> "offline"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = accent,
        modifier = modifier
            .background(
                color = accent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}