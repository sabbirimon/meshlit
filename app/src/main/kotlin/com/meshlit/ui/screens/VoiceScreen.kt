package com.meshlit.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.permissions.PermissionHelper
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Phase 2.x — Voice screen. Captures speech via the mic, streams it
 * through the SDK's STT (sherpa-onnx), lets the user edit the
 * transcript, and reads the final text back via TTS.
 *
 * Permission flow: asks for `RECORD_AUDIO` on first launch. Without
 * it, the screen renders an inline grant button instead of opening
 * the mic — `AudioRecord.<init>` throws `SecurityException` if the
 * permission isn't held.
 *
 * State machine:
 *  - Idle            — transcript field is empty, mic button shows "start"
 *  - Listening       — capture + STT active, partial transcripts stream in
 *  - Final           — STT produced a final transcript, TTS button enabled
 *  - Synthesizing    — TTS playback in flight
 *
 * VAD activity drives the progress bar above the transcript so the
 * user can see the mic is hearing them. We don't gate STT on VAD —
 * the SDK does that internally if `suppress_blank` is on.
 */
@Composable
fun VoiceScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val engine = app.voiceEngine
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(PermissionHelper.hasMicrophonePermission(context))
    }
    var isListening by remember { mutableStateOf(false) }
    var isSynthesizing by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(0f) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var captureJob by remember { mutableStateOf<Job?>(null) }
    var vadJob by remember { mutableStateOf<Job?>(null) }
    var sttJob by remember { mutableStateOf<Job?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    // Pull current permission status on mount in case the user
    // toggled it in App Settings while we were away. Cheap, single
    // read; no harm if the activity isn't available.
    LaunchedEffect(Unit) {
        (context as? Activity)?.let { activity ->
            hasPermission = PermissionHelper.hasMicrophonePermission(activity)
        }
    }

    // Tear down active flows + reset VAD when the screen leaves.
    DisposableEffect(Unit) {
        onDispose {
            captureJob?.cancel()
            vadJob?.cancel()
            sttJob?.cancel()
            scope.launch { engine.resetVad() }
        }
    }

    fun startListening() {
        if (!hasPermission) {
            (context as? Activity)?.let {
                PermissionHelper.requestMicrophoneIfNeeded(it)
            }
            return
        }
        if (isListening) return
        partialText = ""
        activityLevel = 0f
        statusMessage = null
        isListening = true

        // One `AudioRecord` per listening session, shared between
        // VAD (activity meter) and STT (transcript). Sharing via
        // `shareIn` keeps the device open while both subscribers
        // are alive; both flows are paused when no one's
        // collecting. The capture is torn down on stopListening().
        val shared = engine.startCapture().shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000L),
            replay = 0,
        )

        // VAD drives the activity meter.
        vadJob = scope.launch {
            engine.detectActivity(shared).collect { event ->
                when (event) {
                    is RunAnywhereVoiceEngine.VadEvent.Speech ->
                        activityLevel = event.confidence
                    is RunAnywhereVoiceEngine.VadEvent.Silence ->
                        activityLevel = 0f
                }
            }
        }

        // STT streams partial + final transcripts.
        sttJob = scope.launch {
            engine.transcribe(shared).collect { event ->
                when (event) {
                    is RunAnywhereVoiceEngine.TranscriptEvent.Partial ->
                        partialText = event.text
                    is RunAnywhereVoiceEngine.TranscriptEvent.Final -> {
                        transcript = event.finalText
                        partialText = ""
                    }
                    is RunAnywhereVoiceEngine.TranscriptEvent.Failed -> {
                        statusMessage = context.getString(R.string.voice_stt_failed)
                    }
                }
            }
        }
    }

    fun stopListening() {
        captureJob?.cancel(); captureJob = null
        vadJob?.cancel(); vadJob = null
        sttJob?.cancel(); sttJob = null
        isListening = false
        activityLevel = 0f
        // If we have a partial, promote it to the final transcript
        // so the user doesn't lose the tail.
        if (transcript.isBlank() && partialText.isNotBlank()) {
            transcript = partialText
            partialText = ""
        }
    }

    fun speak() {
        val text = transcript.ifBlank { partialText }
        if (text.isBlank()) return
        isSynthesizing = true
        scope.launch {
            val ok = engine.synthesize(text)
            isSynthesizing = false
            if (!ok) {
                statusMessage = context.getString(R.string.voice_tts_failed)
            }
        }
    }

    fun stop() {
        scope.launch { engine.stop() }
        isSynthesizing = false
    }

    fun clear() {
        transcript = ""
        partialText = ""
        statusMessage = null
    }

    Scaffold(
        topBar = {
            MeshlitHeader(
                title = stringResource(R.string.voice_title),
                subtitle = stringResource(R.string.voice_subtitle),
                tier = app.capabilityTier,
                active = isListening || isSynthesizing,
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
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!hasPermission) {
                    PermissionCard(
                        onGrant = {
                            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenSettings = {
                            (context as? Activity)?.let {
                                val intent = PermissionHelper.openAppSettings(
                                    context,
                                    it.packageName,
                                )
                                runCatching { it.startActivity(intent) }
                            }
                        },
                    )
                } else {
                    MicCard(
                        isListening = isListening,
                        isSynthesizing = isSynthesizing,
                        activityLevel = activityLevel,
                        partialText = partialText,
                        onMicTap = {
                            if (isListening) stopListening() else startListening()
                        },
                        listeningHint = stringResource(R.string.voice_listening),
                        idleHint = stringResource(R.string.voice_idle),
                    )
                }

                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                OutlinedTextField(
                    value = transcript,
                    onValueChange = { transcript = it },
                    label = { Text(stringResource(R.string.voice_text_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = ::speak,
                        enabled = !isSynthesizing &&
                            (transcript.isNotBlank() || partialText.isNotBlank()),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.voice_speak))
                    }
                    if (isSynthesizing) {
                        OutlinedButton(onClick = ::stop) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.voice_stop))
                        }
                    }
                    FilledTonalButton(
                        onClick = ::clear,
                        enabled = transcript.isNotBlank() || partialText.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.voice_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_permission_required),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.voice_permission_grant))
                }
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.voice_permission_settings))
                }
            }
        }
    }
}

@Composable
private fun MicCard(
    isListening: Boolean,
    isSynthesizing: Boolean,
    activityLevel: Float,
    partialText: String,
    onMicTap: () -> Unit,
    listeningHint: String,
    idleHint: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isListening)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (isListening)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(96.dp),
                onClick = onMicTap,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isListening)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                text = if (isListening) listeningHint else idleHint,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isListening) {
                LinearProgressIndicator(
                    progress = { activityLevel.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
