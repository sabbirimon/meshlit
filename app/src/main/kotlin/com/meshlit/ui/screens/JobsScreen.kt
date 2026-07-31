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
import com.meshlit.R
import com.meshlit.inference.InferenceForegroundService
import com.meshlit.inference.buildCancelIntent
import com.meshlit.inference.buildInferIntent
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

    val coordinatorState = binder.value?.coordinator()?.state?.collectAsState()
    val coordinator = binder.value?.coordinator()

    // Subscribe to events for streaming tokens.
    LaunchedEffect(coordinator) {
        val coord = coordinator ?: return@LaunchedEffect
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
                state = coordinatorState?.value,
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
                                context.startService(buildInferIntent(context, p))
                                prompt = ""
                            }
                        },
                        enabled = prompt.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.jobs_send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { context.startService(buildCancelIntent(context)) },
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