package com.meshlit.ui.modern

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.di.koinInject
import com.meshlit.media.LocalVisionHost
import com.meshlit.media.withLiveCamera
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@Composable internal fun LiveVisionScreen(back: () -> Unit, setup: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val host = koinInject<LocalVisionHost>()
    val model by host.model.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var question by remember { mutableStateOf("Describe the current scene in one short sentence.") }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var observation by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Stopped") }

    fun start() {
        val pair = model ?: return
        val prompt = question.trim()
        if (job != null || prompt.isBlank()) return
        thumbnail = null
        observation = null
        job = scope.launch {
            try {
                withTimeout(5_000) {
                    owner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                }
                status = "Loading on-device vision model"
                val finished = withTimeoutOrNull(240_000) {
                    host.withSession(pair) { describe ->
                        withLiveCamera(context, owner) { capture ->
                            repeat(12) { index ->
                                status = "Capturing frame ${index + 1}"
                                val frame = capture()
                                thumbnail = frame.thumbnail
                                status = "Analyzing frame ${index + 1} on device"
                                observation = describe(frame.jpeg, prompt).text
                                if (index < 11) {
                                    status = "Waiting for the next frame"
                                    delay(3_000)
                                }
                            }
                        }
                    }
                    true
                }
                status = if (finished == true) "Session complete" else "Session time limit reached"
            } catch (_: TimeoutCancellationException) {
                status = "Camera or model did not respond in time"
            } catch (cancelled: CancellationException) {
                status = "Stopped"
                throw cancelled
            } catch (error: Exception) {
                status = error.message ?: "Live vision could not continue"
            } finally { job = null }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else status = "Camera access was declined"
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                job?.cancel()
                thumbnail = null
                observation = null
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); job?.cancel() }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { job?.cancel(); back() }) { Text("Back") }
        Text("Live camera observations", style = MaterialTheme.typography.headlineSmall)
        Text("Sample the rear camera and describe each frame on this device. Frames are processed one at a time and are never saved. Leaving this screen or the app stops capture.")
        if (model == null) TextButton(onClick = setup) { Text("Set up an offline vision model") }
        OutlinedTextField(question, { question = it.take(600) }, Modifier.fillMaxWidth(),
            enabled = job == null, label = { Text("What should the model look for?") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = job == null && model != null && host.available() && question.isNotBlank(), onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) start()
                else permission.launch(Manifest.permission.CAMERA)
            }) { Text("Start camera") }
            OutlinedButton(enabled = job != null, onClick = { job?.cancel(); thumbnail = null; observation = null }) { Text("Stop") }
        }
        Text(status, style = MaterialTheme.typography.labelLarge)
        thumbnail?.let { Image(it.asImageBitmap(), "Most recent camera frame", Modifier.fillMaxWidth().heightIn(max = 260.dp)) }
        observation?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}
