package com.meshlit.chat

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatOptionsTest {
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
        assertFalse(old.webTools);assertFalse(old.phoneTools);old.validate()
    }
    @Test fun savedExplicitGrantsRoundTripAndCannotSelectRemoteOrRoutedModels() {
        val options=ChatOptions(webTools=true,phoneTools=true)
        options.validate()
        assertEquals(options,Json.decodeFromString<ChatOptions>(Json.encodeToString(ChatOptions.serializer(),options)))
        assertThrows(IllegalArgumentException::class.java){options.copy(onlineProfileId="provider").validate()}
        assertThrows(IllegalArgumentException::class.java){options.copy(routeId="route").validate()}
    }
    @Test fun nodeToolsAreDefaultOffLocalOnlyAndBoundedSeparatelyFromExternalTools() {
        assertFalse(ChatOptions().nodeTools)
        ChatOptions(nodeTools=true,memoryTools=true,localSearchTools=true).validate()
        for(options in listOf(ChatOptions(nodeTools=true,webTools=true),ChatOptions(nodeTools=true,phoneTools=true),ChatOptions(nodeTools=true,onlineProfileId="remote"))) {
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
        for (invalid in listOf(ChatOptions(peerTools=true,remoteTools=true,webTools=true,cryptoTools=true), ChatOptions(gibberlinkTools=true,onlineProfileId="p"), ChatOptions(nodeTools=true,memoryTools=true,localSearchTools=true,cryptoTools=true))) {
            assertThrows(IllegalArgumentException::class.java) { invalid.validate() }
        }
    }
}
