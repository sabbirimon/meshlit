package com.meshlit.chat

import android.content.Context
import com.meshlit.core.inference.*
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable data class ChatOptions(val onlineProfileId:String?=null,val systemPrompt:String="",val maxTokens:Int=1024,val temperature:Float=0.4f,val historyMessages:Int=10,val routeId:String?=null,val routeScenario:String="general",val webTools:Boolean=false,val phoneTools:Boolean=false,val memoryTools:Boolean=false,val showTokenStats:Boolean=true,val localSearchTools:Boolean=false,val outputBudgetMode:OutputBudgetMode=OutputBudgetMode.MANUAL,val outputTargetSeconds:Int=30,val nodeTools:Boolean=false,val colibriMode:String="OFF",val colibriAgentAllowed:Boolean=false,val cryptoTools:Boolean=false,val gibberlinkTools:Boolean=false,val remoteTools:Boolean=false,val peerTools:Boolean=false,val companionStyle:CompanionStyle=CompanionStyle.WARM,val companionWarmth:Int=1,val matureTopics:Boolean=false) {
    val usesLocalTools get()=webTools || phoneTools || memoryTools || localSearchTools || nodeTools || cryptoTools || gibberlinkTools || remoteTools || peerTools
    val toolCount get()=(if(nodeTools)8 else 0)+(if(remoteTools)3 else 0)+(if(peerTools)4 else 0)+(if(gibberlinkTools)2 else 0)+(if(cryptoTools)1 else 0)+(if(memoryTools)1 else 0)+(if(localSearchTools)1 else 0)+(if(webTools)3 else 0)+(if(phoneTools)4 else 0)
    fun validate(){require(companionWarmth in 0..2);require(toolCount<=10){"Select at most ten chat tools; deselect another group"};require(colibriMode in com.meshlit.workspace.ColibriMode.entries.map{it.name});require(outputTargetSeconds in 5..120);require(!usesLocalTools || (onlineProfileId==null && routeId==null)){"Chat tools currently require an on-device model"};require(routeId==null || routeId=="__auto__" || routeId.matches(Regex("[A-Za-z0-9_-]{1,64}")));require(routeScenario.matches(Regex("[a-z0-9_-]{1,40}")));require(routeId==null || (onlineProfileId==null && maxTokens<=1024));require(systemPrompt.length<=4000 && maxTokens in 1..2048 && temperature.isFinite() && temperature in 0f..2f && historyMessages in 0..20)}
}
/** A grant makes tools available; it does not turn every greeting into a tool-planning run. */
internal fun shouldPlanToolCall(message:String,options:ChatOptions):Boolean {
    if(!options.usesLocalTools) return false
    val text=message.trim().lowercase()
    if(text.length<5) return false
    return Regex("\\b(search|browse|look up|google|open|navigate|visit|play|call|dial|send|text|message|read|find|locate|scan|run|execute|list|show|check|fetch|download|install|turn on|turn off|connect|pair|remember|save|forget)\\b").containsMatchIn(text) ||
        options.webTools && Regex("\\b(latest|today|current news|right now|this week)\\b").containsMatchIn(text)
}
data class PhotoChatRequest(val uri:android.net.Uri,val profile:com.meshlit.core.inference.models.OnlineProfile)

@Serializable data class ChatMessage(val id:String=UUID.randomUUID().toString(),val role:String,val text:String,val usage:ChatTokenUsage?=null,val attachmentNote:String?=null,val source:String?=null,val status:String?=null,val knowledgeSources:List<String> = emptyList())
@Serializable data class ChatConversation(val id:String=UUID.randomUUID().toString(),val title:String="New chat",
    val messages:List<ChatMessage> = emptyList(),val updatedAt:Long=System.currentTimeMillis(),val options:ChatOptions=ChatOptions(),val usageNote:String?=null)
data class ChatState(val conversations:List<ChatConversation> = emptyList(),val selectedId:String?=null,
    val running:Boolean=false,val error:String?=null,val generationStartedMs:Long?=null,val activeOutputLimit:Int?=null,val operationLabel:String="Generating") {
    val current get()=conversations.firstOrNull { it.id==selectedId }
}

class ChatController(private val context:Context,private val coordinator:InferenceCoordinator,private val appScope:CoroutineScope,private val providers:com.meshlit.providers.OnlineProviders,private val router:com.meshlit.routing.ModelRoutes,private val localTools:LocalChatTools,private val memory:PersonalMemory,private val library:com.meshlit.models.ModelLibrary,private val colibri:com.meshlit.colibri.ColibriBackend,private val media:com.meshlit.media.MediaGeneration,private val knowledge:com.meshlit.knowledge.KnowledgeLibrary) {
    private val _state=MutableStateFlow(ChatState())
    val state:StateFlow<ChatState> = _state.asStateFlow()
    private val file=File(context.filesDir,"conversations-v1.json")
    private val selectionFile=File(context.filesDir,"chat-selection-v1.txt")
    private val json=Json { ignoreUnknownKeys=true }
    private val persistence=Mutex()
    private var generation:Job?=null
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
    fun select(id:String) {
        if(ready.isCompleted && !_state.value.running && _state.value.conversations.any{it.id==id}) {
            _state.update { it.copy(selectedId=id,error=null) }
            appScope.launch {runCatching{save()}}
        }
    }
    fun delete(id:String) {
        if(!ready.isCompleted || _state.value.running) return
        _state.update { old -> val remaining=old.conversations.filterNot { it.id==id }
            old.copy(conversations=remaining,selectedId=if(old.selectedId==id) remaining.firstOrNull()?.id else old.selectedId) }
        appScope.launch { runCatching { save() } }
    }
    fun setOptions(options:ChatOptions) {
        options.validate();com.meshlit.BuildProfile.requireChat(options);check(!_state.value.running);colibri.clearAgentModes()
        if(_state.value.current==null) newChat()
        _state.update{old->old.copy(conversations=old.conversations.map{if(it.id==old.selectedId) it.copy(options=options) else it})}
        appScope.launch{runCatching{save()}.onFailure{error->_state.update{it.copy(error="Cannot save chat options: ${error.javaClass.simpleName}")}}}
    }
    fun send(text:String,photo:PhotoChatRequest?=null,voiceConversation:Boolean=false):String? {
        if(!ready.isCompleted || _state.value.running || text.isBlank()) return null
        require(text.length<=12000) { "Message is too long" }
        if(_state.value.current==null) newChat()
        val selected=_state.value.selectedId!!
        val user=ChatMessage(role="user",text=text.trim(),attachmentNote=photo?.let{"Photo attached · original image not retained"})
        val assistant=ChatMessage(role="assistant",text="",source=photo?.let{"${it.profile.name} · ${it.profile.model}"},status=photo?.let{"Analyzing photo"})
        val configured=_state.value.current!!.options
        try { configured.validate();com.meshlit.BuildProfile.requireChat(configured)
            if(photo!=null) {
                require(!com.meshlit.BuildProfile.coreCandidate){"Image analysis is unavailable in Core Candidate"}
                require(photo.uri.scheme=="content"){"Select a photo from your device"}
                require(photo.profile.enabled && photo.profile.model.isNotBlank() && photo.profile.protocol in setOf(com.meshlit.core.inference.models.OnlineProtocol.OPENAI,com.meshlit.core.inference.models.OnlineProtocol.OPENAI_COMPATIBLE)){"Choose an enabled vision provider and model"}
                require(providers.profiles.value.any{it==photo.profile}){"Photo provider changed. Review it before sending."}
            }
        } catch(e:Exception) {
            _state.update { it.copy(error=e.message) };return null
        }
        val localRoute=photo==null && configured.onlineProfileId==null && configured.routeId==null && !configured.usesLocalTools
        val colibriDecision=if(localRoute) colibri.decision(selected,com.meshlit.workspace.ColibriMode.parse(configured.colibriMode),configured.colibriAgentAllowed,coordinator.state.value is CoordinatorState.Ready) else null
        if(colibriDecision?.route==com.meshlit.workspace.ColibriRoute.BLOCKED) { _state.update{it.copy(error=colibriDecision.reason)};return null }
        if(photo==null && configured.onlineProfileId==null && configured.routeId==null && colibriDecision?.route!=com.meshlit.workspace.ColibriRoute.HOST && coordinator.state.value !is CoordinatorState.Ready) {
            _state.update { it.copy(error="Load a model from Models before sending a message") };return null
        }
        val decision=budgetDecision(if(colibriDecision?.route==com.meshlit.workspace.ColibriRoute.HOST) configured.copy(outputBudgetMode=OutputBudgetMode.MANUAL) else configured)
        val options=configured.copy(maxTokens=if(photo!=null) 512 else decision.limit)
        val baseInstructions=companionInstructions(options)+if(voiceConversation) "\nThis is a spoken conversation. Reply naturally and concisely, usually in one to three sentences. Avoid markdown and long lists unless requested. Keep the configured identity and permissions." else ""
        val history=_state.value.current!!.messages.filter{it.text.isNotBlank()}.takeLast(options.historyMessages)
        _state.update { old -> old.copy(running=true,error=null,operationLabel=if(photo!=null) "Analyzing photo" else "Generating",generationStartedMs=android.os.SystemClock.elapsedRealtime(),activeOutputLimit=options.maxTokens,conversations=old.conversations.map { c ->
            if(c.id==selected) c.copy(title=if(c.messages.isEmpty()) text.trim().take(56) else c.title,
                messages=(c.messages+user+assistant).takeLast(100),updatedAt=System.currentTimeMillis()) else c }) }
        generationId=assistant.id
        generation=appScope.launch {
            val started=android.os.SystemClock.elapsedRealtime()
            fun recordUsage(usage:ChatTokenUsage) {_state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(usage=usage.copy(budgetReason=if(photo!=null) "Vision request limit; actual usage is reported separately" else decision.explanation)) else it}) else c})}}
            try {
                val remoteKnowledge = options.onlineProfileId != null || options.routeId != null || options.usesLocalTools ||
                    colibriDecision?.route == com.meshlit.workspace.ColibriRoute.HOST || coordinator.engineTag !in setOf("runanywhere", "llama-native-local")
                val grounding = if (photo != null) "" to emptyList<String>() else withContext(Dispatchers.IO) {
                    synchronized(knowledge.corpus) {
                        val reference = knowledge.corpus.context(user.text, remoteKnowledge, agent = options.usesLocalTools)
                        reference to if (reference.isBlank()) emptyList() else knowledge.corpus.search(user.text, 3).mapIndexed { index, hit ->
                            "[K${index + 1}] ${hit.title} · ${hit.source} · part ${hit.chunk + 1} · ${hit.documentId.take(12)}"
                        }
                    }
                }
                val instructions = baseInstructions + grounding.first
                if (grounding.second.isNotEmpty()) _state.update { old -> old.copy(conversations = old.conversations.map { c ->
                    if (c.id == selected) c.copy(messages = c.messages.map { if (it.id == assistant.id) it.copy(knowledgeSources = grounding.second) else it }) else c
                }) }
                ready.await();save()
                androidx.core.content.ContextCompat.startForegroundService(context,
                    android.content.Intent(context,ChatInferenceService::class.java).putExtra("generation",assistant.id))
                if(photo!=null) {
                    val result=withTimeout(180_000) { media.generate(photo.profile.id,"vision","",user.text,image=photo.uri,expectedProfile=photo.profile) }
                    currentCoroutineContext().ensureActive()
                    val answer=result.text?.takeIf{it.isNotBlank()} ?: error("Vision provider returned no answer")
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=answer,status=null) else it},usageNote="Photo answered by ${photo.profile.name} · ${photo.profile.model} · original image not retained") else c})}
                    recordUsage(chatTokenUsage("Vision provider · ${photo.profile.model}",512,result.usage?.inputTokens,result.usage?.outputTokens,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
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
                    val result=router.execute(options.routeId.takeUnless{it=="__auto__"},options.routeScenario,prompt,options.maxTokens)
                    if(!result.success) error("Route ${result.routeId} failed at step ${(result.failedStep ?: 0)+1}: ${result.error}")
                    _state.update{old->old.copy(conversations=old.conversations.map{c->if(c.id==selected) c.copy(messages=c.messages.map{if(it.id==assistant.id) it.copy(text=result.text) else it},usageNote="Route ${result.routeId} · ${result.mode} · ${result.steps.size} completed steps") else c})}
                    recordUsage(chatTokenUsage("Routed run · counts not aggregated",options.maxTokens,null,null,null,android.os.SystemClock.elapsedRealtime()-started,null,null))
                } else if(options.onlineProfileId!=null) {
                    val (profile,reply)=providers.generate(options.onlineProfileId, (history+user).map{com.meshlit.core.inference.models.OnlineMessage(it.role,it.text)},instructions,options.maxTokens,options.temperature)
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
                    val answer=localTools.run(prompt,options){publish(it)}
                    publish(answer.text+if(answer.sources.isEmpty()) "" else "\n\nSources:\n"+answer.sources.joinToString("\n"){"- $it"})
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
                if(completed is MeshlitResult.Failure) error(completed.error.tag)
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
                context.stopService(android.content.Intent(context,ChatInferenceService::class.java))
                _state.update { it.copy(running=false,generationStartedMs=null,activeOutputLimit=null,operationLabel="Generating") }
            }
        }
        return assistant.id
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
