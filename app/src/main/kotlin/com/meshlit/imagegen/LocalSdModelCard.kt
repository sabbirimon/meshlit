package com.meshlit.imagegen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.core.inference.importers.HttpStreamDownloader
import com.meshlit.loader.DownloadProgressBus
import com.meshlit.loader.DownloadTick
import com.meshlit.models.SdModelBundles
import com.meshlit.stable_diffusion.SdRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import android.os.StatFs

/**
 * Phase 4.x — "Local Stable Diffusion" card. Sits above the
 * existing `ImageGenerationCard` on the ImageGenScreen and
 * exposes:
 *  - a runtime picker (Stub / sd.cpp / ONNX / diffusers / ET)
 *  - the active runtime + model path
 *  - the bundle download picker (4 SD bundles + 1 ONNX + 1 ET)
 *  - a download progress banner that piggybacks on the global
 *    [DownloadProgressBus]
 *
 * The card is non-modal — the user can interact with the rest
 * of the screen while a bundle is pulling. When the bundle
 * finishes, the per-bundle file paths are written into the
 * SettingsRepository's `imageGenSd*PathFlow` so the bridge can
 * dispatch through the new local engine transparently.
 *
 * Persistence:
 *  - runtime choice → `imageGenSdRuntimeFlow`
 *  - last imported bundle → `imageGenSdActiveBundleFlow`
 *  - per-bundle file paths → `imageGenSd*PathFlow` (set after
 *    a successful import)
 *
 * Engine lifecycle:
 *  - The Load/Unload button is the only place that calls
 *    `sdEngine.loadModel(...)`. The Compose state stays in
 *    sync via the engine's `isReady` property (read
 *    synchronously through the facade's blocking shim).
 */
@Composable
fun LocalSdModelCard(
    app: MeshlitApplication,
    bridge: StableDiffusionBridge,
) {
    val scope = rememberCoroutineScope()
    val settings = app.settingsRepository

    val runtime by settings.imageGenSdRuntimeFlow.collectAsState(initial = "stub")
    val modelPath by settings.imageGenSdModelPathFlow.collectAsState(initial = "")
    val clipPath by settings.imageGenSdClipPathFlow.collectAsState(initial = "")
    val vaePath by settings.imageGenSdVaePathFlow.collectAsState(initial = "")
    val threads by settings.imageGenSdThreadsFlow.collectAsState(initial = 4)
    val gpuLayers by settings.imageGenSdGpuLayersFlow.collectAsState(initial = 0)
    val vaeTiling by settings.imageGenSdVaeTilingFlow.collectAsState(initial = false)
    val activeBundle by settings.imageGenSdActiveBundleFlow.collectAsState(initial = "")

    val downloadTick by app.downloadProgressBus.tick.collectAsState()
    val sdEngine = app.sdEngine
    var pendingBundle by remember { mutableStateOf<String?>(null) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    val freeBytes = remember {
        StatFs(app.filesDir.absolutePath).availableBytes
    }

    val controller = remember(app) {
        SdImportController(
            context = app,
            bus = app.downloadProgressBus,
            downloader = HttpStreamDownloader().asBundleDownloader(),
            // `modelDownloadHeadersFor` is suspend — wrap in a
            // runBlocking-shim so the controller's non-suspend
            // `headersFor: (String) -> Map<String, String>`
            // callback can call it. The headers map is built
            // once per download (one DataStore read), so the
            // blocking cost is one frame.
            headersFor = { url ->
                    runBlocking { settings.modelDownloadHeadersFor(url) }
                },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Local Stable Diffusion",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Text(
                "Pick a runtime, drop in a model, generate on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Runtime picker — the chip the user taps to switch
            // sd.cpp / ONNX / diffusers / ExecuTorch / stub.
            RuntimePickerRow(
                selected = runtime,
                onSelect = { picked ->
                    scope.launch { settings.setImageGenSdRuntime(picked) }
                },
            )

            // Active state — read-only summary of what's loaded.
            ActiveStateRow(
                runtime = runtime,
                modelPath = modelPath,
                clipPath = clipPath,
                vaePath = vaePath,
                isReady = runCatching { sdEngine.isLibraryLinkedBlocking() }.getOrDefault(false),
            )

            // Bundle download picker. The union of the
            // [SdModelBundles.pickerOrder] defines the display
            // order; each row is a single button that triggers
            // a serial download of the bundle's members.
            BundlePickerRow(
                activeBundle = activeBundle,
                freeBytes = freeBytes,
                onDownload = { bundleId -> pendingBundle = bundleId },
            )

            pendingBundle?.let { bundleId ->
                SdImportDialog(
                    bundleId = bundleId,
                    freeBytes = freeBytes,
                    onStart = {
                        pendingBundle = null
                        importJob = scope.launch {
                            val result = controller.bundle(bundleId)
                            when (result) {
                                is com.meshlit.core.common.MeshlitResult.Success -> {
                                    val set = result.value
                                    settings.setImageGenSdActiveBundle(set.bundleId)
                                    set.byRole["unet"]?.let { settings.setImageGenSdModelPath(it.absolutePath) }
                                    set.byRole["text_encoder"]?.let { settings.setImageGenSdClipPath(it.absolutePath) }
                                    set.byRole["vae"]?.let { settings.setImageGenSdVaePath(it.absolutePath) }
                                    set.byRole["taesd"]?.let { settings.setImageGenSdTaesdPath(it.absolutePath) }
                                }
                                is com.meshlit.core.common.MeshlitResult.Failure -> {
                                    val err = result.error
                                    // B-022: surface 401s as an in-app
                                    // notice with a recovery action so
                                    // the user can pre-attach a HF
                                    // token before retrying.
                                    if (err.tag == "download.unauthorized" ||
                                        err.tag == "sd.member_threw"
                                    ) {
                                        app.reportDownloadUnauthorized(
                                            displayName = SdModelBundles.displayNames[bundleId]
                                                ?: bundleId,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    onDismiss = { pendingBundle = null },
                )
            }

            // Download progress banner — only visible while a
            // bundle is pulling. Cancel cancels the serial coroutine;
            // HttpStreamDownloader's input stream is closed by the
            // coroutine's cancellation path before the next member.
            downloadTick?.let { tick ->
                DownloadBanner(
                    tick = tick,
                    onCancel = { importJob?.cancel() },
                )
            }

            // Load / Unload button — the only call into the
            // engine itself. Wrapped in runBlocking since the
            // facade's `loadModel` is suspend.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val req = com.meshlit.stable_diffusion.SdLoadRequest(
                                runtime = com.meshlit.stable_diffusion.SdRuntime.fromKey(runtime),
                                unetPath = modelPath,
                                textEncoderPath = clipPath.ifBlank { null },
                                vaePath = vaePath.ifBlank { null },
                                taesdPath = null,
                                threads = threads,
                                gpuLayers = gpuLayers,
                                vaeTiling = vaeTiling,
                            )
                            val picked = runCatching { sdEngine.loadModel(req) }
                                .getOrElse { com.meshlit.core.common.MeshlitResult.Failure(
                                    com.meshlit.core.common.MeshlitError.Native("sd.engine_load_threw", it),
                                ) }
                            // Errors are surfaced via the engine's
                            // typed MeshlitResult; the UI shows
                            // them in lastError. We don't trigger
                            // a generate here — the user picks
                            // the LOCAL_SD mode chip and hits
                            // Generate.
                            if (picked is com.meshlit.core.common.MeshlitResult.Failure) {
                                // No-op; the next Generate will
                                // surface the error naturally.
                            }
                        }
                    },
                    enabled = runtime != "stub" && modelPath.isNotBlank(),
                ) {
                    Text("Load into runtime")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch { runCatching { sdEngine.unload() } }
                    },
                ) {
                    Text("Unload")
                }
            }
        }
    }
}

@Composable
private fun RuntimePickerRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val runtimes = listOf(
        SdRuntime.Stub,
        SdRuntime.StableDiffusionCpp,
        SdRuntime.OnnxRuntime,
        SdRuntime.DiffusersPython,
        SdRuntime.ExecuTorch,
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Runtime",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(runtimes) { r ->
                FilterChip(
                    selected = selected == r.key,
                    onClick = { onSelect(r.key) },
                    label = {
                        Text(r.label, style = MaterialTheme.typography.labelSmall)
                    },
                )
            }
        }
    }
}

@Composable
private fun ActiveStateRow(
    runtime: String,
    modelPath: String,
    clipPath: String,
    vaePath: String,
    isReady: Boolean,
) {
    val rt = SdRuntime.fromKey(runtime)
    val filename = modelPath.substringAfterLast('/').ifBlank { "—" }
    val clip = clipPath.substringAfterLast('/').ifBlank { "—" }
    val vae = vaePath.substringAfterLast('/').ifBlank { "—" }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Engine: ${rt.engineTag}", style = MaterialTheme.typography.bodySmall)
        Text("UNet: $filename", style = MaterialTheme.typography.bodySmall)
        Text("Text encoder: $clip", style = MaterialTheme.typography.bodySmall)
        Text("VAE: $vae", style = MaterialTheme.typography.bodySmall)
        Text(
            if (isReady) "Status: loaded" else "Status: not loaded",
            style = MaterialTheme.typography.bodySmall,
            color = if (isReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BundlePickerRow(
    activeBundle: String,
    freeBytes: Long,
    onDownload: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Download from catalog",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            "Disk: ${formatBytes(freeBytes)} free",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SdModelBundles.pickerOrder.forEach { bundleId ->
            val label = SdModelBundles.displayNames[bundleId] ?: bundleId
            val isActive = activeBundle == bundleId
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("active") },
                    )
                }
                OutlinedButton(
                    onClick = { onDownload(bundleId) },
                ) {
                    Text(if (isActive) "Re-download" else "Download")
                }
            }
        }
    }
}

@Composable
private fun DownloadBanner(tick: DownloadTick, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Downloading ${tick.displayName}",
            style = MaterialTheme.typography.bodySmall,
        )
        LinearProgressIndicator(
            progress = { tick.fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (tick.stage) {
            com.meshlit.loader.DownloadStage.Done ->
                Text(
                    "Done",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            com.meshlit.loader.DownloadStage.Failed ->
                Text(
                    "Failed: ${tick.errorMessage ?: "unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            com.meshlit.loader.DownloadStage.Downloading ->
                Text(
                    "${(tick.fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                )
        }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}