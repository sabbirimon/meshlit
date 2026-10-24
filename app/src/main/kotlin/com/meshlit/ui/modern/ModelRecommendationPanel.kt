package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.core.inference.models.CompactModelRecommendations
import com.meshlit.core.inference.models.DeviceRuntimeSnapshot
import com.meshlit.models.ModelLibrary
import kotlinx.coroutines.*

@Composable
internal fun ModelWelcomeSuggestion(onModels:()->Unit) {
    val prefs=LocalContext.current.getSharedPreferences("model-recommendation-welcome",0)
    var dismissed by remember{mutableStateOf(prefs.getBoolean("reviewed",false))}
    if(!dismissed) Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp)) {
            Text("A model that fits your phone",style=MaterialTheme.typography.titleSmall)
            Text("Compare compact offline models using this device's RAM and storage.",style=MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick={prefs.edit().putBoolean("reviewed",true).apply();dismissed=true;onModels()}) {Text("See recommendations")}
                TextButton(onClick={prefs.edit().putBoolean("reviewed",true).apply();dismissed=true}){Text("Later")}
            }
        }
    }
}

@Composable
internal fun ModelRecommendationPanel(library:ModelLibrary,onError:(String)->Unit) {
    val entries by library.models.collectAsStateWithLifecycle()
    val coordinator=com.meshlit.di.koinInject<com.meshlit.core.inference.InferenceCoordinator>()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val loadedPath=(runtime as? com.meshlit.core.inference.CoordinatorState.Ready)?.model?.modelPath
    val scope=rememberCoroutineScope()
    var smallest by remember{mutableStateOf(false)}
    var expanded by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var snapshot by remember{mutableStateOf<DeviceRuntimeSnapshot?>(null)}
    LaunchedEffect(library) { while(isActive) {
        snapshot=withContext(Dispatchers.IO){library.deviceSnapshot()}
        delay(5000)
    } }
    val device=snapshot ?: return
    val advice=CompactModelRecommendations.assess(device,smallest)
    Card(Modifier.fillMaxWidth().testTag("model-hardware-recommendations")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Recommended for this device",style=MaterialTheme.typography.titleMedium)
            Text("${device.model} · ${device.abi} · ${device.cpuCores} CPU cores\n${modelStorageLabel(device.totalRam)} RAM · ${modelStorageLabel(device.availableRam)} available · ${modelStorageLabel(device.freeStorage)} free storage",
                style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!smallest,{smallest=false},label={Text("Best fit")})
                FilterChip(smallest,{smallest=true},label={Text("Save storage")})
            }
            (if(expanded) advice else advice.take(1)).forEach { a ->
                val installed=entries.firstOrNull{it.url==a.model.url || it.sha256==a.model.sha256}
                HorizontalDivider()
                Text(a.model.name,style=MaterialTheme.typography.titleSmall)
                Text(a.model.purpose+" · Apache-2.0",style=MaterialTheme.typography.bodyMedium)
                Text("Download ${modelStorageLabel(a.model.bytes)} · reserve ${modelStorageLabel(a.storageNeeded)} storage\nEstimated model memory ${modelStorageLabel(a.memoryEstimate)} at a short context; planning target ${a.context} tokens",
                    style=MaterialTheme.typography.bodySmall)
                Text(a.reason,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(a.model.reasoning) Text("Qwen3: use /think for a reasoning turn or /no_think for a quicker answer. Long reasoning can reach the current chat output limit. This device has not been benchmarked with this model.",style=MaterialTheme.typography.bodySmall)
                if(installed?.active==true) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${installed.phase} · ${modelStorageLabel(installed.bytes)} received",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={library.pause(installed.id)}){Text("Pause download")}
                } else if(installed?.installed==true) {
                    val loaded=installed.path==loadedPath
                    OutlinedButton(enabled=!loading && !loaded,onClick={scope.launch{loading=true;try{library.load(installed.id)}catch(c:CancellationException){throw c}catch(e:Exception){onError(e.message ?: "Model load failed")}finally{loading=false}}}){Text(if(loaded) "Loaded · ready for chat" else if(loading) "Loading…" else "Load installed model")}
                } else Button(enabled=!loading && a.hardwareFit && device.freeStorage>=a.storageNeeded && library.ready.isCompleted,onClick={
                    try {library.downloadRecommendation(a.model.id)}catch(e:Exception){onError(e.message ?: "Download could not start")}
                }){Text("Download · ${modelStorageLabel(a.model.bytes)}")}
                installed?.error?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
            }
            TextButton(onClick={expanded=!expanded}){Text(if(expanded) "Show best match" else "Compare all ${advice.size} options")}
            Text("Estimates use current memory, storage, CPU architecture, Android version and heat. GPU/NPU speed is not assumed. Downloads are checksum-pinned and start only when you choose one; the existing starter remains available.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

private fun modelStorageLabel(bytes:Long):String=if(bytes>=1024L*1024*1024)
    String.format(java.util.Locale.ROOT,"%.1f GiB",bytes/(1024.0*1024*1024))
else String.format(java.util.Locale.ROOT,"%.0f MiB",bytes/(1024.0*1024))
