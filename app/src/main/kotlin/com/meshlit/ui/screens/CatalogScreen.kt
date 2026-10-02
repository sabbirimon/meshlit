package com.meshlit.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereInferenceEngine
import com.meshlit.di.koinInject
import com.meshlit.inference.buildLoadModelIntent
import com.meshlit.ui.components.MeshlitHeader
import com.meshlit.ui.components.RaGetButton
import com.meshlit.ui.components.RaListCard
import com.meshlit.ui.components.RaPillChip
import com.meshlit.ui.components.RaPillTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults

/**
 * Phase 2.x — Catalog screen. Reads the live SDK model registry via
 * [RunAnywhereCatalogEngine] and lets the user download + load any
 * row directly into the inference FGS.
 *
 * State:
 *
 *  - `query` filters by name or family (case-insensitive contains).
 *  - `engine.entries` is the live StateFlow from the engine. The
 *    engine has its own offline fallback so the list is never empty.
 *  - `engine.live` flips `true` once a real SDK fetch succeeds —
 *    drives the offline banner.
 *  - `downloads[id]` tracks per-row download progress via
 *    [DownloadStatus].
 *
 * The "Get" button drives `engine.downloadModelById(id)` (the LLM
 * engine) — this works the same way the Models screen already
 * drives it. Once the download completes we fire the load-model
 * intent so the FGS auto-loads the new GGUF.
 */
@Composable
fun CatalogScreen(
    onOpenDrawer: () -> Unit,
    /**
     * v2 chrome (MeshlitDeepLinkWrap) renders a lead bar
     * with the same title. When the v1 [MeshlitHeader]
     * also renders, the two headers stack vertically and
     * eat ~70 dp of body real-estate. v2 callers pass
     * `true` to skip the inner header. v1 callers keep the
     * default `false` so standalone launch still shows the
     * header.
     */
    omitHeader: Boolean = false,
) {
    val context = LocalContext.current
    val engine = koinInject<RunAnywhereCatalogEngine>()
    val capabilityTier: com.meshlit.capability.CapabilityTier = koinInject()
    val inferenceCoordinator: com.meshlit.core.inference.InferenceCoordinator = koinInject()
    val scope = rememberCoroutineScope()

    val entries by engine.entries.collectAsState()
    val live by engine.live.collectAsState()

    var query by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf<Map<String, DownloadStatus>>(emptyMap()) }
    var refreshInFlight by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var detailsEntry by remember {
        mutableStateOf<RunAnywhereCatalogEngine.Entry?>(null)
    }
    // Type / Source / Size filter chips. The screen was a flat list
    // before the multi-source refactor — adding the three chip
    // groups lets the user narrow by category (CHAT / CODE /
    // VISION), by provenance (OFFICIAL / COMMUNITY_REQUANT /
    // MIRROR), and (implicitly) by size which the row already
    // renders via its size-class badge. Defaults keep the screen
    // behaving like the unfiltered list.
    var typeFilter by remember { mutableStateOf<RunAnywhereCatalogEngine.ModelType?>(null) }
    var tierFilter by remember { mutableStateOf<RunAnywhereCatalogEngine.SourceTier?>(null) }
    // Picker state — when set, the bottom sheet lists every source
    // for this entry so the user can swap from the primary to a
    // mirror or different quant without re-running the search.
    var sourcePickerEntry by remember {
        mutableStateOf<RunAnywhereCatalogEngine.Entry?>(null)
    }

    // Initial fetch — fire once when the screen mounts. If the
    // user pulled-to-refresh we re-fire from the button.
    LaunchedEffect(Unit) {
        engine.refresh()
    }

    val filtered = remember(entries, query, typeFilter, tierFilter) {
        entries.filter { entry ->
            val typeOk = typeFilter == null || entry.modelType == typeFilter
            val tierOk = tierFilter == null || entry.availableTiers.contains(tierFilter)
            val searchOk = query.isBlank() ||
                entry.displayName.contains(query, ignoreCase = true) ||
                entry.family.contains(query, ignoreCase = true)
            typeOk && tierOk && searchOk
        }
    }

    Scaffold(
        topBar = if (omitHeader) {
            // v2 wrapper owns the lead bar; rendering the v1
            // header here would produce two stacked titles
            // (~70 dp of wasted vertical space).
            { }
        } else {
            {
                MeshlitHeader(
                    title = stringResource(R.string.catalog_title),
                    subtitle = stringResource(R.string.catalog_subtitle),
                    tier = capabilityTier,
                    active = refreshInFlight,
                    onOpenDrawer = onOpenDrawer,
                )
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!live) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_offline_banner),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.catalog_search_hint)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            refreshInFlight = true
                            refreshError = null
                            scope.launch {
                                val r = engine.refresh()
                                refreshInFlight = false
                                if (r is com.meshlit.core.common.MeshlitResult.Failure) {
                                    refreshError = r.error.message
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.catalog_refresh))
                    }
                }

                // --- Type filter chip group (multi-source refactor) ---
                // Lets the user narrow the catalog by category
                // (CHAT / CODE / VISION / MULTIMODAL / EMBEDDING).
                // `null` = no filter (every row visible). Tapping a
                // chip again clears the filter.
                ModelTypeChipRow(
                    selected = typeFilter,
                    onSelect = { typeFilter = it },
                )
                // --- Source-tier filter chip group ---
                // OFFICIAL / COMMUNITY_REQUANT / MIRROR / SDK_BUNDLED.
                // Filtering by tier hides rows whose `availableTiers`
                // don't include the chosen tier — e.g. tapping
                // COMMUNITY_REQUANT shows rows that have at least one
                // community requant source. `null` = no filter.
                SourceTierChipRow(
                    selected = tierFilter,
                    onSelect = { tierFilter = it },
                )

                refreshError?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.catalog_failed) + " ($msg)",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.catalog_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            CatalogRow(
                                entry = entry,
                                status = downloads[entry.id] ?: DownloadStatus.Idle,
                                onGet = {
                                    // Default download — the highest-priority
                                    // source from the curated catalog or the
                                    // synthesized SDK row. Source picker
                                    // (long-press / "Source" chip) calls the
                                    // same helper with a specific source.
                                    scope.launchCatalogDownload(
                                        entry = entry,
                                        source = null,
                                        inferenceCoordinator = inferenceCoordinator,
                                        context = context,
                                        setDownloads = { downloads = it },
                                        getDownloads = { downloads },
                                    )
                                },
                                onShowInfo = { detailsEntry = entry },
                                onPickSource = { sourcePickerEntry = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    detailsEntry?.let { entry ->
        val status = downloads[entry.id] ?: DownloadStatus.Idle
        CatalogDetailsSheet(
            entry = entry,
            status = status,
            onDismiss = { detailsEntry = null },
            onRetry = {
                detailsEntry = null
                scope.launchCatalogDownload(
                    entry = entry,
                    source = null,
                    inferenceCoordinator = inferenceCoordinator,
                    context = context,
                    setDownloads = { downloads = it },
                    getDownloads = { downloads },
                )
            },
        )
    }

    // Source picker sheet — shown when the row's "Source" chip
    // is tapped. Lets the user pick between the entry's
    // DownloadSource list (different orgs / quants) and start the
    // download against the chosen URL. The picker is intentionally
    // distinct from the details sheet: details = inspection,
    // picker = action.
    sourcePickerEntry?.let { entry ->
        CatalogSourcePickerSheet(
            entry = entry,
            currentStatus = downloads[entry.id] ?: DownloadStatus.Idle,
            onDismiss = { sourcePickerEntry = null },
            onPick = { source ->
                sourcePickerEntry = null
                scope.launchCatalogDownload(
                    entry = entry,
                    source = source,
                    inferenceCoordinator = inferenceCoordinator,
                    context = context,
                    setDownloads = { downloads = it },
                    getDownloads = { downloads },
                )
            },
        )
    }
}

@Composable
private fun CatalogRow(
    entry: RunAnywhereCatalogEngine.Entry,
    status: DownloadStatus,
    onGet: () -> Unit,
    onShowInfo: () -> Unit,
    onPickSource: () -> Unit,
) {
    val subtitle = "${formatSizeMb(entry.approxSizeMb)} · ${entry.family}"
    val isTopPick = entry.bundled || entry.sizeClass == RunAnywhereCatalogEngine.SizeClass.SMALL
    // Multi-source refactor: surface the entry's primary source
    // (org + size) inline. Tier grouping (OFFICIAL /
    // COMMUNITY_REQUANT / MIRROR) is the fast read for the row —
    // the user can scan "HuggingFaceTB / 386 MB" at a glance
    // before tapping Get. The full source list is behind the
    // "Sources (N)" pill chip which opens the picker sheet.
    val primary = entry.primarySource
    val sourcesLabel = when (entry.sources.size) {
        0 -> "bundled"
        1 -> primary?.let { "${it.org} · ${it.humanSize()}" } ?: "1 source"
        else -> "Sources (${entry.sources.size})"
    }
    @OptIn(ExperimentalLayoutApi::class)
    RaListCard(
        leadingIcon = Icons.Filled.CloudDownload,
        title = entry.displayName,
        subtitle = subtitle,
        highlightLabel = if (isTopPick) "Top pick" else null,
        metadata = {
            if (status is DownloadStatus.Running) {
                LinearProgressIndicator(
                    progress = { status.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (status is DownloadStatus.Failed) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        chips = {
            entry.badges().take(4).forEach { badge ->
                RaPillChip(
                    text = badge.label,
                    tone = badge.tone.toPillTone(),
                )
            }
            // Source count / primary source pill — tapping
            // opens the picker sheet. The chips slot is a plain
            // composable slot, so we render a clickable
            // Surface here (RaPillChip itself is not
            // clickable). Only rendered when the entry has
            // selectable sources.
            if (entry.sources.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.6f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { onPickSource() }
                        .padding(0.dp),
                ) {
                    Text(
                        text = sourcesLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp,
                        ),
                    )
                }
            }
        },
        trailing = {
            when (status) {
                is DownloadStatus.Idle -> RaGetButton(onClick = onGet, label = "Get")
                is DownloadStatus.Running -> OutlinedButton(onClick = {}) { Text("${status.percent}%") }
                is DownloadStatus.Loaded -> RaPillChip(text = "Loaded", tone = RaPillTone.ACTIVE)
                is DownloadStatus.Failed -> RaGetButton(onClick = onGet, label = "Retry")
            }
        },
        // Already-downloaded rows open the details sheet on tap;
        // idle/failed rows fall through to the existing Get action.
        onClick = when (status) {
            is DownloadStatus.Loaded, is DownloadStatus.Running -> onShowInfo
            else -> onGet
        },
    )
}

/**
 * Per-row details sheet. Shows the entry's metadata (id, family,
 * license, origin, language, architecture, quant, size, strengths)
 * plus the current download status. From here the user can retry a
 * failed download or dismiss. The row's Get/Retry button is the
 * canonical action; this sheet exists for inspection and to make
 * catalog rows feel "manageable" rather than one-shot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogDetailsSheet(
    entry: RunAnywhereCatalogEngine.Entry,
    status: DownloadStatus,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val statusLabel = when (status) {
        is DownloadStatus.Idle -> "Not downloaded"
        is DownloadStatus.Running -> "Downloading · ${status.percent}%"
        is DownloadStatus.Loaded -> "Downloaded"
        is DownloadStatus.Failed -> "Failed: ${status.message}"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "ID: ${entry.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            DetailRow(label = "Status", value = statusLabel)
            DetailRow(label = "Family", value = entry.family)
            DetailRow(label = "Architecture", value = entry.architecture.name)
            if (entry.quant != RunAnywhereCatalogEngine.Quant.UNKNOWN) {
                DetailRow(label = "Quantization", value = entry.quant.name)
            }
            DetailRow(label = "Size class", value = entry.sizeClass.name)
            DetailRow(label = "Approx size", value = formatSizeMb(entry.approxSizeMb))
            DetailRow(label = "License", value = entry.license)
            DetailRow(label = "Origin", value = entry.origin)
            DetailRow(label = "Language", value = entry.language)
            if (entry.strengths.isNotEmpty()) {
                DetailRow(
                    label = "Strengths",
                    value = entry.strengths.joinToString(", "),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Close") }
                if (status is DownloadStatus.Failed) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) { Text("Retry") }
                }
            }
        }
    }
}

/**
 * Filter chip group filtered by [RunAnywhereCatalogEngine.ModelType].
 * Tap a chip to enable that filter; tap the selected chip again
 * to clear it (back to `null`). Renders only the four common
 * categories (`CHAT`, `CODE`, `VISION`, `MULTIMODAL`,
 * `EMBEDDING`); `UNKNOWN` is intentionally skipped because it's
 * just a "no signal" sentinel.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelTypeChipRow(
    selected: RunAnywhereCatalogEngine.ModelType?,
    onSelect: (RunAnywhereCatalogEngine.ModelType?) -> Unit,
) {
    val types = listOf(
        RunAnywhereCatalogEngine.ModelType.CHAT,
        RunAnywhereCatalogEngine.ModelType.CODE,
        RunAnywhereCatalogEngine.ModelType.VISION,
        RunAnywhereCatalogEngine.ModelType.MULTIMODAL,
        RunAnywhereCatalogEngine.ModelType.EMBEDDING,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        types.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(if (selected == t) null else t) },
                label = {
                    Text(
                        text = t.name,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Filter chip group filtered by [RunAnywhereCatalogEngine.SourceTier].
 * Same UX as [ModelTypeChipRow] — tap to enable, tap again to
 * clear. OFFICIAL / COMMUNITY_REQUANT / MIRROR / SDK_BUNDLED are
 * the displayed tiers; `UNKNOWN` is omitted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceTierChipRow(
    selected: RunAnywhereCatalogEngine.SourceTier?,
    onSelect: (RunAnywhereCatalogEngine.SourceTier?) -> Unit,
) {
    val tiers = listOf(
        RunAnywhereCatalogEngine.SourceTier.OFFICIAL,
        RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT,
        RunAnywhereCatalogEngine.SourceTier.MIRROR,
        RunAnywhereCatalogEngine.SourceTier.SDK_BUNDLED,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tiers.forEach { t ->
            FilterChip(
                selected = selected == t,
                onClick = { onSelect(if (selected == t) null else t) },
                label = {
                    Text(
                        text = t.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/**
 * Slide-up sheet that lists every [RunAnywhereCatalogEngine.DownloadSource]
 * for [entry]. Each row shows the org, quant, human size, and
 * tier. Tapping a row fires [onPick] with the chosen source and
 * closes the sheet. The picker is intentionally distinct from the
 * details sheet — details = inspection, picker = action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogSourcePickerSheet(
    entry: RunAnywhereCatalogEngine.Entry,
    currentStatus: DownloadStatus,
    onDismiss: () -> Unit,
    onPick: (RunAnywhereCatalogEngine.DownloadSource) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Choose a source (${entry.sources.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            // If a download is already running, surface its
            // status so the user can confirm before re-picking.
            when (currentStatus) {
                is DownloadStatus.Running -> Text(
                    "Download in progress · ${currentStatus.percent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                is DownloadStatus.Loaded -> Text(
                    "Already downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                else -> Unit
            }
            entry.sources
                .sortedBy { it.priority }
                .forEach { source ->
                    SourceRow(
                        entry = entry,
                        source = source,
                        onClick = { onPick(source) },
                    )
                }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("Close") }
            }
        }
    }
}

/**
 * Single source row inside the picker sheet. Surfaces the source
 * org (heading), the quant tag + size (subtitle), and a tier chip
 * + optional SHA-256 hint. Tapping the row fires [onClick].
 */
@Composable
private fun SourceRow(
    entry: RunAnywhereCatalogEngine.Entry,
    source: RunAnywhereCatalogEngine.DownloadSource,
    onClick: () -> Unit,
) {
    val isPrimary = source.priority == entry.primarySource?.priority
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = source.org,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = source.humanSize(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RaPillChip(
                    text = source.quant.name.replace('_', '-'),
                    tone = RaPillTone.NEUTRAL,
                )
                RaPillChip(
                    text = source.tier.name.replace('_', ' '),
                    tone = when (source.tier) {
                        RunAnywhereCatalogEngine.SourceTier.OFFICIAL -> RaPillTone.ACTIVE
                        RunAnywhereCatalogEngine.SourceTier.COMMUNITY_REQUANT -> RaPillTone.TOP_PICK
                        RunAnywhereCatalogEngine.SourceTier.MIRROR -> RaPillTone.TOP_PICK
                        RunAnywhereCatalogEngine.SourceTier.SDK_BUNDLED -> RaPillTone.BUNDLED
                        RunAnywhereCatalogEngine.SourceTier.UNKNOWN -> RaPillTone.NEUTRAL
                    },
                )
                if (isPrimary) {
                    RaPillChip(
                        text = "default",
                        tone = RaPillTone.ACTIVE,
                    )
                }
            }
            if (source.url.isNotBlank()) {
                Text(
                    text = source.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Launch a download for a chosen [entry] + optional [source]. When
 * [source] is null the engine's primary (lowest-priority) source
 * is used. The download state is published into the [downloads]
 * MutableState via the passed [setDownloads] callback so the row
 * UI re-renders. Used by the row's primary `Get` button, the
 * source-picker sheet, and the details sheet's retry path.
 */
private fun CoroutineScope.launchCatalogDownload(
    entry: RunAnywhereCatalogEngine.Entry,
    source: RunAnywhereCatalogEngine.DownloadSource?,
    inferenceCoordinator: com.meshlit.core.inference.InferenceCoordinator,
    context: android.content.Context,
    setDownloads: (Map<String, DownloadStatus>) -> Unit,
    getDownloads: () -> Map<String, DownloadStatus>,
) {
    setDownloads(getDownloads() + (entry.id to DownloadStatus.Running(0)))
    launch {
        val llm = inferenceCoordinator.runAnywhereEngine()
        val url = source?.url.takeUnless { it.isNullOrBlank() } ?: entry.primaryUrl
        val displayName = buildString {
            append(entry.displayName)
            if (source != null) append(" · ${source.org}/${source.quant.name}")
        }
        runCatching {
            llm.downloadModelById(entry.id, url, displayName).collect { progress ->
                val pct = (progress.progress * 100f).toInt().coerceIn(0, 100)
                setDownloads(getDownloads() + (entry.id to DownloadStatus.Running(pct)))
                if (progress.error != null) {
                    throw IllegalStateException(progress.error)
                }
            }
        }.onSuccess {
            setDownloads(getDownloads() + (entry.id to DownloadStatus.Loaded))
            val intent = buildLoadModelIntent(context, "runanywhere:${entry.id}")
            runCatching { context.startService(intent) }
        }.onFailure { t ->
            setDownloads(
                getDownloads() + (
                    entry.id to DownloadStatus.Failed(
                        t.message ?: t.javaClass.simpleName,
                    )
                    ),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/** Map the engine's tone enum onto the brand pill enum. */
private fun RunAnywhereCatalogEngine.Badge.Tone.toPillTone(): RaPillTone = when (this) {
    RunAnywhereCatalogEngine.Badge.Tone.INFO -> RaPillTone.NEUTRAL
    RunAnywhereCatalogEngine.Badge.Tone.SUCCESS -> RaPillTone.ACTIVE
    RunAnywhereCatalogEngine.Badge.Tone.WARN -> RaPillTone.TOP_PICK
    RunAnywhereCatalogEngine.Badge.Tone.ERROR -> RaPillTone.ERROR
    RunAnywhereCatalogEngine.Badge.Tone.ACCENT -> RaPillTone.MOE
}

/** "1.91 GB" / "514 MB" — pick the right unit. */
private fun formatSizeMb(mb: Long): String = when {
    mb >= 1024 -> "%.2f GB".format(mb / 1024.0)
    else -> "$mb MB"
}

/**
 * Small colored chip rendered for each entry in [Entry.badges()].
 *
 * Tone → container color:
 *  - INFO    → secondaryContainer / onSecondaryContainer
 *  - SUCCESS → tertiaryContainer  / onTertiaryContainer
 *  - WARN    → warm amber (fixed) / onTertiaryContainer
 *  - ERROR   → errorContainer     / onErrorContainer
 *  - ACCENT  → primaryContainer   / onPrimaryContainer
 *
 * Kept as a tiny `Surface` instead of an `AssistChip` so the row
 * doesn't carry an extra click handler and so the look is stable
 * across Material 3 versions.
 */
@Composable
private fun CatalogBadge(
    label: String,
    tone: RunAnywhereCatalogEngine.Badge.Tone,
) {
    val cs = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        RunAnywhereCatalogEngine.Badge.Tone.INFO -> cs.secondaryContainer to cs.onSecondaryContainer
        RunAnywhereCatalogEngine.Badge.Tone.SUCCESS -> cs.tertiaryContainer to cs.onTertiaryContainer
        RunAnywhereCatalogEngine.Badge.Tone.WARN -> Color(0xFFFFE0B2) to Color(0xFF6D4C41)
        RunAnywhereCatalogEngine.Badge.Tone.ERROR -> cs.errorContainer to cs.onErrorContainer
        RunAnywhereCatalogEngine.Badge.Tone.ACCENT -> cs.primaryContainer to cs.onPrimaryContainer
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * Per-row download state — mirrors the pattern used by the
 * existing Models screen so we can swap the row UI later without
 * re-plumbing status logic.
 */
private sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Running(val percent: Int) : DownloadStatus
    data object Loaded : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}
