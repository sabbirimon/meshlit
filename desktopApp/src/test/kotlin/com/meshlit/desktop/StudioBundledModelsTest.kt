package com.meshlit.desktop

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class StudioBundledModelsTest {
    private fun route(id: String) = buildJsonObject { put("kind", "local"); put("modelId", id) }
    private fun session(options: EngineOptions) = LocalSession(HostEndpoint.parse("http://127.0.0.1:12345/v1"), "fixture", options.context, EngineAdmission(1, 2, 1), "fixture")
    @Test fun catalogDoesNotInventReadinessOrPermitArbitraryPaths() {
        assertEquals(DesktopStarter.filename, StudioBundledModels.resolve().filename)
        assertFails { StudioBundledModels.resolve("../../secret.gguf") }
        assertFails { StudioBundledModels.resolve("qwen35-4b") }
        val directory = Files.createTempDirectory("studio-catalog-test")
        try {
            val catalog = StudioBundledModels.catalog(directory)
            assertEquals(2, catalog["models"]!!.jsonArray.size)
            for (model in catalog["models"]!!.jsonArray) assertFalse(model.jsonObject["present"]!!.jsonPrimitive.boolean)
        } finally { directory.toFile().deleteRecursively() }
    }
    @Test fun selectionChangesReloadAndFailedSwitchCannotReuseOldModel() {
        val started = mutableListOf<String>(); var fail = false
        val engine = IdeEngineSession({EngineOptions(context=4096,threads=1,batchThreads=1)}, {true}) { options, model ->
            started += model.id; if(fail) error("failed load"); session(options)
        }
        val first=engine.load();assertSame(first,engine.load());assertEquals(1,started.size)
        val second=engine.load("qwen3-4b");assertNotSame(first,second);assertEquals(listOf("qwen25-1.5b","qwen3-4b"),started)
        fail=true;assertFails { engine.load("qwen25-1.5b") };fail=false
        engine.load("qwen3-4b");assertEquals(4,started.size)
        assertFails { engine.load("untrusted") };assertEquals(4,started.size)
    }
    @Test fun explicitThinkingIsBoundToAllowlistedLocalModelAndOverridesSdkFields() {
        val body = buildJsonObject {
            put("messages", buildJsonArray { add(buildJsonObject { put("role","user");put("content","2+2") }) })
            put("chat_template", "untrusted");put("chat_template_kwargs", buildJsonObject { put("enable_thinking",false);put("untrusted",true) })
            put("reasoning_budget",900000);put("reasoning_format","none")
        }
        val enabled=ideCompletionBody(body,route("qwen3-4b"),"auto",DesktopStarter.alias,2048,true,"on")
        assertEquals(buildJsonObject { put("enable_thinking",true) },enabled["chat_template_kwargs"])
        assertEquals("deepseek",enabled["reasoning_format"]!!.jsonPrimitive.content)
        assertNull(enabled["chat_template"]);assertNull(enabled["reasoning_budget"])
        val disabled=ideCompletionBody(body,route("qwen3-4b"),"auto",DesktopStarter.alias,1024,false,"off")
        assertFalse(disabled["chat_template_kwargs"]!!.jsonObject["enable_thinking"]!!.jsonPrimitive.boolean)
        val starter=ideCompletionBody(body,route("qwen25-1.5b"),"auto",DesktopStarter.alias,1024,false)
        assertNull(starter["chat_template_kwargs"]);assertNull(starter["reasoning_format"])
        assertFails { ideCompletionBody(body,route("qwen25-1.5b"),"auto","local",1024,false,"on") }
        assertFails { studioThinking(null,"on") };assertFails { studioThinking(StudioBundledModels.reasoning,"high") }
        assertTrue(studioThinking(StudioBundledModels.reasoning,"auto"))
    }
    @Test fun outputBudgetsRespectContextAndMissingFinalAnswerIsExplicit() {
        assertEquals(2048,ideOutputBudget(session(EngineOptions()),true))
        assertEquals(256,ideOutputBudget(session(EngineOptions(context=512)),true))
        assertEquals(1024,ideOutputBudget(session(EngineOptions()),false))
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(buildJsonObject { put("streamed",true);put("hasFinalContent",false) }) }
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject { put("message",buildJsonObject { put("reasoning_content","thinking only");put("content","") }) }) }) }) }
        requireStudioFinalAnswer(buildJsonObject { put("streamed",true);put("hasFinalContent",true) })
    }
    @Test fun streamSeparatesReasoningFromFinalContentEvidence() {
        fun stream(content: String) = ("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private thought\"}}]}\n\n"+content+"data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n").byteInputStream()
        val thoughtOnly=readIdeCompletionStream(stream("")) {}
        assertFalse(thoughtOnly["hasFinalContent"]!!.jsonPrimitive.boolean)
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(thoughtOnly) }
        val answered=readIdeCompletionStream(stream("data: {\"choices\":[{\"delta\":{\"content\":\"4\"}}]}\n\n")) {}
        requireStudioFinalAnswer(answered)
    }
    @Test fun genuineDeclaredToolCallsAreAcceptedButBlankOrMalformedCallsAreNot() {
        fun call(name: String="read_file", args: String="{}") = buildJsonObject {
            put("id","call_1");put("type","function");put("function",buildJsonObject { put("name",name);put("arguments",args) })
        }
        fun response(calls: JsonArray)=buildJsonObject { put("choices",buildJsonArray { add(buildJsonObject { put("message",buildJsonObject { put("content",JsonNull);put("tool_calls",calls) }) }) }) }
        val valid=response(JsonArray(listOf(call())))
        requireStudioFinalAnswer(valid,setOf("read_file"))
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(valid) }
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(response(JsonArray(listOf(call(args="bad-json")))),setOf("read_file")) }
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(response(JsonArray(listOf(call("unknown")))),setOf("read_file")) }
        assertFailsWith<StudioNoFinalAnswer> { requireStudioFinalAnswer(response(JsonArray(emptyList())),setOf("read_file")) }
        val fragment=buildJsonObject { put("choices",buildJsonArray { add(buildJsonObject { put("index",0);put("delta",buildJsonObject { put("tool_calls",JsonArray(listOf(JsonObject(call().toMutableMap().apply { put("index",JsonPrimitive(0)) })))) }) }) }) }
        val stream=("data: $fragment\n\ndata: {\"choices\":[{\"finish_reason\":\"tool_calls\"}]}\n\ndata: [DONE]\n\n").byteInputStream()
        val parsed=readIdeCompletionStream(stream,setOf("read_file")) {}
        assertTrue(parsed["hasValidToolCalls"]!!.jsonPrimitive.boolean)
        requireStudioFinalAnswer(parsed,setOf("read_file"))
    }
}
