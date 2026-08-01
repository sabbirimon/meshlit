package com.meshlit.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.meshlit.inference.defaultRequestHints
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
fun JobsScreen() {
    val context = LocalContext.current
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
            TopAppBar(title = { Text(stringResource(R.string.screen_jobs)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                Text(
                    text = state?.let { describeState(it) } ?: stringResource(R.string.jobs_status_idle),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = when (state) {
                        is com.meshlit.core.inference.CoordinatorState.Ready ->
                            state.model.modelName + " · ${state.model.parameterCount / 1_000_000}M params"
                        else -> stringResource(R.string.jobs_engine_stub_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
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
            Text(
                text = text.ifEmpty { stringResource(R.string.jobs_thinking) },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
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