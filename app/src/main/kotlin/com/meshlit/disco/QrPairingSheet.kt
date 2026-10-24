package com.meshlit.disco

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.meshlit.R
import com.meshlit.devices.PairingPayload
import com.meshlit.devices.QrCodec
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.CameraPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
 *  - Offers an in-app live camera reader, full-resolution photo capture,
 *    saved-image import, and manual paste. The live reader requests camera
 *    permission; saved-image import does not.
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
    onSaveOwn: ((PairingPayload) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val resources = LocalResources.current
    var timeLimited by remember { mutableStateOf(false) }
    var refreshedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    val payloadJson = remember(ownPayload, timeLimited, refreshedAt) {
        ownPayload.copy(expiresAtEpochMs = if (timeLimited) refreshedAt + 5 * 60 * 1000L else null).encode()
    }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pasteField by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scannedOther by remember { mutableStateOf<String?>(null) }
    var liveScanner by remember { mutableStateOf(false) }
    var scanAccepted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun acceptImage(image: Bitmap?) {
        if (image == null) { scanError = "No QR image was returned"; return }
        scope.launch {
            scanError = null
            scannedOther = null
            try {
                val raw = withContext(Dispatchers.Default) { QrCodec.decode(image) }
                val payload = runCatching { PairingPayload.decode(raw) }.getOrNull()
                if (payload != null) onAddFromString(payload)
                else {
                    scannedOther = raw.take(4096)
                    scanError = "QR read successfully, but it is not a Meshlit pairing code."
                }
            } catch (e: Exception) { scanError = "Could not read a QR from this image: ${e.message}" }
        }
    }
    fun decodeImage(uri: Uri, cleanup: File? = null) {
        scope.launch {
            try {
                val image = withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048) inSampleSize *= 2
                    }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
                acceptImage(image)
            } catch (e: Exception) { scanError = "Cannot open QR image: ${e.message}" }
            finally { cleanup?.delete() }
        }
    }
    val captureFile = remember(context) { File(context.cacheDir, "exports/captures/pairing-${UUID.randomUUID()}.jpg").apply { parentFile?.mkdirs() } }
    val captureUri = remember(captureFile) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        // Some OEM camera apps write the requested URI but return RESULT_CANCELED.
        if (captured || (captureFile.isFile && captureFile.length() > 0L)) decodeImage(captureUri, captureFile)
        else { captureFile.delete(); scanError = "No photo was saved. Try Scan peer with camera or Choose QR image." }
    }
    val chooseImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) decodeImage(uri)
    }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        check(QrCodec.encode(payloadJson, 512).compress(Bitmap.CompressFormat.PNG, 100, stream))
                    } ?: error("Cannot write the selected file")
                }
            }.onSuccess { scanError = "QR image saved to the selected folder" }
                .onFailure { scanError = "Could not save QR image: ${it.message}" }
        }
    }

    LaunchedEffect(payloadJson) {
        bitmap = runCatching { QrCodec.encode(payloadJson, size = 512) }.getOrNull()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.devices_qr_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (liveScanner) {
                Text("Point the camera at the other device's pairing QR.", style = MaterialTheme.typography.bodySmall)
                ScannerWithPermissions(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    onScanned = { raw ->
                        if (scanAccepted) true else {
                            scanAccepted = true
                            liveScanner = false
                            val payload = runCatching { PairingPayload.decode(raw) }.getOrNull()
                            if (payload != null) onAddFromString(payload)
                            else {
                                scannedOther = raw.take(4096)
                                scanError = "QR read successfully, but it is not a Meshlit pairing code."
                            }
                            true
                        }
                    },
                    types = listOf(CodeType.QR),
                    cameraPosition = CameraPosition.BACK,
                )
                TextButton(onClick = { liveScanner = false }) { Text("Close camera") }
            } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!timeLimited, onClick = { timeLimited = false }, label = { Text("Reusable") })
                FilterChip(timeLimited, onClick = { timeLimited = true; refreshedAt = System.currentTimeMillis() }, label = { Text("5-minute QR") })
            }
            if (timeLimited) {
                Text("Expires 5 minutes after refresh. It can be scanned more than once during that time.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { refreshedAt = System.currentTimeMillis() }) { Text("Refresh QR") }
            } else {
                Text("Reusable identity QR. Saving it does not approve compute; the other device must verify your identity.", style = MaterialTheme.typography.bodySmall)
            }
            if (onSaveOwn != null) TextButton(onClick = {
                runCatching { onSaveOwn(PairingPayload.decode(payloadJson)) }
                    .onFailure { scanError = "Could not save QR: ${it.message}" }
            }) { Text("Save this QR in Device management") }
            TextButton(onClick = { savePng.launch("Meshlit-pairing-QR.png") }) { Text("Save QR image to device") }
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
            Button(onClick = { scanError = null; scannedOther = null; scanAccepted = false; liveScanner = true }) { Text("Scan QR with camera") }
            TextButton(onClick = { scanError = null; takePhoto.launch(captureUri) }) { Text("Take QR photo") }
            OutlinedButton(onClick = { chooseImage.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Choose QR image") }
            OutlinedTextField(
                value = pasteField,
                onValueChange = { pasteField = it.take(8192) },
                label = { Text("Paste pairing link") },
                placeholder = { Text(stringResource(R.string.devices_qr_paste_hint)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                scanError = null
                val parsed = runCatching { PairingPayload.decode(pasteField) }.onFailure { scanError = "pairing.invalid_payload: ${it.message}" }.getOrNull()
                if (parsed != null) onAddFromString(parsed)
                else if (scanError == null) scanError = "pairing.invalid_payload: Check the endpoint and pairing link"
            }, enabled = pasteField.isNotBlank()) { Text("Add pasted link") }
            }
            scanError?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            scannedOther?.let { decoded ->
                OutlinedTextField(
                    value = decoded,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Scanned QR contents") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(decoded)) }) {
                    Text("Copy scanned contents")
                }
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
@Suppress("UNUSED_PARAMETER")
fun PairingPayload.toPeerAdvertisement(localFingerprint: String? = null): com.meshlit.core.discovery.PeerAdvertisement {
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
        tier = "local_sandboxed",
        // Only the scanned public key supplies a peer fingerprint.
        fingerprint = if (publicKey.isBlank()) "" else com.meshlit.core.trust.PeerCrypto.fingerprint(publicKey),
        ttlSec = 60,
        transport = "qr",
    )
}
