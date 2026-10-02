package com.meshlit.ui.v2.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.net.capture.MdnsPacketRecord
import com.meshlit.devices.PairingPayload
import com.meshlit.disco.ClassifiedPeer
import com.meshlit.disco.PeerCategory
import com.meshlit.disco.PeerRepository
import com.meshlit.disco.QrPairingSheet
import com.meshlit.disco.ScanUiState
import com.meshlit.disco.toPeerAdvertisement
import com.meshlit.pcap.PacketCaptureManager
import com.meshlit.pcap.RecorderState
import com.meshlit.ui.theme.MeshlitOutline
import com.meshlit.ui.theme.MeshlitPulseViolet
import com.meshlit.ui.theme.MeshlitSurfaceContainer
import com.meshlit.ui.theme.MeshlitSurfaceHigh
import com.meshlit.ui.theme.MeshlitTextPrimaryV2
import com.meshlit.ui.theme.MeshlitTextSecondaryV2
import com.meshlit.ui.theme.MeshlitTextTertiaryV2
import com.meshlit.ui.v2.components.MeshlitTransportSwitch
import com.meshlit.ui.v2.components.PacketStreamPanel
import com.meshlit.ui.v2.components.PcapCaptureRow
import com.meshlit.ui.v2.components.UsbPeerRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * v2 Scan screen — peer discovery with classification.
 *
 * Reads `PeerRepository.uiState` via `collectAsStateWithLifecycle`
 * and renders the discovered peers grouped by category
 * (LOCAL → CLUSTER → GROUP → INTERNET). Each row shows:
 *  - Category tint (color-coded leading dot)
 *  - `nodeId` + transport chip
 *  - `host:port` mono caption
 *  - `fingerprint` first 12 hex chars (mono)
 *  - Distance label from the classifier ("Same AP" / "Same
 *    subnet" / "Cross-NAT" / "Internet")
 *
 * Phase 0.5 surface added (this PR):
 *  - **Show My QR tile** at the top of the screen — opens the
 *    same `QrPairingSheet` already used for "Pair with QR", so
 *    the *other* device can scan *this* device's QR.
 *  - **Transport switch** (`mDNS` / `BLE`) — flips a transport
 *    on/off via `PeerRepository.setTransportEnabled()`.
 *  - **Per-peer Trust action** — `LinkOff` icon on each row,
 *    confirmation dialog, then `repo.forget(nodeId)`.
 *  - **Fingerprint copy** — `ContentCopy` icon on each row,
 *    full hex copied to clipboard via `LocalClipboardManager`.
 *  - **USB tether row** — coral row at the top of the peer list
 *    when `MeshlitApplication.usbTetherActive` is non-null.
 *
 * The screen is pure rendering — the repository owns the
 * discovery lifecycle (started in `MeshlitApplication.onCreate`,
 * stopped when the process dies). The screen never calls
 * `start()`/`stop()` so it's safe to enter and leave without
 * affecting background peers.
 *
 * Pair-with-QR is wired via a top-end AssistChip that launches
 * a sheet (the v1 `QrPairingSheet`, extracted for reuse). A
 * successful scan calls `PeerRepository.ingest()` which feeds
 * the advertisement into the same dedup path as mDNS.
 *
 * Empty state: a centered "Scanning…" caption with a search
 * icon. We've kept the message factual ("scanning" not "no
 * peers found") so the user doesn't second-guess whether the
 * screen is alive before the first mDNS reply arrives.
 */
@Composable
fun V2ScanScreen(
    onOpenQrPairing: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val repo: PeerRepository = koinInject()
    val capture: PacketCaptureManager = koinInject()
    val state by repo.uiState.collectAsStateWithLifecycle()
    val captureState by capture.state.collectAsStateWithLifecycle()
    val liveRecords = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateListOf<MdnsPacketRecord>()
    }
    androidx.compose.runtime.LaunchedEffect(capture) {
        capture.live.collect { rec ->
            liveRecords.add(rec)
            // Keep the last 64 around in memory; the panel trims to 50 for display.
            if (liveRecords.size > 64) liveRecords.removeAt(0)
        }
    }
    val categories = remember { PeerCategory.entries.toList() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var qrSheetVisible by remember { mutableStateOf(false) }
    var showMyQrVisible by remember { mutableStateOf(false) }
    var forgetTarget by remember { mutableStateOf<PeerAdvertisement?>(null) }
    val app = LocalContext.current.applicationContext as MeshlitApplication
    val ownPayload = remember(app.nodeIdHex, app.displayName, app.localIpAddress) {
        PairingPayload(
            nodeName = app.displayName,
            baseUrl = "http://${app.localIpAddress}:${app.httpServerPort}",
            nodeId = app.nodeIdHex,
            capabilityTier = app.capabilityTier.name,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            com.meshlit.ui.v2.components.MeshlitLeadBar(
                headline = "Scan",
                subtitle = if (state.scanning) {
                    "${state.total} peers" +
                        if (state.localIps.isNotEmpty()) " · my /24 ${state.localIps.first()}"
                        else ""
                } else {
                    "Discovery idle"
                },
            )

            // Show My QR tile — opens the QR pairing sheet with
            // *our* payload. The other device scans this code and
            // their PeerRepository.ingest() collapses the
            // advertisement into the same dedup path as mDNS,
            // surfacing the new peer in INTERNET.
            ShowMyQrTile(
                onClick = { showMyQrVisible = true },
                onCopy = {
                    clipboard.setText(AnnotatedString(ownPayload.nodeId))
                    scope.launch { snackbarHost.showSnackbar("Copied node id") }
                },
            )

            // Transport switch — mDNS / BLE toggles. BLE is
            // opt-in; flipping it off means the coordinator stops
            // calling the BLE scanner / advertiser so the OS
            // doesn't poll the radio.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeshlitTransportSwitch(
                    enabled = state.transportsEnabled,
                    options = listOf("nsd" to "mDNS", "ble" to "BLE"),
                    onToggle = { name ->
                        val isOn = name in state.transportsEnabled
                        repo.setTransportEnabled(name, enabled = !isOn)
                    },
                )
            }

            // mDNS PCAP capture row + live packet stream panel.
            // The user can:
            //  - Tap "Start capture" to begin recording mDNS
            //    packets to a .pcap file on disk.
            //  - Watch decoded Meshlit advertisements appear
            //    inline ( violet rows ) and any other mDNS
            //    traffic roll past as raw rows.
            //  - Tap "Stop & share" to fire an Android share
            //    intent so the .pcap can be opened in Wireshark
            //    / MViewer / Termux tshark.
            val context = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.SideEffect { /* context used below */ }
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            PcapCaptureRow(
                state = captureState,
                onStart = { capture.startCapture() },
                onStop = { capture.stopAndExport() },
                onShare = { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/vnd.tcpdump.pcap"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(intent, "Share .pcap").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        // The chooser process reads the URI for preview
                        // thumbnails — the chooser needs the read flag
                        // too, not just the wrapped intent.
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(chooser)
                },
                onDiscard = { capture.discard() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            // Existing chip row + pulsing activity dot.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { qrSheetVisible = true },
                    label = { Text("Pair with QR") },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (state.scanning) {
                                MeshlitPulseViolet
                            } else {
                                MeshlitOutline
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = if (state.scanning) "SCANNING" else "IDLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.scanning) {
                        MeshlitPulseViolet
                    } else {
                        MeshlitTextTertiaryV2
                    },
                )
            }

            Surface(
                color = MeshlitSurfaceContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (state.total == 0 && state.usbTether == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MeshlitTextTertiaryV2,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = if (state.scanning) {
                                    "Scanning for Meshlit peers…"
                                } else {
                                    "Discovery is idle"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MeshlitTextSecondaryV2,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.usbTether?.let { tether ->
                            item(key = "usb-tether") {
                                UsbPeerRow(info = tether)
                            }
                        }
                        categories.forEach { cat ->
                            val peers = state.grouped[cat].orEmpty()
                            if (peers.isEmpty()) return@forEach
                            item(key = "header-${cat.name}") {
                                CategoryHeader(
                                    category = cat,
                                    count = peers.size,
                                )
                            }
                            items(peers, key = { it.advertisement.nodeId }) { peer ->
                                PeerRow(
                                    peer = peer,
                                    onCopyFingerprint = { fp ->
                                        clipboard.setText(AnnotatedString(fp))
                                        scope.launch {
                                            snackbarHost.showSnackbar("Copied fp")
                                        }
                                    },
                                    onForget = { forgetTarget = peer.advertisement },
                                )
                            }
                        }
                    }
                }
            }

            // Live packet stream — the most recent decoded mDNS
            // packets the listener has seen since the screen
            // mounted. Decoded Meshlit rows are tinted violet;
            // other mDNS traffic rolls past as raw rows.
            PacketStreamPanel(
                records = liveRecords,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (qrSheetVisible) {
        QrPairingSheet(
            ownPayload = ownPayload,
            onAddFromString = { payload ->
                repo.ingest(payload.toPeerAdvertisement())
                qrSheetVisible = false
            },
            onDismiss = { qrSheetVisible = false },
        )
    }

    if (showMyQrVisible) {
        QrPairingSheet(
            ownPayload = ownPayload,
            onAddFromString = { payload ->
                repo.ingest(payload.toPeerAdvertisement())
                showMyQrVisible = false
            },
            onDismiss = { showMyQrVisible = false },
        )
    }

    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("Forget this peer?") },
            text = {
                Text(
                    "Meshlit will treat ${target.nodeId.take(16)} as untrusted. " +
                        "They will need to re-pair before their fingerprint is honoured again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.forget(target.nodeId)
                    forgetTarget = null
                    scope.launch {
                        snackbarHost.showSnackbar("Peer forgotten")
                    }
                }) {
                    Text("Forget", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetTarget = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ShowMyQrTile(
    onClick: () -> Unit,
    onCopy: () -> Unit,
) {
    Surface(
        color = MeshlitSurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MeshlitPulseViolet.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = MeshlitPulseViolet,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show my QR",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                )
                Text(
                    text = "Let another device scan yours",
                    style = MaterialTheme.typography.bodySmall,
                    color = MeshlitTextSecondaryV2,
                )
            }
            TextButton(onClick = onCopy) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy node id",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Copy", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onClick) {
                Text("Show", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: PeerCategory,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = category.tint, shape = CircleShape),
        )
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MeshlitTextPrimaryV2,
        )
        Text(
            text = category.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MeshlitTextTertiaryV2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = category.tint,
        )
    }
}

@Composable
private fun PeerRow(
    peer: ClassifiedPeer,
    onCopyFingerprint: (String) -> Unit,
    onForget: () -> Unit,
) {
    val adv = peer.advertisement
    Surface(
        color = MeshlitSurfaceHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = iconFor(adv, peer.category),
                    contentDescription = null,
                    tint = peer.category.tint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = adv.nodeId.take(16),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MeshlitTextPrimaryV2,
                    modifier = Modifier.weight(1f),
                )
                TransportChip(transport = adv.transport)
                IconButton(onClick = onForget) {
                    Icon(
                        Icons.Filled.LinkOff,
                        contentDescription = "Forget this peer",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = "${adv.host}:${adv.port}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MeshlitTextSecondaryV2,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "fp ${adv.fingerprint.take(12)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = MeshlitTextTertiaryV2,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onCopyFingerprint(adv.fingerprint) },
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy fingerprint",
                        tint = MeshlitTextTertiaryV2,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = peer.distance.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = peer.category.tint,
                )
            }
        }
    }
}

@Composable
private fun TransportChip(transport: String) {
    val (label, tint) = when (transport) {
        "nsd" -> "mDNS" to MeshlitPulseViolet
        "ble" -> "BLE" to Color(0xFF4DD9C0)
        "qr" -> "QR" to Color(0xFFE8C56F)
        else -> transport to MeshlitTextSecondaryV2
    }
    Surface(
        color = tint.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = tint,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun iconFor(adv: PeerAdvertisement, category: PeerCategory): ImageVector {
    return when (category) {
        PeerCategory.LOCAL -> Icons.Filled.Devices
        PeerCategory.CLUSTER -> Icons.Filled.Hub
        PeerCategory.GROUP -> Icons.Filled.Group
        PeerCategory.INTERNET -> Icons.Filled.Cloud
    }
}