package com.meshlit.ui.screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.core.content.FileProvider
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.inference.RunAnywhereVoiceEngine
import com.meshlit.permissions.PermissionHelper
import com.meshlit.ui.components.MeshlitHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // PCM frame buffer for the most recent recording. Filled while
    // the mic is open; consumed by the Save action to write a WAV
    // to {filesDir}/voice/. The buffer caps at 60 seconds of PCM
    // (16 kHz × 2 B × 60 s = 1.92 MB) so a runaway session can't
    // OOM the app — older frames are dropped.
    val frameBuffer = remember { PcmFrameBuffer(maxSeconds = 60, sampleRate = 16_000) }

    // The path of the last saved recording. Surfaced as a Toast
    // so the user can find the file later (no in-app file picker
    // for audio yet — that's a follow-up).
    var lastSavedPath by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    // Import an audio file (.wav / .mp3 / .m4a / .ogg) from
    // device storage and run it through STT. The file is decoded
    // to 16-kHz mono PCM in-memory, then fed to the engine's
    // transcribe flow. Imports work without RECORD_AUDIO permission
    // because no mic capture happens.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val outcome = importAndTranscribe(
                    context = context,
                    engine = engine,
                    uri = uri,
                )
                outcome.fold(
                    onSuccess = { text ->
                        transcript = text
                        partialText = ""
                    },
                    onFailure = { t ->
                        statusMessage = "Import failed: ${t.message ?: t.javaClass.simpleName}"
                    },
                )
            }
        }
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
        frameBuffer.reset()
        isListening = true

        // One `AudioRecord` per listening session, shared between
        // VAD (activity meter), STT (transcript), and the local
        // recorder buffer. Sharing via `shareIn` keeps the device
        // open while all three subscribers are alive; the flows are
        // paused when no one's collecting. The capture is torn
        // down on stopListening().
        val shared = engine.startCapture().shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 1_000L),
            replay = 0,
        )

        // Local recorder — copies every frame into the in-memory
        // buffer so the user can hit Save and get a WAV file.
        captureJob = scope.launch {
            shared.collect { frame -> frameBuffer.append(frame) }
        }

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

    fun saveRecording() {
        scope.launch {
            val outcome = runCatching {
                val dir = File(context.filesDir, "voice").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val out = File(dir, "recording-$stamp.wav")
                frameBuffer.writeAsWav(out, sampleRate = 16_000, channels = 1, bitsPerSample = 16)
                out
            }
            outcome.fold(
                onSuccess = { file ->
                    lastSavedPath = file.absolutePath
                    Toast.makeText(
                        context,
                        context.getString(R.string.llm_output_saved, file.absolutePath),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFailure = { t ->
                    statusMessage = "Save failed: ${t.message ?: t.javaClass.simpleName}"
                },
            )
        }
    }

    fun shareLastRecording() {
        val path = lastSavedPath ?: run {
            statusMessage = "Nothing to share yet — record first."
            return
        }
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(
                android.content.Intent.createChooser(intent, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun importAudio() {
        importLauncher.launch(arrayOf("audio/*", "audio/wav", "audio/mpeg", "audio/mp4", "audio/ogg"))
    }

    fun shareTranscript() {
        val text = transcript.ifBlank { partialText }
        if (text.isBlank()) {
            statusMessage = "Transcript is empty."
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
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

                // ── Recording / file actions ───────────────────────
                // Save the latest recording as a WAV, import an
                // existing audio file, share the transcript, or
                // share the most recent recording. Wrapped in a
                // separate row so the speak/clear controls above
                // stay focused on the TTS state machine.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AssistChip(
                        onClick = ::saveRecording,
                        enabled = frameBuffer.bytesWritten() > 0L,
                        label = { Text(stringResource(R.string.voice_save_recording)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = ::importAudio,
                        label = { Text(stringResource(R.string.voice_import_audio)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.LibraryMusic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = ::shareLastRecording,
                        enabled = lastSavedPath != null,
                        label = { Text(stringResource(R.string.voice_share_recording)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = ::shareTranscript,
                        enabled = transcript.isNotBlank() || partialText.isNotBlank(),
                        label = { Text(stringResource(R.string.voice_share_transcript)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
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

/**
 * Bounded ring buffer for PCM frames collected from the mic. We
 * use this so the user can hit "Save recording" and get a WAV file
 * with everything that was captured during the most recent
 * listening session — no separate "recording mode" toggle needed.
 *
 * Thread safety: every mutation runs on the screen's UI scope, so
 * we don't bother with synchronization. If the engine starts
 * publishing on a background thread in the future, swap the
 * `mutableListOf<ByteArray>` for an `ArrayDeque` guarded by a
 * `Mutex`.
 */
private class PcmFrameBuffer(
    private val maxSeconds: Int,
    private val sampleRate: Int,
) {
    private val maxBytes: Int = maxSeconds * sampleRate * 2  // 16-bit mono
    private val chunks = mutableListOf<ByteArray>()
    private var totalBytes: Int = 0

    fun append(frame: com.meshlit.core.inference.RunAnywhereVoiceEngine.VoiceFrame) {
        val pcm = frame.pcmBytes
        chunks.add(pcm)
        totalBytes += pcm.size
        // Drop oldest chunks until we're under the cap. We don't
        // bother re-copying — the dropped chunk stays in memory
        // until GC, which is fine for a 60-second cap.
        while (totalBytes > maxBytes && chunks.size > 1) {
            val dropped = chunks.removeAt(0)
            totalBytes -= dropped.size
        }
    }

    fun reset() {
        chunks.clear()
        totalBytes = 0
    }

    fun bytesWritten(): Long = totalBytes.toLong()

    /**
     * Persist the buffered PCM as a 16-bit mono WAV file at [out].
     * Writes the standard RIFF header followed by every captured
     * chunk in order.
     */
    fun writeAsWav(
        out: File,
        sampleRate: Int,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ) {
        val totalDataBytes = totalBytes
        val totalFileBytes = 36 + totalDataBytes
        out.outputStream().use { os ->
            // RIFF header
            os.writeAscii("RIFF")
            os.writeIntLe(totalFileBytes)
            os.writeAscii("WAVE")
            // fmt chunk
            os.writeAscii("fmt ")
            os.writeIntLe(16)              // chunk size
            os.writeShortLe(1)             // PCM format
            os.writeShortLe(channels.toShort())
            os.writeIntLe(sampleRate)
            os.writeIntLe(sampleRate * channels * bitsPerSample / 8)
            os.writeShortLe((channels * bitsPerSample / 8).toShort())
            os.writeShortLe(bitsPerSample.toShort())
            // data chunk
            os.writeAscii("data")
            os.writeIntLe(totalDataBytes)
            for (chunk in chunks) {
                os.write(chunk)
            }
        }
    }
}

/** Helpers for writing little-endian WAV header fields. */
private fun OutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value shr 8) and 0xFF)
    write((value shr 16) and 0xFF)
    write((value shr 24) and 0xFF)
}
private fun OutputStream.writeShortLe(value: Short) {
    write(value.toInt() and 0xFF)
    write((value.toInt() shr 8) and 0xFF)
}
private fun OutputStream.writeAscii(text: String) {
    write(text.toByteArray(Charsets.US_ASCII))
}

/**
 * Read an audio file picked via SAF, decode it to 16-kHz mono PCM,
 * and run it through STT. We use Android's stock `MediaExtractor` +
 * `MediaCodec` for decode (no third-party audio library) so the
 * import path adds zero new dependencies.
 *
 * Returns the final transcript text. Throws on decode / STT
 * failure — the screen surfaces the message as a status line.
 */
private suspend fun importAndTranscribe(
    context: Context,
    engine: com.meshlit.core.inference.RunAnywhereVoiceEngine,
    uri: Uri,
): Result<String> = runCatching {
    val pcm = decodeToPcm16k(context, uri)
    val frames = kotlinx.coroutines.flow.flowOf(
        com.meshlit.core.inference.RunAnywhereVoiceEngine.VoiceFrame(
            pcmBytes = pcm,
            timestampMs = 0L,
        ),
    )
    var finalText = ""
    engine.transcribe(frames).collect { event ->
        if (event is com.meshlit.core.inference.RunAnywhereVoiceEngine.TranscriptEvent.Final) {
            finalText = event.finalText
        }
    }
    finalText
}

/**
 * Decode any container Android's `MediaCodec` understands
 * (`.wav`, `.mp3`, `.m4a`, `.ogg`, `.flac`) to 16-kHz mono PCM.
 *
 * For PCM containers (`.wav`) we can skip the codec and just
 * reorder bytes. For compressed containers we run an async
 * decode loop and resample on the fly.
 */
private fun decodeToPcm16k(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val type = resolver.getType(uri)?.lowercase().orEmpty()
    return if (type.contains("wav") || type.contains("x-wav") || uri.toString().endsWith(".wav", true)) {
        decodeWavToPcm16k(resolver.openInputStream(uri) ?: error("cannot open uri"))
    } else {
        decodeCompressedToPcm16k(resolver.openInputStream(uri) ?: error("cannot open uri"))
    }
}

private fun decodeWavToPcm16k(input: InputStream): ByteArray {
    // Minimal RIFF parser — reads `fmt ` + `data` chunks and
    // returns the `data` payload resampled to 16 kHz mono.
    val header = ByteArray(12)
    input.read(header)
    val riff = String(header.copyOfRange(0, 4))
    require(riff == "RIFF") { "not a RIFF file" }
    val wave = String(header.copyOfRange(8, 12))
    require(wave == "WAVE") { "not a WAVE file" }
    var sampleRate = 0
    var channels = 0
    var bitsPerSample = 0
    var audioFormat = 0
    var data: ByteArray? = null
    while (true) {
        val chunkHeader = ByteArray(8)
        val read = input.read(chunkHeader)
        if (read < 8) break
        val id = String(chunkHeader.copyOfRange(0, 4))
        val size = ((chunkHeader[4].toInt() and 0xFF) or
            ((chunkHeader[5].toInt() and 0xFF) shl 8) or
            ((chunkHeader[6].toInt() and 0xFF) shl 16) or
            ((chunkHeader[7].toInt() and 0xFF) shl 24))
        if (id == "fmt ") {
            val fmt = ByteArray(size)
            input.read(fmt)
            audioFormat = (fmt[0].toInt() and 0xFF) or ((fmt[1].toInt() and 0xFF) shl 8)
            channels = (fmt[2].toInt() and 0xFF) or ((fmt[3].toInt() and 0xFF) shl 8)
            sampleRate = ((fmt[4].toInt() and 0xFF)) or
                ((fmt[5].toInt() and 0xFF) shl 8) or
                ((fmt[6].toInt() and 0xFF) shl 16) or
                ((fmt[7].toInt() and 0xFF) shl 24)
            bitsPerSample = (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
        } else if (id == "data") {
            val buf = ByteArray(size)
            var offset = 0
            while (offset < size) {
                val n = input.read(buf, offset, size - offset)
                if (n < 0) break
                offset += n
            }
            data = buf
            break
        } else {
            // Skip unknown chunk
            input.skip(size.toLong())
        }
    }
    val payload = data ?: error("WAV has no `data` chunk")
    return resampleToMono16k(payload, sampleRate, channels, bitsPerSample, audioFormat)
}

/**
 * Run `MediaExtractor` + `MediaCodec` against [input] and pull
 * 16-kHz mono PCM out. This is the slow path — the user sees a
 * brief progress bar while we decode.
 */
private fun decodeCompressedToPcm16k(input: InputStream): ByteArray {
    // Copy the InputStream into a temp file because MediaExtractor
    // needs a file descriptor or content URI, not a raw stream.
    val temp = File.createTempFile("import-audio-", ".bin")
    temp.outputStream().use { out -> input.copyTo(out) }
    return try {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(temp.absolutePath)
        // Find the first audio track.
        var trackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                trackIndex = i
                break
            }
        }
        require(trackIndex >= 0) { "no audio track" }
        extractor.selectTrack(trackIndex)
        val trackFormat = extractor.getTrackFormat(trackIndex)
        val sampleRate = trackFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
        val channels = trackFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
        val codec = android.media.MediaCodec.createDecoderByType(
            trackFormat.getString(android.media.MediaFormat.KEY_MIME) ?: "audio/raw",
        )
        codec.configure(trackFormat, /* surface = */ null, /* crypto = */ null, /* flags = */ 0)
        codec.start()
        val output = mutableListOf<Byte>()
        val info = android.media.MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inIx = codec.dequeueInputBuffer(10_000)
                if (inIx >= 0) {
                    val inBuf = codec.getInputBuffer(inIx) ?: continue
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIx >= 0) {
                val outBuf = codec.getOutputBuffer(outIx) ?: continue
                val chunk = ByteArray(info.size)
                outBuf.get(chunk)
                output.addAll(chunk.toList())
                codec.releaseOutputBuffer(outIx, false)
                if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEos = true
                }
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val combined = ByteArray(output.size) { output[it] }
        resampleToMono16k(combined, sampleRate, channels, bitsPerSample = 16, audioFormat = 1)
    } finally {
        temp.delete()
    }
}

/**
 * Convert any PCM payload (1- or 2-channel, 8 / 16 / 32-bit) to
 * 16-kHz mono int16 LE. Compressed formats are already decoded
 * upstream; this routine only handles the integer-PCM cases.
 */
private fun resampleToMono16k(
    payload: ByteArray,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    audioFormat: Int,
): ByteArray {
    require(audioFormat == 1) { "only PCM (format=1) supported, got $audioFormat" }
    val bytesPerSample = bitsPerSample / 8
    require(bytesPerSample in setOf(1, 2, 4)) { "unsupported bits per sample: $bitsPerSample" }
    val frameSize = bytesPerSample * channels
    require(payload.size % frameSize == 0) { "payload not aligned to frame size" }
    val totalFrames = payload.size / frameSize
    val mono = ShortArray(totalFrames)
    for (i in 0 until totalFrames) {
        var sum = 0L
        for (c in 0 until channels) {
            val offset = i * frameSize + c * bytesPerSample
            val sample = when (bytesPerSample) {
                1 -> (payload[offset].toInt() - 128) * 256
                2 -> (payload[offset].toInt() and 0xFF) or ((payload[offset + 1].toInt() and 0xFF) shl 8)
                4 -> ((payload[offset].toInt() and 0xFF) or
                    ((payload[offset + 1].toInt() and 0xFF) shl 8) or
                    ((payload[offset + 2].toInt() and 0xFF) shl 16) or
                    ((payload[offset + 3].toInt() and 0xFF) shl 24))
                else -> 0
            }
            sum += sample
        }
        mono[i] = (sum / channels).toShort()
    }
    // Linear-resample to 16 kHz. Quality is fine for STT input.
    val targetRate = 16_000
    if (sampleRate == targetRate) {
        val out = ByteArray(mono.size * 2)
        for (i in mono.indices) {
            val v = mono[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
    val outSamples = (mono.size.toLong() * targetRate / sampleRate).toInt()
    val out = ByteArray(outSamples * 2)
    for (i in 0 until outSamples) {
        val srcIdxF = i.toLong() * sampleRate / targetRate
        val i0 = srcIdxF.toInt().coerceAtMost(mono.size - 1)
        val i1 = (i0 + 1).coerceAtMost(mono.size - 1)
        val t = (srcIdxF - i0).toFloat()
        val v0 = mono[i0].toFloat()
        val v1 = mono[i1].toFloat()
        val sample = (v0 + (v1 - v0) * t).toInt().toShort()
        out[i * 2] = (sample.toInt() and 0xFF).toByte()
        out[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
    }
    return out
}
