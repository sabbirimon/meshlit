package com.meshlit.core.mcp.gateway
import org.junit.Assert.*
import org.junit.Test
class FederatedProtocolTest {
    @Test fun mcpToolNamedLikeA2aRetainsItsEnrolledProtocol(){
        val route=RemoteAgentRoute("owned","http://127.0.0.1:18895/mcp","x".repeat(40),allowedTools=setOf("a2a_message_send"))
        assertEquals(RouteProtocol.MCP,route.federatedToolProtocol("remote_owned__a2a_message_send"))
        assertEquals(RouteProtocol.A2A,route.copy(protocol="A2A").federatedToolProtocol("remote_owned__a2a_message_send"))
        assertNull(route.federatedToolProtocol("remote_other__a2a_message_send"))
        assertNull(route.federatedToolProtocol("remote_owned__not_allowed"))
        assertNull(route.copy(protocol="A2A").federatedToolProtocol("remote_owned__not_allowed"))
        // A trailing underscore would alias route "owned_"/tool "name" with
        // route "owned"/tool "_name" when splitting the reserved double delimiter.
        assertThrows(IllegalArgumentException::class.java){route.copy(id="owned_").validate()}
        assertNull(route.copy(id="owned_").federatedToolProtocol("remote_owned___name"))
        val underscored=route.copy(allowedTools=setOf("_name"));underscored.validate()
        assertEquals(RouteProtocol.MCP,underscored.federatedToolProtocol("remote_owned___name"))
    }
}
