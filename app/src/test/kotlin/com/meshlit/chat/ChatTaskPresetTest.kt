package com.meshlit.chat

import com.meshlit.agent.AgentTaskPresets
import org.junit.Assert.*
import org.junit.Test

class ChatTaskPresetTest {
    private fun preset(key:String)=AgentTaskPresets.presets.single { it.key==key }

    @Test fun selectingTaskChangesOnlyADraftAndReplacesOtherToolGroups() {
        val saved=ChatOptions(systemPrompt="Owner instructions",nodeTools=true,memoryTools=true,localSearchTools=true,
            maxTokens=256,temperature=0.7f,historyMessages=3)
        val draft=ChatTaskPreset.apply(preset("research"),saved)
        assertEquals("Owner instructions",saved.systemPrompt)
        assertEquals(12,saved.toolCount) // Legacy selection used the old, incorrect eight-tool node count.
        assertEquals(preset("research").instruction,draft.systemPrompt)
        assertEquals(setOf(ChatToolGroup.LOCAL_SEARCH,ChatToolGroup.WEB),ChatToolGroup.entries.filter { it.selected(draft) }.toSet())
        assertEquals(4,draft.toolCount)
        assertEquals(saved.copy(systemPrompt=draft.systemPrompt,nodeTools=false,memoryTools=false,webTools=true),draft)
        draft.validate()
        // Cancelling simply discards draft; another conversation's immutable options are untouched.
        val other=ChatOptions(systemPrompt="Other conversation",phoneTools=true)
        ChatTaskPreset.apply(preset("writing"),draft)
        assertEquals("Other conversation",other.systemPrompt)
        assertTrue(other.phoneTools)
    }

    @Test fun plainTasksRetainTheChosenRouteAndOtherConversationSettings() {
        for(route in listOf(ChatOptions(onlineProfileId="saved-provider"),ChatOptions(routeId="saved-route"),ChatOptions(colibriMode="ON"))) {
            for(key in listOf("general","writing","code_review")) {
                val task=preset(key)
                assertNull(ChatTaskPreset.unavailableReason(task,route,coreCandidate=true,review=true))
                assertEquals(route.copy(systemPrompt=task.instruction),ChatTaskPreset.apply(task,route,coreCandidate=true,review=true))
            }
        }
    }

    @Test fun toolTasksNeverSilentlyChangeAnUnsupportedRouteOrBypassBuildRestrictions() {
        for(key in listOf("research","planning","phone")) {
            val task=preset(key)
            for(route in listOf(ChatOptions(onlineProfileId="provider"),ChatOptions(routeId="route"),ChatOptions(colibriMode="AUTO"))) {
                assertNotNull(ChatTaskPreset.unavailableReason(task,route))
                assertThrows(IllegalArgumentException::class.java) {ChatTaskPreset.apply(task,route)}
            }
            assertThrows(IllegalArgumentException::class.java) {ChatTaskPreset.apply(task,ChatOptions(),coreCandidate=true)}
            assertThrows(IllegalArgumentException::class.java) {ChatTaskPreset.apply(task,ChatOptions(),review=true)}
        }
    }

    @Test fun everyTaskRemainsWithinTheToolLimitAndDoesNotActivateAnAgentProfile() {
        for(task in AgentTaskPresets.presets) {
            val profile=task.profile("selected-model")
            val draft=ChatTaskPreset.apply(task,ChatOptions())
            draft.validate()
            assertTrue(draft.toolCount<=10)
            assertFalse(profile.enabled)
        }
    }
}
