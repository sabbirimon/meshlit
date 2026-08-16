package com.meshlit.ui.screens.speech

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.glow
import com.meshlit.design.stitchPulseGlow

/**
 * Stitch-parity Speech Lab with live VAD waveform + STT transcription.
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/SpeechLab.tsx`.
 */
@Composable
fun MeshlitV2SpeechLabScreen(palette: StitchPalette = StitchPalette.DARK) {
    val isDark = palette == StitchPalette.DARK
    var recording by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("Meshlit is a federated edge AI cluster…") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = "Speech Lab",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "Whisper STT · VAD waveform · neural TTS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Live waveform visualizer (VAD amplitude).
        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        tint = MeshlitDesignPalette.iridescentStart,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live VAD",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (recording) "Listening" else "Idle",
                        color = if (recording) MeshlitDesignPalette.iridescentStart else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.glow(
                            color = MeshlitDesignPalette.Dark.haloCyanSoft,
                            radius = 12.dp,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                WaveformVisualizer(active = recording, isDark = isDark)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Partial transcript",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = partial,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(80.dp)
                .scale(if (recording) 1.05f else 1.0f)
                .stitchPulseGlow(
                    enabled = recording,
                    cyan = MeshlitDesignPalette.iridescentPink,
                    purple = MeshlitDesignPalette.iridescentMid,
                )
                .clip(CircleShape)
                .background(
                    if (recording) MeshlitDesignPalette.iridescentPink.copy(alpha = 0.25f)
                    else MeshlitDesignPalette.iridescentStart.copy(alpha = 0.25f),
                )
                .border(
                    2.dp,
                    if (recording) MeshlitDesignPalette.iridescentPink else MeshlitDesignPalette.iridescentStart,
                    CircleShape,
                )
                .clickable { recording = !recording },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (recording) Icons.Outlined.Stop else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (recording) MeshlitDesignPalette.iridescentPink else MeshlitDesignPalette.iridescentStart,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (recording) "Tap to stop" else "Tap to record",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun WaveformVisualizer(active: Boolean, isDark: Boolean) {
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-phase",
    )
    val amp by animateFloatAsState(
        targetValue = if (active) 1f else 0.15f,
        animationSpec = tween(400),
        label = "wave-amp",
    )
    val barColor = if (isDark) MeshlitDesignPalette.iridescentStart else MeshlitDesignPalette.Light.textCyanStrong
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MeshlitDesignPalette.Dark.canvasDeep.copy(alpha = 0.4f)),
    ) {
        val centerY = size.height / 2
        val bars = 64
        val gap = size.width / bars
        for (i in 0 until bars) {
            val t = (i.toFloat() / bars) + phase
            val envelope = (kotlin.math.sin(t * 8f) * 0.6f + 0.4f).toFloat()
            val h = envelope * amp * (size.height / 2f - 4f)
            drawLine(
                color = barColor,
                start = Offset(i * gap + gap / 2, centerY - h),
                end = Offset(i * gap + gap / 2, centerY + h),
                strokeWidth = 2.5f,
            )
        }
    }
}