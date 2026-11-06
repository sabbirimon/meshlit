package com.meshlit.ui.modern
import org.junit.Test
import org.junit.Assert.*
class SettingsDestinationsTest {
    @Test fun managementUsesUniqueExistingPlatformDestinations(){
        val sections=com.meshlit.workspace.DashboardDirectory.management
        assertEquals(sections.size,sections.map{it.id}.distinct().size)
        val routes=sections.flatMap{it.destinations}
        assertEquals(routes.size,routes.distinct().size)
        val visible=SettingsDestinations.all.map{it.id}.toSet()
        routes.filter{com.meshlit.BuildProfile.routeAllowed(it)}.forEach {
            assertTrue("Missing Management route: $it",it in visible)
        }
        assertTrue(routes.containsAll(listOf("ssh","cloud","device-dashboard","monitor","usage")))
        assertEquals(SettingsGroup.MANAGEMENT,SettingsDestinations.all.first{it.id=="dashboards"}.group)
    }
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
        assertTrue(basic.size in 1..11)
        assertTrue("appearance" in basic)
        assertTrue("providers" in basic)
        assertTrue("personalization" in basic)
        assertTrue("voice-settings" in basic)
        assertFalse("agents" in basic)
        assertTrue(advanced.size>10)
        assertTrue(basic.intersect(advanced).isEmpty())
        assertEquals(SettingsDestinations.all.map{it.id}.toSet(),basic+advanced)
        assertFalse("rewards" in advanced)
        assertTrue(com.meshlit.BuildProfile.retiredRoutes.intersect(basic+advanced).isEmpty())
        assertTrue("hyperl" in advanced)
    }
    @Test fun everyVisibleDestinationHasExactlyOneCategoryAndRemainsReachable(){
        val visible=SettingsDestinations.all
        assertEquals(visible.size,visible.map{it.id}.distinct().size)
        for(entry in visible) {
            assertEquals("Category for ${entry.id}",1,SettingsGroup.entries.count{entry.id in it.destinations})
            assertTrue(com.meshlit.BuildProfile.routeAllowed(entry.id))
        }
        val grouped=SettingsDestinations.groups(true).flatMap {group->SettingsDestinations.search("",true).filter{it.group==group}}
        assertEquals(SettingsDestinations.search("",true).map{it.id}.toSet(),grouped.map{it.id}.toSet())
        assertEquals(grouped.size,grouped.map{it.id}.distinct().size)
    }
    @Test fun naturalVoiceAndMemorySearchReachWorkingControlsFromEitherTab(){
        for(advanced in listOf(false,true)) {
            assertEquals(listOf("voice-settings"),SettingsDestinations.search("Kokoro preview",advanced).map{it.id})
            assertEquals(listOf("personalization"),SettingsDestinations.search("memory personality",advanced).map{it.id})
        }
    }
}
