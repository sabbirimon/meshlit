package com.meshlit.core.registry

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalServiceRegistryTest {

    private fun desc(
        id: String,
        kind: ServiceKind = ServiceKind.Generic,
        health: HealthState = HealthState.Unknown,
        registeredAtMs: Long = 1_000L,
    ) = ServiceDescriptor(
        id = id,
        name = "svc-$id",
        kind = kind,
        ownerNodeId = "node-A",
        version = "0.1.0",
        capabilities = emptyList(),
        health = health,
        registeredAtMs = registeredAtMs,
    )

    @Test
    fun `register then get returns the descriptor`() = runTest {
        val reg = LocalServiceRegistry()
        val d = desc("svc-1")
        reg.register(d)
        assertEquals(d, reg.get("svc-1"))
    }

    @Test
    fun `register then unregister removes it`() = runTest {
        val reg = LocalServiceRegistry()
        reg.register(desc("svc-1"))
        reg.unregister("svc-1")
        assertNull(reg.get("svc-1"))
    }

    @Test
    fun `unregister of unknown id is a no-op`() = runTest {
        val reg = LocalServiceRegistry()
        reg.unregister("does-not-exist") // should not throw
    }

    @Test
    fun `list is sorted by id`() = runTest {
        val reg = LocalServiceRegistry()
        reg.register(desc("z"))
        reg.register(desc("a"))
        reg.register(desc("m"))
        val list = reg.list().first()
        assertEquals(listOf("a", "m", "z"), list.map { it.id })
    }

    @Test
    fun `updateHealth changes health and lastHeartbeatMs`() = runTest {
        val reg = LocalServiceRegistry()
        reg.register(desc("svc-1", health = HealthState.Unknown))
        reg.updateHealth("svc-1", HealthState.Healthy, timestampMs = 5_000L)
        val updated = reg.get("svc-1")!!
        assertTrue(updated.health is HealthState.Healthy)
        assertEquals(5_000L, updated.lastHeartbeatMs)
    }

    @Test
    fun `updateHealth on unknown id is a no-op`() = runTest {
        val reg = LocalServiceRegistry()
        reg.updateHealth("ghost", HealthState.Healthy) // should not throw
    }

    @Test
    fun `byKind filters by ServiceKind`() = runTest {
        val reg = LocalServiceRegistry()
        reg.register(desc("mcp-1", kind = ServiceKind.McpServer))
        reg.register(desc("agent-1", kind = ServiceKind.AgentRuntime))
        reg.register(desc("mcp-2", kind = ServiceKind.McpServer))
        val mcps = reg.byKind(ServiceKind.McpServer)
        assertEquals(setOf("mcp-1", "mcp-2"), mcps.map { it.id }.toSet())
    }

    @Test
    fun `register twice with same id overwrites`() = runTest {
        val reg = LocalServiceRegistry()
        reg.register(desc("svc-1", health = HealthState.Unknown))
        reg.register(desc("svc-1", health = HealthState.Healthy))
        val d = reg.get("svc-1")!!
        assertTrue("late register should overwrite earlier", d.health is HealthState.Healthy)
    }
}
