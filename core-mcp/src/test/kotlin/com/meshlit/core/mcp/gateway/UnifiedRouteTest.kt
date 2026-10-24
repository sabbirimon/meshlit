package com.meshlit.core.mcp.gateway
import org.junit.Assert.*
import org.junit.Test
class UnifiedRouteTest {
    @Test fun revocationUnknownLatencyAndCooldown(){
        val route=UnifiedRoute("test",RouteProtocol.LLM,listOf("first","second"),selection=RouteSelection.LOWEST_OBSERVED_LATENCY)
        val seen=mapOf("first" to RouteObservation("first",100,false,1),"second" to RouteObservation("second",100,true,20))
        assertEquals("second",UnifiedRouteSelector.choose(route,false,200,seen){true})
        assertEquals("first",UnifiedRouteSelector.choose(route,false,200,emptyMap()){true})
        assertThrows(IllegalArgumentException::class.java){UnifiedRouteSelector.choose(route,true,200,seen){true}}
        assertThrows(IllegalArgumentException::class.java){UnifiedRouteSelector.choose(route.copy(enabled=false),false,200,seen){true}}
        assertThrows(IllegalArgumentException::class.java){UnifiedRouteSelector.choose(route.copy(protocol=RouteProtocol.EXTENSION),false,200,seen){true}}
    }
}
