package com.meshlit.chat

import com.meshlit.core.mcp.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DirectChatToolsTest {
    private val names=setOf("web_search","app_search","android_control_status","agent_team_start","agent_team_status")
    @Test fun freshExplicitNewsRequestUsesOnlySelectedWebTool() {
        val message="current ai news this week"
        assertNull(directChatToolIntent(message,ChatOptions(),names))
        assertNull(directChatToolIntent(message,ChatOptions(webTools=true),names-"web_search"))
        val intent=directChatToolIntent(message,ChatOptions(webTools=true),names)!!
        assertEquals("web_search",intent.name);assertEquals(message,intent.arguments["query"]!!.jsonPrimitive.content)
    }
    @Test fun questionsNegationAndPrivateContentDoNotTriggerPublicSearch() {
        for(text in listOf("how do I search the web for news?","don't search for news","search for my saved files","search for API key secret","write a Dockerfile for nginx","\"search the web for news\"","search the web for AI news and email results to boss@example.com")) {
            assertNull(text,directChatToolIntent(text,ChatOptions(webTools=true,phoneTools=true),names))
        }
        assertEquals("app_search",directChatToolIntent("search my chats for agent",ChatOptions(localSearchTools=true,webTools=true),names)!!.name)
    }
    @Test fun statusReadingNeverEnablesOrChangesPhonePermission() {
        val options=ChatOptions(phoneTools=true)
        assertEquals("android_control_status",directChatToolIntent("check phone control status",options,names)!!.name)
        for(text in listOf("turn on phone control","check and enable accessibility","open phone control settings"))assertNull(directChatToolIntent(text,options,names))
    }
    @Test fun explicitTeamTaskNeedsSelectedTeamToolsAndCreatesOnlyAnAdmission() {
        assertNull(directChatToolIntent("run team to check arithmetic",ChatOptions(),names))
        val intent=directChatToolIntent("run team to check arithmetic",ChatOptions(teamTools=true),names)!!
        assertEquals("agent_team_start",intent.name)
        assertEquals("check arithmetic",intent.arguments["goal"]!!.jsonPrimitive.content)
        assertEquals(setOf("request_id","goal"),intent.arguments.keys)
    }
    @Test fun selectedHandlerResultDrivesSearchAnswerAndProvenance()=runBlocking {
        val intent=directChatToolIntent("current ai news this week",ChatOptions(webTools=true),names)!!
        var calls=0
        val answer=executeDirectChatTool(intent){request->
            calls++;assertEquals("web_search",request.name)
            McpToolResult.Json(buildJsonObject{put("status","ok");put("results",buildJsonArray{add(buildJsonObject{
                put("title","Actual retrieved title");put("snippet","Actual retrieved snippet");put("url","https://example.com/article")
            })})})
        }
        assertEquals(1,calls);assertEquals(1,answer.calls)
        assertTrue(answer.text.contains("Actual retrieved snippet"));assertEquals(listOf("https://example.com/article"),answer.sources)
    }
    @Test fun deniedOrIncompleteToolResponseNeverBecomesAClaimedSuccess()=runBlocking {
        val intent=DirectChatToolIntent("web_search",buildJsonObject{put("query","news")})
        try{executeDirectChatTool(intent){McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,"Search paused")};fail()}catch(e:IllegalStateException){assertTrue(e.message!!.contains("Search paused"))}
        try{executeDirectChatTool(intent){McpToolResult.Json(buildJsonObject{put("status","blocked")})};fail()}catch(_:IllegalStateException){}
    }
    @Test fun onlyClearSelectedPhoneActionsRequireAnAttempt() {
        assertTrue(requiresPhoneToolCall("open the browser app"))
        assertTrue(requiresPhoneToolCall("tap the search button"))
        assertTrue(requiresPhoneToolCall("go back"))
        assertFalse(requiresPhoneToolCall("write a Dockerfile for nginx"))
        assertFalse(requiresPhoneToolCall("how do I open the browser app?"))
    }
    @Test fun explicitRemoteInventoryUsesOnlyTheSelectedReadHandler() {
        val available=names+"remote_nodes"
        assertNull(directChatToolIntent("list remote nodes",ChatOptions(),available))
        assertNull(directChatToolIntent("list remote nodes",ChatOptions(remoteTools=true),names))
        assertEquals("remote_nodes",directChatToolIntent("list remote nodes",ChatOptions(remoteTools=true),available)!!.name)
        for(message in listOf("enable remote commands","don't list remote nodes","list all devices"))
            assertNull(message,directChatToolIntent(message,ChatOptions(remoteTools=true),available))
    }
    @Test fun remoteInventoryRendersActualDelegationWithoutClaimingReachability()=runBlocking {
        val intent=DirectChatToolIntent("remote_nodes",buildJsonObject{})
        val empty=executeDirectChatTool(intent){McpToolResult.Json(buildJsonArray{})}
        assertTrue(empty.text.contains("No remote nodes are delegated"));assertEquals(1,empty.calls)
        val answer=executeDirectChatTool(intent){McpToolResult.Json(buildJsonArray{add(buildJsonObject{
            put("id","node-1");put("name","Approved desktop");put("actions",buildJsonArray{add("STATUS")});put("live_reachability","unknown")
        })})}
        assertTrue(answer.text.contains("Approved desktop"));assertTrue(answer.text.contains("STATUS"))
        assertTrue(answer.text.contains("Live reachability has not been checked"))
    }
    @Test fun deniedRemoteInventoryIsNeverReportedAsAnEmptyOrUsableFleet()=runBlocking {
        try {
            executeDirectChatTool(DirectChatToolIntent("remote_nodes",buildJsonObject{})){
                McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,"Remote commands are off")
            };fail()
        }catch(e:IllegalStateException){assertEquals("Remote commands are off",e.message)}
    }
}
