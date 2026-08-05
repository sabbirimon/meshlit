package com.meshlit.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereCatalogEngine
import com.meshlit.core.inference.RunAnywhereInferenceEngine
import com.meshlit.inference.buildLoadModelIntent
import com.meshlit.ui.components.MeshlitHeader
import com.meshlit.ui.components.RaGetButton
import com.meshlit.ui.components.RaListCard
import com.meshlit.ui.components.RaPillChip
import com.meshlit.ui.components.RaPillTone
import kotlinx.coroutines.launch

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
fun CatalogScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val engine = app.catalogEngine
    val scope = rememberCoroutineScope()

    val entries by engine.entries.collectAsState()
    val live by engine.live.collectAsState()

    var query by remember { mutableStateOf("") }
    var downloads by remember { mutableStateOf<Map<String, DownloadStatus>>(emptyMap()) }
    var refreshInFlight by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }

    // Initial fetch — fire once when the screen mounts. If the
    // user pulled-to-refresh we re-fire from the button.
    LaunchedEffect(Unit) {
        engine.refresh()
    }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { entry ->
            entry.displayName.contains(query, ignoreCase = true) ||
                entry.family.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.catalog_title),
                subtitle = stringResource(R.string.catalog_subtitle),
                tier = app.capabilityTier,
                active = refreshInFlight,
                onOpenDrawer = onOpenDrawer,
            )
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
                                    downloads = downloads + (entry.id to DownloadStatus.Running(0))
                                    scope.launch {
                                        val llm = app.inferenceCoordinator.runAnywhereEngine()
                                        runCatching {
                                            llm.downloadModelById(entry.id).collect { progress ->
                                                val pct = (progress.progress * 100f).toInt()
                                                    .coerceIn(0, 100)
                                                downloads = downloads + (
                                                    entry.id to DownloadStatus.Running(pct)
                                                    )
                                                if (progress.error != null) {
                                                    throw IllegalStateException(progress.error)
                                                }
                                            }
                                        }.onSuccess {
                                            downloads = downloads + (
                                                entry.id to DownloadStatus.Loaded
                                                )
                                            // Auto-load into the FGS so the user
                                            // can hit Jobs → Run right after.
                                            val intent = buildLoadModelIntent(
                                                context,
                                                "runanywhere:${entry.id}",
                                            )
                                            runCatching { context.startService(intent) }
                                        }.onFailure { t ->
                                            downloads = downloads + (
                                                entry.id to DownloadStatus.Failed(
                                                    t.message ?: t.javaClass.simpleName,
                                                )
                                                )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(
    entry: RunAnywhereCatalogEngine.Entry,
    status: DownloadStatus,
    onGet: () -> Unit,
) {
    val subtitle = "${formatSizeMb(entry.approxSizeMb)} · ${entry.family}"
    val isTopPick = entry.bundled || entry.sizeClass == RunAnywhereCatalogEngine.SizeClass.SMALL
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
        },
        trailing = {
            when (status) {
                is DownloadStatus.Idle -> RaGetButton(onClick = onGet, label = "Get")
                is DownloadStatus.Running -> OutlinedButton(onClick = {}) { Text("${status.percent}%") }
                is DownloadStatus.Loaded -> RaPillChip(text = "Loaded", tone = RaPillTone.ACTIVE)
                is DownloadStatus.Failed -> RaGetButton(onClick = onGet, label = "Retry")
            }
        },
        onClick = if (status is DownloadStatus.Idle || status is DownloadStatus.Failed) onGet else null,
    )
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
