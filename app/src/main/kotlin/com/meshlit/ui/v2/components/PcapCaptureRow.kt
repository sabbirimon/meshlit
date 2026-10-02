package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.pcap.RecorderState
import com.meshlit.ui.theme.MeshlitPulseCoral
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Coral-accented row rendered between the BLE transport switch
 * and the Pair-with-QR chip on the v2 Scan screen. Three states:
 *
 *  - **Idle** — outline coral "Start capture" chip.
 *  - **Recording** — coral-filled "Stop & share" chip + a
 *    monospace `mm:ss` timer + a packet count.
 *  - **Ready** — file-name + size + Share / Discard chips.
 *  - **Failed** — same as Idle plus a small reason line in
 *    tertiary text.
 *
 * Driven by [state] from [com.meshlit.pcap.PacketCaptureManager].
 */
@Composable
fun PcapCaptureRow(
    state: RecorderState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShare: (file: java.io.File) -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MeshlitSurfaceContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 1 — dot + title + chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when (state) {
                                is RecorderState.Recording -> MeshlitPulseCoral
                                else -> MeshlitTextTertiaryV2
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                )
                Text(
                    text = "mDNS capture",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                    modifier = Modifier.weight(1f),
                )
                when (state) {
                    is RecorderState.Idle, is RecorderState.Failed -> {
                        TextButton(onClick = onStart) {
                            Text(
                                text = "Start capture",
                                color = MeshlitPulseCoral,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    is RecorderState.Recording -> {
                        TextButton(onClick = onStop) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "Stop",
                                tint = MeshlitPulseCoral,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = " Stop & share",
                                color = MeshlitPulseCoral,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    is RecorderState.Ready -> {
                        TextButton(onClick = { onShare(state.file) }) {
                            Icon(
                                Icons.Filled.SaveAlt,
                                contentDescription = "Share pcap",
                                tint = MeshlitPulseCoral,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = " Share",
                                color = MeshlitPulseCoral,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        TextButton(onClick = onDiscard) {
                            Text(
                                text = "Discard",
                                color = MeshlitTextSecondaryV2,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            // Row 2 — status line (timer / size / reason)
            Text(
                text = statusLine(state),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = when (state) {
                    is RecorderState.Failed -> MeshlitPulseCoral
                    else -> MeshlitTextTertiaryV2
                },
            )
        }
    }
}

@Composable
private fun statusLine(state: RecorderState): String {
    return when (state) {
        is RecorderState.Idle -> "Idle — tap Start to record mDNS packets to a .pcap file"
        is RecorderState.Failed -> "Failed: ${state.reason}"
        is RecorderState.Recording -> {
            val secs = tickAndFormatElapsed(state.startedAtMs)
            String.format(
                Locale.ROOT,
                "RECORDING · %s · %d packets captured",
                secs,
                state.packetCount,
            )
        }
        is RecorderState.Ready -> {
            val kb = state.sizeBytes / 1024.0
            String.format(
                Locale.ROOT,
                "%s · %.1f KB · %d packets",
                state.file.name,
                kb,
                state.packetCount,
            )
        }
    }
}

/**
 * Compose-local ticker that increments every 500 ms. Returns the
 * elapsed mm:ss string for the [startedAtMs] epoch.
 */
@Composable
private fun tickAndFormatElapsed(startedAtMs: Long): String {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsedSec = ((nowMs - startedAtMs) / 1000L).coerceAtLeast(0)
    val mm = elapsedSec / 60
    val ss = elapsedSec % 60
    return String.format(Locale.ROOT, "%02d:%02d", mm, ss)
}

/** Re-export for screens that want the coral accent directly. */
internal val PcapCoral: Color = MeshlitPulseCoral