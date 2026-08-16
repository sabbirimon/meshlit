package com.meshlit.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.ui.motion.MeshlitMotion

/**
 * Stitch-parity circular gauge: a 110dp ring with a glowing
 * foreground stroke, percentage label in the middle, type-coded
 * colour, and a label below.
 *
 * Types map to icons + accent colours:
 *  - `cpu`    → Speed icon,  orange (#F97316)
 *  - `gpu`    → Memory icon, cyan   (#38BDF8)
 *  - `ram`    → Storage icon,green  (#10B981)
 *  - `power`  → Bolt icon,   purple (#A78BFA)
 *
 * Mirrors
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/CircularGauge.tsx`.
 * Animations: 1000ms ease-out on the stroke-dashoffset so the ring
 * "fills" smoothly when the percentage changes.
 */
enum class GaugeType { Cpu, Gpu, Ram, Power }

@Composable
fun MeshlitCircularGauge(
    percentage: Float,
    label: String,
    type: GaugeType,
    modifier: Modifier = Modifier,
    size: Dp = 110.dp,
    isDark: Boolean = true,
) {
    val strokeWidth = 8.dp
    val ringColor = when (type) {
        GaugeType.Cpu -> Color(0xFFF97316)
        GaugeType.Gpu -> Color(0xFF38BDF8)
        GaugeType.Ram -> Color(0xFF10B981)
        GaugeType.Power -> Color(0xFFA78BFA)
    }
    val trackColor = if (isDark)
        Color.White.copy(alpha = 0.10f)
    else
        Color.Black.copy(alpha = 0.08f)

    val animatedPct by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 1000),
        label = "gauge-pct",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val sw = strokeWidth.toPx()
                val r = (this.size.minDimension - sw) / 2f
                val topLeft = Offset(
                    x = (this.size.width - r * 2f) / 2f,
                    y = (this.size.height - r * 2f) / 2f,
                )
                val arcSize = Size(width = r * 2f, height = r * 2f)

                // Track.
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )

                // Foreground arc, sweeps from 12 o'clock.
                val sweep = (animatedPct / 100f) * 360f
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = type.icon(),
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "${animatedPct.toInt()}%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark)
                Color(0xFFCBD5E1)
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = size, height = 16.dp),
        )
    }
}

private fun GaugeType.icon(): ImageVector = when (this) {
    GaugeType.Cpu -> Icons.Outlined.Speed
    GaugeType.Gpu -> Icons.Outlined.Memory
    GaugeType.Ram -> Icons.Outlined.Storage
    GaugeType.Power -> Icons.Outlined.Bolt
}
