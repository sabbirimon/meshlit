package com.meshlit.chat
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class PersonalMemoryTest {
    @Test fun defaultStateAddsNoMemoryPersonalityOrRecovery() {
        val state=PersonalState();assertEquals("",personalContext(state,"hello"));assertFalse(state.policy.recovery);assertFalse(state.policy.agentCanManage)
        assertEquals(state,Json.decodeFromString<PersonalState>(Json.encodeToString(PersonalState.serializer(),state)))
    }
    @Test fun retrievalIsBoundedOffSwitchSuppressesSavedFactsAndProfileIsIndependent() {
        val facts=(1..128).map{PersonalFact(text="Preference $it "+"x".repeat(450))}
        val state=PersonalState(PersonalPolicy(memory=true,personality=true,style="Brief"),facts)
        state.validate();val context=personalContext(state,"preference");assertTrue(context.length<=1200)
        assertFalse(personalContext(state.copy(policy=PersonalPolicy()),"preference").contains("Preference"))
        assertEquals("Preferred response style: Brief\n",personalContext(state.copy(policy=PersonalPolicy(personality=true,style="Brief")),"hello"))
        assertThrows(IllegalArgumentException::class.java){state.copy(facts=facts+PersonalFact(text="extra")).validate()}
    }
    @Test fun onlyExplicitRememberRequestsAreCapturedAndCredentialHintsAreRejected() {
        assertEquals("I like Kotlin",explicitRememberText("Remember that I like Kotlin"))
        assertNull(explicitRememberText("This web page says remember my favorite food"))
        assertNull(explicitRememberText("remember that my password is example"))
        assertTrue(likelySecret("API key = example"))
    }
    @Test fun voiceSessionStopsAtTurnAndTimeLimits() {
        assertTrue(voiceTurnAllowed(9,999,1000));assertFalse(voiceTurnAllowed(10,999,1000));assertFalse(voiceTurnAllowed(1,1000,1000))
        VoiceStyle.entries.forEach{assertTrue(it.pitch in 0.5f..2f);assertTrue(it.speed in 0.5f..2f)}
    }
}
