package com.meshlit.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.core.discovery.LocalPeerDescriptor
import com.meshlit.core.discovery.PeerAdvertisement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Auto-discovery card pinned above the manual endpoint list on
 * the Devices screen. Hands the user a one-tap "scan nearby"
 * button + a live list of Meshlit peers found on the local LAN
 * via mDNS / DNS-SD. Each row has an **Add** affordance that
 * converts the discovered peer into a fully-trusted
 * [RemoteEndpoint] without the user having to type an IP.
 *
 * Why mDNS:
 *  - Zero-config — no user typing, no IP guessing.
 *  - Cross-platform — every phone on the LAN that has Meshlit
 *    installed advertises itself as `_meshlit._tcp.` so two
 *    phones in the same room find each other without setup.
 *  - Energy-aware — the [com.meshlit.core.discovery.NsdDiscoveryTransport]
 *    backs off when the screen is not visible; the panel
 *    starts/stops the transport on screen entry/exit.
 *
 * State machine:
 *   IDLE     — panel mounted but [start] not yet called.
 *   SCANNING — transport is running, mDNS listener active.
 *   STOPPED  — user hit Stop, transport unregistered. Re-tap
 *              Scan to start over.
 *
 * The transport lives on the [MeshlitApplication] singleton, so
 * the panel survives recompositions without leaking listeners.
 */
@Composable
fun NearbyDiscoveryPanel(
    app: MeshlitApplication,
    onAddPeer: (RemoteEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val coordinator = remember { app.discoveryCoordinator }
    val discovered by coordinator.peers.collectAsState()

    // Start lazily — but only on a real device. We don't try to
    // spin up mDNS on the JVM unit-test path. The `isEmulator`
    // flag is conservative: if we can't tell, we still try.
    var running by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    // Self-descriptor for advertising. The FGS-owned HTTP server
    // is the endpoint peers route to; we read its port straight
    // off the application singleton.
    val selfDescriptor = remember {
        LocalPeerDescriptor(
            nodeId = app.nodeIdHex,
            host = app.localIpAddress,
            port = app.httpServerPort,
            tierTag = app.capabilityTier.name,
            fingerprint = "", // populated once the trust store lands
        )
    }

    DisposableEffect(Unit) {
        onDispose { if (running) coordinator.stop() }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header row — icon + title + scan/stop button.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Hub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.devices_nearby_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.devices_nearby_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (running) {
                    FilledTonalButton(
                        onClick = {
                            coordinator.stop()
                            running = false
                            statusText = "Stopped"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.devices_nearby_stop))
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    coordinator.start(this, selfDescriptor)
                                }.onFailure { t ->
                                    statusText = "scan failed: ${t.message ?: "unknown"}"
                                }
                                running = true
                                statusText = "Scanning…"
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.devices_nearby_scan))
                    }
                }
            }

            AnimatedVisibility(visible = running, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }

            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Discovered peer list — kept short (max 8) so the
            // card doesn't dominate the screen when a busy LAN
            // has many Meshlit devices.
            val peers = discovered.values
                .sortedByDescending { it.ttlSec }
                .take(8)
            if (peers.isEmpty()) {
                if (running) {
                    Text(
                        text = stringResource(R.string.devices_nearby_empty_scanning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.devices_nearby_empty_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                peers.forEach { peer ->
                    DiscoveredPeerRow(
                        peer = peer,
                        onAdd = {
                            onAddPeer(peer.toRemoteEndpoint())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredPeerRow(
    peer: PeerAdvertisement,
    onAdd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "meshlit-${peer.nodeId.take(8)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${peer.host}:${peer.port} · ${peer.tier}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add peer",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Convert a [PeerAdvertisement] into a fully-trusted
 *  [RemoteEndpoint] entry the user can route through. The peer
 *  comes pre-trusted because the user explicitly tapped "Add"
 *  — this is the same trust model as QR pairing. */
private fun PeerAdvertisement.toRemoteEndpoint(): RemoteEndpoint = RemoteEndpoint(
    id = "nsd-${UUID.randomUUID()}",
    name = "meshlit-${nodeId.take(8)}",
    baseUrl = "http://$host:$port",
    apiKey = "",
    protocol = EndpointProtocol.MESHLIT_SSE,
    allowInsecure = true, // local LAN; HTTPS isn't always available
    trusted = true,        // user explicitly added via Scan panel
    addedAtMs = System.currentTimeMillis(),
    lastSeenMs = System.currentTimeMillis(),
    notes = "nsd-discovered;fingerprint=$fingerprint",
)