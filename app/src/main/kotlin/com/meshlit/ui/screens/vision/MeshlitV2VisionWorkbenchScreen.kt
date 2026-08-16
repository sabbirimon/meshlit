package com.meshlit.ui.screens.vision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.stitchPulseGlow

/**
 * Stitch-parity Vision Workbench with VLM bounding-box detection.
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/VisionWorkbench.tsx`.
 */
@Composable
fun MeshlitV2VisionWorkbenchScreen(palette: StitchPalette = StitchPalette.DARK) {
    val isDark = palette == StitchPalette.DARK
    var prompt by remember { mutableStateOf("Describe what you see and tag objects with bounding boxes.") }
    val boxes = listOf(
        BBox("Phone", 0.18f, 0.55f, 0.30f, 0.30f, MeshlitDesignPalette.iridescentStart),
        BBox("Cup", 0.65f, 0.40f, 0.18f, 0.22f, MeshlitDesignPalette.iridescentIndigo),
        BBox("Notebook", 0.40f, 0.70f, 0.30f, 0.16f, MeshlitDesignPalette.Dark.textAmber),
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
                text = "Vision Workbench",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "On-device VLM · bounding-box detection · object labels",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Image preview with bounding boxes overlay.
        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeshlitDesignPalette.Dark.dividerSolid.copy(alpha = 0.6f)),
            ) {
                // Mock scene gradient.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MeshlitDesignPalette.Dark.dividerSolid,
                                    MeshlitDesignPalette.canvasDark,
                                ),
                            ),
                        ),
                )
                boxes.forEach { box ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(box.width)
                            .fillMaxWidth(1f)
                            .padding(start = 16.dp + (box.x * 280).dp)
                            .width(80.dp)
                            .height(60.dp)
                            .border(2.dp, box.color, RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = box.label,
                        color = box.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp + (box.x * 280).dp, top = 6.dp),
                    )
                }
                // Pick photo CTA.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .stitchPulseGlow(
                            enabled = true,
                            cyan = MeshlitDesignPalette.iridescentStart,
                            purple = MeshlitDesignPalette.iridescentMid,
                        )
                        .clip(RoundedCornerShape(50))
                        .background(MeshlitDesignPalette.iridescentStart.copy(alpha = 0.25f))
                        .border(1.dp, MeshlitDesignPalette.iridescentStart, RoundedCornerShape(50))
                        .clickable { }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MeshlitDesignPalette.iridescentStart,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pick image",
                        color = MeshlitDesignPalette.iridescentStart,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Prompt",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = prompt,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .stitchPulseGlow(
                    enabled = true,
                    cyan = MeshlitDesignPalette.iridescentStart,
                    purple = MeshlitDesignPalette.iridescentMid,
                )
                .clip(RoundedCornerShape(50))
                .background(MeshlitDesignPalette.iridescentStart.copy(alpha = 0.18f))
                .border(1.dp, MeshlitDesignPalette.iridescentStart, RoundedCornerShape(50))
                .clickable { }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = null,
                    tint = MeshlitDesignPalette.iridescentStart,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Run VLM",
                    color = MeshlitDesignPalette.iridescentStart,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private data class BBox(
    val label: String,
    val x: Float, val y: Float,
    val width: Float, val height: Float,
    val color: Color,
)