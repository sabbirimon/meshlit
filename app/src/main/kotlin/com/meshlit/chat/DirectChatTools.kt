package com.meshlit.chat

import com.meshlit.core.mcp.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.util.UUID

internal data class DirectChatToolIntent(val name:String,val arguments:JsonObject)

/** Narrow explicit requests use their selected handler directly. Only the newest user message
 * is classified: history, retrieved text and custom instructions can never initiate a call here. */
internal fun directChatToolIntent(message:String,options:ChatOptions,available:Set<String>):DirectChatToolIntent? {
    val text=message.trim()
    if(text.length !in 5..600 || text.any{it.isISOControl()} || text.startsWith('"') || text.startsWith('`'))return null
    val lower=text.lowercase()
    if(Regex("\\b(don't|dont|do not|never|without)\\b").containsMatchIn(lower) || lower.startsWith("how ") || lower.startsWith("why "))return null
    fun intent(name:String,args:JsonObject=buildJsonObject{})=DirectChatToolIntent(name,args).takeIf{name in available}
    deviceConnectionChatIntent(message,options)?.takeIf{it.name in available}?.let{return it}
    if(options.phoneTools && Regex("^(check|show|read|get|what is|what's|is)\\b").containsMatchIn(lower) &&
        Regex("\\b(phone control|accessibility|phone agent status)\\b").containsMatchIn(lower) &&
        !Regex("\\b(enable|disable|turn|open|click|tap|type|install|delete|send)\\b").containsMatchIn(lower))return intent("android_control_status")
    if(options.teamTools) {
        if(Regex("^(check|show|list) (the )?(agent )?teams?( status| progress| results)?[?.!]*$").matches(lower))return intent("agent_team_status")
        val task=Regex("^(?:(?:start|run)(?: the| an?| my)? (?:agent )?team(?: to| for|:)?|ask (?:the )?agents to)\\s+(.+)$",RegexOption.IGNORE_CASE)
            .matchEntire(text)?.groupValues?.get(1)
        if(task!=null)return intent("agent_team_start",buildJsonObject{put("request_id","chat-${UUID.randomUUID()}");put("goal",task)})
    }
    if(options.remoteTools && Regex("^(list|show) (the )?((saved|delegated|configured) )?remote nodes[?.!]*$").matches(lower))
        return intent("remote_nodes")
    if(options.localSearchTools) {
        val query=Regex("^search (?:my|saved|local) (?:chats|articles|settings|models)(?: for)?\\s+(.+)$",RegexOption.IGNORE_CASE).matchEntire(text)?.groupValues?.get(1)
        if(query!=null)return intent("app_search",buildJsonObject{put("query",query)})
    }
    if(options.webTools) {
        val query=Regex("^(?:search (?:the web|online)(?: for)?|web search(?: for)?|look up online|google|search for)\\s+(.+)$",RegexOption.IGNORE_CASE)
            .matchEntire(text)?.groupValues?.get(1)
            ?: text.takeIf{Regex("\\b(latest|current|today|this week|right now)\\b").containsMatchIn(lower) && Regex("\\b(news|headlines|updates|releases|events)\\b").containsMatchIn(lower)}
        if(query!=null && '@' !in query &&
            !Regex("\\b(and|then)\\s+(send|email|message|text|delete|install|run|execute)\\b",RegexOption.IGNORE_CASE).containsMatchIn(query) &&
            !Regex("\\b(my|our|saved|local|private|contacts|messages|files|password|secret|token|api key)\\b",RegexOption.IGNORE_CASE).containsMatchIn(query))
            return intent("web_search",buildJsonObject{put("query",query)})
    }
    return null
}

internal suspend fun executeDirectChatTool(intent:DirectChatToolIntent,invoke:suspend(McpToolRequest)->McpToolResult):LocalToolAnswer {
    val result=invoke(McpToolRequest(intent.name,intent.arguments))
    currentCoroutineContext().ensureActive()
    if(result is McpToolResult.Error)error(result.message)
    val payload=(result as? McpToolResult.Json)?.value ?: error("The tool returned an unexpected response")
    fun string(value:JsonObject,key:String)=(value[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    fun readable(value:String)=value.replace(Regex("[\\p{Cntrl}]")," ").trim().take(1000)
    fun literal(value:String)=readable(value).replace("\\","\\\\").replace("[","\\[").replace("]","\\]").replace("*","\\*").replace("_","\\_").replace("`","\\`")
    if(intent.name=="remote_nodes") {
        val nodes=checkNotNull(payload as? JsonArray){"Remote node inventory was not returned"}.map {
            checkNotNull(it as? JsonObject){"Invalid remote node inventory"}
        }
        val text=if(nodes.isEmpty())"No remote nodes are delegated. Add and approve a device before sending remote commands." else buildString {
            append("Saved delegated remote nodes (${nodes.size}):\n\n")
            nodes.take(20).forEach { node ->
                val id=string(node,"id").also{check(it.isNotBlank()){"Remote node ID missing"}}
                val actions=checkNotNull(node["actions"] as? JsonArray){"Remote node actions missing"}.map {
                    checkNotNull((it as? JsonPrimitive)?.takeIf{p->p.isString}?.content){"Invalid remote node action"}
                }
                append("- ").append(literal(string(node,"name").ifBlank{id})).append(" (").append(literal(id)).append("): ")
                append(actions.joinToString(", "){literal(it)}).append('\n')
            }
            if(nodes.size>20)append("Showing 20 of ${nodes.size} nodes.\n")
            append("\nLive reachability has not been checked.")
        }
        return LocalToolAnswer(text,1,emptyList())
    }
    val root=payload as? JsonObject ?: error("The tool returned an unexpected response")
    if(intent.name=="device_connection")return deviceConnectionAnswer(root)
    return when(intent.name) {
        "android_control_status" -> {
            fun enabled(key:String)=checkNotNull((root[key] as? JsonPrimitive)?.booleanOrNull){"Phone status field $key is unavailable"}
            LocalToolAnswer("Phone control: ${if(enabled("enabled"))"enabled" else "off"}.\nAndroid Accessibility: ${if(enabled("service_bound"))"connected" else "not connected — enable Meshlit in Android Accessibility settings"}.\nApp scope: ${if(enabled("all_apps"))"all supported apps" else "${(root["packages"] as? JsonArray)?.size ?: 0} approved apps"}.",1,emptyList())
        }
        "web_search","app_search" -> {
            if(intent.name=="web_search")check(string(root,"status")=="ok"){"Web search did not complete"}
            val rows=checkNotNull(root["results"] as? JsonArray){"Search results were not returned"}.mapNotNull{it as? JsonObject}
            val sources=if(intent.name=="web_search")rows.mapNotNull{string(it,"url").takeIf{url->com.meshlit.search.safeSearchUrl(url)}}.distinct() else emptyList()
            val text=if(rows.isEmpty())"No matching results were returned." else buildString {
                append(if(intent.name=="web_search")"Web search results" else "Local search results").append(":\n\n")
                rows.take(5).forEachIndexed{index,row->
                    append(index+1).append(". ").append(literal(string(row,"title"))).append('\n')
                    val detail=string(row,"snippet").ifBlank{string(row,"detail")}
                    if(detail.isNotBlank())append(literal(detail).take(600)).append('\n')
                    if(intent.name=="web_search")string(row,"url").takeIf{url->com.meshlit.search.safeSearchUrl(url)}?.let{append(it).append('\n')}
                    append('\n')
                }
            }.trim()
            LocalToolAnswer(text,1,sources)
        }
        "agent_team_start" -> {
            val id=string(root,"run_id").also{check(it.isNotBlank()){"Team admission did not return a run ID"}}
            LocalToolAnswer("Team task accepted (${id.take(8)}). Open Agents → Agent teams to follow the conversation or stop it. The task has not been reported as completed.",1,emptyList())
        }
        "agent_team_status" -> {
            val runs=checkNotNull(root["runs"] as? JsonArray){"Team status was not returned"}.mapNotNull{it as? JsonObject}
            LocalToolAnswer(if(runs.isEmpty())"No agent-initiated team runs are recorded." else runs.takeLast(3).asReversed().joinToString("\n"){"${literal(string(it,"id").take(8))}: ${literal(string(it,"phase"))}"},1,emptyList())
        }
        else -> error("Unsupported direct chat request")
    }
}

/** Only clear phone-operation requests require an actual tool attempt. */
internal fun requiresPhoneToolCall(message:String):Boolean = Regex(
    "^(?:(?:open|launch) (?:the )?.*(?:app|browser)\\b|(?:click|tap)\\b|go (?:back|home)\\b|type .+ (?:into|in) (?:the )?(?:field|input|search)\\b)",RegexOption.IGNORE_CASE
).containsMatchIn(message.trim())
