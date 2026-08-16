package com.meshlit.core.bootstrap

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.config.BuiltInConfigKeys
import com.meshlit.core.config.InMemoryConfigRepository
import com.meshlit.core.flags.DefaultFlags
import com.meshlit.core.flags.FeatureFlag
import com.meshlit.core.flags.InMemoryFeatureFlagRegistry
import com.meshlit.core.lifecycle.LifecycleState
import com.meshlit.core.lifecycle.ManagedService
import com.meshlit.core.lifecycle.ServiceLifecycleController
import com.meshlit.core.probe.HardwareCapability
import com.meshlit.core.probe.HardwareProfilerRegistry
import com.meshlit.core.probe.ProfileSample
import com.meshlit.core.registry.HealthState
import com.meshlit.core.registry.LocalServiceRegistry
import com.meshlit.core.registry.ServiceDescriptor
import com.meshlit.core.registry.ServiceKind
import com.meshlit.core.role.Role
import com.meshlit.core.role.RoleManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapCoordinatorTest {

    private class FakeManagedService(
        override val id: String,
        override val requiredFeatureFlag: String? = null,
    ) : ManagedService {
        override val kind: ServiceKind = ServiceKind.Generic
        override val dependencies: List<String> = emptyList()
        override val descriptorFactory: (String) -> ServiceDescriptor = { nodeId ->
            ServiceDescriptor(
                id = id,
                name = "fake-$id",
                kind = kind,
                ownerNodeId = nodeId,
                version = "0.0.1",
                capabilities = emptyList(),
                health = HealthState.Healthy,
                registeredAtMs = 0L,
            )
        }
        var startCount = 0
        override suspend fun start(): MeshlitResult<Unit> {
            startCount++
            return MeshlitResult.Success(Unit)
        }
        override suspend fun stop(): MeshlitResult<Unit> = MeshlitResult.Success(Unit)
        override suspend fun healthCheck(): HealthState = HealthState.Healthy
    }

    private fun midSpecCapability() = HardwareCapability(
        cpu = ProfileSample(0.8f, "arm64-v8a"),
        memory = ProfileSample(0.6f, "8192"),
        thermal = ProfileSample(0.9f, "0"),
        battery = ProfileSample(0.8f, "80"),
        network = ProfileSample(1.0f, "lan"),
        npu = ProfileSample(1.0f, "yes"),
        timestampMs = 0L,
    )

    @Test
    fun `boot generates a node id on first run`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val coordinator = BootstrapCoordinator(
            config = config,
            flags = flags,
            idGenerator = { "fixed-uuid-for-test" },
        )
        val res = coordinator.boot()
        assertTrue(res is MeshlitResult.Success)
        val snap = (res as MeshlitResult.Success).value
        assertEquals("fixed-uuid-for-test", snap.nodeId)
        assertEquals("fixed-uuid-for-test", config.get(BuiltInConfigKeys.nodeId()))
    }

    /**
     * **Regression test for Fix 4** — previously the generated id
     * was handed back without a persistence write, so every
     * `boot()` call returned a fresh value. This test asserts:
     *
     *   1. The first `boot()` persists the id (visible in the
     *      repository immediately after).
     *   2. A second `boot()` against the same repository returns the
     *      *same* id, not a freshly generated one.
     */
    @Test
    fun `boot returns the same node id across restarts — Fix 4`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val firstId = "first-boot-uuid"
        val secondId = "second-boot-uuid"

        val coordA = BootstrapCoordinator(
            config = config,
            flags = flags,
            idGenerator = { firstId },
        )
        val resA = coordA.boot()
        assertTrue(resA is MeshlitResult.Success)
        assertEquals(firstId, (resA as MeshlitResult.Success).value.nodeId)

        val coordB = BootstrapCoordinator(
            config = config,
            flags = flags,
            idGenerator = { secondId },
        )
        val resB = coordB.boot()
        assertTrue(resB is MeshlitResult.Success)
        assertEquals(
            "Fix 4: persisted node id must be returned across restarts",
            firstId,
            (resB as MeshlitResult.Success).value.nodeId,
        )
        assertNotEquals(secondId, (resB as MeshlitResult.Success).value.nodeId)
    }

    @Test
    fun `boot loads flag defaults and surfaces them in the snapshot`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val coordinator = BootstrapCoordinator(config = config, flags = flags)
        val res = coordinator.boot()
        assertTrue(res is MeshlitResult.Success)
        val snap = (res as MeshlitResult.Success).value
        assertEquals(
            DefaultFlags.DISCOVERY_NSD.default,
            snap.flags[DefaultFlags.DISCOVERY_NSD.name],
        )
        assertEquals(
            DefaultFlags.GOSSIP_ENABLED.default,
            snap.flags[DefaultFlags.GOSSIP_ENABLED.name],
        )
    }

    @Test
    fun `boot report records Config and Flags as Ok without registry`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val coordinator = BootstrapCoordinator(config = config, flags = flags)
        val res = coordinator.boot()
        val snap = (res as MeshlitResult.Success).value
        val phases = snap.report.entries.map { it.phase }
        assertEquals(listOf(BootstrapPhase.Config, BootstrapPhase.Flags), phases)
        assertTrue(snap.report.entries.all { it.outcome == BootstrapReport.Outcome.Ok })
    }

    @Test
    fun `boot runs Registry and Services phases when supplied`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val registry = LocalServiceRegistry()
        val controller = ServiceLifecycleController(
            registry = registry,
            ownerNodeId = { "node-A" },
        )
        val svcA = FakeManagedService(id = "svc-a")
        val coordinator = BootstrapCoordinator(
            config = config,
            flags = flags,
            registry = registry,
            lifecycle = controller,
            services = listOf(svcA),
        )
        val res = coordinator.boot()
        val snap = (res as MeshlitResult.Success).value
        val phases = snap.report.entries.map { it.phase }
        assertTrue(BootstrapPhase.Registry in phases)
        assertTrue(BootstrapPhase.Services in phases)
        assertEquals(LifecycleState.Running, controller.stateOf("svc-a"))
        assertEquals(1, svcA.startCount)
    }

    @Test
    fun `boot runs Probe and Role phases and populates the snapshot`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        val profiler = HardwareProfilerRegistry(
            profilers = listOf(
                com.meshlit.core.probe.CpuProfiler { MeshlitResult.Success(ProfileSample(0.8f, "arm64-v8a")) },
                com.meshlit.core.probe.MemoryProfiler { MeshlitResult.Success(ProfileSample(0.6f, "8192")) },
                com.meshlit.core.probe.ThermalProfiler { MeshlitResult.Success(ProfileSample(0.9f, "0")) },
                com.meshlit.core.probe.BatteryProfiler { MeshlitResult.Success(ProfileSample(0.8f, "80")) },
                com.meshlit.core.probe.NetworkProfiler { MeshlitResult.Success(ProfileSample(1.0f, "lan")) },
                com.meshlit.core.probe.NpuProfiler { MeshlitResult.Success(ProfileSample(1.0f, "yes")) },
            ),
        )
        val roleManager = RoleManager(profiler)
        val coordinator = BootstrapCoordinator(
            config = config,
            flags = flags,
            profiler = profiler,
            roleManager = roleManager,
        )
        val res = coordinator.boot()
        val snap = (res as MeshlitResult.Success).value
        val phases = snap.report.entries.map { it.phase }
        assertTrue(BootstrapPhase.Probe in phases)
        assertTrue(BootstrapPhase.Role in phases)
        // Brain should win on a mid-spec device.
        assertNotNull(snap.role)
        assertEquals(Role.Brain, snap.role!!.role)
        assertTrue("confidence must exceed Idle baseline", snap.role.confidence > 0.05f)
    }

    @Test
    fun `unknown persisted node id is rejected and a new one generated`() = runTest {
        val config = InMemoryConfigRepository(
            initial = mapOf(BuiltInConfigKeys.nodeId().name to "   "),
        )
        val flags = InMemoryFeatureFlagRegistry()
        val coordinator = BootstrapCoordinator(
            config = config,
            flags = flags,
            idGenerator = { "fresh-after-blank" },
        )
        val res = coordinator.boot()
        assertTrue(res is MeshlitResult.Success)
        val snap = (res as MeshlitResult.Success).value
        assertEquals("fresh-after-blank", snap.nodeId)
        assertEquals("fresh-after-blank", config.get(BuiltInConfigKeys.nodeId()))
    }

    @Test
    fun `custom registered flag is surfaced in snapshot`() = runTest {
        val config = InMemoryConfigRepository()
        val flags = InMemoryFeatureFlagRegistry()
        flags.register(FeatureFlag(name = "feature.extra.test", default = false, description = ""))
        val coordinator = BootstrapCoordinator(config = config, flags = flags)
        val res = coordinator.boot()
        val snap = (res as MeshlitResult.Success).value
        assertNotNull(snap.flags["feature.extra.test"])
    }
}
