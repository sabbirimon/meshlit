package com.meshlit.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.net.InferRequest
import com.meshlit.inference.InferenceDispatchMode
import com.meshlit.inference.InferenceForegroundService
import com.meshlit.inference.RemoteInferenceClient
import com.meshlit.inference.RemoteInferenceClientFactory
import com.meshlit.inference.buildCancelIntent
import com.meshlit.inference.buildInferIntent
import com.meshlit.inference.buildLoadModelIntent
import com.meshlit.inference.defaultRequestHints
import com.meshlit.ui.motion.MeshlitMotion
import com.meshlit.ui.components.IdentityBadge
import com.meshlit.ui.components.IdentityBadgeVariant
import com.meshlit.ui.components.IdentityResolver
import com.meshlit.ui.components.LlmOutputActions
import kotlinx.coroutines.launch

/**
 * Phase 1 prompt UI. Lives on the Jobs tab.
 *
 * Layout:
 *  - Top: a status card showing the active model + engine tag
 *  - Middle: the prompt input field (single line) + Send/Stop buttons
 *  - Bottom: a streaming output panel that shows the model's reply
 *    as it generates token-by-token. Each completed reply is also
 *    appended to the conversation history above.
 *
 * Backend:
 *  - On first launch the screen binds to [InferenceForegroundService]
 *    and starts it. The service hosts the coordinator.
 *  - Sending a prompt dispatches an [ACTION_INFER] intent to the
 *    service. Tokens stream via the service binder's `events()` flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onOpenDrawer: () -> Unit = {},
    onOpenModels: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as com.meshlit.MeshlitApplication }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var prompt by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<PromptExchange>() }
    val currentReply = remember { mutableStateOf<PromptExchange?>(null) }

    // Dispatch mode + peer IP. Phase 1: simple Local/Remote radio pair.
    // Phase 2 will replace the IP field with a node picker once
    // discovery is wired.
    var dispatchMode by remember { mutableStateOf(InferenceDispatchMode.LOCAL) }
    var remoteIp by remember { mutableStateOf("") }
    // Resolved cluster peer label (e.g. "node-abc:8080") — populated
    // by the cluster-dispatch branch so the identity badge can show
    // which peer the current prompt is being routed to. Empty
    // when cluster mode hasn't resolved a peer yet.
    var resolvedClusterPeer by remember { mutableStateOf("") }

    // Stable factory used by remote dispatches. Owned by the app
    // singleton so we share one HttpClient across the app.
    val remoteFactory = remember {
        // MeshlitApplication holds the factory long-term; we add a
        // tiny throwaway here for Remote-mode Send calls so the FGS
        // factory stays inside its lifecycle. Each Send call uses
        // its own factory — the HttpClient is short-lived for a
        // single request. Phase 2 moves this back to the FGS-owned
        // factory once cluster nodes use it constantly.
        RemoteInferenceClientFactory()
    }
    DisposableEffect(Unit) {
        onDispose { remoteFactory.close() }
    }

    // Bind to the foreground service. The binder gives us the coordinator.
    val binder = remember { mutableStateOf<InferenceForegroundService.LocalBinder?>(null) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder.value = service as? InferenceForegroundService.LocalBinder
                // Auto-start the service once we bind successfully.
                scope.launch {
                    try {
                        InferenceForegroundService.startForInference(context)
                    } catch (t: Throwable) {
                        // startForegroundService may throw on some OEMs without notification perm.
                    }
                }
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                binder.value = null
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, InferenceForegroundService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { runCatching { context.unbindService(connection) } }
    }

    // Read the binder's coordinator *reactively*: `binder.value?.coordinator()`
    // returns null on first composition (the bind hasn't completed), so the
    // state/event collectors must re-evaluate whenever `binder.value` flips.
    //
    // Bug history: prior version captured `coordinator` and `coordinatorState`
    // once at composition time, leaving the UI stuck on "No model loaded"
    // forever — even after the FGS bound successfully. The fix is to read the
    // coordinator inside `LaunchedEffect(binder.value)` so the effect re-runs
    // on every binder change, and to re-key the StateFlow collector on the
    // same value so it re-subscribes.
    val binderValue = binder.value
    val coordinator = binderValue?.coordinator()

    // State<CoordinatorState?>. collectAsState(initial) is the standard
    // Compose pattern for StateFlow. We pass `coordinator` as the key so
    // the collector is rebuilt when the binder resolves.
    val coordinatorState: com.meshlit.core.inference.CoordinatorState? =
        coordinator?.state?.collectAsState(initial = com.meshlit.core.inference.CoordinatorState.Idle)?.value

    // Subscribe to events for streaming tokens.
    LaunchedEffect(binderValue) {
        val coord = binderValue?.coordinator() ?: return@LaunchedEffect
        coord.events.collect { event ->
            when (event) {
                is com.meshlit.core.inference.InferenceEvent.GenerationStarted -> {
                    currentReply.value = PromptExchange(
                        prompt = event.prompt,
                        reply = "",
                        finished = false,
                    )
                }
                is com.meshlit.core.inference.InferenceEvent.GenerationFinished -> {
                    val cur = currentReply.value
                    if (cur != null) {
                        val final = when (val r = event.result) {
                            is com.meshlit.core.common.MeshlitResult.Success -> cur.copy(
                                reply = r.value.finalText,
                                finished = true,
                            )
                            is com.meshlit.core.common.MeshlitResult.Failure -> cur.copy(
                                reply = "[error: ${r.error.tag}]",
                                finished = true,
                            )
                        }
                        history.add(final)
                        currentReply.value = null
                    }
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            com.meshlit.ui.components.MeshlitHeader(
                title = stringResource(R.string.screen_jobs),
                subtitle = if (currentReply.value != null) "generating…" else null,
                tier = (context.applicationContext as com.meshlit.MeshlitApplication).capabilityTier,
                active = currentReply.value != null,
                onOpenDrawer = onOpenDrawer,
                trailing = {
                    // Dispatch picker — Local / Remote / Cluster.
                    // Anchored in the top bar so the chat surface
                    // above the input stays unencumbered. The active
                    // option is filled with the tier accent; the
                    // other two are ghost icons.
                    DispatchPicker(
                        mode = dispatchMode,
                        onChange = { dispatchMode = it },
                        enabled = currentReply.value == null,
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
            // No-engine banner — only visible when no real runtime
            // is loaded. Tells the user up front that prompts
            // can't be answered, so the input field below doesn't
            // look like a broken model.
            val isNoEngine = remember { app.inferenceCoordinator.engineTag == "none" }
            AnimatedVisibility(
                visible = isNoEngine,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.jobs_no_engine_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Compact toolbar — model picker (icon only) + Start/Stop
            // (icon only) + status pill. The previous implementation
            // carved a full-width card row that swallowed ~30% of
            // the screen; this version is a single-line strip so the
            // chat surface dominates.
            CompactToolbar(
                app = app,
                state = coordinatorState,
                dispatchMode = dispatchMode,
                peerLabel = when (dispatchMode) {
                    InferenceDispatchMode.LOCAL -> ""
                    InferenceDispatchMode.REMOTE -> remoteIp
                    // Cluster peer label is set when the user
                    // picks cluster mode and the dispatch
                    // resolves a peer; before that it's blank.
                    InferenceDispatchMode.CLUSTER -> resolvedClusterPeer
                },
                onLoadModel = { path ->
                    context.startService(buildLoadModelIntent(context, path))
                },
                onDownloadStarterModel = {
                    scope.launch {
                        runCatching {
                            downloadRunAnywhereStarterModel(app, context)
                        }
                    }
                },
                onStart = {
                    runCatching {
                        InferenceForegroundService.startForInference(context)
                    }
                },
                onStop = {
                    InferenceForegroundService.stop(context)
                },
                onOpenModels = onOpenModels,
                onRefresh = {
                    scope.launch {
                        runCatching {
                            InferenceForegroundService.startForInference(context)
                        }
                    }
                },
            )

            HorizontalDivider()

            // Conversation history + current reply. The chat surface
            // now owns the entire vertical real-estate between the
            // toolbar strip and the input row.
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (history.isEmpty() && currentReply.value == null) {
                    item { EmptyState() }
                }
                items(history) { exchange ->
                    ExchangeBubble(exchange = exchange)
                }
                currentReply.value?.let { inProgress ->
                    item {
                        ExchangeBubble(
                            exchange = inProgress,
                            streaming = true,
                        )
                    }
                }
            }

            // ── Input row (compact) ─────────────────────────────────
            // Remote IP field stays below the prompt so the chat
            // surface above stays clean. Tapping the input field
            // collapses the field; the corner button reveals it.
            InputRow(
                prompt = prompt,
                onPromptChange = { prompt = it },
                onSend = {
                    val p = prompt.trim()
                    if (p.isEmpty()) return@InputRow
                    when (dispatchMode) {
                        InferenceDispatchMode.LOCAL -> {
                            context.startService(buildInferIntent(context, p))
                        }
                        InferenceDispatchMode.REMOTE -> {
                            if (remoteIp.isBlank()) return@InputRow
                            currentReply.value = PromptExchange(
                                prompt = p,
                                reply = "",
                                finished = false,
                            )
                            prompt = ""
                            scope.launch {
                                dispatchRemote(p, remoteFactory, remoteIp) { event ->
                                    when (event) {
                                        is RemoteEvent.Token -> {
                                            val cur = currentReply.value ?: return@dispatchRemote
                                            currentReply.value = cur.copy(reply = cur.reply + event.text)
                                        }
                                        is RemoteEvent.Done -> {
                                            val cur = currentReply.value ?: return@dispatchRemote
                                            val final = cur.copy(
                                                reply = if (cur.reply.isNotEmpty()) cur.reply else "[empty reply]",
                                                finished = true,
                                            )
                                            history.add(final)
                                            currentReply.value = null
                                        }
                                        is RemoteEvent.Error -> {
                                            val cur = currentReply.value ?: return@dispatchRemote
                                            history.add(
                                                cur.copy(
                                                    reply = "[error: ${event.tag}]",
                                                    finished = true,
                                                ),
                                            )
                                            currentReply.value = null
                                        }
                                    }
                                }
                            }
                        }
                        InferenceDispatchMode.CLUSTER -> {
                            // Cluster dispatch — picks the first
                            // reachable cluster peer and streams the
                            // prompt through the same RemoteEvent
                            // pipeline as REMOTE. The actual peer
                            // resolution lives in ClusterDispatch —
                            // `firstPeer()` is a suspend DataStore
                            // read, so the whole branch is wrapped
                            // in `scope.launch`.
                            scope.launch {
                                val peer = app.clusterDispatch.firstPeer()
                                if (peer == null) {
                                    resolvedClusterPeer = ""
                                    val empty = PromptExchange(
                                        prompt = p,
                                        reply = "[no cluster peers reachable]",
                                        finished = true,
                                    )
                                    history.add(empty)
                                } else {
                                    resolvedClusterPeer = peer
                                    currentReply.value = PromptExchange(
                                        prompt = p,
                                        reply = "",
                                        finished = false,
                                    )
                                    prompt = ""
                                    dispatchRemote(p, remoteFactory, peer) { event ->
                                        when (event) {
                                            is RemoteEvent.Token -> {
                                                val cur = currentReply.value ?: return@dispatchRemote
                                                currentReply.value = cur.copy(reply = cur.reply + event.text)
                                            }
                                            is RemoteEvent.Done -> {
                                                val cur = currentReply.value ?: return@dispatchRemote
                                                history.add(
                                                    cur.copy(
                                                        reply = cur.reply.ifEmpty { "[empty reply]" },
                                                        finished = true,
                                                    ),
                                                )
                                                currentReply.value = null
                                            }
                                            is RemoteEvent.Error -> {
                                                val cur = currentReply.value ?: return@dispatchRemote
                                                history.add(
                                                    cur.copy(
                                                        reply = "[error: ${event.tag}]",
                                                        finished = true,
                                                    ),
                                                )
                                                currentReply.value = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (dispatchMode == InferenceDispatchMode.LOCAL) {
                        prompt = ""
                    }
                },
                onCancel = {
                    context.startService(buildCancelIntent(context))
                },
                isGenerating = currentReply.value != null,
                isRemoteEnabled = remoteIp.isNotBlank(),
                remoteIp = remoteIp,
                onRemoteIpChange = { remoteIp = it.trim() },
                showRemoteIp = dispatchMode == InferenceDispatchMode.REMOTE,
            )
        }
    }
}

/**
 * Three-segment dispatch picker rendered in the top bar so the
 * chat surface above the input row stays unencumbered. The
 * Local / Remote / Cluster options map to the same
 * [InferenceDispatchMode] enum used by the older full-width
 * radio row.
 */
@Composable
private fun DispatchPicker(
    mode: InferenceDispatchMode,
    onChange: (InferenceDispatchMode) -> Unit,
    enabled: Boolean,
) {
    val options = listOf(
        InferenceDispatchMode.LOCAL to stringResource(R.string.jobs_dispatch_local),
        InferenceDispatchMode.REMOTE to stringResource(R.string.jobs_dispatch_remote),
        InferenceDispatchMode.CLUSTER to stringResource(R.string.jobs_dispatch_cluster),
    )
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp),
            )
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val selected = mode == value
            val accent = com.meshlit.ui.theme.MeshlitAmber
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(enabled = enabled) { onChange(value) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Single-line toolbar that owns the model picker and the
 * Start/Stop control. Replaces the previous full-width card row
 * so the chat surface above gets the full vertical real-estate.
 *
 *  - Left: model picker (icon + selected model name, dropdown on tap)
 *  - Center: status pill (Ready / Loading / Generating / Error)
 *    + IdentityBadge so the user always sees which model is
 *    answering
 *  - Right: Start/Stop icon button
 */
@Composable
private fun CompactToolbar(
    app: MeshlitApplication,
    state: com.meshlit.core.inference.CoordinatorState?,
    dispatchMode: InferenceDispatchMode,
    peerLabel: String,
    onLoadModel: (String) -> Unit,
    onDownloadStarterModel: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenModels: () -> Unit,
    onRefresh: () -> Unit,
) {
    val identity = remember(state, dispatchMode, peerLabel) {
        IdentityResolver(app).resolve(dispatchMode, peerLabel, app.inferenceCoordinator)
    }
    val coroutineScope = rememberCoroutineScope()
    var installStatus by remember { mutableStateOf<InstallStatus>(InstallStatus.Idle) }
    val pickerEntries = remember(installStatus) {
        buildList<Pair<String, String>> {
            app.bundledModelPath()?.let { bundled ->
                add("Bundled · ${bundled.name}" to bundled.absolutePath)
            }
            com.meshlit.models.ModelCatalog.importedFiles(app).forEach { f ->
                add(f.name.replaceAfterLast('.', "gguf").removeSuffix(".gguf") to f.absolutePath)
            }
            val customPath = app.settingsRepository.customModelPathSync()
            if (customPath.isNotBlank() && none { it.second == customPath }) {
                val file = java.io.File(customPath)
                if (file.exists()) {
                    add(file.name to customPath)
                }
            }
        }
    }
    val isRunning = state is com.meshlit.core.inference.CoordinatorState.Loading ||
        state is com.meshlit.core.inference.CoordinatorState.Generating
    val isLive = state is com.meshlit.core.inference.CoordinatorState.Ready ||
        state is com.meshlit.core.inference.CoordinatorState.Generating
    var menuOpen by remember { mutableStateOf(false) }

    fun attemptExtract() {
        if (installStatus is InstallStatus.Running) return
        installStatus = InstallStatus.Running
        coroutineScope.launch {
            val installed = runCatching {
                app.bundledModelInstaller.ensureInstalled(app, onProgress = null)
            }.getOrNull()
            installStatus = if (installed != null && installed.exists()) {
                app.setBundledModelPath(installed)
                InstallStatus.Done(installed.absolutePath)
            } else {
                InstallStatus.Failed
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            CompactModelPicker(
                pickerEntries = pickerEntries,
                isReady = pickerEntries.isNotEmpty(),
                isRunning = isRunning,
                onTap = {
                    if (pickerEntries.isEmpty()) attemptExtract()
                    menuOpen = true
                },
            )
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                if (pickerEntries.isEmpty()) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                when (installStatus) {
                                    is InstallStatus.Running -> "Installing…"
                                    is InstallStatus.Failed -> "Install failed"
                                    else -> "No models on disk"
                                },
                            )
                        },
                        onClick = { menuOpen = false },
                    )
                }
                pickerEntries.forEach { (label, path) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            menuOpen = false
                            onLoadModel(path)
                        },
                    )
                }
                androidx.compose.material3.HorizontalDivider()
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Download starter model") },
                    onClick = {
                        menuOpen = false
                        onDownloadStarterModel()
                    },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Open Models picker") },
                    onClick = {
                        menuOpen = false
                        onOpenModels()
                    },
                )
            }
        }
        StatusPill(state = state)
        // Identity badge — Meshlit · model · origin. Surfaces
        // the running model + dispatch origin next to the
        // status pill so the user always sees which model is
        // answering and where it's running.
        IdentityBadge(
            identity = identity,
            variant = IdentityBadgeVariant.Toolbar,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(
            // Always clickable. When live → onStop cancels the
            // running inference; when not live → onStart boots
            // the foreground service. The previous
            // `enabled = !isRunning` blocked the Stop button
            // while the model was running, so users couldn't
            // cancel a generation from the toolbar.
            onClick = { if (isLive) onStop() else onStart() },
        ) {
            Icon(
                imageVector = if (isLive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isLive) "Stop" else "Start",
                tint = if (isLive) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CompactModelPicker(
    pickerEntries: List<Pair<String, String>>,
    isReady: Boolean,
    isRunning: Boolean,
    onTap: () -> Unit,
) {
    val label = when {
        pickerEntries.isEmpty() -> "Pick model"
        pickerEntries.size == 1 -> pickerEntries.first().first
        else -> "${pickerEntries.first().first} · ${pickerEntries.size - 1} more"
    }
    val accent = com.meshlit.ui.theme.MeshlitAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .clickable(enabled = !isRunning, onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Memory,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Inline status pill — Ready / Loading / Generating / Error. Replaces
 * the old full-width `StatusCard` so the chat surface dominates.
 */
@Composable
private fun StatusPill(state: com.meshlit.core.inference.CoordinatorState?) {
    val (label, color) = when (state) {
        is com.meshlit.core.inference.CoordinatorState.Ready -> "Ready" to MaterialTheme.colorScheme.primary
        is com.meshlit.core.inference.CoordinatorState.Loading -> "Loading" to MaterialTheme.colorScheme.tertiary
        is com.meshlit.core.inference.CoordinatorState.Starting -> "Starting…" to MaterialTheme.colorScheme.tertiary
        is com.meshlit.core.inference.CoordinatorState.Generating -> "Generating" to MaterialTheme.colorScheme.tertiary
        is com.meshlit.core.inference.CoordinatorState.Error -> "Error" to MaterialTheme.colorScheme.error
        else -> "Idle" to MaterialTheme.colorScheme.outline
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Compact input surface. Replaces the old `Row { OutlinedTextField + IconButton }`
 * with a single chat-style row: rounded background, inline mic + send
 * buttons, collapsible remote-IP field.
 */
@Composable
private fun InputRow(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isGenerating: Boolean,
    isRemoteEnabled: Boolean,
    remoteIp: String,
    onRemoteIpChange: (String) -> Unit,
    showRemoteIp: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showRemoteIp) {
            OutlinedTextField(
                value = remoteIp,
                onValueChange = onRemoteIpChange,
                label = { Text("Remote IP") },
                placeholder = { Text("192.168.1.42:8080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = { Text(stringResource(R.string.jobs_prompt_hint)) },
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            if (isGenerating) {
                IconButton(
                    onClick = onCancel,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.jobs_cancel),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = prompt.isNotBlank() && (isRemoteEnabled || !showRemoteIp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.jobs_send),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** One prompt + reply in the conversation log. */
data class PromptExchange(
    val prompt: String,
    val reply: String,
    val finished: Boolean,
)

@Composable
private fun ExchangeBubble(
    exchange: PromptExchange,
    streaming: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Bubble(role = stringResource(R.string.jobs_role_you), text = exchange.prompt)
        Bubble(role = stringResource(R.string.jobs_role_model), text = exchange.reply, streaming = streaming)
        if (streaming) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        } else {
            // Copy / Save / Share / Export toolbar. Hidden while
            // the reply is still streaming so the toolbar doesn't
            // flash a "Copy" button on every token.
            LlmOutputActions(text = exchange.reply)
        }
    }
}

@Composable
private fun Bubble(role: String, text: String, streaming: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (streaming)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // SelectionContainer around the bubble body so the user
            // can long-press a finished reply and copy a snippet. We
            // skip the wrap while streaming for the same reason as
            // the Agent bubble — the per-token recomposition fights
            // with the selection anchor.
            val body = text.ifEmpty { stringResource(R.string.jobs_thinking) }
            if (streaming) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                SelectionContainer {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                text = stringResource(R.string.jobs_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/**
 * One event surfaced by [dispatchRemote]. The Compose UI maps each
 * variant onto a state mutation of `currentReply` / `history`.
 */
private sealed interface RemoteEvent {
    data class Token(val text: String) : RemoteEvent
    data class Done(val finishReason: String) : RemoteEvent
    data class Error(val tag: String) : RemoteEvent
}

/**
 * Open a remote inference client to [ip] and stream the SSE reply
 * back through [onEvent]. Errors are surfaced as a synthetic
 * `RemoteEvent.Error` so the UI can render a clean failure bubble
 * instead of silently dropping the request.
 */
private suspend fun dispatchRemote(
    prompt: String,
    factory: RemoteInferenceClientFactory,
    ip: String,
    onEvent: suspend (RemoteEvent) -> Unit,
) {
    val client: RemoteInferenceClient = factory.build("http://$ip:8080")
    val req = InferRequest(prompt = prompt, maxTokens = 256)
    val result = client.streamInfer(
        request = req,
        hints = defaultRequestHints(),
        onToken = { ev -> onEvent(RemoteEvent.Token(ev.text)) },
        onDone = { ev -> onEvent(RemoteEvent.Done(ev.finishReason)) },
        onError = { ev -> onEvent(RemoteEvent.Error(ev.tag)) },
    )
    if (result is MeshlitResult.Failure) {
        onEvent(RemoteEvent.Error(result.error.tag))
    }
}

/**
 * Phase 2.x — kick off a streaming model download via the
 * RunAnywhere SDK, then auto-load the result into the foreground
 * service so the user goes from "I want a model" → "the model is
 * answering" without leaving the Jobs screen.
 *
 * Failure modes:
 *  - No connectivity → toast the user, no download attempt.
 *  - SDK not initialised → toast the user, point them at the
 *    Application.onCreate log line (would indicate a build glitch
 *    since the SDK is initialised at app start).
 *  - SDK download errored → toast the SDK's error tag.
 *
 * On success we hand the loaded file path to the FGS via
 * [buildLoadModelIntent]; from there the existing coordinator
 * routes through `engineFor(.gguf)` which now picks
 * [com.meshlit.core.inference.RunAnywhereInferenceEngine] first.
 */
private suspend fun downloadRunAnywhereStarterModel(
    app: MeshlitApplication,
    context: Context,
) {
    // Confirm we have a coordinator we can dispatch the load to.
    // Without an FGS connection the download would still succeed
    // but the model would have nowhere to land — useless for the
    // user. Start the FGS first.
    runCatching { InferenceForegroundService.startForInference(context) }

    val engine = app.inferenceCoordinator.runAnywhereEngine()
    if (!engine.isReady()) {
        // Initialise is idempotent. Re-running here covers the
        // edge case where the Application's onCreate hook didn't
        // run yet (rare, but observed on some custom ROMs that
        // mount the application class lazily).
        engine.initialize(app)
    }
    try {
        engine.downloadModelById(
            com.meshlit.core.inference.RunAnywhereInferenceEngine.DEFAULT_MODEL_ID,
        ).collect { progress ->
            // We surface progress through the existing log buffer
            // rather than a Toast because the user is on the Jobs
            // screen, not the Models screen — Toast would be
            // disruptive. The Logs screen will show the same
            // progress events for anyone debugging a stuck
            // download.
            app.logBuffer.info(
                tag = "JobsScreen.RunAnywhere",
                message = "Downloading ${progress.modelId}: ${(progress.progress * 100).toInt()}% " +
                    "(${progress.bytesDownloaded}/${if (progress.totalBytes > 0) progress.totalBytes else "?"} bytes, " +
                    "state=${progress.state})",
            )
            if (progress.error != null) {
                app.logBuffer.warn(
                    tag = "JobsScreen.RunAnywhere",
                    message = "Download error: ${progress.error}",
                )
                return@collect
            }
        }
    } catch (t: Throwable) {
        app.logBuffer.warn(
            tag = "JobsScreen.RunAnywhere",
            message = "Download failed: ${t.message ?: t.javaClass.simpleName}",
        )
        return
    }
    // Once the SDK has the bytes, ask the SDK where it landed and
    // dispatch a load. The SDK's storage layout is opaque to us
    // (we don't depend on its filesystem conventions), so the
    // cleanest path is to hand the engine the model id and let
    // the coordinator's normal GGUF-load path run via the FGS.
    // The RunAnywhere SDK puts the file under
    // `context.filesDir/runanywhere/models/<id>.gguf` on a fresh
    // install; we resolve it via the SDK by id which is what
    // `loadModel` already does.
    val modelId = com.meshlit.core.inference.RunAnywhereInferenceEngine.DEFAULT_MODEL_ID
    val intent = buildLoadModelIntent(context, /* path is opaque to us */ "__runanywhere__:$modelId")
    context.startService(intent)
}

/**
 * Top-of-screen controls: an OutlinedButton ("Model") that opens a
 * dropdown of every available GGUF (bundled + imported + custom
 * override), plus a prominent Meshlit-branded Start/Stop button.
 *
 * No persistence here — picking a model dispatches `buildLoadModelIntent`
 * to the FGS, which is the single source of truth for the active model.
 * The dropdown is rebuilt each time it opens so freshly imported GGUFs
 * show up without a screen-level refresh.
 *
 * If no model is on disk yet (fresh install, installer hasn't run, no
 * imports), the dropdown stays interactive: tapping it triggers
 * `BundledModelInstaller.ensureInstalled()` and shows an "Installing…"
 * hint while extraction is in flight, so the user gets feedback
 * instead of staring at a greyed-out button.
 *
 * Phase 2.x — when both the bundled installer and the catalog
 * come up empty, the dropdown also surfaces a "Download starter
 * model (RunAnywhere)" row. Picking it kicks off a streaming
 * download via the RunAnywhere SDK and auto-loads the model once
 * the bytes are on disk.
 */
private sealed interface InstallStatus {
    data object Idle : InstallStatus
    data object Running : InstallStatus
    data class Done(val absolutePath: String) : InstallStatus
    data object Failed : InstallStatus
}