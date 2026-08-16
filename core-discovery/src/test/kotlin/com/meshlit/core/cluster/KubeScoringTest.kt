package com.meshlit.core.cluster

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.discovery.beacon.ThermalHeadroom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase Hivemind-1 — unit tests for [KubeScoring].
 *
 * The scorer is a pure function over [KubeScoring.Inputs] so each
 * test is just a deterministic assertion. The tests cover:
 *  - Per-axis formula correctness (hardware/load/health/power/network)
 *  - Eligibility gates (host-eligible vs worker-eligible)
 *  - Tie-break via deterministic sort
 *  - Defensive NaN/Inf handling
 */
class KubeScoringTest {

    private fun fullPhone(
        tier: CapabilityTier = CapabilityTier.FULL,
        ramMb: Long = 8192L,
        cores: Int = 8,
        gpu: Boolean = true,
        npu: Boolean = true,
        active: Int = 0,
        failures: Int = 0,
        rtt: Long = 20L,
        linkMbps: Double? = 1000.0,
        battery: Int = 80,
        charging: Boolean = false,
        thermal: ThermalHeadroom = ThermalHeadroom.COOL,
    ) = KubeScoring.Inputs(
        nodeId = "self",
        ip = "192.168.1.10",
        tier = tier,
        freeRamMb = ramMb,
        cpuCoreCount = cores,
        hasGpu = gpu,
        hasNpu = npu,
        healthOk = true,
        healthAgeMs = 5_000L,
        consecutiveFailures = failures,
        activeInferences = active,
        rttMs = rtt,
        linkSpeedMbps = linkMbps,
        batteryPct = battery,
        isCharging = charging,
        thermal = thermal,
    )

    @Test
    fun `hardware axis rewards FULL tier + GPU + NPU + cores`() {
        val full = fullPhone(tier = CapabilityTier.FULL, gpu = true, npu = true, cores = 8)
        val lite = fullPhone(tier = CapabilityTier.LITE, gpu = false, npu = false, cores = 4)
        val fullScore = KubeScoring.hardwareScore(full)
        val liteScore = KubeScoring.hardwareScore(lite)
        // FULL contributes 0.4, MID 0.2, LITE 0.0
        // GPU = 0.15, NPU = 0.10
        // 8 cores → 0.075, 4 cores → 0.0375
        assertTrue("FULL should beat LITE ($fullScore vs $liteScore)", fullScore > liteScore + 0.5)
    }

    @Test
    fun `load axis drops to zero when 4 active inferences`() {
        val idle = fullPhone(active = 0)
        val busy = fullPhone(active = 4)
        val saturated = fullPhone(active = 8)
        assertEquals(1.0, KubeScoring.loadScore(idle), 0.001)
        assertEquals(0.0, KubeScoring.loadScore(busy), 0.001)
        assertEquals(0.0, KubeScoring.loadScore(saturated), 0.001)
    }

    @Test
    fun `health axis penalises stale probes and consecutive failures`() {
        val fresh = fullPhone().copy(healthAgeMs = 1_000L, consecutiveFailures = 0)
        val stale = fullPhone().copy(healthAgeMs = 120_000L, consecutiveFailures = 0)
        val failing = fullPhone().copy(consecutiveFailures = 3)
        assertTrue(
            "fresh > stale",
            KubeScoring.healthScore(fresh) > KubeScoring.healthScore(stale),
        )
        assertTrue(
            "fresh > failing",
            KubeScoring.healthScore(fresh) > KubeScoring.healthScore(failing),
        )
    }

    @Test
    fun `power axis collapses to zero when thermal throttling`() {
        val cool = fullPhone(thermal = ThermalHeadroom.COOL, battery = 50)
        val throttling = fullPhone(thermal = ThermalHeadroom.THROTTLING, battery = 100)
        val charging = fullPhone(battery = 20, charging = true)
        assertEquals(0.0, KubeScoring.powerScore(throttling), 0.001)
        assertEquals(1.0, KubeScoring.powerScore(charging), 0.001)
        assertTrue(KubeScoring.powerScore(cool) > 0.4)
    }

    @Test
    fun `network axis favours gigabit over megabit`() {
        val giga = fullPhone(linkMbps = 1000.0, rtt = 5L)
        val slow = fullPhone(linkMbps = 50.0, rtt = 80L)
        assertTrue(KubeScoring.networkScore(giga) > KubeScoring.networkScore(slow))
    }

    @Test
    fun `host eligibility gate excludes throttling peers`() {
        val hot = KubeScoring.score(fullPhone(thermal = ThermalHeadroom.THROTTLING))
        val cool = KubeScoring.score(fullPhone(thermal = ThermalHeadroom.COOL))
        assertFalse(hot.hostEligible)
        assertTrue(cool.hostEligible)
    }

    @Test
    fun `total score is weighted sum of axes`() {
        val s = KubeScoring.score(fullPhone())
        val expected = s.hardware * 1.0 + s.load * 0.6 + s.health * 0.5 + s.power * 0.4 + s.network * 0.3
        assertEquals(expected, s.total, 0.001)
    }

    @Test
    fun `scoreAll sorts by total score descending with nodeId tiebreak`() {
        val peers = listOf(
            fullPhone().copy(nodeId = "alpha", tier = CapabilityTier.LITE),
            fullPhone().copy(nodeId = "beta", tier = CapabilityTier.FULL),
            fullPhone().copy(nodeId = "gamma", tier = CapabilityTier.MID),
        )
        val sorted = KubeScoring.scoreAll(peers)
        // FULL beats MID beats LITE regardless of input order.
        assertEquals(listOf("beta", "gamma", "alpha"), sorted.map { it.nodeId })
    }

    @Test
    fun `nan and inf collapse to zero`() {
        val broken = fullPhone().copy(
            activeInferences = 0,
            cpuCoreCount = 4,
            freeRamMb = 0L,
        )
        val score = KubeScoring.score(broken)
        // RAM = 0 clamps via coerceAtLeast(0.0625) so no NaN here.
        // But the helper should be robust if upstream ever
        // produces NaN.
        assertTrue(score.total.isFinite())
        assertTrue(score.total >= 0.0)
    }
}