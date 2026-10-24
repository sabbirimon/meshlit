package com.meshlit.desktop

import org.junit.Test
import kotlin.test.*

class DesktopSearchTest {
    @Test fun allSourcesAreScopedAndSettingsIncludeAdvancedAliases() {
        val entries=DesktopSearch.entries(listOf("assistant" to "Weather in Dhaka"),
            listOf(SavedModel("/models/dolphin.gguf",24,"a".repeat(64))),
            listOf(SavedNode("11111111-1111-1111-1111-111111111111","Dhaka server",DeviceCategory.CLUSTER,"https://node.example/v1",HostProtocol.OPENAI)))
        assertEquals(setOf(SearchType.CHAT,SearchType.DEVICES),DesktopSearch.find(entries,"Dhaka",SearchType.ALL).map {it.entry.type}.toSet())
        assertEquals(1,DesktopSearch.find(entries,"Dhaka",SearchType.CHAT).size)
        assertTrue(DesktopSearch.find(entries,"A2A",SearchType.SETTINGS).any {it.entry.destination=="gateway" && !it.entry.available})
        assertTrue(DesktopSearch.find(entries,"VPN",SearchType.SETTINGS).isNotEmpty())
        assertEquals("models",DesktopSearch.find(entries,"dolphin.gguf",SearchType.MODELS).single().entry.destination)
    }
    @Test fun snippetsReachTheMatchWithoutUnboundedResultsOrEmptyQueryLeaks() {
        val entries=(1..200).map {SearchEntry("$it",SearchType.CHAT,"assistant", "x".repeat(5000)+" needle "+"x".repeat(5000),"chat")}
        val hits=DesktopSearch.find(entries,"needle",SearchType.ALL)
        assertEquals(100,hits.size);assertTrue(hits.all {it.excerpt.length<=422 && "needle" in it.excerpt})
        assertTrue(DesktopSearch.find(entries," ",SearchType.ALL).isEmpty())
        assertTrue(DesktopSearch.find(entries,"needle missing",SearchType.ALL).isEmpty())
    }
    @Test fun browserHandoffEncodesQueryIntoFixedHttpsSearchDestination() {
        val uri=DesktopSearch.webUri("hello &q=secret / 中文")
        assertEquals("https",uri.scheme);assertEquals("duckduckgo.com",uri.host)
        assertTrue("%26q%3Dsecret" in uri.rawQuery)
        assertFailsWith<IllegalArgumentException> {DesktopSearch.webUri("bad\nquery")}
    }
}
