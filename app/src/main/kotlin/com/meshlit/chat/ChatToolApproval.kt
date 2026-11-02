package com.meshlit.chat

import com.meshlit.core.mcp.McpToolRequest
import com.meshlit.core.mcp.McpToolSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable enum class ChatApprovalMode(val label:String) {
    ASK("Ask each time"), AUTO_READS("Auto approved reads"), SELECTED_TOOLS("Selected approved tools")
}
internal data class PendingChatToolApproval(val id:String,val tool:String,val arguments:String,val expiresAtMs:Long)

/** First-party classifications only. Tool descriptions and model assertions never grant authority. */
internal fun needsChatToolApproval(mode:ChatApprovalMode,request:McpToolRequest,origin:McpToolSpec.Origin):Boolean {
    if(origin!=McpToolSpec.Origin.BuiltIn || mode==ChatApprovalMode.ASK)return true
    if(mode==ChatApprovalMode.SELECTED_TOOLS)return false
    val args=request.arguments as? JsonObject
    fun action()=(args?.get("action") as? JsonPrimitive)?.content
    val readOnly=when(request.name) {
        "android_control_status","android_package_status","app_search","web_search","crawl_url","vm_status","ssh_nodes","remote_nodes","agent_team_status","cyber_skills_list","cyber_skill_read","peer_chat_read" -> true
        "android_control" -> action()=="snapshot"
        "device_connection" -> action() in setOf("list","request","connect","status")
        "personal_memory" -> action() in setOf("list","read","status")
        "crypto_local" -> (args?.get("operation") as? JsonPrimitive)?.content=="sha256"
        else -> false
    }
    return !readOnly
}

/** One ephemeral, exact-argument human decision. Timeout/Stop/restart never imply approval. */
internal class ChatToolApproval(private val timeoutMs:Long=60_000,private val clock:()->Long={System.nanoTime()/1_000_000}) {
    private val mutable=MutableStateFlow<PendingChatToolApproval?>(null)
    val pending=mutable.asStateFlow()
    private var active:Pair<PendingChatToolApproval,CompletableDeferred<Boolean>>?=null
    suspend fun review(mode:ChatApprovalMode,request:McpToolRequest,origin:McpToolSpec.Origin):Boolean {
        if(!needsChatToolApproval(mode,request,origin))return true
        val arguments=request.arguments.toString()
        require(arguments.length<=16_384){"Tool arguments are too large for a reviewed call"}
        val item=PendingChatToolApproval(UUID.randomUUID().toString(),request.name,arguments,clock()+timeoutMs)
        val answer=CompletableDeferred<Boolean>()
        synchronized(this){check(active==null){"Another tool is waiting for review"};active=item to answer;mutable.value=item}
        return try {withTimeoutOrNull(timeoutMs){answer.await()}==true}
        finally {synchronized(this){if(active?.first?.id==item.id){active=null;mutable.value=null}};answer.cancel()}
    }
    @Synchronized fun answer(id:String,allow:Boolean):Boolean {
        val current=active ?: return false
        if(current.first.id!=id || current.second.isCompleted)return false
        return current.second.complete(allow && clock()<current.first.expiresAtMs)
    }
}
