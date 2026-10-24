package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.di.koinInject
import com.meshlit.operations.*
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.mcp.control.ClusterCapacity
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OperationsScreen(back:()->Unit){
    val control=koinInject<OperationsControl>();val stop=koinInject<StopCoordinator>();val cluster=koinInject<ClusterControls>()
    val policy by control.gate.policy.collectAsState();val capacity by cluster.state.collectAsState();val report by stop.state.collectAsState()
    val stopping by stop.stopping.collectAsState()
    var limit by remember{mutableStateOf(capacity.deviceLimit.toString())};var workers by remember{mutableStateOf(capacity.pipelineWorkerLimit.toString())}
    var allow by remember{mutableStateOf(capacity.agentMayManageCapacity)};var minimum by remember{mutableStateOf(capacity.agentMinimum.toString())};var maximum by remember{mutableStateOf(capacity.agentMaximum.toString())}
    var error by remember{mutableStateOf<String?>(null)}
    val directory=koinInject<com.meshlit.control.WebBridgeHost>().directory
    Scaffold(topBar={TopAppBar(title={Text("Operations dashboard")},navigationIcon={TextButton(onClick=back){Text("Back")}})}) {padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item{Text(if(policy.emergencyStopped) "EMERGENCY STOP LATCHED" else "Managed operations enabled",color=if(policy.emergencyStopped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Button(onClick={try{stop.stopAll()}catch(_:Exception){error="Stop latched in memory; persistence failed. Keep this instance stopped."}},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Emergency stop all managed operations")}
                OutlinedButton(enabled=policy.emergencyStopped && !stopping,onClick={try{stop.resumeHuman()}catch(_:Exception){error="Resume could not be saved; stop remains active"}}){Text("Human resume · does not restart sessions")}}
            item{Text("Stops are local to this Meshlit instance. Remote commands, paid tasks and already accepted actions may continue; closing a transport is not remote cancellation proof. Review remote job state. Voice/media capture, external Termux processes and independent OS services still require their own Stop controls.")}
            report.forEach{(name,status)->item{Text("$name: $status")}}
            if(!com.meshlit.BuildProfile.coreCandidate) {
            item{Text("Cluster capacity",style=MaterialTheme.typography.titleMedium);Text("Enrolled inventory: ${directory.read().devices.size}. Previous inventory ceiling was 64; configurable ceiling is now 10,000. Layer execution stays at 2–8 paired workers. Neither ceiling is scale/performance qualification.")}
            item{OutlinedTextField(limit,{limit=it},label={Text("Inventory admission limit · 1–10,000")});OutlinedTextField(workers,{workers=it},label={Text("Native worker limit · 2–8")})}
            item{Row{Text("Allow agents to adjust capacity within bounds",Modifier.weight(1f));Switch(allow,{allow=it})};OutlinedTextField(minimum,{minimum=it},label={Text("Agent minimum")});OutlinedTextField(maximum,{maximum=it},label={Text("Agent maximum")})}
            item{Button(onClick={try{cluster.saveHuman(ClusterCapacity(limit.toInt(),workers.toInt(),allow,minimum.toInt(),maximum.toInt()));error=null}catch(_:Exception){error="Invalid capacity or persistence failure"}}){Text("Save human capacity policy")};Text("Existing identities are retained when lowering the limit; new enrollment is blocked at capacity. Invitations, owner approval and per-device scopes remain separate. Agent changes also require saved Cluster delegation.")}
            }
            item{Text("Per-function controls",style=MaterialTheme.typography.titleMedium);Text("Allowed means the stop gate permits admission; existing grants, device readiness and provider policies still apply. Agent switches can restrict existing delegation; they cannot grant it.")}
            control.gate.supportedFeatures.forEach {feature->item{Card{Column(Modifier.padding(12.dp)){
                Text(when(feature){ManagedFeature.FILES->"Typed code workspace operations";ManagedFeature.CRAWLER->"Crawler tools through the managed MCP registry";else->feature.name.lowercase().replace('_',' ')},style=MaterialTheme.typography.titleMedium)
                Row{Text("Function allowed",Modifier.weight(1f));Switch(feature !in policy.disabled,{value->try{stop.setFeature(feature,value)}catch(_:Exception){error="Policy could not be saved"}})}
                Row{Text("Agent gate allowed",Modifier.weight(1f));Switch(feature !in policy.agentDisabled,{value->try{stop.setFeature(feature,value,true)}catch(_:Exception){error="Agent policy could not be saved"}})}
            }}}}
            error?.let{item{Text(it,color=MaterialTheme.colorScheme.error)}}
        }
    }
}
