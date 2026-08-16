package com.meshlit.core.lifecycle

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.LocalServiceRegistry
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.registry.ServiceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceLifecycleControllerTest {

    private class Stub(
        override val id: String,
        override val kind: ServiceKind = ServiceKind.Generic,
        override val dependencies: List<String> = emptyList(),
        override val requiredFeatureFlag: String? = null,
        private val startBehavior: () -> MeshlitResult<Unit> = { MeshlitResult.Success(Unit) },
        private val stopBehavior: () -> MeshlitResult<Unit> = { MeshlitResult.Success(Unit) },
        private val health: () -> HealthState = { HealthState.Healthy },
    ) : ManagedService {
        var startCount: Int = 0
        var stopCount: Int = 0
        override suspend fun start(): MeshlitResult<Unit> {
            startCount++
            return startBehavior()
        }
        override suspend fun stop(): MeshlitResult<Unit> {
            stopCount++
            return stopBehavior()
        }
        override suspend fun healthCheck(): HealthState = health()
        override val descriptorFactory: (String) -> ServiceDescriptor = { nodeId ->
            ServiceDescriptor(
                id = id,
                name = "stub-$id",
                kind = kind,
                ownerNodeId = nodeId,
                version = "0.0.1",
                capabilities = emptyList(),
                health = HealthState.Unknown,
                registeredAtMs = 0L,
            )
        }
    }

    @Test
    fun `register then startAll brings service to Running`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val svc = Stub(id = "svc-1")
        ctrl.register(svc)
        val res = ctrl.startAll()
        assertTrue(res is MeshlitResult.Success)
        assertEquals(LifecycleState.Running, ctrl.stateOf("svc-1"))
        assertEquals(1, svc.startCount)
    }

    @Test
    fun `startAll is idempotent — second call is a no-op`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val svc = Stub(id = "svc-1")
        ctrl.register(svc)
        ctrl.startAll()
        ctrl.startAll()
        assertEquals(1, svc.startCount)
    }

    @Test
    fun `stopAll brings service to Idle`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val svc = Stub(id = "svc-1")
        ctrl.register(svc)
        ctrl.startAll()
        val res = ctrl.stopAll()
        assertTrue(res is MeshlitResult.Success)
        assertEquals(LifecycleState.Idle, ctrl.stateOf("svc-1"))
    }

    @Test
    fun `service blocked when required flag is disabled`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
            flagEnabled = { false },
        )
        val svc = Stub(id = "svc-1", requiredFeatureFlag = "feature.lifecycle.mcp_stub")
        ctrl.register(svc)
        val res = ctrl.startAll()
        assertTrue(res is MeshlitResult.Success) // no eligible services
        assertEquals(LifecycleState.Idle, ctrl.stateOf("svc-1"))
        assertEquals(0, svc.startCount)
    }

    @Test
    fun `service with unmet dependency is not eligible`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val dependent = Stub(id = "b", dependencies = listOf("a"))
        ctrl.register(dependent)
        ctrl.startAll()
        // "a" was never registered, so the dependency check fails.
        assertEquals(LifecycleState.Idle, ctrl.stateOf("b"))
    }

    @Test
    fun `service with satisfied dependency becomes eligible after dep starts`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val a = Stub(id = "a")
        val b = Stub(id = "b", dependencies = listOf("a"))
        ctrl.register(a)
        ctrl.register(b)
        ctrl.startAll()
        assertEquals(LifecycleState.Running, ctrl.stateOf("a"))
        assertEquals(LifecycleState.Running, ctrl.stateOf("b"))
    }

    @Test
    fun `start failure transitions state to Error and is reported back`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val svc = Stub(
            id = "svc-1",
            startBehavior = { MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Native("boom")) },
        )
        ctrl.register(svc)
        val res = ctrl.startAll()
        assertTrue(res is MeshlitResult.Failure)
        val state = ctrl.stateOf("svc-1")
        assertTrue(state is LifecycleState.Error)
    }

    @Test
    fun `healthCheckAll updates registry with current health`() = runTest {
        val reg = LocalServiceRegistry()
        val ctrl = ServiceLifecycleController(
            registry = reg,
            ownerNodeId = { "node-A" },
        )
        val svc = Stub(id = "svc-1", health = { HealthState.Healthy })
        ctrl.register(svc)
        ctrl.startAll()
        ctrl.healthCheckAll()
        val descriptor = reg.list().first().first { it.id == "svc-1" }
        assertTrue(descriptor.health is HealthState.Healthy)
        assertTrue("lastHeartbeatMs must be set", descriptor.lastHeartbeatMs != null)
    }
}
