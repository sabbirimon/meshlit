package com.meshlit.workspace.agents
import kotlin.test.*
class AgencyRolesTest {
    @Test fun pinnedCatalogLoadsWithoutGrantingCapabilities() {
        val catalog=AgencyRoles.bundled()
        assertEquals("b37b5bb33b78f2c1d6567d98e25ca818e3259219",catalog.commit)
        assertTrue(catalog.roles.size>100)
        catalog.roles.forEach{role->
            assertTrue(AgencyRoles.boundedInstruction(role).length<=1200)
            assertContains(AgencyRoles.boundedInstruction(role),"No template grants tools")
            assertFalse(role.sourcePath.startsWith("/"));assertFalse(".." in role.sourcePath.split('/'))
        }
    }
}
