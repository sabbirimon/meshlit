package com.meshlit.disco

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.meshlit.R
import com.meshlit.devices.PairingPayload
import com.meshlit.devices.QrCodec
import com.meshlit.devices.QrScanner
import kotlinx.coroutines.launch

/**
 * Reusable QR pairing sheet. Extracted from the v1
 * `ui/screens/DevicesScreen.kt` so the v2 Scan screen can launch
 * the same `ModalBottomSheet` without re-implementing the ZXing
 * render or the ML Kit scanner wiring.
 *
 * What it does:
 *  - Renders [ownPayload] as a 512×512 QR via `QrCodec.encode`.
 *  - Lets the user paste a URI/string of a peer's QR for manual
 *    entry.
 *  - Offers a Scan button that hands off to Google ML Kit's GMS
 *    code scanner — no `CAMERA` permission needed because ML Kit
 *    ships its own camera surface.
 *  - On every successful resolution (paste or scan), calls
 *    [onAddFromString] with the decoded `PairingPayload`. The
 *    caller is responsible for converting to a
 *    [com.meshlit.core.discovery.PeerAdvertisement] and routing
 *    into the [PeerRepository.ingest] function.
 *
 * Why the caller owns the conversion: v1 stored QR-paired
 * devices as `RemoteEndpoint`s in a room database; v2 stores
 * them as `PeerAdvertisement`s in the shared `DiscoveryCoordinator`
 * map. Keeping the conversion at the call site means this
 * composable stays reusable across both shapes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairingSheet(
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
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                bitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(220.dp),
                    )
                } ?: Text(
                    text = "Generating QR…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = launchScanner) {
                    Text("Scan peer")
                }
                OutlinedTextField(
                    value = pasteField,
                    onValueChange = { pasteField = it },
                    label = { Text("Paste") },
                    placeholder = { Text(stringResource(R.string.devices_qr_paste_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val parsed = runCatching { PairingPayload.decode(pasteField) }.getOrNull()
                        if (parsed != null) onAddFromString(parsed)
                    },
                    enabled = pasteField.isNotBlank(),
                ) {
                    Text("Add")
                }
            }
            scanError?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Convert a [PairingPayload] (URI-encoded by `QrCodec.encode`) into a
 * [com.meshlit.core.discovery.PeerAdvertisement] suitable for
 * `PeerRepository.ingest()`. The host comes from `baseUrl`'s
 * authority so the resulting peer lands in the right CIDR bucket.
 *
 * `transport = "qr"` is propagated so the row's chip reads "QR"
 * instead of "mDNS".
 */
fun PairingPayload.toPeerAdvertisement(): com.meshlit.core.discovery.PeerAdvertisement {
    val url = java.net.URI(baseUrl.trim())
    val host = url.host ?: baseUrl
    val port = if (url.port != -1) url.port else when (url.scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> 8080
    }
    return com.meshlit.core.discovery.PeerAdvertisement(
        nodeId = nodeId.ifBlank { java.util.UUID.randomUUID().toString() },
        host = host,
        port = port,
        tier = capabilityTier,
        // PairingPayload has no fingerprint field today; we leave
        // a stable short hash derived from the baseUrl so the row
        // can render a fingerprint chip even on freshly-paired
        // peers. A real trust store replaces this with a
        // `DeviceTrustPolicy.publicKeyFingerprint` post-TOFU.
        fingerprint = Integer.toHexString(baseUrl.hashCode()).padStart(12, '0'),
        ttlSec = 60,
        transport = "qr",
    )
}
