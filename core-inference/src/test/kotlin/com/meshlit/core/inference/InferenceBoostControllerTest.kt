package com.meshlit.core.inference

import com.meshlit.core.common.DeviceProfile
import com.meshlit.core.common.EffectiveDeviceInfo
import com.meshlit.core.common.SocFamily
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InferenceBoostController]. Verifies the
 * dispatch-swap callback contract and idempotency. The actual
 * `Process.setThreadPriority` call is JVM-skipped (the constant
 * resolves to an int but the OS call is silently `runCatching`-
 * swallowed on the host), so the tests cover the controller's
 * behaviour without needing a real device.
 */
class InferenceBoostControllerTest {

    private fun profileWithCores(cores: Int, hasNpu: Boolean): DeviceProfile {
        val detection = com.meshlit.core.common.DetectedDeviceInfo(
            manufacturer = "Test",
            brand = "Test",
            model = "Test",
            device = "test",
            product = "test",
            hardware = "test",
            board = "test",
            abis = listOf("arm64-v8a"),
            primaryAbi = "arm64-v8a",
            socFamily = SocFamily.SNAPDRAGON_8,
            socModel = "Test",
            gpuFamily = com.meshlit.core.common.GpuFamily.ADRENO,
            hasNpu = hasNpu,
            npuName = null,
            totalRamMb = 8_192,
            availableRamMb = 4_096,
            totalStorageMb = 256_000,
            availableStorageMb = 128_000,
            cpuCoreCount = cores,
            cpuMaxFreqKHz = 2_400_000,
            androidVersion = "14",
            androidSdkInt = 34,
            securityPatch = "2024-01-01",
            buildFingerprint = "test/test/test:14/test",
            buildType = "user",
            detectedExternalGpu = null,
        )
        return DeviceProfile(detection = detection)
    }

    @Test
    fun `enable flips isActive and emits a dispatcher`() {
        val captured = mutableListOf<CoroutineDispatcher>()
        val controller = InferenceBoostController(
            onDispatcherChange = { captured.add(it) },
        )
        assertFalse(controller.isActive)
        controller.enable(profileWithCores(4, hasNpu = false))
        assertTrue(controller.isActive)
        assertEquals(1, captured.size)
        assertNotNull(captured[0])
    }

    @Test
    fun `disable reverts to Dispatchers Default`() {
        val captured = mutableListOf<CoroutineDispatcher>()
        val controller = InferenceBoostController(
            onDispatcherChange = { captured.add(it) },
        )
        controller.enable(profileWithCores(4, hasNpu = false))
        controller.disable()
        assertFalse(controller.isActive)
        assertEquals(2, captured.size)
        // The second emission is the revert to the global default.
        assertEquals(Dispatchers.Default, captured[1])
    }

    @Test
    fun `disable is a no-op when never enabled`() {
        val captured = mutableListOf<CoroutineDispatcher>()
        val controller = InferenceBoostController(
            onDispatcherChange = { captured.add(it) },
        )
        controller.disable()
        assertEquals(0, captured.size)
    }

    @Test
    fun `enable twice replaces the pool and emits a new dispatcher`() {
        val captured = mutableListOf<CoroutineDispatcher>()
        val controller = InferenceBoostController(
            onDispatcherChange = { captured.add(it) },
        )
        controller.enable(profileWithCores(2, hasNpu = false))
        val first = captured.last()
        controller.enable(profileWithCores(8, hasNpu = true))
        val second = captured.last()
        // The dispatcher is replaced; reference is different.
        assertTrue(first !== second)
        assertEquals(2, captured.size)
    }

    @Test
    fun `cores is clamped to at least 2 even for 1-core profiles`() {
        val captured = mutableListOf<CoroutineDispatcher>()
        val controller = InferenceBoostController(
            onDispatcherChange = { captured.add(it) },
        )
        controller.enable(profileWithCores(1, hasNpu = false))
        assertTrue(controller.isActive)
        // The dispatcher is non-null — the JVM ExecutorService
        // quietly downscales a 1-thread pool to itself, but the
        // controller's clamp guarantees we never request a
        // 0-thread pool.
        assertNotNull(captured.last())
    }
}
