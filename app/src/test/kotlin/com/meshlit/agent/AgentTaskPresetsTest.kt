package com.meshlit.agent

import com.meshlit.chat.ChatToolGroup
import org.junit.Assert.*
import org.junit.Test

class AgentTaskPresetsTest {
    @Test fun createsSixDisabledProfilesBoundToTheSelectedModelWithoutToolGrants() {
        val profiles=AgentTaskPresets.installMissing(emptyList(),"local-installed-model-id")
        assertEquals(6,profiles.size)
        assertEquals(6,profiles.map { it.id }.distinct().size)
        assertTrue(profiles.all { !it.enabled && it.modelId=="local-installed-model-id" })
        profiles.forEach { it.validate() }
        assertEquals(setOf(ChatToolGroup.LOCAL_SEARCH,ChatToolGroup.WEB),AgentTaskPresets.presets.single { it.key=="research" }.suggestedTools)
        assertEquals(setOf(ChatToolGroup.PHONE),AgentTaskPresets.presets.single { it.key=="phone" }.suggestedTools)
        assertTrue(AgentTaskPresets.presets.all { preset->preset.suggestedTools.sumOf { it.count }<=10 })
    }

    @Test fun repeatedInstallKeepsCustomProfilesAndEditedEnabledPresetsExactly() {
        val custom=AgentProfile(name="My own researcher",modelId="custom-model",instruction="My instructions",enabled=true)
        val first=AgentTaskPresets.installMissing(listOf(custom),"first-model")
        val edited=first.map { if(it.id==AgentTaskPresets.presets.first().profileId) it.copy(name="Edited assistant",instruction="Owner instruction",enabled=true) else it }
        assertEquals(edited,AgentTaskPresets.installMissing(edited,"different-model"))
        assertEquals(custom,edited.first())
        assertNull(AgentTaskPresets.forProfile(custom))
        assertEquals("general",AgentTaskPresets.forProfile(edited[1])?.key)
    }

    @Test fun recreatesOnlyMissingPresetWithTheNewSelectedModel() {
        val original=AgentTaskPresets.installMissing(emptyList(),"first-model")
        val remaining=original.dropLast(1)
        val next=AgentTaskPresets.installMissing(remaining,"second-model")
        assertEquals(remaining,next.take(5))
        assertEquals(original.last().id,next.last().id)
        assertEquals("second-model",next.last().modelId)
        assertFalse(next.last().enabled)
    }

    @Test fun capacityFailureDoesNotDropOrOverwriteSavedProfiles() {
        val ten=(1..10).map { AgentProfile(name="Custom $it",modelId="owner-model") }
        assertEquals(16,AgentTaskPresets.installMissing(ten,"new-model").size)
        val eleven=ten+AgentProfile(name="Keep me",modelId="owner-model",enabled=true)
        assertThrows(IllegalArgumentException::class.java) { AgentTaskPresets.installMissing(eleven,"new-model") }
        assertEquals(11,eleven.size)
        assertTrue(eleven.last().enabled)
    }

    @Test fun invalidModelAndDuplicateExistingIdentityAreRejected() {
        for(model in listOf("","  ","model\nsecret","x".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { AgentTaskPresets.installMissing(emptyList(),model) }
        }
        val profile=AgentProfile(name="Custom",modelId="local")
        assertThrows(IllegalArgumentException::class.java) { AgentTaskPresets.installMissing(listOf(profile,profile),"local") }
    }
}
