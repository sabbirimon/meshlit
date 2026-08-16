package com.meshlit.ui.screens.agent

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.stitchPulseGlow

/**
 * Stitch-parity Ghosty Agent Workbench with multi-step Thought →
 * Action → Observation loop, live scratchpad, and node-pipeline
 * sharding.
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/AgentWorkbench.tsx`.
 */
@Composable
fun MeshlitV2AgentWorkbenchScreen(palette: StitchPalette = StitchPalette.DARK) {
    val isDark = palette == StitchPalette.DARK
    val steps = listOf(
        AgentStep(1, "Thought", "Need current device temperature + battery to throttle inference.", MeshlitDesignPalette.iridescentIndigo),
        AgentStep(2, "Action",  "Call MCP tool: device_status (node_id=\"all\", include_thermal=true)", MeshlitDesignPalette.iridescentStart),
        AgentStep(3, "Result",  """{"node-1": {"battery": 82, "temp_c": 34.2}, "node-2": {"battery": 71, "temp_c": 41.0}}""", MeshlitDesignPalette.iridescentEnd),
        AgentStep(4, "Thought", "node-2 is hot (41°C). Reduce shard count by 1.", MeshlitDesignPalette.iridescentIndigo),
        AgentStep(5, "Action",  "Call MCP tool: cluster_dispatch (model_id=\"qwen2.5-1.5b\", shards=3)", MeshlitDesignPalette.iridescentStart),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = "Agent Workbench",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Multi-step Thought → Action → Observation · scratchpad",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Live scratchpad card.
        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MeshlitDesignPalette.iridescentEnd),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scratchpad",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TypeIndicator()
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "→ Plan: Throttle cluster inference based on thermal.\n→ State: 1/5 steps complete · node-2 at 41°C.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(steps) { step -> StepCard(step = step) }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .stitchPulseGlow(
                    enabled = true,
                    cyan = MeshlitDesignPalette.iridescentIndigo,
                    purple = MeshlitDesignPalette.iridescentMid,
                )
                .clip(RoundedCornerShape(50))
                .background(MeshlitDesignPalette.iridescentIndigo.copy(alpha = 0.20f))
                .border(1.dp, MeshlitDesignPalette.iridescentIndigo, RoundedCornerShape(50))
                .clickable { }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = MeshlitDesignPalette.iridescentIndigo,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Run Agent",
                    color = MeshlitDesignPalette.iridescentIndigo,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private data class AgentStep(
    val index: Int,
    val kind: String,
    val content: String,
    val accent: Color,
)

@Composable
private fun StepCard(step: AgentStep) {
    MeshlitGlassCard(palette = StitchPalette.DARK, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(step.accent.copy(alpha = 0.25f))
                        .border(1.dp, step.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${step.index}",
                        color = step.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = step.kind,
                    color = step.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Outlined.SyncAlt,
                    contentDescription = null,
                    tint = step.accent.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.content,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TypeIndicator() {
    val infinite = rememberInfiniteTransition(label = "agent-caret")
    val alpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "caret",
    )
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = 12.dp)
            .background(MeshlitDesignPalette.iridescentIndigo.copy(alpha = alpha)),
    )
}