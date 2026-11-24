package com.meshlit.core.inference.models

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class HostedWebToolsTest {
    private val history=listOf(OnlineMessage("user","What is new?"))
    private fun profile(endpoint:String):OnlineProfile=OnlineProfile("provider","Provider",endpoint,"exact-model",
        enabled=true,agentAllowed=true,requiresApiKey=false).let{it.copy(hostedToolSupport=HostedToolSupport.review(it))}
    private fun decision(id:String="call_1",name:String="web_search",args:String="""{"query":"public news"}""",duplicate:Boolean=false):JsonObject=buildJsonObject {
        put("choices",buildJsonArray{add(buildJsonObject{
            put("finish_reason","tool_calls");put("message",buildJsonObject{put("role","assistant");put("tool_calls",buildJsonArray{
                repeat(if(duplicate)2 else 1){add(buildJsonObject{put("id",id);put("type","function");put("function",buildJsonObject{put("name",name);put("arguments",args)})})}
            })})
        })})
        put("usage",buildJsonObject{put("prompt_tokens",40);put("completion_tokens",8)})
    }

    @Test fun realHttpContractCorrelatesOneApprovedResultAndDisablesFurtherCalls()=runBlocking {
        MockWebServer().use{server->
            server.enqueue(MockResponse().setBody(decision().toString()))
            server.enqueue(MockResponse().setBody("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"One search result."}}],"usage":{"completion_tokens":3}}"""))
            val p=profile(server.url("/v1").newBuilder().host("127.0.0.1").build().toString())
            val review=checkNotNull(p.reviewedHostedTools()).reviewId
            val client=OnlineModelClient()
            val first=client.hostedWebRound("",client.prepareHostedWebRound(p,"",history,"",64,0f,ReasoningEffort.AUTO,review,null))
            val request=server.takeRequest()
            val payload=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("/v1/chat/completions",request.path)
            assertEquals("auto",payload.getValue("tool_choice").jsonPrimitive.content)
            assertFalse(payload.getValue("parallel_tool_calls").jsonPrimitive.boolean)
            assertEquals("web_search",payload.getValue("tools").jsonArray.single().jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content)
            val call=checkNotNull(first.search);assertEquals("public news",call.query);assertEquals(8L,first.outputTokens)
            val evidence=HostedSearchEvidence(call,buildJsonObject{put("results",JsonArray(emptyList()))})
            val last=client.hostedWebRound("",client.prepareHostedWebRound(p,"",history,"",64,0f,ReasoningEffort.AUTO,review,evidence))
            val sent=Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals("none",sent.getValue("tool_choice").jsonPrimitive.content)
            val messages=sent.getValue("messages").jsonArray
            assertEquals("call_1",messages.last().jsonObject.getValue("tool_call_id").jsonPrimitive.content)
            assertEquals("tool",messages.last().jsonObject.getValue("role").jsonPrimitive.content)
            assertEquals("One search result.",last.text)
            assertNull(last.search);assertNull(last.inputTokens)
        }
    }

    @Test fun malformedUnexpectedAndAdditionalCallsCannotDispatch() {
        for(invalid in listOf(decision(name="android_control"),decision(args="[]"),decision(args="""{"query":42}"""),
            decision(args="""{"query":"x","target":"private"}"""),decision(id="bad id"),decision(duplicate=true))) {
            assertTrue(runCatching{parseHostedWebTurn(invalid,true)}.isFailure)
        }
        assertTrue(runCatching{parseHostedWebTurn(decision(),false)}.isFailure)
    }

    @Test fun reviewBindsModelProtocolEndpointAndCurrentAgentPermission() {
        val p=profile("https://example.test/v1");val id=p.reviewedHostedTools()!!.reviewId
        assertTrue(p.hostedToolsAllowed(id));assertFalse(p.hostedToolsAllowed(null))
        for(changed in listOf(p.copy(model="replacement"),p.copy(endpoint="https://other.test/v1"),
            p.copy(protocol=OnlineProtocol.OPENAI),p.copy(gateway=OnlineGateway.OMNIROUTE),p.copy(enabled=false),p.copy(agentAllowed=false))) {
            assertFalse(changed.hostedToolsAllowed(id))
        }
        assertFalse(p.copy(hostedToolSupport=HostedToolSupport.review(p)).hostedToolsAllowed(id))
    }
}
