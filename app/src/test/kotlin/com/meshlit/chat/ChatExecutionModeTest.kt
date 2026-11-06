package com.meshlit.chat

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatExecutionModeTest {
    @Test fun oldConversationsRetainSelectionsWithoutStartingTools() {
        val old=Json.decodeFromString<ChatOptions>("""{"webTools":true,"phoneTools":true}""")
        old.validate()
        assertEquals(ChatExecutionMode.CHAT,old.executionMode)
        assertTrue(old.webTools && old.phoneTools)
        assertFalse(shouldPlanToolCall("Open Chrome and search today",old))
    }

    @Test fun agentPlansShortAndNonEnglishMessagesWithoutKeywordGating() {
        val agent=ChatOptions(webTools=true,executionMode=ChatExecutionMode.AGENT)
        agent.validate()
        for(message in listOf("Hi","go","আজকের খবর খুঁজে দেখো","最新のニュースを調べて","ابحث عن الأخبار","আমাকে সাহায্য করো")) {
            assertTrue(message,shouldPlanToolCall(message,agent))
            assertFalse(message,shouldPlanToolCall(message,agent.copy(executionMode=ChatExecutionMode.CHAT)))
        }
        assertFalse(shouldPlanToolCall(" \n\t",agent))
    }

    @Test fun savedAgentChoiceDoesNotPersistBroaderActionApproval() {
        val original=ChatOptions(localSearchTools=true,executionMode=ChatExecutionMode.AGENT,approvalMode=ChatApprovalMode.SELECTED_TOOLS)
        val encoded=Json.encodeToString(original)
        val restored=Json.decodeFromString<ChatOptions>(encoded)
        restored.validate()
        assertTrue(restored.agentToolsActive)
        assertEquals(ChatApprovalMode.ASK,restored.approvalMode)
        assertFalse(encoded.contains("agentToolsActive"))
    }

    @Test fun unsupportedRoutesAndMissingSelectionsCannotActivateAgentMode() {
        for(options in listOf(ChatOptions(),ChatOptions(onlineProfileId="provider"),ChatOptions(routeId="route"),ChatOptions(colibriMode="ON"))) {
            val agent=options.copy(executionMode=ChatExecutionMode.AGENT)
            assertFalse(agent.agentToolsActive)
            assertThrows(IllegalArgumentException::class.java){agent.validate()}
        }
        for(options in listOf(ChatOptions(webTools=true,onlineProfileId="provider"),ChatOptions(webTools=true,routeId="route"),ChatOptions(webTools=true,colibriMode="AUTO"))) {
            val agent=options.copy(executionMode=ChatExecutionMode.AGENT)
            assertFalse(shouldPlanToolCall("Search the web",agent))
            assertThrows(IllegalArgumentException::class.java){agent.validate()}
        }
    }

    @Test fun removingLastToolReturnsToChatAndAddingToolsDoesNotActivateAgent() {
        val agent=ChatOptions(webTools=true,phoneTools=true,executionMode=ChatExecutionMode.AGENT)
        val phone=ChatToolGroup.WEB.update(agent,false)
        assertTrue(phone.agentToolsActive)
        val empty=ChatToolGroup.PHONE.update(phone,false)
        empty.validate()
        assertEquals(ChatExecutionMode.CHAT,empty.executionMode)
        val selected=ChatToolGroup.PHONE.update(empty,true)
        assertTrue(selected.phoneTools)
        assertFalse(selected.agentToolsActive)
    }

    @Test fun replacingToolsWithAPresetRetainsExplicitAgentModeOnlyWhenToolsRemain() {
        val agent=ChatOptions(phoneTools=true,executionMode=ChatExecutionMode.AGENT)
        val research=com.meshlit.agent.AgentTaskPresets.presets.single{it.key=="research"}
        val researcher=ChatTaskPreset.apply(research,agent)
        researcher.validate()
        assertTrue(researcher.agentToolsActive)
        assertTrue(researcher.webTools && researcher.localSearchTools)
        assertFalse(researcher.phoneTools)
        val writing=com.meshlit.agent.AgentTaskPresets.presets.single{it.key=="writing"}
        val writer=ChatTaskPreset.apply(writing,researcher)
        writer.validate()
        assertEquals(ChatExecutionMode.CHAT,writer.executionMode)
    }

    @Test fun chatAndAgentChoicesAreIndependentAcrossConversationsAndTaskPresets() {
        val agent=ChatOptions(localSearchTools=true,executionMode=ChatExecutionMode.AGENT)
        val chat=agent.copy(executionMode=ChatExecutionMode.CHAT)
        assertTrue(agent.agentToolsActive)
        assertFalse(chat.agentToolsActive)
        for(preset in com.meshlit.agent.AgentTaskPresets.presets) {
            val draft=ChatTaskPreset.apply(preset,chat)
            assertEquals(ChatExecutionMode.CHAT,draft.executionMode)
            assertFalse(draft.agentToolsActive)
        }
    }
}
