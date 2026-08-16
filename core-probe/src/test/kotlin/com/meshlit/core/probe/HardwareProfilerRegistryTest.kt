package com.meshlit.core.probe

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareProfilerRegistryTest {

    private fun ok(score: Float, raw: String) =
        MeshlitResult.Success(ProfileSample(score, raw))

    @Test
    fun `profileAll aggregates every axis into a HardwareCapability`() = runTest {
        val reg = HardwareProfilerRegistry(
            profilers = listOf(
                CpuProfiler { ok(0.8f, "arm64-v8a") },
                MemoryProfiler { ok(0.6f, "8192") }, // 8 GB
                ThermalProfiler { ok(0.9f, "0") }, // THERMAL_STATUS_NONE
                BatteryProfiler { ok(0.8f, "80") }, // 80%
                NetworkProfiler { ok(1.0f, "lan") },
                NpuProfiler { ok(1.0f, "yes") },
            ),
            clock = { 12_345L },
        )
        val res = reg.profileAll()
        assertTrue(res is MeshlitResult.Success)
        val cap = (res as MeshlitResult.Success).value
        assertEquals(0.8f, cap.cpu.score)
        assertEquals("8192", cap.memory.rawValue)
        assertEquals(12_345L, cap.timestampMs)
        assertEquals(8_192L, cap.totalRamMb)
        assertEquals(80, cap.batteryPct)
        assertTrue(cap.hasNpu)
        assertTrue(cap.networkReachable)
        assertTrue(!cap.isThrottling)
    }

    @Test
    fun `profileAll still succeeds when one profiler fails`() = runTest {
        val reg = HardwareProfilerRegistry(
            profilers = listOf(
                CpuProfiler { ok(0.5f, "x86_64") },
                MemoryProfiler { MeshlitResult.Failure(MeshlitError.Native("nope")) },
                ThermalProfiler { ok(0.9f, "0") },
                BatteryProfiler { ok(0.8f, "80") },
                NetworkProfiler { ok(1.0f, "lan") },
                NpuProfiler { ok(1.0f, "yes") },
            ),
        )
        val res = reg.profileAll()
        assertTrue(res is MeshlitResult.Success)
        val cap = (res as MeshlitResult.Success).value
        // Memory axis is filled with a zero-score sample rather than
        // crashing the snapshot.
        assertNotNull(cap.memory)
        assertEquals(0f, cap.memory.score)
        assertNull(cap.totalRamMb)
    }

    @Test
    fun `isThrottling is true when thermal score is below 0_5`() = runTest {
        val reg = HardwareProfilerRegistry(
            profilers = listOf(
                CpuProfiler { ok(0.8f, "arm64") },
                MemoryProfiler { ok(0.6f, "4096") },
                ThermalProfiler { ok(0.2f, "4") }, // THERMAL_STATUS_4 = overheating
                BatteryProfiler { ok(0.5f, "50") },
                NetworkProfiler { ok(1.0f, "wifi") },
                NpuProfiler { ok(0f, "no") },
            ),
        )
        val cap = (reg.profileAll() as MeshlitResult.Success).value
        assertTrue(cap.isThrottling)
        assertTrue(!cap.hasNpu)
    }
}
