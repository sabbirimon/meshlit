package com.meshlit.ui.modern
import org.junit.Test
import org.junit.Assert.*
class SettingsDestinationsTest {
    @Test fun cloudVaultIsDiscoverableFromBasicSettings(){
        assertEquals(listOf("cloud"),SettingsDestinations.search("AWS credentials",false).map{it.id})
        assertEquals(listOf("cloud"),SettingsDestinations.search("GCP costs",false).map{it.id})
    }
    @Test fun searchHonorsAllWordsAcrossBothTabs(){
        assertEquals(listOf("agent-logs", "logs"),SettingsDestinations.search("log export",false).map{it.id})
        assertEquals(listOf("runtime"),SettingsDestinations.search("rootless",false).map{it.id})
        assertEquals(listOf("runtime"),SettingsDestinations.search("rootless",true).map{it.id})
        assertEquals(listOf("appearance"),SettingsDestinations.search("wallpaper colors",false).map{it.id})
    }
    @Test fun dashboardLinksRespectTheBuildBoundary(){
        assertTrue(com.meshlit.BuildProfile.routeAllowed("dashboards"))
        val ids=SettingsDestinations.all.map{it.id}.toSet()
        assertTrue("dashboards" in ids)
        com.meshlit.workspace.DashboardDirectory.entries.filter { com.meshlit.BuildProfile.routeAllowed(it.id) }.forEach {
            assertTrue("Missing dashboard destination: ${it.id}", it.id in ids)
        }
    }
    @Test fun basicAndAdvancedTabsShowDistinctCompleteMenus(){
        val basic=SettingsDestinations.search("",false).map{it.id}.toSet()
        val advanced=SettingsDestinations.search("",true).map{it.id}.toSet()
        assertTrue(basic.size in 1..6)
        assertTrue("appearance" in basic)
        assertFalse("providers" in basic)
        assertFalse("agents" in basic)
        assertTrue(advanced.size>10)
        assertTrue(basic.intersect(advanced).isEmpty())
        assertEquals(SettingsDestinations.all.map{it.id}.toSet(),basic+advanced)
        assertTrue("rewards" in advanced)
        assertTrue("hyperl" in advanced)
    }
}
