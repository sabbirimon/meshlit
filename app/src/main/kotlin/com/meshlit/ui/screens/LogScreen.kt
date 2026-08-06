package com.meshlit.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.observability.LogSource
import com.meshlit.observability.LogBuffer
import com.meshlit.observability.LogExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase M.4 — In-app log viewer (Task #203 follow-up).
 *
 * Layout:
 *  - Filter row at the top: a free-text query box, level chips,
 *    a tag-substring match row, and a source dropdown that filters
 *    `LogBuffer.Entry.source` (App / Network / Inference / Agent /
 *    System).
 *  - Body: a sticky list of [LogBuffer.Entry] entries with severity
 *    coloring. Newest first. When the user is already at the top
 *    (i.e. they're watching live logs), new entries push the list
 *    without ripping the scroll position; otherwise we leave them
 *    alone so they can read.
 *  - Top-bar actions: clear (wipes the buffer) and an export
 *    dropdown that fires either an "Export as TXT" or
 *    "Export as JSONL" intent, both routed through the shared
 *    [LogExporter].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val scope = rememberCoroutineScope()
    val buffer = remember { app.logBuffer }
    val entries by buffer.entries.collectAsState()

    var query by remember { mutableStateOf("") }
    var minLevel by remember { mutableStateOf(LogBuffer.Level.INFO) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    // null = "All sources". Otherwise the [LogSource] enum value.
    var sourceFilter by remember { mutableStateOf<LogSource?>(null) }

    val filtered = remember(entries, query, minLevel, tagFilter, sourceFilter) {
        filterEntries(entries, query, minLevel, tagFilter, sourceFilter)
    }

    val listState = rememberLazyListState()
    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 8 } }
    LaunchedEffect(filtered.size, isAtTop) {
        if (isAtTop && filtered.isNotEmpty()) {
            // Push the new entry into view without ripping position.
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { buffer.clear() }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.logs_clear))
                    }
                    ExportMenu(
                        onExportTxt = {
                            scope.launch { exportLogs(context, filtered, LogExporter.Format.TXT) }
                        },
                        onExportJsonl = {
                            scope.launch { exportLogs(context, filtered, LogExporter.Format.JSONL) }
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FilterBar(
                query = query,
                onQuery = { query = it },
                minLevel = minLevel,
                onLevel = { minLevel = it },
                tagFilter = tagFilter,
                onTag = { tagFilter = it },
                sourceFilter = sourceFilter,
                onSource = { sourceFilter = it },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                if (filtered.isEmpty()) {
                    EmptyLogs()
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(filtered, key = { it.timestampMs.toString() + it.tag + it.message.hashCode() }) { entry ->
                            LogRow(entry)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top-bar overflow menu exposing the two supported export formats.
 * Lives next to the existing Clear / Share icons so the visual
 * rhythm of the action row is preserved.
 */
@Composable
private fun ExportMenu(
    onExportTxt: () -> Unit,
    onExportJsonl: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.logs_export))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logs_export_txt)) },
                onClick = {
                    expanded = false
                    onExportTxt()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.logs_export_jsonl)) },
                onClick = {
                    expanded = false
                    onExportJsonl()
                },
            )
        }
    }
}

/**
 * Source-filter dropdown. Renders as a clickable pill showing the
 * current selection ("All" / "App" / "Network" / …) and pops a
 * [DropdownMenu] with the [LogSource] enum values when tapped.
 *
 * Filter logic lives in [filterEntries]; this composable only owns
 * presentation and the lifted `sourceFilter` state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDropdown(
    selected: LogSource?,
    onSelect: (LogSource?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { LogSource.label(it) } ?: LogSource.ALL_FILTER

    Box {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (selected == null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            },
            modifier = Modifier
                .padding(vertical = 2.dp),
            onClick = { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.logs_filter_source_prefix) + ": " + label,
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(LogSource.ALL_FILTER) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            LogSource.entries.forEach { src ->
                DropdownMenuItem(
                    text = { Text(LogSource.label(src)) },
                    onClick = {
                        expanded = false
                        onSelect(src)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterBar(
    query: String,
    onQuery: (String) -> Unit,
    minLevel: LogBuffer.Level,
    onLevel: (LogBuffer.Level) -> Unit,
    tagFilter: String?,
    onTag: (String?) -> Unit,
    sourceFilter: LogSource?,
    onSource: (LogSource?) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.logs_filter_query_label)) },
            placeholder = { Text(stringResource(R.string.logs_filter_query_hint)) },
            leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogBuffer.Level.entries.forEach { lvl ->
                FilterChip(
                    selected = minLevel == lvl,
                    onClick = { onLevel(lvl) },
                    label = { Text(lvl.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    ),
                )
            }
            // Source dropdown sits at the end of the level row so
            // it's reachable with the same horizontal scroll.
            SourceDropdown(selected = sourceFilter, onSelect = onSource)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tagFilter == null,
                onClick = { onTag(null) },
                label = { Text(stringResource(R.string.logs_filter_tag_any)) },
            )
            listOf(
                "InferenceHttpServer",
                "InferenceForegroundService",
                "MiniRouter",
                "PeerHealthCache",
                "MetricsRegistry",
                "BundledModelInstaller",
            ).forEach { tag ->
                FilterChip(
                    selected = tagFilter == tag,
                    onClick = { onTag(tag) },
                    label = { Text(tag) },
                )
            }
        }
    }
}

@Composable
private fun EmptyLogs() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogRow(entry: LogBuffer.Entry) {
    val accent = when (entry.level) {
        LogBuffer.Level.DEBUG -> MaterialTheme.colorScheme.outline
        LogBuffer.Level.INFO -> MaterialTheme.colorScheme.primary
        LogBuffer.Level.WARN -> MaterialTheme.colorScheme.tertiary
        LogBuffer.Level.ERROR -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(color = accent, shape = RoundedCornerShape(2.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${entry.level.name} · ${entry.source.name} · ${entry.tag}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (entry.context.isNotEmpty()) {
                    Text(
                        text = entry.context.entries.joinToString(", ") { "${it.key}=${it.value}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private fun filterEntries(
    entries: List<LogBuffer.Entry>,
    query: String,
    minLevel: LogBuffer.Level,
    tagFilter: String?,
    sourceFilter: LogSource?,
): List<LogBuffer.Entry> {
    val q = query.trim().lowercase()
    val levelRank = { it: LogBuffer.Level ->
        when (it) {
            LogBuffer.Level.DEBUG -> 0
            LogBuffer.Level.INFO -> 1
            LogBuffer.Level.WARN -> 2
            LogBuffer.Level.ERROR -> 3
        }
    }
    val minRank = levelRank(minLevel)
    return entries.filter { e ->
        if (levelRank(e.level) < minRank) return@filter false
        if (tagFilter != null && e.tag != tagFilter) return@filter false
        if (sourceFilter != null && e.source != sourceFilter) return@filter false
        if (q.isNotEmpty() &&
            !e.message.lowercase().contains(q) &&
            !e.tag.lowercase().contains(q)
        ) {
            return@filter false
        }
        true
    }.asReversed() // newest first
}

private suspend fun exportLogs(
    context: android.content.Context,
    entries: List<LogBuffer.Entry>,
    format: LogExporter.Format,
) {
    val app = context.applicationContext as MeshlitApplication
    val outFile = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "logs")
        LogExporter.export(
            entries = entries,
            outFile = LogExporter.newOutputFile(dir, format),
            format = format,
        )
    }
    val share = Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType
        putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            outFile,
        ))
        putExtra(Intent.EXTRA_SUBJECT, outFile.name)
    }
    val chooser = Intent.createChooser(share, "Meshlit log export")
    chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.startActivity(chooser)
}
