package com.meshlit.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereVisionEngine
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Phase 2.x — Vision screen. Picks an image via
 * `ActivityResultContracts.PickVisualMedia`, lets the user author a
 * prompt, then drives the SDK's VLM (`processImageStream`) and
 * surfaces the streaming tokens.
 *
 * **Backend reality**: the current SDK 0.20.12 doesn't ship the
 * VLM native AAR on the classpath — `RAVLMImage` is declared but
 * the `NoClassDefFoundError` fires the first time the call is
 * dispatched. The engine catches that and surfaces a
 * [RunAnywhereVisionEngine.VisionStreamView.BackendMissing] event;
 * the screen renders a friendly "not yet shipped" card in that
 * case so the user knows the UI is ready and we're just waiting
 * on the AAR.
 */
@Composable
fun VisionScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val engine = app.visionEngine
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var prompt by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var promptTokens by remember { mutableStateOf(0) }
    var completionTokens by remember { mutableStateOf(0) }
    var ttftMs by remember { mutableStateOf(0L) }
    var totalMs by remember { mutableStateOf(0L) }
    var backendMissing by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }

    var runJob by remember { mutableStateOf<Job?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val picked = uri
        imageUri = picked
        if (picked == null) return@rememberLauncherForActivityResult
        // Read the bytes off the main thread — the URI may live in
        // a content provider that's slow on cold launch.
        scope.launch {
            runCatching {
                context.contentResolver.openInputStream(picked).use { it?.readBytes() }
            }.onSuccess { bytes ->
                imageBytes = bytes
                statusMessage = null
            }.onFailure { t ->
                statusMessage = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun run() {
        val bytes = imageBytes
        if (bytes == null || bytes.isEmpty()) {
            statusMessage = "Pick an image first"
            return
        }
        if (prompt.isBlank()) {
            statusMessage = "Type a prompt first"
            return
        }
        if (running) return
        running = true
        caption = ""
        promptTokens = 0
        completionTokens = 0
        ttftMs = 0
        totalMs = 0
        backendMissing = false
        statusMessage = null
        runJob?.cancel()
        runJob = scope.launch {
            engine.processImage(bytes, prompt).collect { event ->
                when (event) {
                    is RunAnywhereVisionEngine.VisionStreamView.Started -> { /* nothing */ }
                    is RunAnywhereVisionEngine.VisionStreamView.ImageEncoded -> { /* nothing */ }
                    is RunAnywhereVisionEngine.VisionStreamView.Token ->
                        caption += event.text
                    is RunAnywhereVisionEngine.VisionStreamView.Done -> {
                        caption = event.text
                        promptTokens = event.promptTokens
                        completionTokens = event.completionTokens
                        ttftMs = event.timeToFirstTokenMs
                        totalMs = event.totalDurationMs
                    }
                    is RunAnywhereVisionEngine.VisionStreamView.Failed ->
                        statusMessage = context.getString(R.string.vision_failed) +
                            ": " + event.message
                    is RunAnywhereVisionEngine.VisionStreamView.BackendMissing -> {
                        backendMissing = true
                        statusMessage = null
                    }
                }
            }
            running = false
        }
    }

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.vision_title),
                subtitle = stringResource(R.string.vision_subtitle),
                tier = app.capabilityTier,
                active = running,
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
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.vision_pick))
                    }
                    Button(
                        onClick = ::run,
                        enabled = !running && imageBytes != null,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.vision_run))
                    }
                }

                if (imageUri != null && imageBytes != null) {
                    val bitmap = remember(imageBytes) {
                        android.graphics.BitmapFactory.decodeByteArray(
                            imageBytes, 0, imageBytes!!.size,
                        )
                    }
                    bitmap?.let { bmp ->
                        Card {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                if (running) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.vision_loading) +
                                    " / " + stringResource(R.string.vision_streaming),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.vision_prompt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )

                if (backendMissing) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.vision_backend_missing),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                if (caption.isNotBlank()) {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.vision_section_result),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.vision_section_meta),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "prompt=$promptTokens · " +
                                    "completion=$completionTokens",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "ttft=${ttftMs} ms · " +
                                    "total=${totalMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}