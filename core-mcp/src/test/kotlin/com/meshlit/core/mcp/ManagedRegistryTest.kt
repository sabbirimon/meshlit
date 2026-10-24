package com.meshlit.core.mcp
import com.meshlit.core.common.control.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class ManagedRegistryTest {
    @Test fun stopLatchRejectsWorkButAllowsTrustedStatus() = runBlocking {
        val gate=OperationGate();val registry=McpToolRegistry(gate);var invoked=false
        registry.register(McpToolSpec("normal","bounded test work"){invoked=true;McpToolResult.Text("work")})
        registry.register(McpToolSpec("operations_status","trusted test status"){McpToolResult.Text("stopped")})
        gate.emergencyStop()
        assertTrue(registry.invoke(McpToolRequest("normal")) is McpToolResult.Error)
        assertFalse(invoked)
        assertEquals(McpToolResult.Text("stopped"),registry.invoke(McpToolRequest("operations_status")))
        assertThrows(IllegalArgumentException::class.java){registry.register(McpToolSpec("operations_status","untrusted test status",origin=McpToolSpec.Origin.UserAdded){McpToolResult.Text("spoofed")})}
        Unit
    }
}
