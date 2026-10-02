package com.meshlit.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.core.net.capture.MdnsPacketRecord
import com.meshlit.ui.theme.MeshlitOutline
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inline list of the most recent mDNS packets decoded by
 * [com.meshlit.core.net.capture.MdnsCaptureListener]. Shows up to
 * [maxRecords] entries; older entries roll off the top.
 *
 * Two row types:
 *  - **Decoded** (violet leading dot) — packet contained a
 *    `_meshlit._tcp.local.` TXT record with the standard JSON
 *    payload; row shows source IP:port, decoded `nodeId`,
 *    host:port, fingerprint prefix.
 *  - **Raw** (tertiary leading dot) — packet was mDNS traffic
 *    but not Meshlit (Bonjour browse, queries to other services).
 *    Row shows `raw mDNS · N bytes`.
 */
@Composable
fun PacketStreamPanel(
    records: List<MdnsPacketRecord>,
    modifier: Modifier = Modifier,
    maxRecords: Int = 50,
) {
    val visible = records.takeLast(maxRecords)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = MeshlitPulseViolet,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = "Packet stream",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "${records.size} captured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
            if (visible.isEmpty()) {
                Text(
                    text = "Listening for mDNS packets… tap Start capture to begin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visible) { rec -> PacketRow(rec) }
                }
            }
        }
    }
}

@Composable
private fun PacketRow(rec: MdnsPacketRecord) {
    val decoded = rec.decoded
    val dotColor = if (decoded != null) MeshlitPulseViolet else MeshlitOutline
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .background(color = dotColor, shape = CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Line 1 — timestamp + source IP:port
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = TIME_FMT.format(Date(rec.timestampMs)),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = MeshlitTextTertiaryV2,
                )
                Text(
                    text = "${rec.sourceIp}:${rec.sourcePort}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MeshlitTextSecondaryV2,
                )
            }
            // Line 2 — decoded or raw payload summary
            if (decoded != null) {
                Text(
                    text = decoded.nodeId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitPulseViolet,
                )
                Text(
                    text = "${decoded.host}:${decoded.port} · fp ${decoded.fingerprint.take(16)}…",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
            } else {
                Text(
                    text = "raw mDNS · ${rec.length} bytes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextTertiaryV2,
                )
            }
        }
    }
}

private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)