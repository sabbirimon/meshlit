package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.capability.CapabilityBadge
import com.meshlit.core.inference.RuntimeRegistry
import com.meshlit.inference.RunAnywhereCatalog
import com.meshlit.models.ModelCatalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val scope = rememberCoroutineScope()

    // Per-row download status — one map per catalog so each row's
    // recomposition is bounded to that row's id.
    val altStatus = remember { mutableStateMapOf<String, DownloadStatus>() }
    val altInstalled = remember { mutableStateMapOf<String, Boolean>() }
    val runStatus = remember { mutableStateMapOf<String, DownloadStatus>() }
    val runLoaded = remember { mutableStateMapOf<String, Boolean>() }
    var installStatus by remember { mutableStateOf<String?>(null) }

    // Per-row download handle — set when a fetch is in flight so the
    // user can cancel mid-download. Holding the Job (not the
    // coroutine) keeps the UI composable stateless and lets the
    // cancel button dispose the underlying OKHttp call.
    val altJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }
    val runJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }

    /**
     * Approximate transfer rate tracker. The row's `onProgress`
     * supplies cumulative bytes — we sample the delta over time so
     * the UI can show "12.4 MB/s" while the download is running.
     */
    val altRateTracker = remember { mutableStateMapOf<String, ByteRateTracker>() }
    val runRateTracker = remember { mutableStateMapOf<String, ByteRateTracker>() }

    // Live search query — case-insensitive substring match against
    // name, family, origin, language, and runtime. Empty == show all.
    var query by remember { mutableStateOf("") }
    val filteredAlt = remember(query) {
        if (query.isBlank()) ModelCatalog.all
        else ModelCatalog.all.filter { entry -> entry.matchesQuery(query) }
    }
    val filteredRun = remember(query) {
        if (query.isBlank()) RunAnywhereCatalog.all
        else RunAnywhereCatalog.all.filter { entry -> entry.matchesQuery(query) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_models)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                BundledModelCard(
                    app = app,
                    status = installStatus,
                    onReextract = { msg -> installStatus = msg },
                )
            }
            // Live search filter — affects both catalog cards below.
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Filter models") },
                    placeholder = { Text("name, family, origin, language, runtime") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                )
                            }
                        }
                    },
                )
            }
            item { ModelsSectionHeader("Alternative models") }
            item {
                AlternativeModelsCard(
                    installedIds = altInstalled,
                    rowStatus = altStatus,
                    visibleEntries = filteredAlt,
                    rateTracker = altRateTracker,
                    onPick = { entry ->
                        // Mark running at 0% so the bar doesn't fake
                        // 50% out of nowhere and the user sees real
                        // progress climb from the network.
                        altStatus[entry.id] = DownloadStatus.Running(0)
                        val tracker = ByteRateTracker()
                        altRateTracker[entry.id] = tracker
                        val job = scope.launch {
                            val outcome = ModelCatalog.download(
                                context = context,
                                entry = entry,
                                onProgress = { percent, bytesDownloaded, _totalBytes ->
                                    tracker.update(bytesDownloaded)
                                    altStatus[entry.id] = DownloadStatus.Running(percent.toInt().coerceIn(0, 100))
                                },
                            )
                            when {
                                outcome.file != null -> {
                                    altInstalled[entry.id] = true
                                    altStatus[entry.id] = DownloadStatus.Done(outcome.file.absolutePath)
                                }
                                else -> {
                                    altStatus[entry.id] = DownloadStatus.Failed(
                                        outcome.errorMessage ?: "Download failed",
                                    )
                                }
                            }
                            altJobs.remove(entry.id)
                            altRateTracker.remove(entry.id)
                        }
                        altJobs[entry.id] = job
                    },
                    onCancel = { entry ->
                        altJobs.remove(entry.id)?.cancel()
                        altRateTracker.remove(entry.id)
                        altStatus[entry.id] = DownloadStatus.Idle
                    },
                    onDelete = { entry ->
                        // Drop the on-disk file too — Delete isn't
                        // just a state reset, it actually frees the
                        // ~1 GB the model occupied.
                        runCatching {
                            val file = java.io.File(
                                java.io.File(context.filesDir, "imported-models"),
                                "${entry.id}.gguf",
                            )
                            if (file.exists()) file.delete()
                        }
                        altInstalled[entry.id] = false
                        altStatus[entry.id] = DownloadStatus.Idle
                    },
                )
            }
            item { ModelsSectionHeader("RunAnywhere catalog") }
            item {
                RunAnywhereCatalogCard(
                    loadedIds = runLoaded,
                    rowStatus = runStatus,
                    visibleEntries = filteredRun,
                    rateTracker = runRateTracker,
                    onGet = { entry ->
                        runStatus[entry.id] = DownloadStatus.Running(0)
                        val tracker = ByteRateTracker()
                        runRateTracker[entry.id] = tracker
                        val job = scope.launch {
                            runCatching {
                                val llm = app.inferenceCoordinator.runAnywhereEngine()
                                // Push the catalog URL into the
                                // engine so the SDK's registerModel
                                // call has a URL to plan against.
                                // Without this the SDK's planner
                                // rejects the download with
                                // "Unable to create a download plan".
                                if (entry.url.isNotBlank()) {
                                    llm.setCatalogDownloadUrl(entry.id, entry.url)
                                }
                                llm.downloadModelById(
                                    modelId = entry.id,
                                    url = entry.url,
                                    displayName = entry.displayName,
                                    memoryRequirementBytes = entry.approxSizeMb * 1024L * 1024L,
                                ).collect { progress ->
                                    val pct = (progress.progress * 100f).toInt().coerceIn(0, 100)
                                    tracker.update(progress.bytesDownloaded)
                                    runStatus[entry.id] = DownloadStatus.Running(pct)
                                }
                                runLoaded[entry.id] = true
                                runStatus[entry.id] = DownloadStatus.Done("runanywhere:${entry.id}")
                            }.onFailure { t ->
                                runStatus[entry.id] = DownloadStatus.Failed(
                                    t.message ?: t.javaClass.simpleName,
                                )
                            }
                            runJobs.remove(entry.id)
                            runRateTracker.remove(entry.id)
                        }
                        runJobs[entry.id] = job
                    },
                    onCancel = { entry ->
                        runJobs.remove(entry.id)?.cancel()
                        runRateTracker.remove(entry.id)
                        runStatus[entry.id] = DownloadStatus.Idle
                    },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Supported formats",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        engineFormats.forEach { row ->
                            EngineFormatRowView(row)
                            HorizontalDivider()
                        }
                    }
                }
            }
            items(
                items = RuntimeRegistry.all,
                key = { it.runtimeId },
            ) { engine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(engine.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(engine.status.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                CapabilityBadge(app = app)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Case-insensitive substring match against every searchable field of
 * a ModelCatalog entry. Empty query matches everything.
 */
private fun ModelCatalog.Entry.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return displayName.lowercase().contains(needle) ||
        family.lowercase().contains(needle) ||
        origin.lowercase().contains(needle) ||
        language.lowercase().contains(needle) ||
        runtimeDisplayName.lowercase().contains(needle) ||
        license.lowercase().contains(needle)
}

/**
 * Case-insensitive substring match against every searchable field of
 * a RunAnywhereCatalog entry.
 */
private fun RunAnywhereCatalog.Entry.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return true
    return displayName.lowercase().contains(needle) ||
        family.lowercase().contains(needle) ||
        origin.lowercase().contains(needle) ||
        language.lowercase().contains(needle) ||
        "runanywhere".contains(needle) ||
        id.lowercase().contains(needle) ||
        license.lowercase().contains(needle)
}