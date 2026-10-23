package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.resources.ResourceSnapshotReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable internal fun ResourcesScreen(onBack: () -> Unit) {
    val reader = koinInject<ResourceSnapshotReader>()
    var snapshot by remember { mutableStateOf(reader.read()) }
    LaunchedEffect(reader) { while (isActive) { snapshot = reader.read(); delay(3_000) } }
    fun size(bytes: Long) = "%.1f GiB".format(bytes / 1_073_741_824.0)
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("resources-list"), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back to settings") }; Text("Resources and sensors", style = MaterialTheme.typography.headlineSmall) }
        item { Text("Live readings refresh every 3 seconds. Sensor inventory shows hardware available to Android, not proof that a sensor is recording.") }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Compute and storage", style = MaterialTheme.typography.titleMedium)
            Text("Available RAM: ${size(snapshot.availableRamBytes)} / ${size(snapshot.totalRamBytes)}")
            Text("Free app storage: ${size(snapshot.freeAppStorageBytes)}")
            Text("CPU cores reported: ${snapshot.cpuCores}")
            Text("Battery: ${snapshot.power.batteryPercent?.let { "$it%" } ?: "Unavailable"} · Thermal: ${snapshot.power.thermalStatus ?: "Unavailable"}")
            Text("Network: ${if (snapshot.power.internetValidated) "Validated" else "Not validated"} · ${if (snapshot.power.metered) "Metered" else "Unmetered"}")
        } } }
        item { Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Meshlit activity", style = MaterialTheme.typography.titleMedium)
            Text("Local model: ${if (snapshot.modelLoaded) "Loaded" else "Not loaded"} · Generation: ${if (snapshot.generating) "Running" else "Idle"}")
            Text("Model transfers: ${snapshot.activeModelTransfers} active")
            Text("Pipeline worker: ${if (snapshot.pipelineWorker) "Running" else "Off"} · Coordinator: ${if (snapshot.pipelineCoordinator) "Running" else "Off"}")
            Text("Camera, microphone and other sensor session use is unavailable as a combined system-wide reading.")
        } } }
        item { Text("Available sensors (${snapshot.availableSensors.size})", style = MaterialTheme.typography.titleMedium) }
        items(snapshot.availableSensors, key = { "${it.type}:${it.name}" }) { sensor ->
            ListItem(headlineContent = { Text(sensor.name) }, supportingContent = { Text("${sensor.vendor} · Android type ${sensor.type} · Active use unknown") })
        }
    }
}
