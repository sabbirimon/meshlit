package com.meshlit.ui.modern
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.chat.*
import com.meshlit.providers.OnlineProviders
import com.meshlit.di.koinInject
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun ChatOptionsDialog(state:ChatState,controller:ChatController,onSearchSettings:()->Unit,onShardingSettings:()->Unit,onClose:()->Unit) {
    val providers=koinInject<OnlineProviders>();val profiles by providers.profiles.collectAsStateWithLifecycle()
    val router=koinInject<com.meshlit.routing.ModelRoutes>();val routes by router.routes.collectAsStateWithLifecycle()
    val colibri=koinInject<com.meshlit.colibri.ColibriBackend>()
    val colibriConfig by colibri.config.collectAsStateWithLifecycle()
    val searchService=koinInject<com.meshlit.search.AppSearchService>()
    val searchAccess by searchService.access.state.collectAsStateWithLifecycle()
    val operationGate=koinInject<com.meshlit.operations.OperationsControl>().gate
    val operationPolicy by operationGate.policy.collectAsStateWithLifecycle()
    var hasWebKey by remember {mutableStateOf<Boolean?>(null)}
    var webKeyReadFailed by remember {mutableStateOf(false)}
    LaunchedEffect(searchService,searchAccess) {
        try {hasWebKey=withContext(Dispatchers.IO){searchService.hasWebKey()};webKeyReadFailed=false}
        catch(e:CancellationException){throw e}
        catch(_:Exception){hasWebKey=null;webKeyReadFailed=true}
    }
    var options by remember{mutableStateOf(state.current?.options ?: ChatOptions())}
    var budget by remember{mutableStateOf(options.maxTokens.toString())}
    val maxOutput=if(options.routeId==null) 2048 else 1024
    val validBudget=budget.toIntOrNull()?.let{it in 1..maxOutput}==true
    LaunchedEffect(options.routeId){if((budget.toIntOrNull() ?: 0)>maxOutput) budget=maxOutput.toString()}
    val coordinator=koinInject<com.meshlit.core.inference.InferenceCoordinator>()
    val context=LocalContext.current
    val savedAgents=remember(context){runCatching{com.meshlit.agent.AgentProfileStore(context).load()}.getOrDefault(emptyList())}
    val library=koinInject<com.meshlit.models.ModelLibrary>()
    val installedModels by library.models.collectAsStateWithLifecycle()
    val pipeline=koinInject<com.meshlit.pipeline.PipelineHost>()
    val pipelineStatus by pipeline.status.collectAsStateWithLifecycle()
    var selectedGroup by remember { mutableStateOf(pipeline.activeGroup()) }
    var groups by remember { mutableStateOf(pipeline.groups()) }
    val scope=rememberCoroutineScope()
    val runtime by coordinator.state.collectAsStateWithLifecycle()
    val evidence by controller.clusterEvidence.collectAsStateWithLifecycle()
    val capacity=(runtime as? com.meshlit.core.inference.CoordinatorState.Ready)?.model?.contextSize?.takeIf{it>0}
    var error by remember{mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
    Column(Modifier.fillMaxWidth().heightIn(max=720.dp).navigationBarsPadding().imePadding().padding(horizontal=12.dp)) {
    Text("Conversation settings",style=MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("chat-settings"),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Access shown in Chat reflects this conversation's saved route and tool switches. A switch only permits a request: each tool also needs its separate app, agent and device grant. Online provider selection sends chat text to that endpoint when you press Send.",style=MaterialTheme.typography.bodySmall)
        SettingsFeatureSection("Task for this chat") {
            Text("Choose a starting task. This replaces only this chat's draft instructions and tool groups. Save applies it; Cancel keeps the current chat unchanged. App-wide permissions and agent profiles are never enabled here.",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                com.meshlit.agent.AgentTaskPresets.presets.forEach { preset ->
                    val reason=ChatTaskPreset.unavailableReason(preset,options,com.meshlit.BuildProfile.coreCandidate,com.meshlit.BuildConfig.PLAY_REVIEW)
                    val selected=options.systemPrompt==preset.instruction && ChatToolGroup.entries.filter { it.selected(options) }.toSet()==preset.suggestedTools
                    FilterChip(selected,onClick={options=ChatTaskPreset.apply(preset,options,com.meshlit.BuildProfile.coreCandidate,com.meshlit.BuildConfig.PLAY_REVIEW);error=null},
                        enabled=reason==null,label={Text(preset.name)},modifier=Modifier.testTag("chat-task-${preset.key}"))
                }
            }
            com.meshlit.agent.AgentTaskPresets.presets.mapNotNull {
                ChatTaskPreset.unavailableReason(it,options,com.meshlit.BuildProfile.coreCandidate,com.meshlit.BuildConfig.PLAY_REVIEW)
            }.distinct().forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
        }
        if(savedAgents.any{it.enabled && !it.modelId.startsWith("cloud:")}) {
            Text("Local agent profiles",style=MaterialTheme.typography.titleMedium)
            Text("Apply a saved agent's instructions and local route to this conversation. Existing chat tool selections are kept; the profile does not grant app-wide or device access.",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                savedAgents.filter{it.enabled && !it.modelId.startsWith("cloud:")}.forEach{profile->
                    val available=installedModels.any{it.id==profile.modelId && it.path==coordinator.loadedModel()?.modelPath}
                    AssistChip(onClick={options=options.copy(systemPrompt=profile.instruction,onlineProfileId=null,routeId=null,colibriMode="OFF")},
                        enabled=available,label={Text(if(available) profile.name else "${profile.name} · load its model")})
                }
            }
        }
        SettingsFeatureSection("Conversation style") {
        Text("Companion personality",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompanionStyle.entries.forEach { style ->
                FilterChip(options.companionStyle==style,{options=options.copy(companionStyle=style)},label={Text(style.name.lowercase().replaceFirstChar{it.uppercase()})},modifier=Modifier.testTag("chat-style-${style.name}"))
            }
        }
        Text("Warmth: ${when(options.companionWarmth){0->"Neutral";1->"Natural";else->"Warm"}}")
        Slider(options.companionWarmth.toFloat(),{options=options.copy(companionWarmth=it.toInt())},valueRange=0f..2f,steps=1,modifier=Modifier.testTag("chat-warmth"))
        Text("Style changes response tone. It cannot grant tools or make a small model more capable.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(options.systemPrompt,{options=options.copy(systemPrompt=it.take(4000))},Modifier.fillMaxWidth(),label={Text("Your custom instructions")},maxLines=4)
        Row { Text("18+ topics preference",Modifier.weight(1f));Switch(options.matureTopics,{options=options.copy(matureTopics=it)},modifier=Modifier.testTag("chat-mature-topics")) }
        Text("Off by default. On requests open discussion of mature topics; the selected model or provider may still limit replies. This switch does not verify age or change tool permissions.",style=MaterialTheme.typography.bodySmall)
        }
        SettingsFeatureSection("Model and routing") {
        FilterChip(options.onlineProfileId==null && options.routeId==null && options.colibriMode=="OFF",{options=options.copy(onlineProfileId=null,routeId=null,colibriMode="OFF")},label={Text(if(pipelineStatus.coordinator) "Paired cluster · currently loaded model" else "Local · currently loaded model")})
        if (!com.meshlit.BuildProfile.coreCandidate) {
            Text(if (pipelineStatus.coordinator) "Cluster active: ${pipeline.activeGroup()} · ${pipeline.groupPeers().size} paired workers. Chat uses the cluster's loaded model when Local is selected."
                else "No cluster active. Chat uses the loaded phone model when Local is selected. A paired group must be negotiated before cluster chat is available.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onShardingSettings) { Text("Manage cluster") }
                if (pipelineStatus.coordinator) TextButton(enabled=!state.running,onClick={scope.launch {
                    try { pipeline.stopPipeline(); error=null } catch(e:Exception) { error=e.message }
                }}) { Text("Remove cluster from chat") }
            }
            Text("Approved worker group for the next cluster load",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                groups.forEach { group -> FilterChip(selectedGroup==group,onClick={
                    selectedGroup=group;error=null
                },enabled=!pipelineStatus.coordinator && !pipelineStatus.starting,label={Text("$group · ${pipeline.peers().count{it.group==group}} workers")}) }
            }
            Text("Save selects the worker group for the next cluster load. Cancel keeps the existing group. It does not move an active conversation; stop the current cluster first.")
            if (pipelineStatus.coordinator) Text("Removing the cluster unloads its model. Load a phone model in Models before sending locally.")
            Text("Agent tools below run through the selected local or cluster model only when their separate grants are enabled. Online profiles and model routes use their own controls.")
        }
        profiles.filter{it.enabled && !com.meshlit.BuildProfile.coreCandidate}.forEach{profile->FilterChip(options.onlineProfileId==profile.id && options.routeId==null,{options=options.copy(onlineProfileId=profile.id,routeId=null,colibriMode="OFF",webTools=false,phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,gibberlinkTools=false,remoteTools=false,peerTools=false)},label={Text("${profile.sourceKind.name.lowercase().replace('_',' ')} · ${profile.name} / ${profile.model}")})}
        if(!com.meshlit.BuildProfile.coreCandidate && routes.any{it.enabled}) {
            FilterChip(options.routeId=="__auto__",{options=options.copy(routeId="__auto__",onlineProfileId=null,colibriMode="OFF",webTools=false,phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,gibberlinkTools=false,remoteTools=false,peerTools=false,maxTokens=minOf(options.maxTokens,1024))},label={Text("Model router · automatic rules")})
            routes.filter{it.enabled}.forEach{route->FilterChip(options.routeId==route.id,{options=options.copy(routeId=route.id,onlineProfileId=null,colibriMode="OFF",webTools=false,phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,gibberlinkTools=false,remoteTools=false,peerTools=false,maxTokens=minOf(options.maxTokens,1024))},label={Text("Route · ${route.name} / ${route.mode}")})}
            if(options.routeId!=null) OutlinedTextField(options.routeScenario,{options=options.copy(routeScenario=it.lowercase())},label={Text("Route scenario")})
        }
        if(!com.meshlit.BuildProfile.coreCandidate) {
            Text("Colibri host",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                com.meshlit.workspace.ColibriMode.entries.forEach{mode->FilterChip(options.colibriMode==mode.name,{options=if(mode==com.meshlit.workspace.ColibriMode.OFF) options.copy(colibriMode=mode.name) else options.copy(colibriMode=mode.name,onlineProfileId=null,routeId=null,webTools=false,phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,gibberlinkTools=false,remoteTools=false,peerTools=false)},label={Text(mode.name.lowercase().replaceFirstChar{it.uppercase()})},modifier=Modifier.testTag("chat-colibri-${mode.name}"))}
            }
            Text("On needs saved host access and a model refreshed within 5 minutes. Auto keeps local inference unless no local model is ready or you prefer the host. Configure Settings → Colibri host. No automatic download or host-error fallback.",style=MaterialTheme.typography.bodySmall)
            Row{Text("Allow agent mode requests for this chat",Modifier.weight(1f));Switch(options.colibriAgentAllowed,{options=options.copy(colibriAgentAllowed=it)},enabled=options.colibriAgentAllowed || (colibriConfig.hostAccess && colibriConfig.agentSwitching),modifier=Modifier.testTag("chat-colibri-agent-grant"))}
            Text("Also requires the saved host agent grant and Models/cloud/automation delegation. Agent requests expire after 30 minutes; local tool conversations retain their on-device route.",style=MaterialTheme.typography.bodySmall)
        }
        Text("Configure API profiles in Settings → Online providers. Online sends this conversation to the selected endpoint and may incur charges. Replies are buffered; local replies stream.")
        }
        SettingsFeatureSection("Permissions and tools") {
        Text("This conversation only. Main Settings controls device-wide permission; Save here does not change another chat.",style=MaterialTheme.typography.bodySmall)
        if(operationPolicy.agentsPaused || operationPolicy.emergencyStopped) {
            Text(if(operationPolicy.emergencyStopped) "App-wide emergency stop is active. Resume in Settings → Operations."
                else "Agents are paused for this device. Enable Agents on this device in main Settings → Agent management.",color=MaterialTheme.colorScheme.error)
        }
        if(com.meshlit.BuildProfile.coreCandidate) {
            Text("Tools are unavailable in the Core candidate build. This build supports local chat, models and files; use the Experimental app for agent tools.",modifier=Modifier.testTag("chat-tools-build-reason"))
        } else if(!options.supportsLocalToolSelection) {
            Text("Chat tools currently run through a local or paired-cluster model. This conversation is using an online provider, model route or Colibri host. Saved service and device permissions remain separate.",modifier=Modifier.testTag("chat-tools-route-reason"))
            OutlinedButton(onClick={options=options.copy(onlineProfileId=null,routeId=null,colibriMode="OFF")},modifier=Modifier.testTag("chat-tools-use-local")) {Text("Use local model for tools")}
            Text("Save applies the route change. Load a capable local model before sending; switching here does not load one or grant tool access.",style=MaterialTheme.typography.bodySmall)
        } else {
            Text("Selected tools: ${options.toolCount}/10. Combine groups as needed; each action still requires its own saved grant.",style=MaterialTheme.typography.bodySmall,color=if(options.toolCount>10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            ChatToolSwitch(ChatToolGroup.MEMORY,options){options=it}
            Text("Requires saved agent management in Memory and personality. Agents can manage switches and facts; recall is applied only to on-device chat.")
            ChatToolSwitch(ChatToolGroup.LOCAL_SEARCH,options){options=it}
            Text("The on-device model can search saved app content only after your separate local-content agent grant. No Internet is needed.")
            ChatToolSwitch(ChatToolGroup.WEB,options,label="This chat: web access"){options=it}
            Text("App-wide search: ${if(searchAccess.webEnabled) "on" else "off"} · agent search: ${if(searchAccess.agentWeb) "allowed" else "not allowed"} · ${if(searchAccess.agentWebPaused) "paused" else "not paused"}. Brave key: ${when {webKeyReadFailed->"could not read status";hasWebKey==true->"stored";hasWebKey==false->"missing";else->"checking"}}.",
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("chat-web-global-status"))
            Text("This switch changes only the current chat after Save. It cannot turn on app-wide Internet or agent access.",style=MaterialTheme.typography.bodySmall)
            Text("The local model can request web_search using your configured Brave API key, Internet switch and agent grant. Queries go to Brave. crawl_url also requires the HTTPS Crawl4AI companion and approved domains; URLs and fetched content go to that host. Neither service is silently enabled.")
            TextButton(onClick=onSearchSettings){Text("App-wide search setup")}
            ChatToolSwitch(ChatToolGroup.PHONE,options,label="This chat: phone-agent access"){options=it}
            Text("Runs with the loaded on-device model and needs no external host. Requires saved autonomy, app scope and Android Accessibility in Settings → Phone agent and autonomy. Install/remove/permission requests use Android human confirmation; no silent OS grant.")
            ChatToolSwitch(ChatToolGroup.NODE,options){options=it}
            Text("Uses only saved node actions and the configured guest, with separate per-key/host and VM agent grants. No agent app/root shell or permission changes. The combined chat tool list is limited to ten. VM/QEMU and remote nodes must actually be available.")
            ChatToolSwitch(ChatToolGroup.CRYPTO,options){options=it}
            Text("Requires the saved cryptography agent grant. Keys/tool results are visible to the model and saved chat; keep real credentials in the vault.")
            ChatToolSwitch(ChatToolGroup.AUDIO,options){options=it}
            Text("Saved audio/agent and Media operation grants plus a visible GibberLink screen are required. Incoming English transcripts never auto-execute.")
            ChatToolSwitch(ChatToolGroup.REMOTE,options){options=it}
            Text("Commands only saved fingerprint-pinned SSH nodes within per-node action grants. Configure Device and cluster commands plus SSH delegation.")
            ChatToolSwitch(ChatToolGroup.PEER,options){options=it}
            Text("Requires the foreground, manually paired peer and saved P2P/agent grants. Receiver approves command scopes independently; native layer inference over Internet is not implemented.")
            Text("Experimental local tool protocol: up to eight calls and five minutes with phone tools, otherwise three calls and 180 seconds. Stop cancels it. A model trained for JSON/tool use is required; invalid output dispatches no action for that step. The starter model is not qualified for reliable autonomy.")
        }
        }
        SettingsFeatureSection("Token management") {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(options.outputBudgetMode==OutputBudgetMode.MANUAL,{options=options.copy(outputBudgetMode=OutputBudgetMode.MANUAL)},label={Text("Manual")},modifier=Modifier.testTag("chat-budget-manual"))
            if(!com.meshlit.BuildProfile.coreCandidate) FilterChip(options.outputBudgetMode==OutputBudgetMode.AUTOMATIC,{options=options.copy(outputBudgetMode=OutputBudgetMode.AUTOMATIC)},label={Text("Automatic cluster")},modifier=Modifier.testTag("chat-budget-automatic"))
        }
        if(options.outputBudgetMode==OutputBudgetMode.AUTOMATIC) {
            Text("Target output duration: ${options.outputTargetSeconds}s")
            Slider(options.outputTargetSeconds.toFloat(),{options=options.copy(outputTargetSeconds=it.toInt())},valueRange=5f..120f)
            val decision=clusterOutputBudget(options.copy(maxTokens=budget.toIntOrNull()?.coerceIn(1,maxOutput) ?: options.maxTokens),coordinator.engineTag,coordinator.loadedModel(),evidence,android.os.SystemClock.elapsedRealtime())
            Text(decision.explanation,modifier=Modifier.testTag("chat-budget-explanation"))
            Text("Uses the last successful native cluster decode rate from this exact loaded session, with at least 8 reported output tokens and age ≤10 minutes. Device rates are not added. The result stays below your ceiling and ¼ of verified context; it is not remaining-context accounting. Unknown evidence falls back to Manual.")
        }
        OutlinedTextField(budget,{budget=it.take(5)},Modifier.fillMaxWidth().testTag("chat-output-budget"),label={Text("Maximum output tokens")},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=!validBudget,supportingText={Text("1–$maxOutput · a ceiling, not a guaranteed reply length")})
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(256,512,1024,2048).filter{it<=maxOutput}.forEach{limit->FilterChip(budget==limit.toString(),{budget=limit.toString()},label={Text(limit.toString())})}
        }
        Text("History messages: ${options.historyMessages}");Slider(options.historyMessages.toFloat(),{options=options.copy(historyMessages=it.toInt())},valueRange=0f..20f,steps=19)
        Text("History is selected by message count, not measured token count. Reduce history for a smaller context; saved messages are kept.",style=MaterialTheme.typography.bodyMedium)
        Text(if(options.onlineProfileId==null && options.routeId==null) "Loaded context capacity: ${capacity ?: "unknown"} tokens. Model metadata and runtime capacity are different." else "Provider/routed context capacity is not reported here.",style=MaterialTheme.typography.bodyMedium)
        Row{Text("Show token speed indicator",Modifier.weight(1f));Switch(options.showTokenStats,{options=options.copy(showTokenStats=it)},Modifier.semantics{contentDescription="Show token speed indicator"})}
        Text("Rates use authoritative runtime/provider counts after completion. Native runtime rate is preferred; otherwise end-to-end average is labelled. Unreported counts stay unavailable. Text callbacks and characters are not tokens.",style=MaterialTheme.typography.bodyMedium)
        }
        SettingsFeatureSection("Sampling") {
        Text("Temperature: ${"%.2f".format(options.temperature)}");Slider(options.temperature,{options=options.copy(temperature=it)},valueRange=0f..2f)
        Text("Context capacity and weight quantization are configured per local model in Models. Online temperature is sent only when enabled for that profile.")
        }
    }
    error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("chat-options-error"))}
    if(options.toolCount>10) Text("Select at most ten tools. Turn off a group to save.",color=MaterialTheme.colorScheme.error)
    Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.End) {
        TextButton(onClick=onClose){Text("Cancel")}
        Button(enabled=validBudget && options.toolCount<=10 && !state.running,onClick={try{val next=options.copy(maxTokens=budget.toInt());next.validate();com.meshlit.BuildProfile.requireChat(next)
            check(controller.ready.isCompleted){"Chat history is still loading"}
            check(!controller.state.value.running){"Wait for the current reply"}
            val previousGroup=pipeline.activeGroup()
            if(selectedGroup!=previousGroup) {
                check(!pipeline.status.value.coordinator && !pipeline.status.value.starting){"Stop the active cluster before changing its worker group"}
                pipeline.setActiveGroup(selectedGroup)
            }
            try{controller.setOptions(next)}catch(failure:Exception){
                if(selectedGroup!=previousGroup) runCatching{pipeline.setActiveGroup(previousGroup)}
                    .onFailure{throw IllegalStateException("Chat settings failed and the prior worker group could not be restored. Review Sharding settings.",failure)}
                throw failure
            }
            onClose()}catch(e:Exception){error=e.message}}){Text("Save")}
    }
    }}
}

@Composable private fun ChatToolSwitch(group:ChatToolGroup,options:ChatOptions,label:String=group.label,onChange:(ChatOptions)->Unit) {
    val reason=group.unavailableReason(options,com.meshlit.BuildConfig.PLAY_REVIEW)
    val tag=if(group==ChatToolGroup.LOCAL_SEARCH) "chat-local-search-tools" else "chat-tool-${group.name.lowercase()}"
    Column {
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("$label · ${group.count} tools",Modifier.weight(1f))
            Switch(group.selected(options),{onChange(group.update(options,it,com.meshlit.BuildConfig.PLAY_REVIEW))},
                enabled=reason==null,modifier=Modifier.testTag(tag).semantics{contentDescription=label})
        }
        reason?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
