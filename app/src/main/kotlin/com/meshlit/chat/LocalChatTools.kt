package com.meshlit.chat

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.common.control.OperationGate
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.HostedSearchCall
import kotlinx.serialization.json.*
import com.meshlit.core.mcp.*
import com.meshlit.core.observability.LogSource
import com.meshlit.observability.LogBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Chat grants expose a small tool list; each existing tool retains its own
 * saved delegation, OS permission and operation checks. No generic shell tool. */
class LocalChatTools(private val inference:InferenceCoordinator,private val gate:OperationGate,private val registry:()->McpToolRegistry,private val search:()->com.meshlit.search.AppSearchService,private val logs:LogBuffer,private val libraryModelName:()->String?={null}) {
    internal suspend fun reviewedHostedSearch(call:HostedSearchCall,authorize:suspend()->Unit,
        review:suspend(McpToolRequest,McpToolSpec.Origin)->Boolean,onDispatch:()->Unit):JsonObject = gate.run(ManagedFeature.AUTOMATION,true) {
        call.validate();authorize()
        val selected=registry()
        val tool=selected.list().singleOrNull{it.name=="web_search" && it.origin==McpToolSpec.Origin.BuiltIn}
            ?: error("The public web search tool is unavailable")
        val request=McpToolRequest(tool.name,buildJsonObject{put("query",call.query)})
        check(review(request,tool.origin)){"Web search was denied or review expired; no query was sent"}
        currentCoroutineContext().ensureActive();authorize();gate.requireAllowed(ManagedFeature.AUTOMATION,true)
        onDispatch()
        val result=selected.invoke(request,tool)
        currentCoroutineContext().ensureActive();authorize()
        hostedSearchEvidence(result)
    }

    suspend fun run(prompt:String,options:ChatOptions,status:(String)->Unit,
        onToolEvent:(String,McpToolResult?)->Unit={_,_->},latestUserMessage:String?=null,conversationId:String?=null,connectionSelection:DeviceConnectionSelection?=null,
        reviewTool:suspend(McpToolRequest,McpToolSpec.Origin)->Boolean={_,_->true}):LocalToolAnswer=gate.run(ManagedFeature.AUTOMATION,true) {
        val parentTurn=currentCoroutineContext().job
        withContext(com.meshlit.control.LocalAgentTurn(parentTurn,conversationId)) {
        options.validate();com.meshlit.BuildProfile.requireChat(options)
        check(options.agentToolsActive){"Choose Agent mode before running chat tools"}
        val names=buildSet {
            if(options.nodeTools) addAll(listOf("cyber_skills_list","cyber_skill_read","vm_status","vm_start","vm_wait","vm_stop","vm_exec","ssh_nodes","ssh_node_request","colibri_mode"))
            if(options.connectionTools)add("device_connection")
            if(options.teamTools)addAll(listOf("agent_team_start","agent_team_status","agent_team_control"))
            if(options.cryptoTools) add("crypto_local")
            if(options.remoteTools) addAll(listOf("remote_commands_access","remote_nodes","remote_node_command"))
            if(options.peerTools) addAll(listOf("peer_chat_access","peer_chat_send","peer_chat_read","peer_node_command"))
            if(options.memoryTools) add("personal_memory")
            if(options.webTools) addAll(listOf("crawl_url","web_search","search_access"))
            if(options.localSearchTools) add("app_search")
            if(options.phoneTools) addAll(listOf("android_control_status","android_control","android_package_status","android_package_request"))
        }
        val selectedRegistry=registry()
        val tools=selectedRegistry.list().filter{it.name in names && it.origin==McpToolSpec.Origin.BuiltIn}
        val selectedTools=tools.associateBy{it.name}
        check(tools.isNotEmpty()){"No selected built-in tools are available"}
        logs.info(LogSource.AGENT,"LocalAgent","Task started",mapOf("tool_count" to tools.size,"phone_tools" to options.phoneTools))
        suspend fun dispatch(request:McpToolRequest):McpToolResult {
            logs.info(LogSource.AGENT,"LocalAgent","Tool requested",mapOf("tool" to request.name))
            onToolEvent(request.name,null)
            gate.requireAllowed(ManagedFeature.AUTOMATION,true)
            val selected=selectedTools.getValue(request.name)
            if(!reviewTool(request,selected.origin))return McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,"Tool call denied or its review expired; no action was dispatched")
            currentCoroutineContext().ensureActive();gate.requireAllowed(ManagedFeature.AUTOMATION,true)
            val result=if(request.name=="crawl_url") search().access.web(true){selectedRegistry.invoke(request,selected)} else selectedRegistry.invoke(request,selected)
            currentCoroutineContext().ensureActive();gate.requireAllowed(ManagedFeature.AUTOMATION,true)
            onToolEvent(request.name,result)
            logs.info(LogSource.AGENT,"LocalAgent","Tool finished",mapOf("tool" to request.name,"success" to (result !is McpToolResult.Error)))
            return result
        }
        try {
        val direct=if(connectionSelection!=null) {
            require(options.connectionTools && "device_connection" in selectedTools){"Connection tools are not selected"}
            DirectChatToolIntent("device_connection",kotlinx.serialization.json.buildJsonObject {
                put("action",kotlinx.serialization.json.JsonPrimitive("connect"));put("offerId",kotlinx.serialization.json.JsonPrimitive(connectionSelection.offerId));put("sourceId",kotlinx.serialization.json.JsonPrimitive(connectionSelection.sourceId))
            })
        } else latestUserMessage?.let{directChatToolIntent(it,options,selectedTools.keys)}
        val answer=if(direct!=null) {
            status("Using ${direct.name}")
            executeDirectChatTool(direct,::dispatch)
        } else {
        val model=inference.loadedModel() ?: error("Load a local model before enabling tools")
        val templateName=libraryModelName()?.takeIf{it.isNotBlank()} ?: model.modelName
        check(inference.engineTag in setOf("runanywhere","onnx-ort","llama-native-local")) {
            "Chat tool planning requires an on-device model. Switch from the cluster or remote runtime before using chat tools."
        }
        LocalToolLoop(complete={planningPrompt->
            gate.run(ManagedFeature.INFERENCE,true) {
                val chatMl=usesChatMlPrompt(inference.engineTag,templateName)
                val result=inference.infer(InferenceRequest(localToolPlanningPrompt(inference.engineTag,templateName,planningPrompt),maxTokens=minOf(options.maxTokens,1024),temperature=0f,
                    stopSequences=if(chatMl) listOf("<|im_end|>") else emptyList(),
                    expectedModelPath=model.modelPath,onDeviceOnly=true,publishEvents=false,onToken={}))
                when(result){is MeshlitResult.Success->result.value.finalText;is MeshlitResult.Failure->error("Local tool planning failed: ${result.error.tag}")}
            }
        },invoke=::dispatch,checkAllowed={gate.requireAllowed(ManagedFeature.AUTOMATION,true)},maxCalls=if(options.phoneTools) 8 else 3,requireFirstTool=options.phoneTools && latestUserMessage?.let(::requiresPhoneToolCall)==true).run(prompt,tools,status)
        }
        answer.also{
            logs.info(LogSource.AGENT,"LocalAgent","Task finished",mapOf("calls" to it.calls))
        }} catch(cancelled:CancellationException){logs.warn(LogSource.AGENT,"LocalAgent","Task cancelled");throw cancelled}
        catch(error:Exception){logs.error(LogSource.AGENT,"LocalAgent","Task failed");throw error}
        }
    }
}
