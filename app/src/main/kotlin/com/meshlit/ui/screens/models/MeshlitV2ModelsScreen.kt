package com.meshlit.ui.screens.models

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.MeshlitShimmerProgressBar
import com.meshlit.design.StitchPalette
import kotlinx.coroutines.delay

/**
 * Stitch-parity Model Download Manager.
 *
 * Mirror of `ModelDownloadManager.tsx` from the Stitch source.
 * Uses the canonical glass card surface, iridescent accent on the
 * active filter pill, and the design-system color tokens
 * (`iridescentStart` for cyan, `iridescentEnd` for emerald,
 * `textAmber` for the paused state).
 */
@Composable
fun MeshlitV2ModelsScreen(palette: StitchPalette = StitchPalette.DARK) {
    var models by remember { mutableStateOf(seedModels()) }
    var activeFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "LLM", "Vision", "Speech")
    val visible = models.filter {
        activeFilter == "All" || it.category == activeFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = "Model Catalog",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Direct Hugging Face downloads · SAF import · on-device flash storage",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        StorageCard(palette = palette, models = models)

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { filter ->
                val sel = filter == activeFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (sel) MeshlitDesignPalette.iridescentStart.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        .border(
                            1.dp,
                            if (sel) MeshlitDesignPalette.iridescentStart else Color.Transparent,
                            RoundedCornerShape(50),
                        )
                        .clickable { activeFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = filter,
                        color = if (sel) MeshlitDesignPalette.iridescentStart
                                else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { model ->
                ModelRow(
                    palette = palette,
                    model = model,
                    onStart = { models = startDownload(models, model.id) },
                    onPause = { models = pauseDownload(models, model.id) },
                )
            }
        }
    }
}

private enum class ModelStatus { Idle, Downloading, Paused, Downloaded }

private data class Model(
    val id: String,
    val name: String,
    val category: String,
    val sizeGb: Float,
    val quantization: String,
    val status: ModelStatus,
    val progress: Float,
    val speedMbps: Float,
)

private fun seedModels() = listOf(
    Model("qwen-2.5-1.5b", "Qwen 2.5 1.5B Instruct", "LLM", 0.94f, "Q4_K_M", ModelStatus.Downloaded, 1.0f, 0f),
    Model("llama-3.2-1b", "Llama 3.2 1B Instruct", "LLM", 1.20f, "Q4_K_M", ModelStatus.Idle, 0f, 0f),
    Model("mobilevlm-3b", "MobileVLM-3B", "Vision", 2.10f, "Q4_K_M", ModelStatus.Idle, 0f, 0f),
    Model("whisper-tiny", "Whisper Tiny (sherpa)", "Speech", 0.075f, "ONNX", ModelStatus.Downloaded, 1.0f, 0f),
    Model("kokoro-82m", "Kokoro 82M TTS", "Speech", 0.31f, "ONNX", ModelStatus.Idle, 0f, 0f),
)

private fun startDownload(models: List<Model>, id: String) = models.map {
    if (it.id == id) it.copy(status = ModelStatus.Downloading, progress = 0.02f, speedMbps = 18f)
    else it
}

private fun pauseDownload(models: List<Model>, id: String) = models.map {
    if (it.id == id && it.status == ModelStatus.Downloading) it.copy(status = ModelStatus.Paused)
    else it
}

@Composable
private fun StorageCard(palette: StitchPalette, models: List<Model>) {
    val totalGb = 128f
    val usedGb = models.filter { it.status == ModelStatus.Downloaded }.sumOf { it.sizeGb.toDouble() }.toFloat()
    val systemGb = 42.5f
    val usedPct = ((usedGb + systemGb) / totalGb).coerceIn(0f, 1f)

    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Flash Storage",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${"%.1f".format(totalGb - usedGb - systemGb)} GB free",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // Storage bar — iridescent gradient fill (cyan→purple→emerald).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedPct)
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MeshlitDesignPalette.iridescentStart,
                                    MeshlitDesignPalette.iridescentMid,
                                    MeshlitDesignPalette.iridescentEnd,
                                ),
                            ),
                            RoundedCornerShape(50),
                        ),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${"%.1f".format(usedGb + systemGb)} GB / ${totalGb.toInt()} GB · ${"%.1f".format(usedGb)} GB models · 42.5 GB system",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ModelRow(
    palette: StitchPalette,
    model: Model,
    onStart: () -> Unit,
    onPause: () -> Unit,
) {
    var progress by remember { mutableStateOf(model.progress) }
    LaunchedEffect(model.status) {
        while (model.status == ModelStatus.Downloading && progress < 1f) {
            delay(400)
            progress = (progress + 0.05f).coerceAtMost(1f)
        }
    }
    LaunchedEffect(model.id) {
        progress = model.progress
    }

    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when (model.status) {
                        ModelStatus.Downloaded -> Icons.Outlined.CheckCircle
                        ModelStatus.Downloading -> Icons.Outlined.CloudDownload
                        ModelStatus.Paused -> Icons.Outlined.Pause
                        ModelStatus.Idle -> Icons.Outlined.Download
                    },
                    contentDescription = null,
                    tint = when (model.status) {
                        ModelStatus.Downloaded -> MeshlitDesignPalette.iridescentEnd
                        ModelStatus.Downloading -> MeshlitDesignPalette.iridescentStart
                        ModelStatus.Paused -> MeshlitDesignPalette.Dark.textAmber
                        ModelStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "${model.category} · ${model.quantization} · ${"%.2f".format(model.sizeGb)} GB",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                when (model.status) {
                    ModelStatus.Downloaded -> {
                        Text(
                            text = "Installed",
                            color = MeshlitDesignPalette.iridescentEnd,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    ModelStatus.Downloading -> {
                        Icon(
                            imageVector = Icons.Outlined.Pause,
                            contentDescription = "Pause",
                            tint = MeshlitDesignPalette.Dark.textAmber,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onPause),
                        )
                    }
                    ModelStatus.Paused -> {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Resume",
                            tint = MeshlitDesignPalette.iridescentStart,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(onClick = onStart),
                        )
                    }
                    ModelStatus.Idle -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MeshlitDesignPalette.iridescentStart.copy(alpha = 0.2f))
                                .border(1.dp, MeshlitDesignPalette.iridescentStart, RoundedCornerShape(50))
                                .clickable(onClick = onStart)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Get",
                                color = MeshlitDesignPalette.iridescentStart,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            if (model.status == ModelStatus.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                MeshlitShimmerProgressBar(progress = progress)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}% · ${"%.1f".format(model.speedMbps)} MB/s",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
