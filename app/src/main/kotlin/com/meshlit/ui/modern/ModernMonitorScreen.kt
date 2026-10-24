package com.meshlit.ui.modern

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.inference.*
import com.meshlit.di.koinInject
import com.meshlit.models.ModelLibrary
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@Composable
fun ModernMonitorScreen() {
    val context=LocalContext.current
    val coordinator=koinInject<InferenceCoordinator>()
    val library=koinInject<ModelLibrary>()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val models by library.models.collectAsStateWithLifecycle()
    var memory by remember { mutableLongStateOf(0) }
    var total by remember { mutableLongStateOf(0) }
    var storage by remember { mutableLongStateOf(0) }
    var battery by remember { mutableIntStateOf(-1) }
    var temperature by remember { mutableStateOf<Float?>(null) }
    var thermal by remember { mutableStateOf("Unavailable") }
    var lastResult by remember { mutableStateOf<InferenceResult?>(null) }
    LaunchedEffect(coordinator) {
        coordinator.events.collect { event ->
            if(event is InferenceEvent.GenerationFinished) {
                val result=event.result
                if(result is com.meshlit.core.common.MeshlitResult.Success) lastResult=result.value
            }
        }
    }
    LaunchedEffect(library) {
        while(isActive) {
            val info=ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            memory=info.availMem;total=info.totalMem;storage=library.freeStorage()
            context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
                val scale=it.getIntExtra(BatteryManager.EXTRA_SCALE,-1)
                val level=it.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
                battery=if(scale>0 && level>=0) level*100/scale else -1
                temperature=it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)
                    .takeIf { value -> value!=Int.MIN_VALUE }?.div(10f)
            }
            if(Build.VERSION.SDK_INT>=29) thermal=when(context.getSystemService(PowerManager::class.java).currentThermalStatus) {
                0 -> "Normal";1 -> "Light";2 -> "Moderate";3 -> "Severe";4 -> "Critical";5 -> "Emergency";else -> "Shutdown"
            }
            delay(2000)
        }
    }
    LazyColumn(Modifier.fillMaxSize().widthIn(max=T.contentMax),contentPadding=PaddingValues(T.large),
        verticalArrangement=Arrangement.spacedBy(T.medium)) {
        item { StatusHeroCard("Your device, at a glance", "${runtime.javaClass.simpleName} · ${if (total > 0) "${formatBytes(memory)} available RAM" else "Reading device memory"} · ${if (battery >= 0) "$battery% battery" else "Battery unavailable"}") }
        item {DeviceRuntimeCard(library)}
        item { MonitorCard("Inference") {
            Text("${coordinator.runtimeDisplayName} · ${runtime.javaClass.simpleName}")
            coordinator.loadedModel()?.let { Text(it.modelName);Text("Model file: ${formatBytes(it.sizeBytes)}");Text("Context: ${if(it.contextSize>0) "${it.contextSize} tokens" else "Not exposed by backend"}") }
            lastResult?.let { Text("Last response: ${it.generatedTokens ?: "Unknown"} tokens · ${it.totalDurationMs} ms · ${it.tokensPerSecond ?: "Unknown"} tokens/s") }
        } }
        item { MonitorCard("Memory and storage") {
            Text("Available RAM: ${formatBytes(memory)} / ${formatBytes(total)}")
            if(total>0) LinearProgressIndicator(progress={1f-memory.toFloat()/total},modifier=Modifier.fillMaxWidth())
            Text("Free app storage: ${formatBytes(storage)}")
            Text("${models.count { it.active }} active transfers · ${models.count { it.installed }} installed models")
        } }
        item { MonitorCard("Power and temperature") {
            Text("Battery: ${if(battery>=0) "$battery%" else "Unavailable"}")
            Text("Battery sensor: ${temperature?.let { "$it °C" } ?: "Unavailable"}")
            Text("System thermal state: $thermal")
        } }
        item { com.meshlit.pipeline.PipelinePanel() }
    }
}
@Composable private fun MonitorCard(title:String,body:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)) {
        Text(title,style=MaterialTheme.typography.titleMedium);body()
    } }
}
