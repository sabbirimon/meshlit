package com.meshlit.chat

import com.meshlit.core.inference.models.OnlineProfile
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.jsonObject

class NamedModelTaskTest {
    @Test fun parsesExplicitDestinationAndKeepsTaskText() {
        assertEquals(NamedModelTask(TaskProvider.GEMINI,"summarize this\nvery short note"),namedModelTask("Please ask Gemini to summarize this\nvery short note"))
        assertEquals(NamedModelTask(TaskProvider.CHATGPT,"To whom it may concern"),namedModelTask("give this task to ChatGPT: To whom it may concern"))
        assertEquals(NamedModelTask(TaskProvider.CHATGPT,""),namedModelTask("connect to OpenAI"))
        assertEquals(NamedModelTask(TaskProvider.GEMINI,"reply with hello"),namedModelTask("connect to Gemini and ask it to reply with hello"))
        assertEquals(NamedModelTask(TaskProvider.CHATGPT,"write a poem"),namedModelTask("@ChatGPT write a poem"))
        assertEquals(NamedModelTask(TaskProvider.GEMINI,"write a poem"),namedModelTask("Could you please ask Gemini to write a poem"))
        assertEquals(NamedModelTask(TaskProvider.GEMINI,"write a poem"),namedModelTask("/delegate gemini write a poem"))
    }
    @Test fun discussionAndLookalikeNamesStayInChat() {
        for(text in listOf("What do you think of Gemini?", "Do not ask ChatGPT to respond", "askgemini something", "ask GeminiWorks", "ask ChatGPT.com", "> ask Gemini to scan", "The file says\nAsk Gemini to do this.")) assertNull(text,namedModelTask(text))
    }
    @Test fun attachmentCannotSilentlyTakeTheDefaultVisionRoute() {
        assertNotNull(namedTaskAttachmentProblem("ask Gemini to describe the file",true))
        assertNull(namedTaskAttachmentProblem("ask Gemini to describe the file",false))
        assertNull(namedTaskAttachmentProblem("describe the file",true))
    }
    @Test fun compatibleProtocolAloneDoesNotIdentifyChatGPT() {
        val profile=OnlineProfile("local","Local Qwen","http://127.0.0.1:8000","qwen",enabled=true)
        assertFalse(TaskProvider.CHATGPT.accepts(profile))
        assertFalse(TaskProvider.GEMINI.accepts(profile))
        val openai=profile.copy(endpoint="https://api.openai.com/v1")
        assertTrue(TaskProvider.CHATGPT.accepts(openai))
        assertFalse(TaskProvider.CHATGPT.accepts(openai.copy(enabled=false)))
        assertFalse(TaskProvider.CHATGPT.accepts(profile.copy(endpoint="https://api.openai.com.example.org")))
    }
    @Test fun agentNeedsASeparatePinnedApprovedRouteAndScopedClient() {
        val profile=OnlineProfile("one","Gemini QA","http://127.0.0.1:8000","test",enabled=true,agentAllowed=true)
        val request=NamedModelTask(TaskProvider.GEMINI,"summarize a note")
        val key=com.meshlit.core.mcp.gateway.GatewayPrincipal("one",setOf(com.meshlit.core.mcp.gateway.GatewayScope.MCP,
            com.meshlit.core.mcp.gateway.GatewayScope.CHAT),setOf("cloud:one"),setOf("named_model_task"),128,Long.MAX_VALUE)
        val grant=namedProfileBinding(profile)
        requireAgentTaskGrant(key,request,profile,grant)
        for(changed in listOf(profile.copy(model="another"),profile.copy(endpoint="http://127.0.0.1:9000"),profile.copy(agentAllowed=false)))
            assertTrue(runCatching { requireAgentTaskGrant(key,request,changed,grant) }.isFailure)
        assertTrue(runCatching { requireAgentTaskGrant(key,request,profile,null) }.isFailure)
        val missingTool=com.meshlit.core.mcp.gateway.GatewayPrincipal("other",key.scopes,key.modelIds,emptySet(),128,Long.MAX_VALUE)
        assertTrue(runCatching { requireAgentTaskGrant(missingTool,request,profile,grant) }.isFailure)
        val missingModel=com.meshlit.core.mcp.gateway.GatewayPrincipal("other",key.scopes,emptySet(),key.toolNames,128,Long.MAX_VALUE)
        assertTrue(runCatching { requireAgentTaskGrant(missingModel,request,profile,grant) }.isFailure)
    }
    @Test fun bothProvidersReceiveAnIsolatedTaskThroughTheirOwnProtocol() = kotlinx.coroutines.runBlocking {
        val client=com.meshlit.core.inference.models.OnlineModelClient()
        val server=okhttp3.mockwebserver.MockWebServer()
        server.start(java.net.InetAddress.getByName("127.0.0.1"),0)
        try {
            for(provider in TaskProvider.entries) {
                val gemini=provider==TaskProvider.GEMINI
                val profile=OnlineProfile("test",provider.label,"http://127.0.0.1:${server.port}","qa",
                    protocol=if(gemini) com.meshlit.core.inference.models.OnlineProtocol.GEMINI else com.meshlit.core.inference.models.OnlineProtocol.OPENAI,
                    enabled=true,requiresApiKey=false)
                val named=requireNotNull(namedModelTask("Ask ${provider.label}: tell me one thing"))
                server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(if(gemini)
                    """{"candidates":[{"content":{"parts":[{"text":"hello"}]}}]}"""
                    else """{"choices":[{"message":{"content":"hello"}}]}"""))
                assertEquals("hello",client.generate(profile,"",named.messages(),maxTokens=32).text)
                val sent=server.takeRequest()
                assertEquals(if(gemini)"/models/qa:generateContent" else "/chat/completions",sent.path)
                val body=kotlinx.serialization.json.Json.parseToJsonElement(sent.body.readUtf8()).jsonObject
                if(gemini) {
                    assertEquals("""[{"role":"user","parts":[{"text":"tell me one thing"}]}]""",body["contents"].toString())
                    assertFalse(body.containsKey("systemInstruction"))
                } else assertEquals("""[{"role":"user","content":"tell me one thing"}]""",body["messages"].toString())
                assertFalse(body.containsKey("tools"))
            }
        } finally {server.shutdown()}
    }
}
