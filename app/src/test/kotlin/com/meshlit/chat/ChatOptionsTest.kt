package com.meshlit.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatOptionsTest {
    @Test fun restartDropsSessionApprovalsButPreservesExplicitModelEffort() {
        val options=ChatOptions(onlineProfileId="provider",reasoningEffort=com.meshlit.core.inference.models.ReasoningEffort.HIGH,
            approvalMode=ChatApprovalMode.SELECTED_TOOLS,maxTokens=8192)
        options.validate()
        val encoded=Json.encodeToString(ChatOptions.serializer(),options)
        assertFalse(encoded.contains("approvalMode"))
        val loaded=Json.decodeFromString<ChatOptions>(encoded)
        assertEquals(ChatApprovalMode.ASK,loaded.approvalMode)
        assertEquals(options.reasoningEffort,loaded.reasoningEffort)
        assertEquals(8192,clusterOutputBudget(loaded,"runanywhere",null,null,0).limit)
        assertThrows(IllegalArgumentException::class.java){loaded.copy(onlineProfileId=null).validate()}
        assertThrows(IllegalArgumentException::class.java){ChatOptions(maxTokens=8192).validate()}
    }
    @Test fun toolGrantsDoNotForceOrdinaryChatThroughPlanner() {
        val tools=ChatOptions(webTools=true,phoneTools=true)
        assertFalse(shouldPlanToolCall("Hi",tools))
        assertFalse(shouldPlanToolCall("How are you?",tools))
        assertTrue(shouldPlanToolCall("Search the web for the latest release",tools))
        assertTrue(shouldPlanToolCall("Open Chrome and play a video",tools))
        assertFalse(shouldPlanToolCall("Search the web",ChatOptions()))
    }
    @Test fun oldChatsDoNotSilentlyGainWebOrPhoneTools() {
        val old=Json.decodeFromString<ChatOptions>("{\"maxTokens\":16}")
        assertFalse(old.webTools);assertFalse(old.phoneTools);assertFalse(old.teamTools);old.validate()
    }
    @Test fun savedExplicitGrantsRoundTripAndCannotSelectRemoteOrRoutedModels() {
        val options=ChatOptions(webTools=true,phoneTools=true)
        options.validate()
        assertEquals(options,Json.decodeFromString<ChatOptions>(Json.encodeToString(ChatOptions.serializer(),options)))
        assertThrows(IllegalArgumentException::class.java){ChatOptions(webTools=true,phoneTools=true,onlineProfileId="provider").validate()}
        assertThrows(IllegalArgumentException::class.java){ChatOptions(webTools=true,phoneTools=true,routeId="route").validate()}
    }
    @Test fun nodeToolsAreDefaultOffLocalOnlyAndBoundedSeparatelyFromExternalTools() {
        assertFalse(ChatOptions().nodeTools)
        ChatOptions(nodeTools=true).validate()
        for(options in listOf(ChatOptions(nodeTools=true,memoryTools=true,localSearchTools=true),ChatOptions(nodeTools=true,webTools=true),ChatOptions(nodeTools=true,phoneTools=true),ChatOptions(nodeTools=true,onlineProfileId="remote"))) {
            assertThrows(IllegalArgumentException::class.java) { options.validate() }
        }
    }
    @Test fun newDeviceAndCryptoGrantsStayOffForOldChatsAndRejectUnboundedToolMixes() {
        val old = Json.decodeFromString<ChatOptions>("{\"maxTokens\":256}")
        assertFalse(old.cryptoTools); assertFalse(old.gibberlinkTools); assertFalse(old.remoteTools); assertFalse(old.peerTools)
        assertEquals("OFF", old.colibriMode); assertFalse(old.colibriAgentAllowed)
        ChatOptions(peerTools=true,cryptoTools=true).validate()
        ChatOptions(remoteTools=true).validate()
        ChatOptions(gibberlinkTools=true).validate()
        ChatOptions(remoteTools=true,gibberlinkTools=true).validate()
        ChatOptions(peerTools=true,remoteTools=true,webTools=true).validate()
        for (invalid in listOf(ChatOptions(peerTools=true,remoteTools=true,webTools=true,cryptoTools=true), ChatOptions(nodeTools=true,memoryTools=true,localSearchTools=true,cryptoTools=true))) {
            assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
        }
    }
    @Test fun teamToolsRequireAnExplicitLocalGrantAndRecognizeDelegation() {
        val options=ChatOptions(teamTools=true)
        options.validate()
        assertEquals(3,options.toolCount)
        assertTrue(shouldPlanToolCall("Coordinate agents for this task",options))
        assertFalse(shouldPlanToolCall("Hi",options))
        assertThrows(IllegalArgumentException::class.java) { ChatOptions(teamTools=true,onlineProfileId="remote").validate() }
    }
    @Test fun retiredAudioFlagCannotExposeToolsOrForceALocalRoute() {
        val saved=Json.decodeFromString<ChatOptions>("""{"gibberlinkTools":true,"onlineProfileId":"saved-provider"}""")
        saved.validate();assertFalse(saved.usesLocalTools);assertEquals(0,saved.toolCount)
    }
}
