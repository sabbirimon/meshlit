package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.meshlit.core.inference.models.*
import com.meshlit.models.ModelLibrary
import com.meshlit.ui.theme.ChatTokens as T
import kotlinx.coroutines.*

@Composable fun DeviceRuntimeCard(library:ModelLibrary) {
    var device by remember{mutableStateOf<DeviceRuntimeSnapshot?>(null)}
    LaunchedEffect(library){while(isActive){device=withContext(Dispatchers.IO){library.deviceSnapshot()};delay(5000)}}
    Card(Modifier.fillMaxWidth()){Column(Modifier.padding(T.large),verticalArrangement=Arrangement.spacedBy(T.small)){
        Text("Device runtime policy",style=MaterialTheme.typography.titleMedium)
        val snapshot=device
        if(snapshot==null) Text("Reading actual device resources…")
        else {
            val plan=DeviceRuntimePolicy.plan(snapshot)
            Text("${snapshot.manufacturer} ${snapshot.model} · ${snapshot.soc}")
            Text("${snapshot.os} API ${snapshot.api} · ${snapshot.abi} · ${snapshot.cpuCores} CPU cores")
            Text("${plan.category} · available RAM ${formatBytes(snapshot.availableRam)} / ${formatBytes(snapshot.totalRam)}")
            Text("Planning budget ${formatBytes(plan.memoryBudget)} · suggested native context ${plan.recommendedContext} tokens · cap ${plan.maxContext}")
            Text("Local engine artifacts: ${if(plan.localInferenceSupported) "Installed (runtime load still required)" else "Unavailable"} · native tuning ${if(plan.nativeTuningSupported) "Available" else "Unavailable"}")
            Text("Thermal status: ${snapshot.thermal?.toString() ?: "Unavailable"} · new heavy work ${if(plan.allowHeavyWork) "Allowed by estimate" else "Blocked"}")
            plan.reasons.forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
        }
    }}
}
