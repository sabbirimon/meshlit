package com.meshlit.chat

import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class HostedChatSearchTest {
    @Test fun hostedGrantNeedsAnExplicitModeAndNeverEnablesLocalTools() {
        val selected=ChatOptions(onlineProfileId="provider",hostedToolReviewId="0".repeat(36))
        selected.validate();assertFalse(selected.hostedAgentToolsActive)
        val agent=selected.copy(executionMode=ChatExecutionMode.AGENT)
        agent.validate();assertTrue(agent.hostedAgentToolsActive);assertFalse(shouldPlanToolCall("open the phone",agent))
        assertThrows(IllegalArgumentException::class.java){agent.copy(phoneTools=true).validate()}
        assertThrows(IllegalArgumentException::class.java){agent.copy(onlineProfileId=null).validate()}
        assertThrows(IllegalArgumentException::class.java){agent.copy(maxTokens=16).validate()}
    }

    @Test fun onlyActualPublicResultFieldsReachTheHostedModel() {
        val raw=Json.parseToJsonElement("""{"status":"ok","provider":"Search","debug":"sensitive","results":[{"url":"https://example.test/a","title":"Result","snippet":"Evidence","debug":"sensitive"},{"url":"file:///private.txt","title":"Private"}]}""")
        val result=hostedSearchEvidence(McpToolResult.Json(raw))
        assertFalse(result.toString().contains("sensitive"));assertFalse(result.toString().contains("file:///"))
        assertEquals(listOf("https://example.test/a"),chatSourcesFromTool("web_search",McpToolResult.Json(result)).map{it.url})
    }
}
