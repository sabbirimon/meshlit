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
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when (status) {
                    is DownloadStatus.Idle -> Button(onClick = onGet) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.catalog_get))
                    }
                    is DownloadStatus.Running -> {
                        OutlinedButton(onClick = {}) { Text("${status.percent}%") }
                    }
                    is DownloadStatus.Loaded -> {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.catalog_loaded)) },
                        )
                    }
                    is DownloadStatus.Failed -> {
                        Button(onClick = onGet) {
                            Text(stringResource(R.string.catalog_get))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(entry.family) })
                AssistChip(onClick = {}, label = { Text(entry.origin) })
                AssistChip(onClick = {}, label = { Text("${entry.approxSizeMb} MB") })
                AssistChip(onClick = {}, label = { Text(entry.language) })
            }
            if (status is DownloadStatus.Running) {
                LinearProgressIndicator(
                    progress = { status.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (status is DownloadStatus.Failed) {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
