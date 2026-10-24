package com.meshlit.ui.modern
import com.meshlit.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceNavigationTest {
    @Test fun everyConnectedSettingIsDiscoverableAndRoutesAreUnique() {
        val ids=WorkspaceDestinations.all.map{it.id}
        assertEquals(ids.size,ids.distinct().size)
        assertTrue(ids.containsAll(SettingsDestinations.all.map{it.id}))
        assertTrue(ids.containsAll(listOf("monitor","network","ssh","securitylab","gateway","hyperl")))
        assertFalse("legacy" in ids)
        assertTrue(WorkspaceDestinations.search("SSH").any{it.id=="ssh"})
    }
    @Test fun sidebarAdaptsAndFocusModeDoesNotStealEditorSpace() {
        assertFalse(WorkspaceDestinations.wideSidebar(899f,WorkspaceLayout.ADAPTIVE))
        assertTrue(WorkspaceDestinations.wideSidebar(900f,WorkspaceLayout.ADAPTIVE))
        assertFalse(WorkspaceDestinations.wideSidebar(1600f,WorkspaceLayout.FOCUS))
    }
    @Test fun structuredOutputDoesNotTreatInvalidOrScalarJsonAsSuccess() {
        validateStructuredJson("{\"value\":1}");validateStructuredJson("[1,2]")
        for(text in listOf("","hello","null","42","```json\n{}\n```")) assertThrows(IllegalArgumentException::class.java){validateStructuredJson(text)}
    }
}
