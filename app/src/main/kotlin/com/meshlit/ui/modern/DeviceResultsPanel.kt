package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.meshlit.resources.DeviceResultsHub
import kotlinx.coroutines.*
@Composable internal fun DeviceResultsPanel() {
    val context=LocalContext.current
    val hub=remember{DeviceResultsHub.get(context)}
    val results by hub.results.collectAsState();val scope=rememberCoroutineScope()
    var busy by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)}
    var tick by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(isActive){tick=System.currentTimeMillis();delay(2000)}}
    WebDeviceConnectionsPanel()
    Text("Paired and external device results",style=MaterialTheme.typography.titleMedium)
    Text("Uses owner-configured MCP connections and their existing authentication. Each source must expose resource_status. Sensor sources must opt into sharing. Device identity is the configured source binding; advertised names are untrusted.")
    if(hub.sources().isEmpty())Text("No enabled source configured. Add an authenticated device connector in MCP settings.")
    hub.sources().forEach{source->
        var allowed by remember(source){mutableStateOf(hub.agentAllowed(source))}
        Text(source.name)
        Row{Switch(allowed,{hub.approveAgent(source,it);allowed=it});Text("Allow agent observation reads from this source")}
        Button(enabled=busy==null,onClick={busy=source.id;error=null;scope.launch{try{hub.fetch(source.id)}catch(e:CancellationException){throw e}catch(_:Exception){error="Could not read device results. Check endpoint, resource_status support, approval and freshness."}finally{busy=null}}}){Text(if(busy==source.id)"Reading source…" else "Read device results")}
        results[source.id]?.let{value->Text("${if(value.fresh(tick))"Fresh" else "Stale · refresh required"} · receipt ${value.receiptId.take(8)}\nObserved ${value.observedAtMs} · received ${value.receivedAtMs}\nCPU cores ${value.cpuCores ?: "unknown"} · available RAM ${value.availableRamBytes ?: "unknown"} bytes\nShared sensor samples: ${value.sensors.size}")
            value.sensors.forEach{s->Text("${s.name}: ${s.values.mapIndexed{index,v->"$v ${s.units.getOrElse(index){"unknown"}}"}.joinToString()} · observed ${s.observedAtMs}")}}
    }
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
}
