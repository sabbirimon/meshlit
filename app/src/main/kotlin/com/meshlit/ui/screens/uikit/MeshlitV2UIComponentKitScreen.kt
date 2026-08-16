package com.meshlit.ui.screens.uikit

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitBreathingGlowButton
import com.meshlit.design.MeshlitBreathingGlowVariant
import com.meshlit.design.MeshlitCircularGauge
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.MeshlitPulsingClusterNode
import com.meshlit.design.MeshlitShimmerProgressBar
import com.meshlit.design.GaugeType
import com.meshlit.design.StitchPalette

/**
 * Stitch-parity UI Component Kit — a one-screen gallery of the
 * design widgets (glass card, breathing button, circular gauge,
 * pulsing cluster node, shimmer progress bar, status pills).
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/UIComponentKit.tsx`.
 */
@Composable
fun MeshlitV2UIComponentKitScreen(palette: StitchPalette = StitchPalette.DARK) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                Text(
                    text = "UI Component Kit",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Glass · glow · shimmer · gauge · pulsing node",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Glass card showcase.
        item {
            SectionLabel("Glass card")
            MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Semi-transparent with hairline border",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Breathing glow button.
        item {
            SectionLabel("Breathing glow button")
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MeshlitBreathingGlowButton(
                    label = "Run Inference",
                    onClick = {},
                    palette = palette,
                    variant = MeshlitBreathingGlowVariant.PILL_GRADIENT,
                )
            }
        }

        // Circular gauges row.
        item {
            SectionLabel("Circular gauges")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                MeshlitCircularGauge(percentage = 65f, label = "CPU",  type = GaugeType.Cpu)
                MeshlitCircularGauge(percentage = 42f, label = "GPU",  type = GaugeType.Gpu)
                MeshlitCircularGauge(percentage = 78f, label = "RAM",  type = GaugeType.Ram)
                MeshlitCircularGauge(percentage = 30f, label = "PWR",  type = GaugeType.Power)
            }
        }

        // Pulsing cluster node row.
        item {
            SectionLabel("Pulsing cluster node")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                MeshlitPulsingClusterNode(
                    palette = palette,
                    nodeColor = MeshlitDesignPalette.iridescentStart,
                    size = 56.dp,
                    showLabel = true,
                    label = "node-1",
                )
                MeshlitPulsingClusterNode(
                    palette = palette,
                    nodeColor = MeshlitDesignPalette.iridescentIndigo,
                    size = 56.dp,
                    showLabel = true,
                    label = "node-2",
                )
                MeshlitPulsingClusterNode(
                    palette = palette,
                    nodeColor = MeshlitDesignPalette.iridescentEnd,
                    size = 56.dp,
                    showLabel = true,
                    label = "node-3",
                )
            }
        }

        // Shimmer progress bar.
        item {
            SectionLabel("Shimmer progress bar")
            Column {
                MeshlitShimmerProgressBar(progress = 0.72f)
                Spacer(modifier = Modifier.height(8.dp))
                MeshlitShimmerProgressBar(progress = 0.30f)
            }
        }

        // Status pills.
        item {
            SectionLabel("Status pills")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill("Ready", MeshlitDesignPalette.iridescentEnd)
                StatusPill("Online", MeshlitDesignPalette.iridescentStart)
                StatusPill("Syncing", MeshlitDesignPalette.Dark.textAmber)
                StatusPill("Offline", MeshlitDesignPalette.Dark.textQuaternary)
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}