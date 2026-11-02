package com.meshlit.core.inference.models

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReasoningEffortTest {
    private fun profile()=OnlineProfile("fixture","Fixture","https://api.example.invalid/v1","model",enabled=true)
    private fun declared(profile:OnlineProfile,adapter:EffortAdapter=EffortAdapter.OPENAI)=profile.copy(effortSupport=EffortSupport(profile.endpoint,profile.model,adapter,setOf(ReasoningEffort.LOW,ReasoningEffort.HIGH)))
    @Test fun unknownAndChangedIdentitiesStayAutoOnly() {
        val p=declared(profile())
        assertEquals(setOf(ReasoningEffort.LOW,ReasoningEffort.HIGH),p.supportedEfforts())
        for(changed in listOf(p.copy(endpoint="https://other.invalid/v1"),p.copy(model="other"),p.copy(protocol=OnlineProtocol.GEMINI),profile())) {
            changed.validateEffort(ReasoningEffort.AUTO)
            assertTrue(runCatching{changed.validateEffort(ReasoningEffort.HIGH)}.isFailure)
        }
        assertTrue(runCatching{p.validateEffort(ReasoningEffort.MEDIUM)}.isFailure)
    }
    @Test fun autoOmitsUntrustedEffortButKeepsReasoningModelCompatibility() {
        val body=buildJsonObject{put("reasoning_effort","high");put("thinking",true);put("temperature",0.5);put("max_tokens",99999)}
        val result=reasoningPayload(body,declared(profile()),ReasoningEffort.AUTO,512)
        assertFalse("reasoning_effort" in result);assertFalse("thinking" in result);assertFalse("temperature" in result);assertFalse("max_tokens" in result)
        assertEquals(512,result["max_completion_tokens"]!!.jsonPrimitive.int)
        val router=declared(profile().copy(endpoint="https://openrouter.ai/api/v1"),EffortAdapter.OPENROUTER)
        val nested=reasoningPayload(buildJsonObject{},router,ReasoningEffort.HIGH,1024)
        assertEquals("high",nested["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue(declared(profile(),EffortAdapter.OPENROUTER).supportedEfforts().isEmpty())
    }
    @Test fun realHttpRequestUsesTheExactDeclaredEffortAndCommittedTokenLimit()=runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"Fixture response"}}],"usage":{"prompt_tokens":3,"completion_tokens":2}}"""))
            val p=declared(profile().copy(endpoint=server.url("/v1").newBuilder().host("127.0.0.1").build().toString().removeSuffix("/"),requiresApiKey=false))
            val reply=OnlineModelClient().generate(p,"",listOf(OnlineMessage("user","Fixture request")),maxTokens=4096,effort=ReasoningEffort.HIGH)
            val request=server.takeRequest();assertEquals("/v1/chat/completions",request.path)
            val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("high",body["reasoning_effort"]!!.jsonPrimitive.content)
            assertEquals(4096,body["max_completion_tokens"]!!.jsonPrimitive.int)
            assertEquals("Fixture response",reply.text)
        }
    }
}
