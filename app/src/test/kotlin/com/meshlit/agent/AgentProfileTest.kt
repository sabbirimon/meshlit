package com.meshlit.agent

import org.junit.Assert.*
import org.junit.Test

class AgentProfileTest {
    @Test fun newAgentHasNoAuthorityOrRunnableState() {
        val profile=AgentProfile(name="Research",modelId="local",instruction="Summarize notes")
        profile.validate()
        assertFalse(profile.enabled)
        assertThrows(IllegalArgumentException::class.java) { profile.copy(name="").validate() }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(instruction="x".repeat(1201)).validate() }
    }
}
