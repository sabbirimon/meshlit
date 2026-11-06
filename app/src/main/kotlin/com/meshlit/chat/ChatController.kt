package com.meshlit.chat

import android.content.Context
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.HostedSearchEvidence
import com.meshlit.core.inference.models.OnlineMessage
import com.meshlit.core.inference.models.OnlineProfile
import com.meshlit.core.inference.models.hostedToolsAllowed
import com.meshlit.core.common.MeshlitResult
import com.meshlit.recovery.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable data class ChatOptions(val onlineProfileId:String?=null,val systemPrompt:String="",val maxTokens:Int=1024,val temperature:Float=0.4f,val historyMessages:Int=10,val routeId:String?=null,val routeScenario:String="general",val webTools:Boolean=false,val phoneTools:Boolean=false,val memoryTools:Boolean=false,val showTokenStats:Boolean=true,val localSearchTools:Boolean=false,val outputBudgetMode:OutputBudgetMode=OutputBudgetMode.MANUAL,val outputTargetSeconds:Int=30,val nodeTools:Boolean=false,val colibriMode:String="OFF",val colibriAgentAllowed:Boolean=false,val cryptoTools:Boolean=false,val gibberlinkTools:Boolean=false,val remoteTools:Boolean=false,val peerTools:Boolean=false,val companionStyle:CompanionStyle=CompanionStyle.WARM,val companionWarmth:Int=1,val matureTopics:Boolean=false,val teamTools:Boolean=false,val connectionTools:Boolean=false,
    @kotlinx.serialization.Transient val approvalMode:ChatApprovalMode=ChatApprovalMode.ASK,
    val reasoningEffort:com.meshlit.core.inference.models.ReasoningEffort=com.meshlit.core.inference.models.ReasoningEffort.AUTO,
    val executionMode:ChatExecutionMode=ChatExecutionMode.CHAT,
    val hostedToolReviewId:String?=null) {
    val usesLocalTools get()=connectionTools || webTools || phoneTools || memoryTools || localSearchTools || nodeTools || cryptoTools || remoteTools || peerTools || teamTools
    val hasHostedWebGrant get()=hostedToolReviewId!=null && onlineProfileId!=null && routeId==null && colibriMode=="OFF" && !usesLocalTools
    val canSelectAgentMode get()=usesLocalTools && supportsLocalToolSelection || hasHostedWebGrant
    val agentToolsActive get()=executionMode==ChatExecutionMode.AGENT && usesLocalTools && supportsLocalToolSelection
    val hostedAgentToolsActive get()=executionMode==ChatExecutionMode.AGENT && hasHostedWebGrant
    val toolCount get()=(if(connectionTools)1 else 0)+(if(teamTools)3 else 0)+(if(nodeTools)10 else 0)+(if(remoteTools)3 else 0)+(if(peerTools)4 else 0)+(if(cryptoTools)1 else 0)+(if(memoryTools)1 else 0)+(if(localSearchTools)1 else 0)+(if(webTools)3 else 0)+(if(phoneTools)4 else 0)
    fun validate(){require(hostedToolReviewId==null || hasHostedWebGrant && hostedToolReviewId.matches(Regex("[a-f0-9-]{36}"))){"Hosted web access must bind a single provider and cannot include local tools"};require(executionMode!=ChatExecutionMode.AGENT || canSelectAgentMode){"Agent mode needs selected local tools or a reviewed hosted web connection"};require(!hostedAgentToolsActive || maxTokens>=64){"Hosted Agent mode needs at least 64 output tokens"};require(companionWarmth in 0..2);require(toolCount<=10){"Select at most ten chat tools; deselect another group"};require(colibriMode in com.meshlit.workspace.ColibriMode.entries.map{it.name});require(outputTargetSeconds in 5..120);require(!usesLocalTools || (onlineProfileId==null && routeId==null)){"Chat tools currently require an on-device model"};require(routeId==null || routeId=="__auto__" || routeId.matches(Regex("[A-Za-z0-9_-]{1,64}")));require(routeScenario.matches(Regex("[a-z0-9_-]{1,40}")));require(routeId==null || (onlineProfileId==null && maxTokens<=1024));require(reasoningEffort==com.meshlit.core.inference.models.ReasoningEffort.AUTO || onlineProfileId!=null || routeId!=null){"This runner has no reasoning effort control; choose Auto"};require(systemPrompt.length<=4000 && maxTokens in 1..(if(onlineProfileId!=null)16384 else 2048) && temperature.isFinite() && temperature in 0f..2f && historyMessages in 0..20)}
}
data class PhotoChatRequest(val uri:android.net.Uri,val profile:OnlineProfile?=null,val offline:com.meshlit.media.LocalVisionModel?=null) {
    init {require((profile!=null)!=(offline!=null)){"Choose one photo destination"}}
    val label:String get()=profile?.let{"${it.name} · ${it.model}"} ?: "On-device photo · ${checkNotNull(offline).label}"
}

@Serializable data class ChatMessage(val id:String=UUID.randomUUID().toString(),val role:String,val text:String,val usage:ChatTokenUsage?=null,val attachmentNote:String?=null,val source:String?=null,val status:String?=null,val knowledgeSources:List<String> = emptyList(),val webSources:List<ChatWebSource> = emptyList(),val deviceOffer:com.meshlit.core.mcp.control.DeviceConnectionOffer?=null,val deviceReceipt:com.meshlit.core.mcp.control.DeviceConnectionReceipt?=null,val isolatedPhoto:Boolean=false)
@Serializable data class ChatConversation(val id:String=UUID.randomUUID().toString(),val title:String="New chat",
    val messages:List<ChatMessage> = emptyList(),val updatedAt:Long=System.currentTimeMillis(),val options:ChatOptions=ChatOptions(),val usageNote:String?=null,val draft:String="",val recoveryBinding:ReplicaChatBinding?=null)
data class ChatState(val conversations:List<ChatConversation> = emptyList(),val selectedId:String?=null,
    val running:Boolean=false,val error:String?=null,val generationStartedMs:Long?=null,val activeOutputLimit:Int?=null,val operationLabel:String="Generating",val activeTool:String?=null) {
    val current get()=conversations.firstOrNull { it.id==selectedId }
}

class ChatController(private val context:Context,private val coordinator:InferenceCoordinator,private val appScope:CoroutineScope,private val providers:com.meshlit.providers.OnlineProviders,private val router:com.meshlit.routing.ModelRoutes,private val localTools:LocalChatTools,private val memory:PersonalMemory,private val library:com.meshlit.models.ModelLibrary,private val colibri:com.meshlit.colibri.ColibriBackend,private val media:com.meshlit.media.MediaGeneration,private val knowledge:com.meshlit.knowledge.KnowledgeLibrary,private val localVision:com.meshlit.media.LocalVisionHost) {
    private val _state=MutableStateFlow(ChatState())
    val state:StateFlow<ChatState> = _state.asStateFlow()
    private val file=File(context.filesDir,"conversations-v1.json")
    private val selectionFile=File(context.filesDir,"chat-selection-v1.txt")
    private val json=Json { ignoreUnknownKeys=true }
    private val persistence=Mutex()
    private var generation:Job?=null
    private var draftSave:Job?=null
    internal val toolApprovals=ChatToolApproval()
    private val evidence=MutableStateFlow<ClusterOutputEvidence?>(null)
    val clusterEvidence=evidence.asStateFlow()
    fun budgetDecision(options:ChatOptions)=clusterOutputBudget(options,coordinator.engineTag,coordinator.loadedModel(),evidence.value,android.os.SystemClock.elapsedRealtime())
    @Volatile var generationId:String?=null; private set
    val ready=appScope.async(Dispatchers.IO) {
        val items=runCatching { json.decodeFromString<List<ChatConversation>>(file.readText()) }.getOrDefault(emptyList()).take(40).map{c->c.copy(messages=c.messages.map{m->if(m.status=="Analyzing photo") m.copy(status="Interrupted · attach the photo again to retry") else m})}
        _state.value=ChatState(items,restoredChatSelection(items,runCatching{selectionFile.readText()}.getOrNull()))
    }
    private suspend fun save()=persistence.withLock { withContext(Dispatchers.IO) {
        val temp=File(file.parentFile,file.name+".tmp")
        temp.writeText(json.encodeToString(_state.value.conversations.take(40)))
        check(temp.renameTo(file)) { "Cannot save conversation history" }
        val selectionTemp=File(selectionFile.parentFile,selectionFile.name+".tmp")
        selectionTemp.writeText(_state.value.selectedId.orEmpty())
        check(selectionTemp.renameTo(selectionFile)) { "Cannot save conversation selection" }
    } }
    fun newChat() {
        if(!ready.isCompleted || _state.value.running) return
        val item=ChatConversation()
        _state.update { it.copy(conversations=(listOf(item)+it.conversations).take(40),selectedId=item.id,error=null) }
        appScope.launch { runCatching { save() } }
    }
    /** A reviewed inbox item becomes a local Chat draft and never starts a model. */
    internal suspend fun createBotDraft(source: String, eventId: String, text: String) {
        ready.await()
        withContext(Dispatchers.Main.immediate) {
            val previous = _state.value
            check(!previous.running) { "Finish the current reply first" }
            val id = UUID.nameUUIDFromBytes("meshlit-bot-draft:$source:$eventId".toByteArray()).toString()
            if (previous.conversations.any { it.id == id }) {
                select(id)
                return@withContext
            }
            check(previous.conversations.size < 40) { "Make room for a new chat" }
            val content = text.lines().joinToString("\n") { "> $it" }
            val item = ChatConversation(
                id = id,
                title = "Bot message · ${source.take(40)}",
                draft = "Review this untrusted message from $source.\n\n$content",
            )
            require(item.draft.length <= 12_000)
            _state.update { it.copy(conversations = listOf(item) + it.conversations, selectedId = item.id, error = null) }
            try { withContext(NonCancellable) { save() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update {
                    it.copy(conversations = it.conversations.filterNot { chat -> chat.id == item.id },
                        selectedId = if (it.selectedId == item.id) previous.selectedId else it.selectedId)
                }
                throw error
            }
        }
    }
    /** Per-chat draft, retained in the same private app history as messages. Never submitted automatically. */
    fun setDraft(id:String?,text:String) {
        require(text.length<=12000)
        if(!ready.isCompleted)return
        if(id==null && _state.value.current==null)newChat()
        val target=id ?: _state.value.selectedId ?: return
        if(_state.value.conversations.none{it.id==target})return
        _state.update{old->old.copy(conversations=old.conversations.map{if(it.id==target)it.copy(draft=text) else it})}
        draftSave?.cancel()
        draftSave=appScope.launch {
            delay(400)
            val saved=saveChatDraft{save()}
            _state.update { current ->
                if(saved) current.copy(error=current.error.takeUnless{it==DRAFT_SAVE_ERROR})
                else current.copy(error=DRAFT_SAVE_ERROR)
            }
        }
    }
    fun select(id:String) {
        if(ready.isCompleted && !_state.value.running && _state.value.conversations.any{it.id==id}) {
            _state.update { it.copy(selectedId=id,error=null) }
            appScope.launch {runCatching{save()}}
        }
    }
    fun delete(id:String) {
        if(!ready.isCompleted || _state.value.running) return
        if(_state.value.conversations.any{it.id==id && it.options.connectionTools})org.koin.core.context.GlobalContext.get().get<com.meshlit.devices.DeviceConnectionHost>().coordinator.cancelOwner("chat:$id")
        _state.update { old -> val remaining=old.conversations.filterNot { it.id==id }
            old.copy(conversations=remaining,selectedId=if(old.selectedId==id) remaining.firstOrNull()?.id else old.selectedId) }
        appScope.launch { runCatching { save() } }
    }
    fun setOptions(options:ChatOptions) {
        check(ready.isCompleted){"Chat history is still loading. Try saving again in a moment."}
        options.validate();com.meshlit.BuildProfile.requireChat(options);check(!_state.value.running);colibri.clearAgentModes()
        if(_state.value.current?.options?.connectionTools==true && (!options.connectionTools || !options.agentToolsActive))_state.value.selectedId?.let{org.koin.core.context.GlobalContext.get().get<com.meshlit.devices.DeviceConnectionHost>().coordinator.cancelOwner("chat:$it")}
        if(_state.value.current==null) newChat()
        _state.update{old->old.copy(conversations=old.conversations.map{if(it.id==old.selectedId) it.copy(options=options) else it})}
        appScope.launch{runCatching{save()}.onFailure{error->_state.update{it.copy(error="Cannot save chat options: ${error.javaClass.simpleName}")}}}
    }
    /** Check the actual reply route before asking someone to record a voice turn. */
    fun voiceRouteProblem():String? {
        if(!ready.isCompleted) return "Chats are still loading. Try again in a moment."
        if(state.value.running) return "Wait for the current chat reply before starting voice."
        val selected=state.value.current?.options ?: ChatOptions()
        if(selected.onlineProfileId!=null) return if(providers.profiles.value.any{it.id==selected.onlineProfileId && it.enabled}) null
            else "The chat provider is missing or disabled. Choose a reply model in Conversation settings."
        if(selected.routeId!=null || selected.colibriMode!="OFF" && !selected.usesLocalTools) return null
        return if(coordinator.loadedModel()==null) "Load a local chat model in Models before starting voice. Speech packs recognize and speak; they do not generate a reply." else null
    }
    fun send(text:String,photo:PhotoChatRequest?=null,voiceConversation:Boolean=false,connectionSelection:DeviceConnectionSelection?=null):String? {
        if(!ready.isCompleted || _state.value.running || text.isBlank()) return null
        require(text.length<=12000) { "Message is too long" }
        if(_state.value.current==null) newChat()
        val selected=_state.value.selectedId!!
        val user=ChatMessage(role="user",text=text.trim(),attachmentNote=photo?.let{"Photo attached · original image not retained"},isolatedPhoto=photo?.offline!=null)
        val assistant=ChatMessage(role="assistant",text="",source=photo?.label,status=photo?.let{"Analyzing photo"},isolatedPhoto=photo?.offline!=null)
        val configured=_state.value.current!!.options
        val hostedProfile:OnlineProfile?
        try { configured.validate();com.meshlit.BuildProfile.requireChat(configured)
            hostedProfile=if(photo==null && configured.hostedAgentToolsActive) {
                providers.profiles.value.singleOrNull{it.id==configured.onlineProfileId && it.hostedToolsAllowed(configured.hostedToolReviewId)}
                    ?: error("Hosted tool permission changed. Review the exact model in Online providers and this chat's settings.")
            } else null
            require(connectionSelection==null || configured.agentToolsActive && configured.connectionTools){"Choose Agent mode and device connection tools before connecting from chat"}
            if(photo!=null) {
                require(!com.meshlit.BuildProfile.coreCandidate){"Image analysis is unavailable in Core Candidate"}
                require(text.length<=4000){"Photo questions support up to 4,000 characters"}
                require(photo.uri.scheme=="content"){"Select a photo from your device"}
                val profile=photo.profile
                if(profile!=null){
                    require(profile.enabled && profile.model.isNotBlank() && profile.protocol in setOf(com.meshlit.core.inference.models.OnlineProtocol.OPENAI,com.meshlit.core.inference.models.OnlineProtocol.OPENAI_COMPATIBLE)){"Choose an enabled vision provider and model"}
                    require(providers.profiles.value.any{it==profile}){"Photo provider changed. Review it before sending."}
                }else require(localVision.available() && localVision.model.value==photo.offline){"Import a matching offline vision model and projector first"}
            }
        } catch(e:Exception) {
            _state.update { it.copy(error=e.message) };return null
        }
        val nativeConnectionRequest=if(photo==null && configured.agentToolsActive && configured.connectionTools) deviceConnectionChatIntent(text,configured) else null
        val nativeConnection= nativeConnectionRequest!=null || (connectionSelection!=null && configured.agentToolsActive && configured.connectionTools)
        val localRoute=photo==null && configured.onlineProfileId==null && configured.routeId==null && !configured.usesLocalTools
        val colibriDecision=if(localRoute) colibri.decision(selected,com.meshlit.workspace.ColibriMode.parse(configured.colibriMode),configured.colibriAgentAllowed,coordinator.state.value is CoordinatorState.Ready) else null
        if(colibriDecision?.route==com.meshlit.workspace.ColibriRoute.BLOCKED) { _state.update{it.copy(error=colibriDecision.reason)};return null }
        if(photo==null && configured.onlineProfileId==null && configured.routeId==null && colibriDecision?.route!=com.meshlit.workspace.ColibriRoute.HOST && coordinator.state.value !is CoordinatorState.Ready && !nativeConnection) {
            _state.update { it.copy(error="Load a model from Models before sending a message") };return null
        }
        val decision=budgetDecision(if(colibriDecision?.route==com.meshlit.workspace.ColibriRoute.HOST) configured.copy(outputBudgetMode=OutputBudgetMode.MANUAL) else configured)
        val options=configured.copy(maxTokens=if(photo!=null) 512 else decision.limit)
        val baseInstructions=companionInstructions(options)+if(voiceConversation) "\nThis is a spoken conversation. Reply naturally and concisely, usually in one to three sentences. Avoid markdown and long lists unless requested. Keep the configured identity and permissions." else ""
        val history=chatPromptHistory(_state.value.current!!.messages,options.historyMessages)
        _state.update { old -> old.copy(running=true,error=null,operationLabel=if(photo!=null) "Analyzing photo" else "Generating",generationStartedMs=android.os.SystemClock.elapsedRealtime(),activeOutputLimit=options.maxTokens,conversations=old.conversations.map { c ->
            if(c.id==selected) c.copy(title=if(c.messages.isEmpty()) text.trim().take(56) else c.title,
                messages=(c.messages+user+assistant).takeLast(100),updatedAt=System.currentTimeMillis(),draft="") else c }) }
        generationId=assistant.id
        generation=appScope.launch {
            val started=android.os.SystemClock.elapsedRealtime()
            var foregroundRequested=false
            fun recordUsage(usage:ChatTokenUsage) {_state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(usage=usage.copy(budgetReason=if(photo!=null) "Vision request limit; actual usage is reported separately" else decision.explanation)) else it}) else c})}}
            try {
                val remoteKnowledge = options.onlineProfileId != null || options.routeId != null || options.agentToolsActive ||
                    colibriDecision?.route == com.meshlit.workspace.ColibriRoute.HOST || coordinator.engineTag !in setOf("runanywhere", "llama-native-local")
                val grounding = if(photo!=null || hostedProfile!=null) "" to emptyList<String>() else knowledge.grounding(user.text,remoteKnowledge,agent=options.agentToolsActive).let{it.prompt to it.sources}
                val instructions = baseInstructions + grounding.first
                if (grounding.second.isNotEmpty()) _state.update { old -> old.copy(conversations = old.conversations.map { c ->
                    if (c.id == selected) c.copy(messages = c.messages.map { if (it.id == assistant.id) it.copy(knowledgeSources = grounding.second) else it }) else c
                }) }
                ready.await();save()
                androidx.core.content.ContextCompat.startForegroundService(context,
                    android.content.Intent(context,ChatInferenceService::class.java).putExtra("generation",assistant.id))
                foregroundRequested=true
                if(photo!=null) {
                    val profile=photo.profile
                    val reply=withTimeout(240_000) {
                        if(profile==null)localVision.analyze(checkNotNull(photo.offline),media.readImage(photo.uri),user.text)
                        else checkNotNull(media.generate(profile.id,"vision","",user.text,image=photo.uri,expectedProfile=profile).usage)
                    }
                    currentCoroutineContext().ensureActive()
                    val answer=reply.text.takeIf{it.isNotBlank()} ?: error("Vision model returned no answer")
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=answer,status=null) else it},usageNote="${photo.label} · original photo not retained") else c})}
                    recordUsage(chatTokenUsage(photo.label,512,reply.inputTokens,reply.outputTokens,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else if(colibriDecision?.route==com.meshlit.workspace.ColibriRoute.HOST) {
                    val (model,reply)=colibri.generate((history+user).map{com.meshlit.core.inference.models.OnlineMessage(it.role,it.text)},instructions,options.maxTokens,options.temperature)
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=reply.text) else it},usageNote="Colibri · $model · buffered text · ${colibriDecision.reason}") else c})}
                    recordUsage(chatTokenUsage("Colibri host · $model",options.maxTokens,reply.inputTokens,reply.outputTokens,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else if(options.routeId!=null) {
                    val prompt=buildString {
                        if(instructions.isNotBlank()) append("Instructions: ").append(instructions).append("\n\n")
                        history.forEach{append(it.role).append(": ").append(it.text.take(4000)).append('\n')}
                        append("user: ").append(user.text)
                    }
                    val result=router.execute(options.routeId.takeUnless{it=="__auto__"},options.routeScenario,prompt,options.maxTokens,effort=options.reasoningEffort)
                    if(!result.success) error("Route ${result.routeId} failed at step ${(result.failedStep ?: 0)+1}: ${result.error}")
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=result.text) else it},usageNote="Route ${result.routeId} · ${result.mode} · ${result.steps.size} completed steps") else c})}
                    recordUsage(chatTokenUsage("Routed run · counts not aggregated",options.maxTokens,null,null,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else if(hostedProfile!=null) {
                    val reviewId=checkNotNull(options.hostedToolReviewId)
                    val messages=(history+user).map{OnlineMessage(it.role,it.text)}
                    val gate=com.meshlit.operations.OperationsControl.get(context).gate
                    val firstLimit=minOf(512,options.maxTokens/2)
                    val (reply,calls)=gate.run(com.meshlit.core.common.control.ManagedFeature.AUTOMATION,true) {
                        withTimeout(240_000) {
                            val boundCredential=providers.requireHostedProfile(hostedProfile,reviewId)
                            val first=providers.hostedWebRound(hostedProfile,reviewId,boundCredential,messages,instructions,firstLimit,options.temperature,options.reasoningEffort)
                            val call=first.search
                            if(call==null) com.meshlit.core.inference.models.OnlineReply(checkNotNull(first.text),first.inputTokens,first.outputTokens) to 0
                            else {
                                _state.update{it.copy(operationLabel="Reviewing web search")}
                                val result=localTools.reviewedHostedSearch(call,authorize={require(providers.requireHostedProfile(hostedProfile,reviewId)==boundCredential){"Provider authorization changed during this Agent turn"}},
                                    review={request,origin->toolApprovals.review(ChatApprovalMode.ASK,request,origin)},
                                    onDispatch={_state.update{it.copy(operationLabel="Searching the web",activeTool="web_search")}})
                                val sources=chatSourcesFromTool("web_search",com.meshlit.core.mcp.McpToolResult.Json(result))
                                _state.update{old->old.copy(activeTool=null,operationLabel="Preparing hosted reply",conversations=old.conversations.map{c->
                                    if(c.id==selected)c.copy(messages=c.messages.map{m->if(m.id==assistant.id)m.copy(webSources=sources) else m})else c
                                })}
                                val last=providers.hostedWebRound(hostedProfile,reviewId,boundCredential,messages,instructions,options.maxTokens-firstLimit,
                                    options.temperature,options.reasoningEffort,HostedSearchEvidence(call,result))
                                fun sum(a:Long?,b:Long?)=if(a==null || b==null)null else a+b
                                com.meshlit.core.inference.models.OnlineReply(checkNotNull(last.text),sum(first.inputTokens,last.inputTokens),sum(first.outputTokens,last.outputTokens)) to 1
                            }
                        }
                    }
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected)c.copy(
                        messages=c.messages.map{if(it.id==assistant.id)it.copy(text=reply.text)else it},
                        usageNote="${hostedProfile.name} · $calls reviewed web searches · each API request recorded in Usage")else c})}
                    recordUsage(chatTokenUsage("Hosted agent usage · ${hostedProfile.model}",options.maxTokens,reply.inputTokens,reply.outputTokens,null,
                        android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else if(options.onlineProfileId!=null) {
                    val (profile,reply)=providers.generate(options.onlineProfileId, (history+user).map{com.meshlit.core.inference.models.OnlineMessage(it.role,it.text)},instructions,options.maxTokens,options.temperature,effort=options.reasoningEffort)
                    val cost=reply.estimatedCost(profile)
                    val note="${profile.name} · input ${reply.inputTokens ?: "unknown"}, output ${reply.outputTokens ?: "unknown"} tokens · "+
                        (cost?.let{"estimated ${"%.6f".format(it)} ${profile.currency} (user prices)"} ?: "cost unknown")
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=reply.text) else it},usageNote=note) else c})}
                    recordUsage(chatTokenUsage("Provider usage",options.maxTokens,reply.inputTokens,reply.outputTokens,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else {
                memory.captureRequest(user.text)
                val prompt=memory.context(user.text)+instructions.takeIf{it.isNotBlank()}?.let{"Instructions: $it\n\n"}.orEmpty()+if(history.isEmpty()) user.text else buildString {
                    append("Continue this conversation. Answer the latest user message.\n")
                    history.forEach { append(it.role).append(": ").append(it.text.take(4000)).append('\n') }
                    append("user: ").append(user.text)
                }
                if(shouldPlanToolCall(user.text,options)) {
                    fun publish(value:String){_state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=value) else it}) else c})}}
                    val answer=localTools.run(prompt,options,latestUserMessage=user.text,conversationId=selected,connectionSelection=connectionSelection,
                        reviewTool={request,origin->toolApprovals.review(options.approvalMode,request,origin)},status={ label ->
                        _state.update { it.copy(operationLabel=label) }
                    },onToolEvent={ tool,result ->
                        val sources=result?.let { chatSourcesFromTool(tool,it) }.orEmpty()
                        if(tool=="device_connection" && result is com.meshlit.core.mcp.McpToolResult.Json) {
                            val connection=runCatching{deviceConnectionAnswer(result.value as kotlinx.serialization.json.JsonObject)}.getOrNull()
                            if(connection!=null)_state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected)c.copy(messages=c.messages.map{m->if(m.id==assistant.id)m.copy(deviceOffer=connection.deviceOffer,deviceReceipt=connection.deviceReceipt) else m}) else c})}
                        }
                        _state.update { old -> old.copy(activeTool=if(result==null) tool else null,
                            operationLabel=if(result==null) chatToolActivity(tool) ?: "Using $tool" else "Preparing reply",
                            conversations=if(sources.isEmpty()) old.conversations else old.conversations.map { c ->
                                if(c.id==selected) c.copy(messages=c.messages.map { m ->
                                    if(m.id==assistant.id) m.copy(webSources=mergeChatSources(m.webSources,sources)) else m
                                }) else c
                            }) }
                        if(sources.isNotEmpty()) appScope.launch { runCatching { save() } }
                    })
                    publish(answer.text)
                    if(answer.deviceOffer!=null || answer.deviceReceipt!=null) _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected)c.copy(messages=c.messages.map{m->if(m.id==assistant.id)m.copy(deviceOffer=answer.deviceOffer,deviceReceipt=answer.deviceReceipt) else m}) else c})}
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(usageNote="On-device tool loop · ${answer.calls} tool calls · native token totals not aggregated") else c})}
                    recordUsage(chatTokenUsage("Tool loop · counts not aggregated",options.maxTokens,null,null,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else {
                val originalInfo=coordinator.loadedModel()
                val originalEngine=coordinator.engineTag
                val originalModel=library.models.value.firstOrNull{it.installed && it.path==originalInfo?.modelPath}
                val qwen3=originalModel?.name?.contains("Qwen3",ignoreCase=true)==true
                val usesChatMl=originalModel!=null && usesChatMlPrompt(originalEngine,originalModel.name)
                fun visibleReply(raw:String)=if(qwen3 && usesChatMl) qwen3AnswerText(raw,qwen3ThinkingRequested(user.text)) else raw
                fun chatPrompt(system:String,turns:List<ChatMessage>)=if(qwen3) qwen3ChatPrompt(system,turns,user.text) else smolLmChatPrompt(system,turns,user.text)
                var localPrompt=if(usesChatMl) chatPrompt(memory.context(user.text)+instructions,history) else prompt
                var localTemperature=options.temperature
                var freshRetry=false
                val buffer=StringBuilder()
                suspend fun generate()=coordinator.infer(InferenceRequest(prompt=localPrompt,maxTokens=options.maxTokens,temperature=localTemperature,
                    stopSequences=if(usesChatMl) listOf("<|im_end|>") else emptyList(),
                    reuseContext=coordinator.engineTag=="llama-native-local" && !freshRetry,expectedModelPath=originalInfo?.modelPath,onToken={ token ->
                    buffer.append(token)
                    _state.update { old -> old.copy(conversations=old.conversations.map { c ->
                        if(c.id==selected) c.copy(messages=c.messages.map { if(it.id==assistant.id) it.copy(text=visibleReply(buffer.toString())) else it }) else c }) }
                }))
                var result=generate()
                if(result is MeshlitResult.Failure && result.error is com.meshlit.core.common.MeshlitError.Native && memory.state.value.policy.recovery && originalModel!=null && library.models.value.any{it==originalModel} && coordinator.loadedModel()?.modelPath==originalModel.path) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    library.load(originalModel.id)
                    buffer.clear()
                    result=generate()
                }
                if(result is MeshlitResult.Success && isUnrelatedRepeat(user.text,history,completedChatText(buffer.toString(),result.value.finalText))) {
                    freshRetry=true
                    localPrompt=if(usesChatMl) chatPrompt(memory.context(user.text)+instructions+"\nPast assistant replies may be mistaken. Answer only the latest user message; do not repeat a previous reply.",emptyList())
                        else "${memory.context(user.text)}$instructions\nAnswer only this latest user message, without repeating earlier replies.\nUser: ${user.text}"
                    localTemperature=minOf(options.temperature,0.4f)
                    buffer.clear()
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text="") else it}) else c})}
                    result=generate()
                }
                val completed=result
                if(completed is MeshlitResult.Failure) {
                    if(originalEngine=="llama-rpc-layer") _state.update { old ->
                        old.copy(conversations=old.conversations.map { chat ->
                            if(chat.id!=selected) chat else chat.copy(messages=chat.messages.map { message ->
                                if(message.id==assistant.id) message.copy(status=INTERRUPTED_CLUSTER_REPLY) else message
                            })
                        })
                    }
                    error(completed.error.tag)
                }
                if(completed is MeshlitResult.Success) {
                    val answer=visibleReply(completedChatText(buffer.toString(),completed.value.finalText))
                    check(answer.isNotBlank()) { "The reasoning model did not reach a final answer within this response. Increase the output limit or try /no_think." }
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=answer) else it}) else c})}
                }
                if(completed is MeshlitResult.Success) _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(usageNote="Input ${completed.value.promptTokens ?: "unknown"}, output ${completed.value.generatedTokens ?: "unknown"} tokens · cached ${completed.value.cachedPromptTokens ?: "unknown"}${if(freshRetry) " · fresh-context retry; first-run usage not included" else ""}") else c})}
                if(completed is MeshlitResult.Success && originalEngine=="llama-rpc-layer" && coordinator.engineTag==originalEngine && originalInfo!=null && coordinator.loadedModel()===originalInfo && (completed.value.generatedTokens ?: 0)>=8) {
                    completed.value.tokensPerSecond?.takeIf{it.isFinite() && it>0}?.let{evidence.value=ClusterOutputEvidence(originalInfo,it.toDouble(),android.os.SystemClock.elapsedRealtime())}
                }
                if(completed is MeshlitResult.Success) recordUsage(chatTokenUsage("Runtime usage",options.maxTokens,completed.value.promptTokens?.toLong(),completed.value.generatedTokens?.toLong(),completed.value.cachedPromptTokens?.toLong(),
                    android.os.SystemClock.elapsedRealtime()-started,completed.value.tokensPerSecond,originalInfo?.contextSize,completed.value.finishReason.tag))
                }
                }
            } catch(timeout:TimeoutCancellationException) { _state.update{it.copy(error=if(photo!=null) "Photo analysis timed out. Check the provider and retry." else "Generation timed out. Check the model and retry.")} }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { _state.update { it.copy(error=error.message ?: "Generation failed") } }
            finally {
                if(photo!=null) _state.update { old -> old.copy(conversations=old.conversations.map { c ->
                    if(c.id==selected) c.copy(messages=c.messages.map{m->if(m.id==assistant.id && m.text.isBlank()) m.copy(status=if(old.error!=null) "Photo failed · review the error and retry" else "Stopped · photo not answered") else m}) else c
                }) }
                withContext(NonCancellable) { runCatching { save() } }
                generationId=null
                if(foregroundRequested)runCatching { ChatInferenceService.finish(context,assistant.id) }.onFailure {
                    _state.update { state->state.copy(error=state.error ?: "Reply ended, but Android could not close its notification. Use Stop in the notification.") }
                }
                _state.update { it.copy(running=false,generationStartedMs=null,activeOutputLimit=null,operationLabel="Generating",activeTool=null) }
            }
        }
        return assistant.id
    }
    private suspend fun recoveryModelHash():String {
        val engine=coordinator.engineTag
        require(engine in setOf("runanywhere","llama-native-local","llama-rpc-layer")){"Load a compatible local or cluster GGUF model first"}
        val model=coordinator.loadedModel() ?: error("Load the matching GGUF model first")
        val sha=withContext(Dispatchers.IO) {
            val file=File(model.modelPath)
            com.meshlit.core.inference.models.ModelFiles.validateGguf(file)
            com.meshlit.core.inference.models.ModelFiles.sha256(file)
        }
        check(coordinator.loadedModel()===model && coordinator.engineTag==engine){"The loaded model changed during recovery verification"}
        return sha
    }
    internal suspend fun prepareReplicaChat():ReplicaChatDraft {
        ready.await()
        val current=state.value
        check(!current.running){"Wait for the current reply or press Stop first"}
        val chat=current.current ?: error("Open a conversation with a completed local reply first")
        require(chat.options.supportsLocalToolSelection && chat.options.executionMode==ChatExecutionMode.CHAT){"Choose a local Chat conversation before checkpointing text"}
        val transcript=replicaChatTranscript(chat)
        return ReplicaChatDraft(chat.recoveryBinding?.taskId ?: "chat-${chat.id}",chat.recoveryBinding?.epoch ?: 1L,
            recoveryModelHash(),transcript)
    }
    internal suspend fun verifyReplicaChat(checkpoint:ReplicaChatCheckpoint) {
        ready.await()
        check(!state.value.running){"Stop the current reply before recovering a conversation"}
        check(state.value.conversations.size<40 || state.value.conversations.any{it.recoveryBinding?.checkpointId==checkpoint.requestId}){
            "Chat history is full. Make room before recovering a conversation."}
        require(recoveryModelHash()==checkpoint.modelSha256){"Load the exact GGUF model used by this checkpoint before recovering it"}
    }
    internal suspend fun restoreReplicaChat(checkpoint:ReplicaChatCheckpoint,binding:ReplicaChatBinding) {
        require(binding.taskId==checkpoint.taskId && binding.checkpointId==checkpoint.requestId && binding.epoch>0)
        verifyReplicaChat(checkpoint)
        withContext(Dispatchers.Main.immediate) {
            val previous=_state.value
            check(!previous.running){"Stop the current reply before recovering a conversation"}
            val existing=previous.conversations.firstOrNull{it.recoveryBinding==binding}
            if(existing!=null){select(existing.id);return@withContext}
            check(previous.conversations.size<40){"Chat history is full. Make room before recovering a conversation."}
            val chat=ChatConversation(title="Recovered · ${checkpoint.transcript.messages.first().text.take(44)}",
                messages=checkpoint.transcript.messages.map{line->ChatMessage(role=line.role,text=line.text,
                    source=RECOVERED_REPLY_SOURCE.takeIf{line.role=="assistant"})},recoveryBinding=binding)
            _state.update{it.copy(conversations=listOf(chat)+it.conversations,selectedId=chat.id,error=null)}
            try {withContext(NonCancellable){save()}}
            catch(e:CancellationException){throw e}
            catch(e:Exception){
                _state.update{it.copy(conversations=it.conversations.filterNot{item->item.id==chat.id},
                    selectedId=if(it.selectedId==chat.id)previous.selectedId else it.selectedId)}
                throw e
            }
        }
    }
    fun connectDevice(offerId:String,sourceId:String) {
        val conversation=_state.value.current ?: return
        val offer=conversation.messages.mapNotNull{it.deviceOffer}.firstOrNull{it.id==offerId}
        val choice=offer?.choices?.firstOrNull{it.id==sourceId}
        if(choice==null || !choice.canConnect || offer.expiresAtMs<=System.currentTimeMillis() || !conversation.options.connectionTools || !conversation.options.agentToolsActive) {
            _state.update{it.copy(error="Device choices or connection access changed. Request fresh choices in this chat.")};return
        }
        send("Connect ${choice.name}",connectionSelection=DeviceConnectionSelection(offerId,sourceId))
    }
    fun requestColibriModeByAgent(mode:com.meshlit.workspace.ColibriMode) {
        val chat=_state.value.current ?: error("Select a user-delegated conversation first")
        com.meshlit.BuildProfile.requireChat(chat.options)
        colibri.requestByAgent(chat.id,mode,chat.options.colibriAgentAllowed)
    }
    fun stopIfMatching(id:String?) { if(id!=null && id==generationId) stop() }
    fun stop() { generation?.cancel();if(_state.value.operationLabel!="Analyzing photo") coordinator.cancel() }
    fun attachmentError(message:String){_state.update{it.copy(error=message)}}
    fun clearError() { _state.update { it.copy(error=null) } }
}

/** Prefer the completed runtime result, including engines which emit no text callbacks. */
internal fun completedChatText(streamed:String,completed:String):String {
    val answer=completed.ifBlank{streamed}
    check(answer.isNotBlank()){ "The model returned an empty answer. Try another prompt or check the selected model; no response was substituted." }
    return answer
}

/** Retry only when a new question receives a long near-copy of a prior assistant answer. */
internal fun isUnrelatedRepeat(user:String,history:List<ChatMessage>,answer:String):Boolean {
    fun normalized(text:String)=text.trim().lowercase().replace(Regex("\\s+")," ")
    val currentQuestion=normalized(user)
    if(history.any{it.role=="user" && normalized(it.text)==currentQuestion}) return false
    val current=normalized(answer)
    if(current.length<96) return false
    return history.any { previous ->
        if(previous.role!="assistant") false else {
            val prior=normalized(previous.text)
            prior.length>=96 && (current==prior ||
                (current.startsWith(prior) && current.length-prior.length<=100) ||
                (current.length>=160 && prior.length>=160 && current.take(160)==prior.take(160)))
        }
    }
}
internal fun restoredChatSelection(items:List<ChatConversation>,saved:String?)=items.firstOrNull{it.id==saved}?.id ?: items.firstOrNull()?.id

/** Offline photo turns stay visible without being reused as context or generating derived context. */
internal fun chatPromptHistory(messages:List<ChatMessage>,limit:Int):List<ChatMessage> =
    messages.filter{!it.isolatedPhoto && it.status!=INTERRUPTED_CLUSTER_REPLY && it.text.isNotBlank()}.takeLast(limit)

private const val INTERRUPTED_CLUSTER_REPLY="Cluster reply interrupted · send the request again when ready"
