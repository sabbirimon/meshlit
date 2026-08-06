package com.meshlit.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.core.common.NetworkScope
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.devices.QrCodec
import com.meshlit.devices.PairingPayload
import com.meshlit.devices.QrScanner
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Redesigned Devices screen — the "where do my model's connections go?"
 * hub. Built around three pillars:
 *
 *  1. **Network-scope selector** — a row of chips at the top: Local /
 *     Internet / VPN / Selective group / Custom. The chosen scope is
 *     persisted in [com.meshlit.settings.SettingsRepository] and is
 *     the single source of truth for what the inference coordinator
 *     is allowed to talk to. Each scope chip carries a tiny icon
 *     (Wi-Fi for local, Globe for internet, Shield for VPN,
 *     People for group, Storage for custom).
 *
 *  2. **Endpoint list** — one card per [RemoteEndpoint] the user has
 *     added (manual paste, QR scan, or auto-discovered). Each card
 *     shows the display name, base URL, protocol, last-seen age,
 *     and a trust toggle. Long-pressing opens an edit / remove
 *     action sheet.
 *
 *  3. **Add menu** — a + FAB that opens a bottom sheet with three
 *     actions: "Manual IP / port / URL paste", "QR code pairing",
 *     "Scan QR / paste pairing string". Manual paste is the
 *     most reliable path on dev devices; QR pairing is the fastest
 *     on real phones.
 *
 * The screen also renders this device's own pairing QR code at the
 * bottom so another phone can scan it to register this instance as
 * a remote endpoint (one-tap trust, no typing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val settings = remember { app.settingsRepository }
    val scope = rememberCoroutineScope()

    val scopeValue by settings.networkScopeFlow.collectAsState(initial = NetworkScope.Default)
    val endpoints by settings.remoteEndpointsFlow.collectAsState(initial = emptyList())
    val activeId by settings.activeEndpointIdFlow.collectAsState(initial = "")

    var showAddSheet by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RemoteEndpoint?>(null) }

    val tier = app.capabilityTier

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.devices_title),
                subtitle = stringResource(R.string.devices_subtitle, scopeValue.shortLabel),
                tier = tier,
                active = false,
                onOpenDrawer = onOpenDrawer,
            )
        },
        floatingActionButton = {
            AssistChip(
                onClick = { showAddSheet = true },
                label = { Text(stringResource(R.string.devices_add)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            ScopePicker(
                current = scopeValue,
                onChange = { newScope ->
                    scope.launch { settings.setNetworkScope(newScope) }
                },
            )
            // Auto-discovery panel — scans the LAN for Meshlit
            // peers via mDNS / DNS-SD. Each discovered peer can
            // be added as a fully-trusted RemoteEndpoint with
            // one tap (no IP typing, no QR scanning).
            NearbyDiscoveryPanel(
                app = app,
                onAddPeer = { endpoint ->
                    scope.launch {
                        settings.upsertEndpoint(endpoint)
                        if (activeId.isBlank()) settings.setActiveEndpoint(endpoint.id)
                    }
                },
            )
            EndpointList(
                endpoints = endpoints,
                activeId = activeId,
                scope = scopeValue,
                onSetActive = { id ->
                    scope.launch { settings.setActiveEndpoint(id) }
                },
                onEdit = { editing = it },
                onRemove = { id ->
                    scope.launch { settings.removeEndpoint(id) }
                },
                onTrust = { id, trusted ->
                    scope.launch { settings.trustEndpoint(id, trusted) }
                },
            )
            if (scopeValue == NetworkScope.VPN) {
                VpnImportCard(
                    onImportFromFile = { uri ->
                        val saved = copyVpnConfigToInternal(context, uri)
                        if (saved != null) {
                            val ep = parseVpnConfigToEndpoint(saved)
                            scope.launch { settings.upsertEndpoint(ep) }
                        }
                    },
                    onSaveManual = { name, host, port ->
                        val sanitizedHost = host.trim()
                        if (sanitizedHost.isBlank()) return@VpnImportCard
                        val ep = RemoteEndpoint(
                            id = "vpn-${UUID.randomUUID()}",
                            name = name.ifBlank { "$sanitizedHost:$port" },
                            baseUrl = "vpn://$sanitizedHost:$port",
                            protocol = EndpointProtocol.CUSTOM,
                            allowInsecure = true,
                            trusted = false,
                            addedAtMs = System.currentTimeMillis(),
                            notes = "vpn-kind=manual",
                        )
                        scope.launch { settings.upsertEndpoint(ep) }
                    },
                )
            }
            PairingCard(
                onShowQr = { showQrSheet = true },
                ownPayload = PairingPayload(
                    nodeName = app.displayName,
                    baseUrl = "http://${app.localIpAddress}:${app.httpServerPort}",
                    nodeId = app.nodeIdHex,
                    capabilityTier = tier.name,
                ),
            )
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showAddSheet) {
        AddEndpointSheet(
            initial = null,
            onDismiss = { showAddSheet = false },
            onSave = { endpoint ->
                scope.launch {
                    settings.upsertEndpoint(endpoint)
                    if (activeId.isBlank()) settings.setActiveEndpoint(endpoint.id)
                }
                showAddSheet = false
            },
        )
    }

    editing?.let { existing ->
        AddEndpointSheet(
            initial = existing,
            onDismiss = { editing = null },
            onSave = { endpoint ->
                scope.launch { settings.upsertEndpoint(endpoint) }
                editing = null
            },
        )
    }

    if (showQrSheet) {
        QrPairingSheet(
            ownPayload = PairingPayload(
                nodeName = app.displayName,
                baseUrl = "http://${app.localIpAddress}:${app.httpServerPort}",
                nodeId = app.nodeIdHex,
                capabilityTier = tier.name,
            ),
            onAddFromString = { parsed ->
                scope.launch { settings.upsertEndpoint(parsed.toRemoteEndpoint()) }
                showQrSheet = false
            },
            onDismiss = { showQrSheet = false },
        )
    }
}

@Composable
private fun ScopePicker(
    current: NetworkScope,
    onChange: (NetworkScope) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.devices_scope_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ScopeChip(NetworkScope.LOCAL, current, onChange)
                ScopeChip(NetworkScope.INTERNET, current, onChange)
                ScopeChip(NetworkScope.VPN, current, onChange)
                ScopeChip(NetworkScope.GROUP, current, onChange)
                ScopeChip(NetworkScope.CUSTOM, current, onChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.devices_scope_hint, current.displayLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScopeChip(
    scope: NetworkScope,
    current: NetworkScope,
    onChange: (NetworkScope) -> Unit,
) {
    val selected = scope == current
    val icon = when (scope) {
        NetworkScope.LOCAL -> Icons.Filled.WifiTethering
        NetworkScope.INTERNET -> Icons.Filled.Public
        NetworkScope.VPN -> Icons.Filled.Shield
        NetworkScope.GROUP -> Icons.Filled.Router
        NetworkScope.CUSTOM -> Icons.Filled.Storage
    }
    FilterChip(
        selected = selected,
        onClick = { onChange(scope) },
        label = {
            Text(
                text = scope.shortLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        modifier = Modifier.heightIn(min = 32.dp),
    )
}

@Composable
private fun EndpointList(
    endpoints: List<RemoteEndpoint>,
    activeId: String,
    scope: NetworkScope,
    onSetActive: (String) -> Unit,
    onEdit: (RemoteEndpoint) -> Unit,
    onRemove: (String) -> Unit,
    onTrust: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.devices_endpoints_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (endpoints.isEmpty()) {
            EmptyEndpoints(scope)
        } else {
            endpoints.forEach { ep ->
                EndpointCard(
                    endpoint = ep,
                    isActive = ep.id == activeId,
                    onSetActive = { onSetActive(ep.id) },
                    onEdit = { onEdit(ep) },
                    onRemove = { onRemove(ep.id) },
                    onTrust = { trusted -> onTrust(ep.id, trusted) },
                )
            }
        }
    }
}

@Composable
private fun EmptyEndpoints(scope: NetworkScope) {
    val message = when (scope) {
        NetworkScope.LOCAL -> R.string.devices_empty_local
        NetworkScope.INTERNET -> R.string.devices_empty_internet
        NetworkScope.VPN -> R.string.devices_empty_vpn
        NetworkScope.GROUP -> R.string.devices_empty_group
        NetworkScope.CUSTOM -> R.string.devices_empty_custom
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun EndpointCard(
    endpoint: RemoteEndpoint,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onTrust: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (endpoint.isReachable())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = endpoint.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                if (isActive) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.devices_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = endpoint.baseUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(endpoint.protocol.name, fontSize = 10.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                )
                if (endpoint.lastSeenMs > 0L) {
                    Text(
                        text = stringResource(R.string.devices_last_seen, ageString(endpoint.lastSeenMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.devices_never_seen),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.devices_edit))
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.devices_remove))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.devices_trust_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Switch(checked = endpoint.trusted, onCheckedChange = onTrust)
            }
            Spacer(Modifier.height(6.dp))
            if (!isActive) {
                TextButton(onClick = onSetActive) {
                    Text(stringResource(R.string.devices_use_as_active))
                }
            }
        }
    }
}

@Composable
private fun PairingCard(
    ownPayload: PairingPayload,
    onShowQr: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val payloadJson = remember { ownPayload.encode() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.devices_pairing_header),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.devices_pairing_body, ownPayload.nodeName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ownPayload.baseUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onShowQr,
                    label = { Text(stringResource(R.string.devices_show_qr)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                AssistChip(
                    onClick = {
                        clipboard.setText(AnnotatedString(payloadJson))
                    },
                    label = { Text(stringResource(R.string.devices_copy_payload)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEndpointSheet(
    initial: RemoteEndpoint?,
    onDismiss: () -> Unit,
    onSave: (RemoteEndpoint) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var protocol by remember {
        mutableStateOf(initial?.protocol ?: EndpointProtocol.MESHLIT_SSE)
    }
    var allowInsecure by remember { mutableStateOf(initial?.allowInsecure ?: false) }
    var trusted by remember { mutableStateOf(initial?.trusted ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) R.string.devices_add_title else R.string.devices_edit_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            // URL is the primary identifier — promote it to the top
            // of the sheet so the user lands on the field that
            // actually matters. The hint copy nudges manual entry
            // as the most reliable path on dev devices.
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.devices_field_url)) },
                placeholder = { Text("https://example.com:8443") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                supportingText = {
                    Text(
                        text = stringResource(R.string.devices_field_url_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.devices_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.devices_field_api_key)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.devices_field_protocol),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EndpointProtocol.entries.forEach { p ->
                    FilterChip(
                        selected = protocol == p,
                        onClick = { protocol = p },
                        label = { Text(p.name, fontSize = 10.sp) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.devices_field_insecure),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.devices_field_trust),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = trusted, onCheckedChange = { trusted = it })
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.devices_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val sanitized = url.trim().trimEnd('/')
                    if (sanitized.isEmpty()) {
                        error = "URL required"
                        return@Button
                    }
                    val parsedProto = if (protocol == EndpointProtocol.MESHLIT_SSE &&
                        !sanitized.startsWith("http")
                    ) {
                        EndpointProtocol.CUSTOM
                    } else {
                        protocol
                    }
                    val ep = RemoteEndpoint(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        name = name.ifBlank { sanitized },
                        baseUrl = sanitized,
                        apiKey = apiKey,
                        protocol = parsedProto,
                        allowInsecure = allowInsecure,
                        trusted = trusted,
                        addedAtMs = initial?.addedAtMs ?: 0L,
                        lastSeenMs = initial?.lastSeenMs ?: 0L,
                        notes = initial?.notes ?: "",
                    )
                    onSave(ep)
                }) {
                    Text(stringResource(R.string.devices_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrPairingSheet(
    ownPayload: PairingPayload,
    onAddFromString: (PairingPayload) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val payloadJson = remember { ownPayload.encode() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pasteField by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(payloadJson) {
        bitmap = runCatching { QrCodec.encode(payloadJson, size = 512) }.getOrNull()
    }

    /**
     * Launch Google ML Kit Code Scanner via the GMS Play-Services UI.
     * The scanner downloads its own module on first launch and renders
     * the camera surface for us — no `CAMERA` permission, no
     * `CameraX`, no PreviewView. When it returns a value we feed it
     * through the same `PairingPayload.decode` path as a manual paste.
     */
    val launchScanner = {
        scope.launch {
            when (val r = QrScanner.scan(context)) {
                is QrScanner.ScanResult.Success -> {
                    val parsed = runCatching { PairingPayload.decode(r.rawValue) }.getOrNull()
                    if (parsed != null) {
                        onAddFromString(parsed)
                    } else {
                        scanError = context.getString(R.string.devices_qr_scan_invalid)
                    }
                }
                is QrScanner.ScanResult.Cancelled -> {
                    // user backed out — silent
                }
                is QrScanner.ScanResult.PlayServicesMissing -> {
                    scanError = context.getString(R.string.devices_qr_scan_play_services)
                }
                is QrScanner.ScanResult.MissingActivity -> {
                    scanError = context.getString(R.string.devices_qr_scan_failed)
                }
                is QrScanner.ScanResult.Failed -> {
                    scanError = context.getString(
                        R.string.devices_qr_scan_failed_code,
                        r.code,
                    )
                }
            }
        }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.devices_qr_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "QR pairing code",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                    )
                } ?: Text(
                    text = stringResource(R.string.devices_qr_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = ownPayload.baseUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.devices_qr_paste_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = pasteField,
                onValueChange = { pasteField = it },
                placeholder = { Text("meshlit://…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        val parsed = runCatching { PairingPayload.decode(pasteField.trim()) }.getOrNull()
                        if (parsed != null) onAddFromString(parsed)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = pasteField.isNotBlank(),
                ) {
                    Text(stringResource(R.string.devices_qr_add_pasted))
                }
                Button(
                    onClick = { launchScanner() },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.devices_qr_scan))
                }
            }
            scanError?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VpnImportCard(
    onImportFromFile: (Uri) -> Unit,
    onSaveManual: (name: String, host: String, port: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("51820") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImportFromFile(uri)
            savedMessage = context.getString(R.string.devices_vpn_imported, uri.lastPathSegment ?: "profile")
        }
    }

    Card(
        modifier = Modifier
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.devices_vpn_import_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.devices_vpn_import_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = { picker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.devices_vpn_import_file))
            }
            Text(
                text = stringResource(R.string.devices_vpn_manual_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.devices_vpn_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.devices_vpn_host)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value ->
                        portText = value.filter { it.isDigit() }.take(5)
                    },
                    label = { Text(stringResource(R.string.devices_vpn_port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
            }
            Button(
                onClick = {
                    if (host.isBlank()) {
                        savedMessage = context.getString(R.string.devices_vpn_invalid)
                        return@Button
                    }
                    onSaveManual(name, host, portText.toIntOrNull() ?: 51820)
                    savedMessage = context.getString(
                        R.string.devices_vpn_imported,
                        host,
                    )
                },
                enabled = host.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.devices_vpn_save))
            }
            savedMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Copy a picked WireGuard/OpenVPN profile into app-private storage. */
private fun copyVpnConfigToInternal(context: Context, uri: Uri): java.io.File? {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "vpn-profile.conf"
        }
    }.getOrNull() ?: "vpn-profile.conf"
    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val dir = java.io.File(context.filesDir, "vpn-profiles").apply { mkdirs() }
    val dest = java.io.File(dir, safeName)
    return runCatching {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest
    }.getOrNull()
}

/**
 * Parse enough of WireGuard/OpenVPN config to surface a useful endpoint
 * card. Private keys stay only in the app-private config file — never in
 * DataStore or the QR pairing payload.
 */
private fun parseVpnConfigToEndpoint(file: java.io.File): RemoteEndpoint {
    val text = runCatching { file.readText() }.getOrDefault("")
    val endpointLine = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Endpoint", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?: text.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("remote ", ignoreCase = true) }
            ?.substringAfter("remote ")
            ?.trim()
        ?: "unknown"
    return RemoteEndpoint(
        id = "vpn-${UUID.randomUUID()}",
        name = file.nameWithoutExtension,
        baseUrl = "vpn://$endpointLine",
        protocol = EndpointProtocol.CUSTOM,
        allowInsecure = true,
        trusted = false,
        addedAtMs = System.currentTimeMillis(),
        notes = "vpn-config=${file.absolutePath}",
    )
}

private fun ageString(ts: Long): String {
    val deltaMs = System.currentTimeMillis() - ts
    val s = deltaMs / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86400 -> "${s / 3600}h"
        else -> "${s / 86400}d"
    }
}
