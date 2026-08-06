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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.capability.CapabilityBadge
import com.meshlit.core.inference.ModelPredicates
import com.meshlit.core.inference.RuntimeRegistry
import com.meshlit.models.ModelCatalog
import com.meshlit.ui.components.RaListCard
import com.meshlit.ui.theme.RaOrange
import com.meshlit.ui.theme.RaSurface
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage

/**
 * Models picker. Now wired to the RunAnywhere-parity ViewModel +
 * `RaListCard` + `ModelTrailingAction` state machine. The screen
 * remains a thin shell that:
 *
 *  - observes `ModelSelectionState` (single source of truth for
 *    `models`, `currentModelId`, `busyModelId`, `progressPercent`,
 *    `error`, `activeFramework`, `searchQuery`)
 *  - delegates "RunAnywhere catalog" downloads to the VM
 *  - delegates "Alternative imports" downloads to the screen-level
 *    coroutine (the VM reuses the same `_state` to track `busyModelId`
 *    / `progressPercent` so the row's `ModelTrailingAction` doesn't
 *    need a second source)
 *  - shows `ModelFilterRow`, `ConfirmDeleteDialog`, and `ErrorDialog`
 *    driven by VM state
 *  - hides the bundled + Top-pick cards during search or when a
 *    backend filter is active (mirrors upstream rule)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }
    val scope = rememberCoroutineScope()

    val (vm, state) = rememberModelSelectionState()

    // Alternative-import downloads still happen at the screen level
    // (they stream a single GGUF via OkHttp, not the SDK). We track
    // status + on-disk presence here so the row's `ModelTrailingAction`
    // gets a consistent view of "busy + percent".
    val altStatus = remember { mutableStateMapOf<String, DownloadStatus>() }
    val altInstalled = remember { mutableStateMapOf<String, Boolean>() }
    val altJobs = remember { mutableStateMapOf<String, kotlinx.coroutines.Job>() }
    val altRateTracker = remember { mutableStateMapOf<String, ByteRateTracker>() }
    // Per-entry download bytes — surfaced into the row's
    // DownloadStatusPanel so the user sees "1.4 MB / 368 MB"
    // alongside the percent + ETA + MB/s columns.
    val altBytesDownloaded = remember { mutableStateMapOf<String, Long>() }
    val altTotalBytes = remember { mutableStateMapOf<String, Long>() }

    // Bundled-model re-extract status — separate from the VM because
    // it's a one-shot APK→files-dir copy, not an SDK download.
    var installStatus by remember { mutableStateOf<String?>(null) }

    // Pending-delete confirmation. Set on row trash tap, cleared on
    // dialog dismiss / confirm. Mirrors upstream
    // `ModelSelectionSheet.pendingDelete`.
    var pendingDelete by remember { mutableStateOf<ModelSelectionEntry?>(null) }

    // ── Filter / search ──────────────────────────────────────────
    val query = state.searchQuery
    val activeFramework = state.activeFramework
    val showRecommended = query.isBlank() && activeFramework == ModelPredicates.ActiveFramework.ALL

    val filteredModels = remember(state.models, query, activeFramework) {
        state.models
            .filter { it.matchesQuery(query) }
            .filter { it.matchesFramework(activeFramework) }
    }
    val recommendedIds = remember(state.models) {
        // Top pick = smallest entry that fits 33% of free RAM. Today
        // we approximate free RAM as 0 so every entry is eligible —
        // the picker still surfaces one recommendation even on
        // untested hardware.
        recommendTopPick(state.models, freeRamMb = 0L)?.let { setOf(it) } ?: emptySet()
    }
    val recommended = filteredModels.filter { it.id in recommendedIds }
    val alternatives = filteredModels.filter {
        it.source == ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT && it.id !in recommendedIds
    }
    val runAnywhere = filteredModels.filter {
        it.source == ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG && it.id !in recommendedIds
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Bundled model card (hidden during search/filter) ──
            if (showRecommended) {
                item(key = "bundled-card") {
                    Spacer(Modifier.height(4.dp))
                    BundledModelCard(
                        app = app,
                        status = installStatus,
                        onReextract = { msg -> installStatus = msg },
                    )
                }
            }

            // ── Filter row + search ───────────────────────────────
            item(key = "filter-row") {
                Spacer(Modifier.height(4.dp))
                ModelFilterRow(
                    active = activeFramework,
                    onSelect = vm::setActiveFramework,
                )
            }
            item(key = "search-field") {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.ra_search_filter)) },
                    placeholder = {
                        Text(stringResource(R.string.ra_search_filter_placeholder))
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = RaOrange,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                )
                            }
                        }
                    },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = RaSurface,
                        unfocusedContainerColor = RaSurface,
                        focusedBorderColor = RaOrange,
                        unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            // ── Recommended (Top pick) section ────────────────────
            if (showRecommended && recommended.isNotEmpty()) {
                item(key = "header-recommended") {
                    ModelsSectionHeader(stringResource(R.string.ra_section_recommended))
                }
                items(recommended, key = { "rec-${it.id}" }) { entry ->
                    ModelRowCard(
                        entry = entry,
                        state = state,
                        onSelect = { vm.select(entry) },
                        onDownload = { vm.download(entry) },
                        onCancel = { vm.cancelDownload(entry.id) },
                        onSetToken = { /* HF-token sheet — out of scope here */ },
                        onDelete = { pendingDelete = entry },
                        onPickAlt = { /* alternative-import download path handled inline below */ },
                        altStatus = altStatus[entry.id] ?: DownloadStatus.Idle,
                        altInstalled = altInstalled[entry.id] == true,
                        altRateTracker = altRateTracker[entry.id],
                        altBytesDownloaded = altBytesDownloaded[entry.id] ?: 0L,
                        altTotalBytes = altTotalBytes[entry.id] ?: 0L,
                        onAltDownload = {
                            altStatus[entry.id] = DownloadStatus.Running(0)
                            val tracker = ByteRateTracker()
                            altRateTracker[entry.id] = tracker
                            val job = scope.launch {
                                val outcome = ModelCatalog.download(
                                    context = context,
                                    entry = run {
                                        // Map VM entry → ModelCatalog.Entry
                                        // for the file download. The VM
                                        // entry carries every field we
                                        // need to look up the catalog.
                                        ModelCatalog.all.firstOrNull {
                                            it.id == entry.id
                                        } ?: return@launch
                                    },
                                    onProgress = { percent, bytesDownloaded, totalBytes ->
                                        tracker.update(bytesDownloaded)
                                        altBytesDownloaded[entry.id] = bytesDownloaded
                                        altTotalBytes[entry.id] = totalBytes
                                        altStatus[entry.id] = DownloadStatus.Running(
                                            percent.toInt().coerceIn(0, 100),
                                        )
                                    },
                                )
                                when {
                                    outcome.file != null -> {
                                        altInstalled[entry.id] = true
                                        altStatus[entry.id] = DownloadStatus.Done(
                                            outcome.file.absolutePath,
                                        )
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
                        onAltCancel = {
                            altJobs.remove(entry.id)?.cancel()
                            altRateTracker.remove(entry.id)
                            altStatus[entry.id] = DownloadStatus.Idle
                        },
                        onAltDelete = { pendingDelete = entry },
                    )
                }
            }

            // ── Alternative models section ───────────────────────
            if (alternatives.isNotEmpty()) {
                item(key = "header-alt") {
                    Spacer(Modifier.height(8.dp))
                    ModelsSectionHeader("Alternative models")
                }
                items(alternatives, key = { "alt-${it.id}" }) { entry ->
                    ModelRowCard(
                        entry = entry,
                        state = state,
                        onSelect = { vm.select(entry) },
                        onDownload = { vm.download(entry) },
                        onCancel = { vm.cancelDownload(entry.id) },
                        onSetToken = { },
                        onDelete = { pendingDelete = entry },
                        onPickAlt = { /* unused in this branch */ },
                        altStatus = altStatus[entry.id] ?: DownloadStatus.Idle,
                        altInstalled = altInstalled[entry.id] == true,
                        altRateTracker = altRateTracker[entry.id],
                        altBytesDownloaded = altBytesDownloaded[entry.id] ?: 0L,
                        altTotalBytes = altTotalBytes[entry.id] ?: 0L,
                        onAltDownload = {
                            altStatus[entry.id] = DownloadStatus.Running(0)
                            val tracker = ByteRateTracker()
                            altRateTracker[entry.id] = tracker
                            val job = scope.launch {
                                val altEntry = ModelCatalog.all.firstOrNull {
                                    it.id == entry.id
                                } ?: return@launch
                                val outcome = ModelCatalog.download(
                                    context = context,
                                    entry = altEntry,
                                    onProgress = { percent, bytesDownloaded, totalBytes ->
                                        tracker.update(bytesDownloaded)
                                        altBytesDownloaded[entry.id] = bytesDownloaded
                                        altTotalBytes[entry.id] = totalBytes
                                        altStatus[entry.id] = DownloadStatus.Running(
                                            percent.toInt().coerceIn(0, 100),
                                        )
                                    },
                                )
                                when {
                                    outcome.file != null -> {
                                        altInstalled[entry.id] = true
                                        altStatus[entry.id] = DownloadStatus.Done(
                                            outcome.file.absolutePath,
                                        )
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
                        onAltCancel = {
                            altJobs.remove(entry.id)?.cancel()
                            altRateTracker.remove(entry.id)
                            altStatus[entry.id] = DownloadStatus.Idle
                        },
                        onAltDelete = { pendingDelete = entry },
                    )
                }
            }

            // ── RunAnywhere catalog section ──────────────────────
            if (runAnywhere.isNotEmpty()) {
                item(key = "header-run") {
                    Spacer(Modifier.height(8.dp))
                    ModelsSectionHeader("RunAnywhere catalog")
                }
                items(runAnywhere, key = { "run-${it.id}" }) { entry ->
                    ModelRowCard(
                        entry = entry,
                        state = state,
                        onSelect = { vm.select(entry) },
                        onDownload = { vm.download(entry) },
                        onCancel = { vm.cancelDownload(entry.id) },
                        onSetToken = { },
                        onDelete = { pendingDelete = entry },
                        onPickAlt = { },
                        altStatus = DownloadStatus.Idle,
                        altInstalled = false,
                        altRateTracker = null,
                        onAltDownload = { },
                        onAltCancel = { },
                        onAltDelete = { },
                    )
                }
            }

            // ── Supported formats + runtimes ─────────────────────
            item(key = "supported-formats") {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
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
                key = { "rt-${it.runtimeId}" },
            ) { engine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(engine.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(engine.status.displayName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "capability") {
                CapabilityBadge(app = app)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ── Dialogs (error + delete) ────────────────────────────────
    state.error?.let { err ->
        ErrorDialog(error = err, onDismiss = vm::clearError)
    }
    pendingDelete?.let { entry ->
        ConfirmDeleteDialog(
            displayName = entry.displayName,
            approxSizeMb = entry.approxSizeMb,
            onConfirm = {
                when (entry.source) {
                    ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG ->
                        vm.delete(entry)
                    ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT -> {
                        runCatching {
                            ModelPredicates.importedModelFile(context, entry.id)
                                .takeIf { it.exists() }
                                ?.delete()
                        }
                        altInstalled[entry.id] = false
                        altStatus[entry.id] = DownloadStatus.Idle
                    }
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // Kick a refresh when the screen first opens so the VM loads
    // its model list from the catalogs.
    LaunchedEffect(Unit) { vm.refresh() }
}

/**
 * Single row card. Pure presentation: pulls state from the VM for
 * `RUNANYWHERE_CATALOG` source, falls back to the screen-side maps
 * for `ALTERNATIVE_IMPORT`. The two paths differ only in where
 * `isBusy` / `isReady` come from.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ModelRowCard(
    entry: ModelSelectionEntry,
    state: ModelSelectionState,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSetToken: () -> Unit,
    onDelete: () -> Unit,
    onPickAlt: () -> Unit,
    altStatus: DownloadStatus,
    altInstalled: Boolean,
    altRateTracker: ByteRateTracker?,
    altBytesDownloaded: Long = 0L,
    altTotalBytes: Long = 0L,
    onAltDownload: () -> Unit,
    onAltCancel: () -> Unit,
    onAltDelete: () -> Unit,
) {
    val isCurrent = state.currentModelId == entry.id
    val vmBusy = state.busyModelId == entry.id
    val vmProgress = if (vmBusy) state.progressPercent else 0
    val altBusy = altStatus is DownloadStatus.Running
    val altProgress = (altStatus as? DownloadStatus.Running)?.progress ?: 0

    val isReady: Boolean = when (entry.source) {
        ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG ->
            state.models.firstOrNull { it.id == entry.id }?.isDownloaded == true
        ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT -> altInstalled
    }
    val isBusy: Boolean = when (entry.source) {
        ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG -> vmBusy
        ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT -> altBusy
    }
    val progress: Int = if (vmBusy) vmProgress else altProgress

    // Top pick highlight only for the recommended section; once the
    // user filters or searches the highlight collapses so the user
    // isn't misled by an out-of-context badge.
    val highlightLabel = if (entry.requiresHfAuth) {
        stringResource(R.string.ra_set_token)
    } else {
        null
    }

    val (onDownloadEffective, onCancelEffective, onSelectEffective, onDeleteEffective) =
        when (entry.source) {
            ModelSelectionEntry.ModelSource.RUNANYWHERE_CATALOG ->
                arrayOf(onDownload, onCancel, onSelect, onDelete)
            ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT ->
                arrayOf(onAltDownload, onAltCancel, onPickAlt, onAltDelete)
        }

    // Speed class chip — derived from the entry's approxSizeMb so
    // the user can pick a model that matches their patience
    // without reading the spec sheet.
    //   ≤ 500 MB   → Easy    (downloads in <1 min on Wi-Fi)
    //   ≤ 1.5 GB   → Fast    (downloads in ~5 min on Wi-Fi)
    //   ≤ 4 GB     → Balanced
    //   anything larger → Heavy
    val speedClassLabelId = when {
        entry.approxSizeMb <= 500L -> R.string.models_chip_easy
        entry.approxSizeMb <= 1500L -> R.string.models_chip_fast
        entry.approxSizeMb <= 4000L -> R.string.models_chip_balanced
        else -> R.string.models_chip_heavy
    }
    val speedClassLabel = stringResource(speedClassLabelId)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RaListCard(
            leadingIcon = familyIcon(entry),
            title = entry.displayName,
            subtitle = "${entry.license} · ~${entry.approxSizeMb} MB · ${entry.language}",
            highlightLabel = highlightLabel,
            chips = {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(entry.family) },
                    )
                    // Runtime / quant / architecture / NPU /
                    // speed-class chips. Order matters — the most
                    // decision-relevant tag (speed class) lands
                    // first so it's the first thing the user
                    // processes when scanning.
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(speedClassLabel) },
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.models_chip_llamacpp)) },
                    )
                    entry.tags.take(2).forEach { tag ->
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(tag) },
                        )
                    }
                }
            },
            trailing = {
                ModelTrailingAction(
                    isCurrent = isCurrent,
                    isReady = isReady,
                    isBusy = isBusy,
                    progressPercent = progress,
                    requiresHfAuth = entry.requiresHfAuth,
                    onCancel = onCancelEffective,
                    onDownload = onDownloadEffective,
                    onSelect = onSelectEffective,
                    onSetToken = onSetToken,
                )
            },
            onClick = if (isReady) onSelectEffective else null,
        )
        // Inline progress panel for non-VM downloads (alternative
        // imports). The VM-driven row's progress shows through
        // `ModelTrailingAction`'s spinner — the panel is redundant
        // for those.
        if (entry.source == ModelSelectionEntry.ModelSource.ALTERNATIVE_IMPORT &&
            altStatus !is DownloadStatus.Idle
        ) {
            DownloadStatusPanel(
                status = altStatus,
                displayName = entry.displayName,
                bytesPerSecond = altRateTracker?.bytesPerSecond() ?: 0.0,
                bytesDownloaded = altBytesDownloaded,
                totalBytes = altTotalBytes,
                approxSizeMb = entry.approxSizeMb,
            )
        }
        // Delete affordance for ready rows — the upstream
        // `ModelRow` exposes a small trash glyph at the row's
        // bottom-right; we surface it as a compact text button so
        // the row's trailing slot stays focused on the state
        // machine.
        if (isReady && !isCurrent) {
            androidx.compose.material3.TextButton(onClick = onDeleteEffective) {
                Text(stringResource(R.string.ra_delete))
            }
        }
    }
}

/**
 * Family → leading icon mapping. Pure function — keeps the
 * visual contract consistent across both catalogs.
 */
private fun familyIcon(entry: ModelSelectionEntry): androidx.compose.ui.graphics.vector.ImageVector =
    when {
        entry.family.contains("Qwen", ignoreCase = true) -> Icons.Filled.Storage
        entry.family.contains("SmolLM", ignoreCase = true) -> Icons.Filled.Memory
        entry.family.contains("Llama", ignoreCase = true) -> Icons.Filled.Memory
        else -> Icons.Filled.Storage
    }