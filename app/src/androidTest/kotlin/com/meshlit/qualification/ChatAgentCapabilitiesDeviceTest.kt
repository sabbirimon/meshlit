package com.meshlit.qualification

import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatOptions
import com.meshlit.models.ModelLibrary
import com.meshlit.observability.LogBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Real Chat-controller requests, selected conversation tools and production handlers.
 * App/device grants stay unchanged; only this temporary QA conversation is configured. */
class ChatAgentCapabilitiesDeviceTest {
    @Test fun chatRequestsActuallyDispatchPhoneWebAndDeviceHandlers()=runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("qualifyChatAgents")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val graph=GlobalContext.get()
        val chat=graph.get<ChatController>();chat.ready.await()
        assertFalse("No owner's reply may be active",chat.state.value.running)
        assertTrue("Do not evict an owner's saved chat",chat.state.value.conversations.size<40)
        val library=graph.get<ModelLibrary>();library.ready.await()
        library.load(args.getString("modelId") ?: "url-824902c942c3cc73")
        val logs=graph.get<LogBuffer>()
        val selected=chat.state.value.selectedId
        chat.newChat();val qa=chat.state.value.selectedId!!
        val rows=mutableListOf<JsonObject>()
        val report=File(context.filesDir,"qualification/chat-agents-device.json")
        fun save(){report.parentFile!!.mkdirs();report.writeText(buildJsonObject{
            put("device",android.os.Build.MODEL);put("productionChatController",true)
            put("appAndDeviceGrantsChanged",false);put("checks",JsonArray(rows))
        }.toString())}
        try {
            // Only classified reads in this disposable QA conversation; saved device grants remain unchanged.
            val cases=listOf(
                Triple("phone","check phone control status",ChatOptions(executionMode=com.meshlit.chat.ChatExecutionMode.AGENT,approvalMode=com.meshlit.chat.ChatApprovalMode.AUTO_READS,phoneTools=true,maxTokens=256,historyMessages=0)),
                Triple("web","current ai news this week",ChatOptions(executionMode=com.meshlit.chat.ChatExecutionMode.AGENT,approvalMode=com.meshlit.chat.ChatApprovalMode.AUTO_READS,webTools=true,maxTokens=256,historyMessages=0)),
                Triple("devices","list remote nodes",ChatOptions(executionMode=com.meshlit.chat.ChatExecutionMode.AGENT,approvalMode=com.meshlit.chat.ChatApprovalMode.AUTO_READS,remoteTools=true,maxTokens=256,historyMessages=0)),
            )
            for((name,prompt,options) in cases) {
                val previous=logs.entries.value.toSet()
                var error:String?=null
                var answer="";var sources=0
                try {
                    chat.setOptions(options)
                    val id=checkNotNull(chat.send(prompt)){"Chat did not admit the request: ${chat.state.value.error}"}
                    val done=withTimeout(if(name=="devices")240000 else 60000){chat.state.first{s->!s.running && s.conversations.any{c->c.id==qa && c.messages.any{it.id==id}}}}
                    val reply=done.conversations.single{it.id==qa}.messages.single{it.id==id}
                    answer=reply.text;sources=reply.webSources.size;error=done.error
                } catch(e:Exception) {
                    error=e.message ?: e.javaClass.simpleName
                    chat.stop()
                    withTimeout(20000){chat.state.first{!it.running}}
                }
                val calls=logs.entries.value.filter{it !in previous && it.tag=="LocalAgent" && it.message=="Tool requested"}
                    .mapNotNull{it.context["tool"] as? String}
                rows+=buildJsonObject {
                    put("case",name);put("request",prompt);put("actualTools",buildJsonArray{calls.forEach{add(it)}})
                    put("answer",answer.take(2000));put("webSources",sources)
                    error?.let{put("error",it.take(500))}
                };save()
            }
            delay(6500) // Also catch Android's delayed foreground-service failure after fast replies.
        } finally {
            if(chat.state.value.running){chat.stop();withTimeout(20000){chat.state.first{!it.running}}}
            chat.delete(qa);selected?.let{chat.select(it)};save()
            withTimeout(5000) {
                while(runCatching { File(context.filesDir,"chat-selection-v1.txt").readText() }.getOrNull()!=selected.orEmpty())delay(25)
            }
        }
        fun calls(name:String)=rows.single{it["case"]?.jsonPrimitive?.content==name}["actualTools"]!!.jsonArray.map{it.jsonPrimitive.content}
        assertTrue("Phone status must run the actual handler", "android_control_status" in calls("phone"))
        assertTrue("Web search must run the actual handler", "web_search" in calls("web"))
        assertTrue("Web answer needs actual source receipts",rows.single{it["case"]?.jsonPrimitive?.content=="web"}["webSources"]!!.jsonPrimitive.int>0)
        assertTrue("Device inventory must call the read handler without changing command access","remote_nodes" in calls("devices"))
        assertFalse("Listing devices must not resume command dispatch","remote_commands_access" in calls("devices"))
    }
}
