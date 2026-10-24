package com.meshlit.ui.modern

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.observability.AppLoggerFactory.appLogger as logger
import com.meshlit.core.inference.models.EnergyEstimate
import com.meshlit.power.*
import com.meshlit.devices.*
import com.meshlit.di.koinInject
import com.meshlit.control.AgentBackend
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable fun PowerCostsScreen(onBack:()->Unit,onCloud:(()->Unit)?=null) {
    val context=LocalContext.current;val repository=remember{PowerRepository(context)};val scope=rememberCoroutineScope()
    var reading by remember{mutableStateOf<PowerReading?>(null)}
    var policy by remember{mutableStateOf(repository.policy())};var energy by remember{mutableDoubleStateOf(0.0)};var observedSeconds by remember{mutableLongStateOf(0)}
    val samples=remember{mutableListOf<PowerReading>()}
    var price by remember{mutableStateOf(repository.pricePerKwh()?.toString().orEmpty())}
    var efficiency by remember{mutableStateOf(repository.efficiency().toString())};var currency by remember{mutableStateOf(repository.currency())}
    var message by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){var previous:PowerReading?=null
        while(isActive){val next=repository.reading();previous?.let{old->EnergyEstimate.dischargeWh(old.observedAtMs,next.observedAtMs,old.signedBatteryWatts,next.signedBatteryWatts,next.charging!=false)?.let{energy+=it;observedSeconds+=(next.observedAtMs-old.observedAtMs)/1000}}
            reading=next;previous=next;samples.add(next);if(samples.size>720) samples.removeAt(0);delay(5000)}
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")){uri->if(uri!=null){val snapshot=samples.toList();scope.launch{try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter()?.use{writer->snapshot.forEach{writer.write(Json.encodeToString(it));writer.newLine()}} ?: error("Cannot open export")};message="Exported ${snapshot.size} observed samples"}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message}}}}
    fun savePolicy(next:PowerPolicy){try{repository.save(next);policy=next;message="Saved; transfers may pause within 5 seconds. Resume manually when ready."}catch(e:Exception){message=e.message}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Power & costs",style=MaterialTheme.typography.headlineSmall)}
        item{Card{Column(Modifier.padding(16.dp)){
            Text("Battery: ${reading?.batteryPercent?.let{"$it%"} ?: "unknown"} · ${when(reading?.charging){true->"charging";false->"not charging";null->"unknown status"}}")
            Text("Current: ${reading?.currentUa?.let{"$it µA"} ?: "unavailable"} · voltage: ${reading?.voltageMv?.let{"$it mV"} ?: "unavailable"}")
            Text("Signed battery power: ${reading?.signedBatteryWatts?.let{"%.3f W".format(it)} ?: "unavailable"}")
            Text("Temperature: ${reading?.temperatureC?.let{"$it °C"} ?: "unknown"} · thermal status: ${reading?.thermalStatus ?: "unsupported"} · power saver: ${reading?.powerSave ?: "unknown"}")
            Text("Observed discharge: ${"%.6f".format(energy)} Wh over $observedSeconds valid seconds since opening this screen.")
            Text("Battery-side whole-device estimate. Charging, missing sensors and gaps over 15 seconds are excluded. Zero may mean no valid discharge samples. This does not isolate Meshlit or measure USB output / wall-plug input.",style=MaterialTheme.typography.bodySmall)
        }}}
        item{Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Text("Meshlit-wide measurement coverage",style=MaterialTheme.typography.titleMedium)
            Text("Whole phone: live battery readings while this page is open; not isolated to Meshlit.")
            Text("Local models and agents: per-task energy unavailable. Runtime token counts appear only when the engine reports them.")
            Text("Downloads and peer workers: bytes and workload events need a persistent collector before energy or rental cost can be assigned.")
            Text("Cloud requests: per-response estimates need reported usage and your configured prices; provider invoices are authoritative.")
            if(onCloud!=null) OutlinedButton(onClick=onCloud){Text("Open cloud costs and credentials")}
        }}}
        item{Text("Workload policy",style=MaterialTheme.typography.titleMedium)
            Text("Minimum battery: ${policy.minimumBattery}%");Slider(policy.minimumBattery.toFloat(),{savePolicy(policy.copy(minimumBattery=it.toInt()))},valueRange=5f..80f)
            Row{Text("Pause downloads below threshold",Modifier.weight(1f));Switch(policy.pauseLowBatteryDownloads,{savePolicy(policy.copy(pauseLowBatteryDownloads=it))})}
            Row{Text("Downloads on unmetered network only",Modifier.weight(1f));Switch(policy.unmeteredDownloadsOnly,{savePolicy(policy.copy(unmeteredDownloadsOnly=it))})}
            Row{Text("Require charging for new model loads",Modifier.weight(1f));Switch(policy.chargingForNewLoads,{savePolicy(policy.copy(chargingForNewLoads=it))})}
            Text("Running inference is not forcibly interrupted by these controls. USB power delivery and bypass charging depend on device firmware.")
            OutlinedButton(onClick={BatteryOptimizationHelper(context).openBatteryOptimizationSettings()}){Text("Android battery restrictions")}}
        item{Text("Electricity estimate",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(price,{price=it},label={Text("Your electricity price / kWh")});OutlinedTextField(efficiency,{efficiency=it},label={Text("Charging efficiency assumption (0.1–1.0)")});OutlinedTextField(currency,{currency=it.uppercase()},label={Text("Currency")})
            Button(onClick={try{repository.saveCost(price.takeIf{it.isNotBlank()}?.let{it.toDoubleOrNull() ?: error("Invalid electricity price")},efficiency.toDoubleOrNull() ?: error("Invalid efficiency"),currency);message="Cost assumptions saved"}catch(e:Exception){message=e.message}}){Text("Save assumptions")}
            val estimate=repository.pricePerKwh()?.let{EnergyEstimate.cost(energy,it,repository.efficiency())}
            Text(estimate?.let{"Observed discharge recharge estimate: ${"%.8f".format(it)} ${repository.currency()}"} ?: "Cost unknown; enter your tariff to estimate.")
            Text("Cloud token usage and per-response cost appear in chat when reported by the provider and both token prices are configured. Provider invoices remain authoritative.")}
        item{OutlinedButton(onClick={exporter.launch("meshlit-power-${System.currentTimeMillis()}.jsonl")}){Text("Export observed power samples")};message?.let{Text(it)}}
    }
}

@Composable fun ExternalDevicesScreen(onBack:()->Unit) {
    val context=LocalContext.current;val repository=remember{ExternalDevices(context)}
    var status by remember{mutableStateOf<ExternalDeviceStatus?>(null)};var message by remember{mutableStateOf<String?>(null)}
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null) try{
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        message="Folder granted; use Files for storage access and Models to import GGUF files.";status=repository.status()
    }catch(e:Exception){message=e.message}}
    LaunchedEffect(Unit){var paths:Set<String>?=null;while(isActive){try{val next=withContext(Dispatchers.IO){repository.status()};val newPaths=next.devices.map{it.path}.toSet();if(paths!=null && newPaths!=paths) logger("ExternalDevices").info("hardware.usb.changed","USB topology changed",mapOf("attached" to (newPaths-paths!!).size.toString(),"detached" to (paths!!-newPaths).size.toString()));paths=newPaths;status=next}catch(e:CancellationException){throw e}catch(e:Exception){message="Device probe failed: ${e.javaClass.simpleName}"};delay(3000)}}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("External devices / OTG",style=MaterialTheme.typography.headlineSmall)
            Text("USB host support: ${status?.usbHostSupported ?: "probing"}. Detected ${status?.devices?.size ?: 0} USB devices. Refreshes every 3 seconds while open.")}
        items(status?.devices.orEmpty(),key={it.path}){device->Card{Column(Modifier.padding(16.dp)){
            Text("USB %04x:%04x".format(device.vendorId,device.productId),style=MaterialTheme.typography.titleMedium);Text(device.kind);Text("${device.endpoints} endpoints · classes ${device.classes} · access ${if(device.permission) "granted" else "not granted"}");Text(device.executionSupport,style=MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled=!device.permission,onClick={try{repository.requestPermission(device.path);message="Android permission requested. Access status updates after the prompt."}catch(e:Exception){message=e.message}}){Text("Request device access")}
        }}}
        item{Text("Android storage volumes",style=MaterialTheme.typography.titleMedium)}
        items(status?.volumes.orEmpty()){volume->Text("${volume.description} · ${volume.state} · ${if(volume.removable) "removable" else "built in"}")}
        item{Button(onClick={folder.launch(null)}){Text("Grant an external storage folder")};Text("${status?.grantedTrees?.size ?: 0} persisted storage grants. Android must mount a drive before its folder can be selected. No raw disk writes or automatic formatting.")
            Text("GPU, NPU, FPGA, serial adapters and Ethernet require an actual driver/plugin. USB authorization alone does not supply one. Networked accelerators can join as authenticated peers after runtime capability verification.")
            message?.let{Text(it)}}
    }
}

@Composable fun AgentManagementScreen(onBack:()->Unit,onOpen:(String)->Unit={}) {
    val context=LocalContext.current
    val agentStore=remember { com.meshlit.agent.AgentProfileStore(context) }
    val initialProfiles=remember { runCatching { agentStore.load() } }
    var savedAgents by remember { mutableStateOf(initialProfiles.getOrDefault(emptyList())) }
    var agentStoreError by remember { mutableStateOf(if(initialProfiles.isFailure) "Could not read saved agents" else "") }
    var newAgentName by remember { mutableStateOf("") }
    var newAgentInstruction by remember { mutableStateOf("") }
    val agentTasks=remember { mutableStateMapOf<String,String>() }
    fun saveAgents(next:List<com.meshlit.agent.AgentProfile>) {
        runCatching { agentStore.save(next) }.onSuccess { savedAgents=next;agentStoreError="" }
            .onFailure { agentStoreError="Could not save agents; previous profiles remain" }
    }
    val backend=koinInject<AgentBackend>();val jobs by backend.controller.jobs.collectAsStateWithLifecycle()
    val registry=koinInject<com.meshlit.core.mcp.McpToolRegistry>();val scope=rememberCoroutineScope()
    val library=koinInject<com.meshlit.models.ModelLibrary>();val models by library.models.collectAsStateWithLifecycle()
    val inference=koinInject<com.meshlit.core.inference.InferenceCoordinator>();val inferenceState by inference.state.collectAsStateWithLifecycle()
    val providers=koinInject<com.meshlit.providers.OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    var revision by remember{mutableIntStateOf(0)};var query by remember{mutableStateOf("")}
    val prompts=remember { mutableStateListOf("","") }
    var selectedModel by remember { mutableStateOf("") }
    var modelMenu by remember { mutableStateOf(false) }
    var parallelError by remember { mutableStateOf<String?>(null) }
    var maxTokens by remember { mutableIntStateOf(256) }
    val choices=models.filter{it.installed && it.path==inference.loadedModel()?.modelPath}.map{it.id to "Loaded local · ${it.name}"}+
        profiles.filter{it.enabled && it.agentAllowed}.map{"cloud:${it.id}" to "Online · ${it.name}"}
    val selectedLabel=choices.firstOrNull{it.first==selectedModel}?.second ?: "Choose a model"
    val tools=remember(revision){registry.list()}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item{TextButton(onClick=onBack){Text("Back to settings")};Text("Agent management",style=MaterialTheme.typography.headlineSmall);Text("Saved permissions govern typed commands. Online access also needs opt-in on the selected provider profile. The built-in phone agent and optional OpenClaw gateway have separate controls.")
            Column {
                TextButton(onClick={onOpen("agent-registry")}){Text("Agent registry")}
                TextButton(onClick={onOpen("agent-logs")}){Text("Agent logs")}
            }
            OutlinedButton(onClick={onOpen("mcp-catalog")}){Text("MCP connections and tools")}}
        item{OutlinedButton(onClick={onOpen("openclaw")}){Text("Phone autonomy and background node")}
            Button(onClick={onOpen("operations")},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Emergency stop all")}}
        item { Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Create an agent",style=MaterialTheme.typography.titleMedium)
            Text("A saved agent is a named task template. It starts disabled and has no tools, device access or network grant. Choose a loaded local model or a separately agent-approved online profile; enable MODELS delegation to run it.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(newAgentName,{newAgentName=it.take(48)},Modifier.fillMaxWidth(),label={Text("Agent name")},singleLine=true)
            OutlinedTextField(newAgentInstruction,{newAgentInstruction=it.take(1200)},Modifier.fillMaxWidth(),label={Text("Task instruction · optional")},maxLines=3)
            Box { OutlinedButton(onClick={modelMenu=true}) { Text(selectedLabel) }
                DropdownMenu(expanded=modelMenu,onDismissRequest={modelMenu=false}) { choices.forEach { (id,label) ->
                    DropdownMenuItem(text={Text(label)},onClick={selectedModel=id;modelMenu=false})
                } } }
            Button(enabled=initialProfiles.isSuccess && savedAgents.size<16 && newAgentName.isNotBlank() && selectedModel.isNotBlank(),onClick={
                val profile=com.meshlit.agent.AgentProfile(name=newAgentName.trim(),modelId=selectedModel,instruction=newAgentInstruction.trim())
                saveAgents(savedAgents+profile)
                if(agentStoreError.isBlank()) { newAgentName="";newAgentInstruction="" }
            }) { Text("Save disabled agent") }
            if(agentStoreError.isNotBlank()) Text(agentStoreError,color=MaterialTheme.colorScheme.error)
        } } }
        items(savedAgents,key={it.id}) { profile -> Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(profile.name,style=MaterialTheme.typography.titleMedium)
            Text(choices.firstOrNull{it.first==profile.modelId}?.second ?: "Model unavailable · select a new agent model",style=MaterialTheme.typography.bodySmall)
            if(profile.instruction.isNotBlank()) Text("Instruction: ${profile.instruction.take(180)}",style=MaterialTheme.typography.bodySmall)
            Row { Text("Enabled",Modifier.weight(1f));Switch(profile.enabled,{saveAgents(savedAgents.map { if(it.id==profile.id) it.copy(enabled=!it.enabled) else it })}) }
            OutlinedTextField(agentTasks[profile.id].orEmpty(),{agentTasks[profile.id]=it.take(4000)},Modifier.fillMaxWidth(),label={Text("Task for ${profile.name}")},maxLines=3)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(enabled=profile.enabled && backend.delegated(AgentBackend.Scope.MODELS) && choices.any{it.first==profile.modelId} && agentTasks[profile.id].orEmpty().isNotBlank(),onClick={scope.launch {
                    parallelError=null
                    try { backend.controller.submit(com.meshlit.core.mcp.control.AgentCommand(
                        requestId="profile-${profile.id.take(8)}-${java.util.UUID.randomUUID()}",operation=com.meshlit.core.mcp.control.AgentOperation.MODEL_GENERATE,
                        modelId=profile.modelId,prompt=listOf(profile.instruction,agentTasks[profile.id].orEmpty()).filter{it.isNotBlank()}.joinToString("\n\n"),maxTokens=maxTokens)) }
                    catch(error:CancellationException) { throw error }
                    catch(error:Exception) { parallelError=error.message ?: "Agent job failed to queue" }
                }}) { Text("Run task") }
                TextButton(onClick={saveAgents(savedAgents.filterNot{it.id==profile.id});agentTasks.remove(profile.id)}) { Text("Delete agent") }
            }
        } } }
        items(AgentBackend.Scope.entries){permission->Row{Text(permission.name,Modifier.weight(1f));Switch(backend.delegated(permission),{backend.setDelegated(permission,it);revision++})}}
        item { Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Parallel agent jobs",style=MaterialTheme.typography.titleMedium)
            Text("Run 2–4 independent prompts as separate typed jobs. At most two execute at once; others wait. One loaded phone model serializes its own inference, while separately approved online profiles may run concurrently. Each job can be cancelled independently.",style=MaterialTheme.typography.bodySmall)
            Text("Selected model: $selectedLabel",style=MaterialTheme.typography.bodySmall)
            Text("Maximum output tokens: $maxTokens")
            Slider(maxTokens.toFloat(),{maxTokens=it.toInt()},valueRange=64f..1024f)
            prompts.indices.forEach { index -> OutlinedTextField(prompts[index],{prompts[index]=it.take(4000)},Modifier.fillMaxWidth(),label={Text("Agent ${index+1} task")},maxLines=3) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=prompts.size<4,onClick={prompts.add("")}) { Text("Add agent") }
                TextButton(enabled=prompts.size>2,onClick={prompts.removeAt(prompts.lastIndex)}) { Text("Remove last") }
            }
            Button(enabled=backend.delegated(AgentBackend.Scope.MODELS) && selectedModel.isNotBlank() && prompts.count{it.isNotBlank()}>=2,
                onClick={scope.launch {
                    parallelError=null
                    try {
                        val tasks=prompts.filter{it.isNotBlank()}.toList()
                        tasks.forEachIndexed { index,task -> backend.controller.submit(com.meshlit.core.mcp.control.AgentCommand(
                            requestId="parallel-${java.util.UUID.randomUUID()}-$index",operation=com.meshlit.core.mcp.control.AgentOperation.MODEL_GENERATE,
                            modelId=selectedModel,prompt=task,maxTokens=maxTokens)) }
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { parallelError=e.message ?: "Could not queue agent jobs" }
                }}) { Text("Start ${prompts.count{it.isNotBlank()}} jobs") }
            if(!backend.delegated(AgentBackend.Scope.MODELS)) Text("Enable MODELS delegation above before starting agent jobs.",style=MaterialTheme.typography.bodySmall)
            parallelError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        } } }
        item{Text("${jobs.count{!it.terminal}} active typed agent jobs · ${tools.size} registered MCP tools")
            OutlinedButton(onClick={scope.launch{backend.controller.ready.await();backend.controller.jobs.value.filter{!it.terminal}.forEach{backend.controller.cancel(it.command.requestId)}}}){Text("Cancel active typed agent jobs")}
            OutlinedTextField(query,{query=it},label={Text("Filter registered tools")});TextButton(onClick={revision++}){Text("Refresh registry")}}
        items(jobs.filter{it.command.requestId.startsWith("parallel-") || it.command.requestId.startsWith("profile-")}.takeLast(12).asReversed(),key={it.command.requestId}) { job ->
            Card { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("${job.phase} · ${job.command.modelId}",style=MaterialTheme.typography.titleSmall)
                Text(job.command.prompt.orEmpty().take(180),style=MaterialTheme.typography.bodySmall)
                job.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                job.result?.let{Text(it.toString().take(2000),style=MaterialTheme.typography.bodySmall)}
                if(!job.terminal) TextButton(onClick={scope.launch{backend.controller.cancel(job.command.requestId)}}) { Text("Cancel this job") }
            } }
        }
        items(tools.filter{"${it.name} ${it.description}".contains(query,true)},key={it.name}){tool->Text("${tool.name}\n${tool.description}",style=MaterialTheme.typography.bodySmall)}
    }
}
