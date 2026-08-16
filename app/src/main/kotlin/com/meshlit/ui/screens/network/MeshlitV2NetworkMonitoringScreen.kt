package com.meshlit.ui.screens.network

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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.MeshlitApplication
import com.meshlit.core.trust.TrustTier
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.ping
import com.meshlit.design.stitchPulseGlow
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.TrustedPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Stitch-parity Network Monitoring / PCAP view.
 *
 * Mirror of `NetworkMonitoring.tsx` from the Stitch source. Uses
 * design-system tokens for the cyan / emerald / pink role colours of
 * the three primary cards (Endpoints, PCAP, Peer Health) and the
 * purple GitHub Diagnostic Report tile.
 *
 * Data sources:
 *  - **Endpoint rows** — live HTTP probes against this device's own
 *    server (`http://<localIp>:<port>/v1/...`) kicked off on a
 *    5 s timer. Status / latency reflect the real round-trip.
 *  - **Peer Health Matrix** — derived from
 *    `MeshlitApplication.peerRegistry.trustedPeers` + the live
 *    `PeerHealthCache.state` so an online peer shows the iridescent
 *    emerald dot, a stale entry shows amber, and a missing entry
 *    shows grey. The "LAN / WAN" label is derived from the trust
 *    tier (`LOCAL_*` → LAN, `WAN` → WAN).
 *  - **PCAP card** — kept as a deliberate placeholder. The previous
 *    SS-Deepstability build had a VpnService-based PCAP toggle that
 *    never actually started the ring buffer; the user-visible button
 *    is still useful as a "Start diagnostic capture" affordance that
 *    downstream diagnostic-agent wiring can wire to. We keep the
 *    hex-dump terminal inside the card so the visual contract with
 *    the Stitch mockup is preserved.
 *  - **Diagnostic Report** — kept as a static tile (Phase 3 will
 *    wire to `SelfDiagnosticAgent.exportGitHubReport()`).
 */
@Composable
fun MeshlitV2NetworkMonitoringScreen(palette: StitchPalette = StitchPalette.DARK) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val trustedPeers by app.peerRegistry.trustedPeers.collectAsState(initial = emptyList())
    val healthMap = app.activePeerHealthCache()?.state?.collectAsState()?.value
                     ?: emptyMap()
    // Live endpoint probes — refreshed every 5s. Probing localhost
    // is cheap (~ms) but doing it on every recomposition would be
    // wasteful, so we drive it from a LaunchedEffect timer.
    var endpoints by remember { mutableStateOf(defaultEndpoints(app)) }
    var capturing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            endpoints = probeEndpoints(app)
            delay(5_000L)
        }
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
                text = "Network Monitor",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "HTTP/SSE endpoint health · VpnService PCAP · peer matrix",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(endpoints, key = { it.route }) { ep ->
                EndpointCard(palette = palette, endpoint = ep)
            }
            item { PcapCard(palette = palette, capturing = capturing, onToggle = { capturing = !capturing }) }
            item {
                PeerHealthCard(
                    palette = palette,
                    peers = trustedPeers,
                    health = healthMap,
                )
            }
            item { DiagnosticReportCard(palette = palette) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/** One row inside the Endpoint card list. */
private data class EndpointRow(
    val route: String,
    val status: String,
    val latencyMs: Int,
    val online: Boolean,
)

/** Default row order — the screen always renders these four routes
 *  in the same order, even when the live probe fails (so the screen
 *  still reads as a "monitoring" surface during cold start). */
private fun defaultEndpoints(app: MeshlitApplication): List<EndpointRow> = listOf(
    EndpointRow("/v1/health", "—", 0, online = false),
    EndpointRow("/v1/runtimes", "—", 0, online = false),
    EndpointRow("/v1/model", "—", 0, online = false),
    EndpointRow("/v1/infer", "SSE", 0, online = false),
)

/** Probe this device's HTTP server for each known route. Runs on
 *  Dispatchers.IO; bounded by the OkHttp client's 5s read timeout. */
private suspend fun probeEndpoints(app: MeshlitApplication): List<EndpointRow> {
    val base = "http://127.0.0.1:${app.httpServerPort}"
    val client = app.cloudHttpClient
    val routes = listOf("/v1/health", "/v1/runtimes", "/v1/model", "/v1/infer")
    return withContext(Dispatchers.IO) {
        routes.map { route ->
            val req = Request.Builder().url(base + route).head().build()
            val started = System.currentTimeMillis()
            try {
                client.newCall(req).execute().use { resp ->
                    val elapsed = (System.currentTimeMillis() - started).toInt()
                    val status = "${resp.code} " + when (resp.code) {
                        200 -> "OK"
                        404 -> "Not found"
                        in 500..599 -> "Server err"
                        else -> "—"
                    }
                    EndpointRow(
                        route = route,
                        status = status,
                        latencyMs = elapsed,
                        online = resp.isSuccessful,
                    )
                }
            } catch (t: Throwable) {
                EndpointRow(
                    route = route,
                    status = "Offline",
                    latencyMs = 0,
                    online = false,
                )
            }
        }
    }
}

@Composable
private fun EndpointCard(palette: StitchPalette, endpoint: EndpointRow) {
    val dotColor = if (endpoint.online) MeshlitDesignPalette.iridescentEnd
                   else MeshlitDesignPalette.Dark.textQuaternary
    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                tint = MeshlitDesignPalette.iridescentStart,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ":8080${endpoint.route}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${endpoint.status} · ${endpoint.latencyMs}ms",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .ping(color = dotColor, enabled = endpoint.online)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
    }
}

@Composable
private fun PcapCard(palette: StitchPalette, capturing: Boolean, onToggle: () -> Unit) {
    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = null,
                    tint = if (capturing) MeshlitDesignPalette.iridescentPink
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VpnService PCAP",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (capturing) MeshlitDesignPalette.iridescentPink.copy(alpha = 0.25f)
                            else MeshlitDesignPalette.iridescentStart.copy(alpha = 0.25f),
                        )
                        .border(
                            1.dp,
                            if (capturing) MeshlitDesignPalette.iridescentPink
                            else MeshlitDesignPalette.iridescentStart,
                            RoundedCornerShape(50),
                        )
                        .clickable(onClick = onToggle)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (capturing) "Stop" else "Start",
                        color = if (capturing) MeshlitDesignPalette.iridescentPink
                                else MeshlitDesignPalette.iridescentStart,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            val terminalFill = if (palette == StitchPalette.DARK)
                MeshlitDesignPalette.Dark.canvasDeep.copy(alpha = 0.6f)
            else
                MeshlitDesignPalette.Light.canvasDeep.copy(alpha = 0.6f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(terminalFill)
                    .border(
                        1.dp,
                        MeshlitDesignPalette.iridescentPink.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp),
                    )
                    .stitchPulseGlow(
                        enabled = capturing,
                        cyan = MeshlitDesignPalette.iridescentPink,
                        purple = MeshlitDesignPalette.iridescentMid,
                    )
                    .padding(10.dp),
            ) {
                Text(
                    text = if (capturing)
                        "0x00  45 00 00 3c 1c 46 40 00 40 06 b1 e6 c0 a8 01 2a\n" +
                        "0x10  c0 a8 01 01 04 d2 16 4e 00 00 00 00 a0 02 72 10\n" +
                        "0x20  00 00 00 00 02 04 05 b4 04 02 08 0a 00 00 00 00\n" +
                        "0x30  00 00 00 00 01 03 03 07\n"
                    else
                        "PCAP idle — tap Start to capture to .pcap for Wireshark.",
                    color = if (capturing) MeshlitDesignPalette.iridescentPink
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun PeerHealthCard(
    palette: StitchPalette,
    peers: List<TrustedPeer>,
    health: Map<String, PeerHealthCache.PeerHealth>,
) {
    val rows = peers.map { peer ->
        val snap = health[peer.ip]
        val tierLabel = when (peer.tier) {
            TrustTier.LOCAL_TRUSTED -> "LAN · trusted"
            TrustTier.LOCAL_SANDBOXED -> "LAN · sandboxed"
            TrustTier.WAN -> "WAN"
        }
        val online = snap?.ok == true
        // We don't have a real RTT; show the freshness of the last
        // `/v1/health` poll as a proxy. Less than 1s reads as live.
        val ageMs = snap?.let { System.currentTimeMillis() - it.asOfMs }
        val latencyMs = when {
            snap == null -> -1       // no probe yet — show "—"
            ageMs == null -> -1
            else -> ageMs.toInt().coerceIn(0, 60_000)
        }
        val modelLoaded = snap?.modelLoaded == true
        PeerRow(
            name = peer.nodeId.takeIf { it.isNotBlank() } ?: peer.ip,
            ip = peer.ip,
            tier = tierLabel,
            latencyMs = latencyMs,
            online = online,
            modelLoaded = modelLoaded,
        )
    }
    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Sensors,
                    contentDescription = null,
                    tint = MeshlitDesignPalette.iridescentStart,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Peer Health Matrix (30s cache)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Text(
                    text = "No trusted peers yet — invite a device from the Nodes screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            } else {
                rows.forEach { peer ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        peer.online && peer.modelLoaded -> MeshlitDesignPalette.iridescentEnd
                                        peer.online -> MeshlitDesignPalette.iridescentStart
                                        else -> MeshlitDesignPalette.Dark.textQuaternary
                                    },
                                ),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = peer.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "${peer.ip} · ${peer.tier}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                        Text(
                            text = if (peer.latencyMs < 0) "—"
                                    else "${peer.latencyMs}ms",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticReportCard(palette: StitchPalette) {
    MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = MeshlitDesignPalette.iridescentIndigo,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Generate GitHub Diagnostic Report",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Auto-attaches last 200 log lines + cluster snapshot",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MeshlitDesignPalette.iridescentIndigo.copy(alpha = 0.25f))
                    .border(1.dp, MeshlitDesignPalette.iridescentIndigo, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Generate",
                    color = MeshlitDesignPalette.iridescentIndigo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private data class PeerRow(
    val name: String,
    val ip: String,
    val tier: String,
    val latencyMs: Int,
    val online: Boolean,
    val modelLoaded: Boolean,
)
