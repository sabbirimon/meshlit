package com.meshlit.teams

import com.meshlit.core.inference.models.OnlineProfile
import org.junit.Assert.*
import org.junit.Test

class TeamProviderTargetTest {
    @Test fun endpointModelAndAgentGrantChangesInvalidatePreviouslySelectedTarget() {
        val profile=OnlineProfile("studio","Studio","https://studio.example/v1","local-model",enabled=true,agentAllowed=true)
        val approved=teamProviderTargetId(profile)
        assertEquals(approved,teamProviderTargetId(profile.copy()))
        for(changed in listOf(profile.copy(endpoint="https://other.example/v1"),profile.copy(model="other-model"),profile.copy(agentAllowed=false),profile.copy(enabled=false))) {
            assertNotEquals(approved,teamProviderTargetId(changed))
        }
        assertTrue(approved.length<=200)
        assertFalse(approved.contains("studio.example"))
    }
}
