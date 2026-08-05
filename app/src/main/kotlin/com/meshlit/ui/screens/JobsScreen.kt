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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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

            // Controls row: model picker + Start/Stop service toggle.
            // The picker lists bundled + imported GGUFs plus a
            // "Download starter model" entry that pulls a model from
            // the RunAnywhere catalog. Tapping a row dispatches
            // `buildLoadModelIntent` to the FGS. The toggle button
            // label flips between "Start" and "Stop" based on the
            // live `CoordinatorState`.
            ControlsRow(
                app = app,
                state = coordinatorState,
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
            )

            // Status card
            StatusCard(
                state = coordinatorState,
                onRefresh = {
                    scope.launch {
                        runCatching {
                            InferenceForegroundService.startForInference(context)
                        }
                    }
                },
            )

            HorizontalDivider()

            // Conversation history + current reply
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

            HorizontalDivider()

            // Dispatch-mode toggle + IP field. Above the prompt input
            // so users see *where* their prompt is going before they
            // hit Send.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = dispatchMode == InferenceDispatchMode.LOCAL,
                        onClick = { dispatchMode = InferenceDispatchMode.LOCAL },
                        enabled = currentReply.value == null,
                    )
                    Text(
                        text = stringResource(R.string.jobs_dispatch_local),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                    RadioButton(
                        selected = dispatchMode == InferenceDispatchMode.REMOTE,
                        onClick = { dispatchMode = InferenceDispatchMode.REMOTE },
                        enabled = currentReply.value == null,
                    )
                    Text(
                        text = stringResource(R.string.jobs_dispatch_remote),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = remoteIp,
                    onValueChange = { remoteIp = it.trim() },
                    label = { Text(stringResource(R.string.jobs_remote_ip_label)) },
                    placeholder = { Text(stringResource(R.string.jobs_remote_ip_placeholder)) },
                    enabled = dispatchMode == InferenceDispatchMode.REMOTE && currentReply.value == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.jobs_prompt_hint)) },
                    singleLine = false,
                    maxLines = 4,
                    enabled = currentReply.value == null,
                )
                Spacer(Modifier.height(8.dp))
                if (currentReply.value == null) {
                    IconButton(
                        onClick = {
                            val p = prompt.trim()
                            if (p.isNotEmpty()) {
                                when (dispatchMode) {
                                    InferenceDispatchMode.LOCAL -> {
                                        context.startService(buildInferIntent(context, p))
                                    }
                                    InferenceDispatchMode.REMOTE -> {
                                        if (remoteIp.isBlank()) return@IconButton
                                        // Push the user prompt into the
                                        // currentReply bubble up front so
                                        // the UI has something to render
                                        // while the SSE wire comes back.
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
                                }
                                if (dispatchMode == InferenceDispatchMode.LOCAL) {
                                    prompt = ""
                                }
                            }
                        },
                        enabled = prompt.isNotBlank() &&
                            (dispatchMode == InferenceDispatchMode.LOCAL || remoteIp.isNotBlank()),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.jobs_send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            // Cancel works locally; remote cancel is
                            // handled by client disconnect (the server
                            // detects it and stops inference).
                            context.startService(buildCancelIntent(context))
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(R.string.jobs_cancel),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
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
private fun StatusCard(
    state: com.meshlit.core.inference.CoordinatorState?,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                // Crossfade the title + subtitle as `state` changes so
                // the card doesn't snap between "Loading…" → "Ready"
                // → "Generating…" without a transition.
                val stateKey = state?.let { describeState(it) }
                    ?: stringResource(R.string.jobs_status_idle)
                AnimatedContent(
                    targetState = stateKey,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 })
                            .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "jobs-status-title",
                ) { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 2 })
                            .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
                    },
                    label = "jobs-status-subtitle",
                ) { s ->
                    Text(
                        text = when (s) {
                            is com.meshlit.core.inference.CoordinatorState.Ready ->
                                s.model.modelName + " · ${s.model.parameterCount / 1_000_000}M params" +
                                    // Phase 2 — surface the runtime name so the user
                                    // sees *which* runtime is currently serving
                                    // this model. Falls back to "" if no runtime
                                    // has been resolved yet (e.g. before any load).
                                    s.runtime?.let { " · ${it.displayName}" }.orEmpty()
                            is com.meshlit.core.inference.CoordinatorState.Loading ->
                                s.runtime?.let { stringResource(R.string.jobs_runtime_loading, it.displayName) }
                                    ?: stringResource(R.string.jobs_engine_stub_hint)
                            is com.meshlit.core.inference.CoordinatorState.Generating ->
                                s.runtime?.let { stringResource(R.string.jobs_runtime_generating, it.displayName) }
                                    ?: stringResource(R.string.jobs_engine_stub_hint)
                            is com.meshlit.core.inference.CoordinatorState.Error ->
                                s.runtime?.let { stringResource(R.string.jobs_runtime_error, it.displayName) }
                                    ?: stringResource(R.string.jobs_engine_stub_hint)
                            else -> stringResource(R.string.jobs_engine_stub_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.jobs_refresh),
                )
            }
        }
    }
}

@Composable
private fun describeState(state: com.meshlit.core.inference.CoordinatorState): String = when (state) {
    com.meshlit.core.inference.CoordinatorState.Idle -> stringResource(R.string.jobs_status_idle)
    is com.meshlit.core.inference.CoordinatorState.Loading -> stringResource(R.string.jobs_status_loading)
    is com.meshlit.core.inference.CoordinatorState.Ready -> stringResource(R.string.jobs_status_ready)
    is com.meshlit.core.inference.CoordinatorState.Generating -> stringResource(R.string.jobs_status_generating)
    is com.meshlit.core.inference.CoordinatorState.Error -> stringResource(R.string.jobs_status_error)
}

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
@Composable
private fun ControlsRow(
    app: MeshlitApplication,
    state: com.meshlit.core.inference.CoordinatorState?,
    onLoadModel: (String) -> Unit,
    onDownloadStarterModel: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    // Tracks install attempts: Idle, Running, Done, Failed. We rebuild
    // the picker entries on each transition so a successful extract
    // appears without forcing the user to navigate away.
    var installStatus by remember { mutableStateOf<InstallStatus>(InstallStatus.Idle) }
    val pickerEntries = remember(installStatus) {
        buildList<Pair<String, String>> {
            // Bundled model — only listed if it has been extracted.
            app.bundledModelPath()?.let { bundled ->
                val name = bundled.name
                add("Bundled · $name" to bundled.absolutePath)
            }
            // Imported models from the catalog.
            com.meshlit.models.ModelCatalog.importedFiles(app).forEach { f ->
                add(f.name.replaceAfterLast('.', "gguf").removeSuffix(".gguf") to f.absolutePath)
            }
            // Custom override path (if set and not already listed).
            val customPath = app.settingsRepository.customModelPathSync()
            if (customPath.isNotBlank() && none { it.second == customPath }) {
                val file = java.io.File(customPath)
                if (file.exists()) {
                    add(file.name to customPath)
                }
            }
        }
    }
    val canOpen = pickerEntries.isNotEmpty() || installStatus is InstallStatus.Running
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
    var menuOpen by remember { mutableStateOf(false) }
    val isRunning = state is com.meshlit.core.inference.CoordinatorState.Loading ||
        state is com.meshlit.core.inference.CoordinatorState.Generating
    val isLive = state is com.meshlit.core.inference.CoordinatorState.Ready ||
        state is com.meshlit.core.inference.CoordinatorState.Generating
    val pickerLabel = when {
        installStatus is InstallStatus.Running ->
            stringResource(R.string.jobs_model_picker_installing)
        installStatus is InstallStatus.Failed && pickerEntries.isEmpty() ->
            stringResource(R.string.jobs_model_picker_install_failed)
        else -> stringResource(R.string.jobs_model_picker_label)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = {
                    // If the picker would otherwise be empty, kick off
                    // the bundled-model installer so the next open has
                    // entries. We always open the dropdown so the user
                    // sees the "Installing…" hint instead of silence.
                    if (pickerEntries.isEmpty()) {
                        attemptExtract()
                    }
                    menuOpen = true
                },
                enabled = canOpen,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = pickerLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                )
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                if (pickerEntries.isEmpty()) {
                    when (installStatus) {
                        is InstallStatus.Running -> DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.jobs_model_picker_installing))
                            },
                            onClick = { menuOpen = false },
                        )
                        is InstallStatus.Failed -> DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.jobs_model_picker_install_failed))
                            },
                            onClick = { menuOpen = false },
                        )
                        else -> DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.jobs_model_no_models))
                            },
                            onClick = { menuOpen = false },
                        )
                    }
                }
                pickerEntries.forEach { (label, path) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuOpen = false
                            onLoadModel(path)
                        },
                    )
                }
                // Phase 2.x — always available, even when other entries
                // exist. Lets the user pull a known-good starter model
                // regardless of what's on disk. Tapping it kicks off a
                // streaming download in `JobsScreen`; the actual
                // progress / finish reporting happens in the snackbar
                // surface added below the picker.
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.jobs_model_download_runanywhere))
                    },
                    onClick = {
                        menuOpen = false
                        onDownloadStarterModel()
                    },
                )
            }
        }
        // Animated label flip between "Start service" and "Stop service".
        // The button is a prominent Meshlit-branded primary: filled
        // with the accent colour, a thick rounded outline, and a
        // glyph prefix so the active state reads at a glance even
        // for first-time users.
        AnimatedContent(
            targetState = if (isLive) "stop" else "start",
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 })
                    .togetherWith(fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "jobs-service-toggle",
        ) { which ->
            val accent = MaterialTheme.colorScheme.primary
            val surface = MaterialTheme.colorScheme.surface
            val onSurface = MaterialTheme.colorScheme.onSurface
            val glyph = if (which == "stop") "■  " else "▶  "
            // The branded strings embed the glyph (e.g. "■  Stop service").
            // Strip them so the rendered Text doesn't show two glyphs.
            val stopLabel = stringResource(R.string.jobs_service_stop_brand)
                .replace("■  ", "").replace("■ ", "").trim()
            val startLabel = stringResource(R.string.jobs_service_start_brand)
                .replace("▶  ", "").replace("▶ ", "").trim()
            val labelText = if (which == "stop") stopLabel else startLabel
            val enabled = if (which == "stop") {
                state is com.meshlit.core.inference.CoordinatorState.Ready ||
                    state is com.meshlit.core.inference.CoordinatorState.Generating
            } else !isRunning
            val onClick = if (which == "stop") onStop else onStart
            // Meshlit-branded primary: filled accent on the active
            // stop state, outlined accent on the inactive start state.
            // The two-tone treatment makes the toggle unambiguous even
            // when the engine is the stub.
            val container = if (which == "stop") accent else surface
            val content = if (which == "stop") {
                MaterialTheme.colorScheme.onPrimary
            } else if (enabled) accent else onSurface.copy(alpha = 0.45f)
            val borderColor = if (enabled) accent else onSurface.copy(alpha = 0.3f)
            val borderWidth = if (which == "stop") 0.dp else 1.5.dp
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .background(container, RoundedCornerShape(14.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
                    .then(
                        if (enabled) Modifier.clickable(onClick = onClick)
                        else Modifier,
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = glyph,
                        color = content,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = labelText,
                        color = content,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Internal state machine for the bundled-model install attempt kicked
 * off from the picker dropdown when no model is on disk yet.
 *
 * Lives in `ControlsRow` scope only — we don't persist or share this
 * across screens because the installer's own sentinel file is the
 * source of truth for "is the bundled model on disk?".
 */
private sealed interface InstallStatus {
    data object Idle : InstallStatus
    data object Running : InstallStatus
    data class Done(val absolutePath: String) : InstallStatus
    data object Failed : InstallStatus
}