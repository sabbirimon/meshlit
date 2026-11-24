package com.meshlit.chat

import com.meshlit.core.inference.models.OnlineProfile
import com.meshlit.core.inference.models.OnlineProtocol
import java.net.URI

internal enum class TaskProvider(val label:String,val appPackage:String) {
    GEMINI("Gemini","com.google.android.apps.bard"), CHATGPT("ChatGPT","com.openai.chatgpt");

    fun accepts(profile:OnlineProfile):Boolean {
        if(!profile.enabled || profile.model.isBlank())return false
        val host=runCatching{URI(profile.endpoint).host}.getOrNull()
        val name=profile.name.lowercase()
        return when(this){
            GEMINI -> profile.protocol==OnlineProtocol.GEMINI || host=="generativelanguage.googleapis.com" || Regex("""\bgemini\b""").containsMatchIn(name)
            CHATGPT -> host=="api.openai.com" || Regex("""\bchat\s?gpt\b""").containsMatchIn(name)
        }
    }
}

internal data class NamedModelTask(val provider:TaskProvider,val task:String) {
    fun messages()=listOf(com.meshlit.core.inference.models.OnlineMessage("user",task))
}
internal const val NAMED_TASK_ATTACHMENT_ERROR="Named model tasks send text only. Remove the photo to send this task."
internal fun namedTaskAttachmentProblem(text:String,hasAttachment:Boolean)=
    if(hasAttachment && namedModelTask(text)!=null) NAMED_TASK_ATTACHMENT_ERROR else null

internal data class NamedModelDispatch(val request:NamedModelTask,val profile:OnlineProfile)

private const val providerWord="""(gemini|chat\s?gpt|openai)"""
private val direct=Regex("""(?is)^(?:(?:please|plz)\s+)?(?:(?:can|could|would)\s+you\s+(?:please\s+)?)?(?:(?:/?ask|tell|use|have|/delegate|delegate(?:\s+(?:the|a|this)\s+task)?\s+to|send(?:\s+(?:a|the|this)\s+task)?\s+to|give\s+(?:a\s+|this\s+|the\s+)?task\s+to|connect(?:\s+to)?)\s+|@)$providerWord\b(.*)$""")
private val taskPrefix=Regex("""(?is)^(?:(?:and\s+)?(?:ask|tell)\s+(?:it\s+)?(?:to\s+)?|(?:and\s+)?give\s+it\s+(?:a\s+|this\s+)?task\s*[:,-]?\s*|to\s+|and\s+)""")
internal fun namedModelTask(text:String):NamedModelTask? {
    val hit=direct.matchEntire(text.trim()) ?: return null
    val rest=hit.groupValues[2]
    if(rest.isNotEmpty() && !rest.first().isWhitespace() && rest.first() !in ":,-")return null
    val payload=rest.trimStart()
    val task=if(payload.startsWith(":")) payload.drop(1).trim()
        else payload.trimStart { it.isWhitespace() || it in ",-" }.replaceFirst(taskPrefix,"").trim()
    return NamedModelTask(if(hit.groupValues[1].equals("gemini",true)) TaskProvider.GEMINI else TaskProvider.CHATGPT,
        task)
}

// These choices live separately from importable conversations. Editing the destination invalidates the choice.
internal class NamedModelGrants(context:android.content.Context){
    private val prefs=context.getSharedPreferences("named-provider-auto",0)
    fun allows(chat:String?,request:NamedModelTask,p:OnlineProfile)=chat!=null && request.provider.accepts(p) &&
        prefs.getString("$chat/${request.provider.name}",null)==namedProfileBinding(p)
    fun allow(chat:String,request:NamedModelTask,p:OnlineProfile){
        check(request.provider.accepts(p))
        prefs.edit().putString("$chat/${request.provider.name}",namedProfileBinding(p)).apply()
    }
    fun revoke(chat:String?,request:NamedModelTask){if(chat!=null)prefs.edit().remove("$chat/${request.provider.name}").apply()}
    fun clear(chat:String?){if(chat!=null)prefs.edit().apply {TaskProvider.entries.forEach{remove("$chat/${it.name}")}}.apply()}
}

internal fun namedProfileBinding(profile:OnlineProfile)=
    listOf(profile.id,profile.protocol.name,profile.endpoint,profile.model).joinToString("\u0000")

internal fun requireAgentTaskGrant(principal:com.meshlit.core.mcp.gateway.GatewayPrincipal,
                                   request:NamedModelTask,profile:OnlineProfile,grant:String?) {
    require(!principal.owner && principal.allows(com.meshlit.core.mcp.gateway.GatewayScope.MCP) &&
        principal.allows(com.meshlit.core.mcp.gateway.GatewayScope.CHAT) &&
        principal.allowsTool("named_model_task") && principal.allowsModel("cloud:${profile.id}"))
    require(request.task.isNotBlank() && request.task.length<=12000 && profile.agentAllowed &&
        request.provider.accepts(profile) && grant==namedProfileBinding(profile))
}
