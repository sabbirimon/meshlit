package com.meshlit.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.capability.CapabilityBadge
import com.meshlit.core.inference.BundledModelInstaller
import com.meshlit.core.inference.RuntimeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Models settings screen. Reachable from Settings → Models and (in
 * future) from the top-level Models tab once the file picker flow
 * lands.
 *
 * Renders inside the parent [CategoryScreen]'s scaffold when reached
 * via Settings; the standalone route uses its own scaffold. Two
 * pieces of state live here:
 *  1. Custom GGUF path override — written to
 *     [com.meshlit.settings.SettingsRepository.setCustomModelPath].
 *  2. Bundled model extraction — surfaces the install state for
 *     the bundled Qwen2.5-1.5B-Instruct asset.
 *
 * Auto-load wiring lives in `InferenceForegroundService.onCreate`;
 * this screen is the read-side / settings-side UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val scope = rememberCoroutineScope()

    val customPath by app.settingsRepository.customModelPathFlow
        .collectAsState(initial = "")
    var pathField by remember(customPath) { mutableStateOf(customPath) }
    var installStatus by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_models)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            val pickFile = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    val resolved = copyUriToInternal(app, uri)
                    if (resolved != null) {
                        pathField = resolved.absolutePath
                        scope.launch {
                            app.settingsRepository.setCustomModelPath(resolved.absolutePath)
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    runCatching {
                        pickFile.launch(arrayOf("*/*"))
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.models_import),
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 160.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tier") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.capability_tier_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    CapabilityBadge(app = app)
                }
            }
            // Phase 2.x — runtime-upgrade banner. Surfaces a one-liner
            // when the on-disk registry version is older than the
            // build's compile-time REGISTRY_VERSION. Dismissed by
            // tapping the close icon; persists the seen version so we
            // don't nag on every visit.
            item(key = "runtime_upgrade_banner") {
                val seenVersion by app.settingsRepository.runtimeRegistryVersionFlow
                    .collectAsState(initial = 0)
                val dismissed = rememberSaveable { mutableStateOf(false) }
                val show = !dismissed.value && seenVersion < RuntimeRegistry.REGISTRY_VERSION
                androidx.compose.animation.AnimatedVisibility(
                    visible = show,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Runtime catalog updated (v${RuntimeRegistry.REGISTRY_VERSION})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    text = RuntimeRegistry.REGISTRY_CHANGE_NOTE,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            IconButton(
                                onClick = {
                                    dismissed.value = true
                                    scope.launch {
                                        app.settingsRepository.setRuntimeRegistryVersionSeen(
                                            RuntimeRegistry.REGISTRY_VERSION,
                                        )
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "bundled-header") {
                SectionHeader(text = stringResource(R.string.models_bundled_section))
            }
            item(key = "bundled-card") {
                BundledModelCard(
                    app = app,
                    onReextract = { status ->
                        scope.launch {
                            installStatus = status
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    BundledModelInstaller().ensureInstalled(app)
                                }
                            }
                            installStatus = result.fold(
                                onSuccess = { file ->
                                    if (file != null) {
                                        app.resources.getString(
                                            R.string.models_reextract_done,
                                            file.absolutePath,
                                        )
                                    } else {
                                        app.resources.getString(R.string.models_no_asset)
                                    }
                                },
                                onFailure = { t ->
                                    app.resources.getString(
                                        R.string.models_reextract_failed,
                                        t.message ?: "unknown",
                                    )
                                },
                            )
                        }
                    },
                    status = installStatus,
                )
            }

            item(key = "formats-header") {
                SectionHeader(text = stringResource(R.string.engine_supported_formats))
            }
            item(key = "formats-card") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.engine_supported_formats_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        engineFormats.forEachIndexed { idx, row ->
                            EngineFormatRowView(row = row)
                            if (idx != engineFormats.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            item(key = "alternatives-header") {
                SectionHeader(text = stringResource(R.string.models_alternative_label))
            }
            item(key = "alternatives-list") {
                // Per-row state lives at the screen level so that
                // a successful download invalidates the row that ran
                // it. A `mutableStateMapOf` keyed by `entry.id`
                // recomposes any row whose value changes.
                val installedIds = remember {
                    mutableStateMapOf<String, Boolean>().apply {
                        com.meshlit.models.ModelCatalog.all.forEach { entry ->
                            this[entry.id] = java.io.File(
                                app.filesDir,
                                "imported-models/${entry.id}.gguf",
                            ).exists()
                        }
                    }
                }
                val rowStatus = remember { mutableStateMapOf<String, DownloadStatus>() }
                AlternativeModelsCard(
                    app = app,
                    installedIds = installedIds,
                    rowStatus = rowStatus,
                    onPick = { entry ->
                        scope.launch {
                            rowStatus[entry.id] = DownloadStatus.Running(0)
                            val outcome = com.meshlit.models.ModelCatalog.download(
                                app,
                                entry,
                                onProgress = { pct ->
                                    rowStatus[entry.id] = DownloadStatus.Running(pct.toInt())
                                },
                            )
                            if (outcome.isSuccess) {
                                val file = outcome.file!!
                                installedIds[entry.id] = true
                                app.settingsRepository.setCustomModelPath(file.absolutePath)
                                rowStatus[entry.id] = DownloadStatus.Done(file.absolutePath)
                                Toast.makeText(
                                    app,
                                    app.resources.getString(
                                        R.string.models_download_done,
                                        file.absolutePath,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                rowStatus[entry.id] = DownloadStatus.Failed(
                                    outcome.errorMessage ?: "unknown",
                                )
                                Toast.makeText(
                                    app,
                                    app.resources.getString(
                                        R.string.models_download_failed,
                                        outcome.errorMessage ?: "unknown",
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    onDelete = { entry ->
                        val file = java.io.File(
                            app.filesDir,
                            "imported-models/${entry.id}.gguf",
                        )
                        if (file.exists() && file.delete()) {
                            installedIds[entry.id] = false
                            rowStatus[entry.id] = DownloadStatus.Idle
                            // If the deleted file was the active model
                            // path, drop the override so the FGS falls
                            // back to the bundled model on next load.
                            scope.launch {
                                val current = app.settingsRepository
                                    .customModelPathSync()
                                if (current == file.absolutePath) {
                                    app.settingsRepository.setCustomModelPath("")
                                }
                            }
                            Toast.makeText(
                                app,
                                "Deleted ${entry.displayName}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }

            item(key = "override-header") {
                SectionHeader(text = stringResource(R.string.models_override_section))
            }
            item(key = "override-card") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.models_override_path_label),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.models_override_path_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pathField,
                            onValueChange = { pathField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.models_override_path_hint)) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        app.settingsRepository.setCustomModelPath(pathField)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.models_override_save))
                            }
                            Button(
                                onClick = {
                                    pathField = ""
                                    scope.launch {
                                        app.settingsRepository.setCustomModelPath("")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.models_override_clear))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolve a SAF-picked content URI to a real file under the app's
 * internal `filesDir/imported-models/` directory. The engine needs a
 * regular filesystem path (not a content:// URI) to mmap the GGUF.
 *
 * Returns null if the URI is unreadable or empty. The caller is
 * expected to surface the failure in a snackbar / status line.
 */
private fun copyUriToInternal(app: MeshlitApplication, uri: android.net.Uri): java.io.File? {
    val resolver = app.contentResolver
    val name = runCatching {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "model.gguf"
        }
    }.getOrNull() ?: "model.gguf"
    val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val destDir = java.io.File(app.filesDir, "imported-models").apply { mkdirs() }
    val dest = java.io.File(destDir, safeName)
    return runCatching {
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        dest
    }.getOrNull()
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun BundledModelCard(
    app: MeshlitApplication,
    onReextract: (String) -> Unit,
    status: String?,
) {
    val context = LocalContext.current
    val installer = remember { BundledModelInstaller() }
    val installed = remember { installer.installedFile(app) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.models_bundled_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.models_bundled_quant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (installed != null) {
                    app.resources.getString(
                        R.string.models_bundled_installed,
                        installed.absolutePath,
                        installed.length(),
                    )
                } else {
                    stringResource(R.string.models_bundled_not_installed)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onReextract(context.getString(R.string.models_reextract_in_progress)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_reextract_button))
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Per-row download state. Driven by `mutableStateMapOf` so any
 *  row that flips to a new state recomposes immediately. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Running(val progress: Int) : DownloadStatus
    data class Done(val absolutePath: String) : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus
}

@Composable
private fun AlternativeModelsCard(
    app: MeshlitApplication,
    installedIds: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    rowStatus: androidx.compose.runtime.snapshots.SnapshotStateMap<String, DownloadStatus>,
    onPick: (com.meshlit.models.ModelCatalog.Entry) -> Unit,
    onDelete: (com.meshlit.models.ModelCatalog.Entry) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.meshlit.models.ModelCatalog.all.forEach { entry ->
                val installed = installedIds[entry.id] == true
                val status = rowStatus[entry.id] ?: DownloadStatus.Idle
                AlternativeRow(
                    entry = entry,
                    isInstalled = installed,
                    status = status,
                    onDownload = { onPick(entry) },
                    onDelete = { onDelete(entry) },
                )
                if (entry != com.meshlit.models.ModelCatalog.all.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AlternativeRow(
    entry: com.meshlit.models.ModelCatalog.Entry,
    isInstalled: Boolean,
    status: DownloadStatus,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val originFlag = when (entry.origin) {
        "USA" -> "\uD83C\uDDFA\uD83C\uDDF8"
        "China" -> "\uD83C\uDDE8\uD83C\uDDF3"
        else -> ""
    }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$originFlag ${entry.origin}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.license} · ~${entry.approxSizeMb} MB · ${entry.language}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isInstalled) "✓" else entry.family,
                style = MaterialTheme.typography.labelSmall,
                color = if (isInstalled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
            )
        }
        // Phase 2 — surface the runtime this model would be carried by.
        // Highlighted in tertiary so the user sees the format/runtime
        // pairing without it competing with the download CTA below.
        Text(
            text = "runtime: ${entry.runtimeDisplayName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Download button: disabled while a download is in flight
            // or the model is already imported.
            val isRunning = status is DownloadStatus.Running
            OutlinedButton(
                onClick = onDownload,
                enabled = !isInstalled && !isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when {
                        isRunning -> "Downloading…"
                        isInstalled -> "Already imported"
                        else -> "Download"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            // Delete button: only visible when the model is installed
            // and no download is in flight. Fades in via AnimatedVisibility.
            AnimatedVisibility(
                visible = isInstalled && !isRunning,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) +
                    expandVertically(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
                    shrinkVertically(animationSpec = tween(durationMillis = 200)),
            ) {
                FilledTonalButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                    )
                }
            }
        }
        // Status panel — animated reveal so the user sees the row
        // wake up when a download starts.
        AnimatedVisibility(
            visible = status !is DownloadStatus.Idle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DownloadStatusPanel(status = status, displayName = entry.displayName)
        }
    }
}

@Composable
private fun DownloadStatusPanel(
    status: DownloadStatus,
    displayName: String,
) {
    when (status) {
        is DownloadStatus.Idle -> Unit
        is DownloadStatus.Running -> {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = stringResource(R.string.models_download_progress, status.progress, displayName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (status.progress.coerceIn(0, 100)) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        is DownloadStatus.Done -> {
            Text(
                text = stringResource(R.string.models_download_done, status.absolutePath),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        is DownloadStatus.Failed -> {
            Text(
                text = stringResource(R.string.models_download_failed, status.reason),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Render one row of the supported-formats card. */
@Composable
private fun EngineFormatRowView(row: EngineFormatRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = row.formatLabel(),
            style = MaterialTheme.typography.titleSmall,
            color = if (row.isShipped) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.statusLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = if (row.isShipped) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "runtime: ${row.runtimeLabel()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
