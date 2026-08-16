package com.meshlit.core.role

import com.meshlit.core.probe.HardwareCapability
import com.meshlit.core.probe.ProfileSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RolePolicyTest {

    private fun cap(
        ramMb: Long = 8_192L,
        batteryPct: Int = 80,
        throttling: Boolean = false,
        npu: Boolean = true,
        networkReachable: Boolean = true,
        thermalScore: Float = 0.9f,
        cpuScore: Float = 0.8f,
        memoryScore: Float = 0.6f,
        networkScore: Float = 1.0f,
        npuScore: Float = 1.0f,
    ) = HardwareCapability(
        cpu = ProfileSample(cpuScore, "arm64-v8a"),
        memory = ProfileSample(memoryScore, ramMb.toString()),
        thermal = ProfileSample(if (throttling) 0.2f else thermalScore, if (throttling) "4" else "0"),
        battery = ProfileSample(0.8f, batteryPct.toString()),
        network = ProfileSample(networkScore, if (networkReachable) "lan" else "offline"),
        npu = ProfileSample(if (npu) npuScore else 0f, if (npu) "yes" else "no"),
        timestampMs = 0L,
    )

    /**
     * **Regression test for Fix 1.**
     *
     * The original `Role.Idle -> 1.0f` made Idle the ceiling — any
     * non-perfect device lost to it and the role engine handed out
     * Idle to most real devices. With the fix, Idle is a 0.05
     * baseline that's never preferred unless nothing else scored
     * above it.
     *
     * A mid-spec phone with NPU + 8 GB RAM + 80% battery + not
     * throttling is exactly the case the bug broke. The Brain
     * score must exceed the Idle baseline.
     */
    @Test
    fun `mid-spec device scores Brain higher than Idle — Fix 1 regression`() {
        val mid = cap(ramMb = 8_192L, batteryPct = 80, npu = true, throttling = false)
        val brainScore = RolePolicy.score(mid, Role.Brain)
        val idleScore = RolePolicy.score(mid, Role.Idle)
        assertTrue(
            "Brain ($brainScore) must beat Idle ($idleScore) on a mid-spec device",
            brainScore > idleScore,
        )
        assertTrue(
            "Idle must be a low baseline, not a ceiling — was $idleScore",
            idleScore < 0.1f,
        )
    }

    @Test
    fun `Brain returns 0 when NPU missing`() {
        val c = cap(npu = false)
        assertEquals(0f, RolePolicy.score(c, Role.Brain), 0.001f)
    }

    @Test
    fun `Brain returns 0 when RAM below 6 GB`() {
        val c = cap(ramMb = 5 * 1024L - 100) // 4.9 GB
        assertEquals(0f, RolePolicy.score(c, Role.Brain), 0.001f)
    }

    @Test
    fun `Brain returns 0 when battery below 30 percent`() {
        val c = cap(batteryPct = 25)
        assertEquals(0f, RolePolicy.score(c, Role.Brain), 0.001f)
    }

    @Test
    fun `Brain returns 0 when throttling`() {
        val c = cap(throttling = true)
        assertEquals(0f, RolePolicy.score(c, Role.Brain), 0.001f)
    }

    @Test
    fun `Brain succeeds at the boundary — exactly 6 GB RAM, 30 percent battery`() {
        val c = cap(ramMb = 6 * 1024L, batteryPct = 30)
        assertTrue(
            "Brain should accept 6 GB / 30% / not throttling / NPU",
            RolePolicy.score(c, Role.Brain) > 0f,
        )
    }

    @Test
    fun `Tool requires at least 4 GB RAM`() {
        val tooSmall = cap(ramMb = 3 * 1024L)
        assertEquals(0f, RolePolicy.score(tooSmall, Role.Tool), 0.001f)
        val ok = cap(ramMb = 4 * 1024L)
        assertTrue(RolePolicy.score(ok, Role.Tool) > 0f)
    }

    @Test
    fun `Monitor requires battery and network`() {
        val noBattery = cap(batteryPct = 40)
        assertEquals(0f, RolePolicy.score(noBattery, Role.Monitor), 0.001f)
        val noNet = cap(networkReachable = false)
        assertEquals(0f, RolePolicy.score(noNet, Role.Monitor), 0.001f)
    }

    @Test
    fun `Relay requires network only`() {
        val noNet = cap(networkReachable = false)
        assertEquals(0f, RolePolicy.score(noNet, Role.Relay), 0.001f)
        val ok = cap(networkReachable = true)
        assertTrue(RolePolicy.score(ok, Role.Relay) > 0f)
    }

    @Test
    fun `Idle score is independent of capability`() {
        val a = RolePolicy.score(cap(ramMb = 16 * 1024L), Role.Idle)
        val b = RolePolicy.score(cap(ramMb = 1 * 1024L, npu = false, networkReachable = false), Role.Idle)
        assertEquals("Idle score must not depend on capability", a, b, 0.0001f)
    }

    @Test
    fun `suggestion engine picks the highest scorer`() {
        // Brain should win on this mid-spec phone.
        val mid = cap(ramMb = 8 * 1024L, batteryPct = 80, npu = true, throttling = false)
        val decision = RoleSuggestionEngine.suggest(mid)
        assertEquals(Role.Brain, decision.role)
        assertTrue(decision.confidence > 0.05f)
        assertTrue(decision.reasons.isNotEmpty())
        assertEquals(5, decision.scores.size) // every Role enumerated
    }

    @Test
    fun `suggestion engine falls back to Idle when nothing else scores above the baseline`() {
        val worst = cap(
            ramMb = 0L, // profiler couldn't read — no ram
            batteryPct = 0,
            npu = false,
            networkReachable = false,
            throttling = true,
        )
        val decision = RoleSuggestionEngine.suggest(worst)
        // Idle is the only role that has a non-zero score on a fully
        // unknown device — it must win.
        assertEquals(Role.Idle, decision.role)
        assertEquals(0.05f, decision.confidence, 0.0001f)
        // Every other role scored exactly 0.
        val others = decision.scores.filterKeys { it != Role.Idle }
        assertTrue(
            "every non-Idle role should score 0 on a fully unknown device",
            others.values.all { it == 0f },
        )
    }

    @Test
    fun `explain returns reasons for Brain when Brain wins`() {
        val mid = cap(ramMb = 8 * 1024L, batteryPct = 80, npu = true, throttling = false)
        val reasons = RolePolicy.explain(mid, Role.Brain)
        assertTrue("NPU available" in reasons)
        assertTrue(reasons.any { it.startsWith("RAM") })
        assertTrue(reasons.any { it.startsWith("battery") })
        assertTrue(reasons.any { it.contains("throttling") })
    }

    @Test
    fun `Brain always scores higher than Idle when both are eligible`() {
        // Boundary case: Brain is *eligible* (all thresholds met)
        // but not perfect (battery low). Idle's 0.05 must still
        // lose.
        val c = cap(ramMb = 6 * 1024L, batteryPct = 35, npu = true, throttling = false)
        assertNotEquals(0f, RolePolicy.score(c, Role.Brain))
        assertTrue(RolePolicy.score(c, Role.Brain) > RolePolicy.score(c, Role.Idle))
    }
}
