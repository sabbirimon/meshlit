package com.meshlit.inference

import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PeerLoadScorer]. Pure-function / IO-free
 * assertions over the [PeerLoadScorer.CandidateLoad] record. No
 * FGS, no HTTP, no DataStore — just arithmetic.
 *
 * Scoring weights under test (mirrors the table in
 * [PeerLoadScorer]):
 *
 *  - model loaded: +0.5
 *  - tier FULL: +0.4, MID: +0.2, LITE: 0
 *  - GPU offload: +0.2
 *  - active inferences: −0.2 each (clamped at −1.0)
 *  - queue > 0: −0.2
 *  - p50 latency > 500ms: −0.05 / 100ms over (clamped at −0.5)
 *  - free RAM < 512 MiB: −0.3
 *  - consecutive failures: −0.1 each (clamped at −1.0)
 *  - trust SANDBOXED: −0.1
 */
class PeerLoadScorerTest {

    /** Build a [PeerCapabilities] with sensible defaults for the
     *  field under test. Keeps the assertions focused on one knob. */
    private fun caps(
        tier: CapabilityTier = CapabilityTier.FULL,
        freeRamMb: Long = 4_096L,
        gpuBackend: com.meshlit.core.inference.GpuBackend = com.meshlit.core.inference.GpuBackend.NONE,
        isExternalGpu: Boolean = false,
    ) = PeerCapabilities(
        peerId = "self",
        capabilityTier = tier,
        freeRamMb = freeRamMb,
        freeDiskMb = 8_192L,
        hostedShardIds = emptySet(),
        lastSeenMs = System.currentTimeMillis(),
        tier = TrustTier.LOCAL_TRUSTED,
        gpuBackend = gpuBackend,
        vramMb = 0L,
        isExternalGpu = isExternalGpu,
    )

    /** Build a [PeerHealthCache.PeerHealth] with sensible defaults. */
    private fun health(
        ok: Boolean = true,
        modelLoaded: Boolean = true,
        asOfMs: Long = System.currentTimeMillis(),
        activeInferences: Int = 0,
        queueDepth: Int = 0,
        p50LatencyMs: Long = 0L,
        rttMs: Long = 0L,
        consecutiveFailures: Int = 0,
    ) = PeerHealthCache.PeerHealth(
        ok = ok,
        modelLoaded = modelLoaded,
        asOfMs = asOfMs,
        activeInferences = activeInferences,
        queueDepth = queueDepth,
        p50LatencyMs = p50LatencyMs,
        rttMs = rttMs,
        consecutiveFailures = consecutiveFailures,
    )

    private fun candidate(
        ip: String = "192.168.1.20",
        caps: PeerCapabilities = caps(),
        health: PeerHealthCache.PeerHealth = health(),
        activeInferences: Int = health.activeInferences,
        queueDepth: Int = health.queueDepth,
        p50LatencyMs: Long = health.p50LatencyMs,
        rttMs: Long = health.rttMs,
        consecutiveFailures: Int = health.consecutiveFailures,
        trustTier: TrustTier = TrustTier.LOCAL_TRUSTED,
    ) = PeerLoadScorer.CandidateLoad(
        ip = ip,
        capabilities = caps,
        health = health,
        activeInferences = activeInferences,
        queueDepth = queueDepth,
        p50LatencyMs = p50LatencyMs,
        rttMs = rttMs,
        consecutiveFailures = consecutiveFailures,
        trustTier = trustTier,
    )

    @Test
    fun `healthy FULL tier with model loaded scores baseline 0_9`() {
        // modelLoaded 0.5 + tier FULL 0.4 = 0.9
        val score = PeerLoadScorer().scoreOf(candidate())
        assertEquals(0.9, score, 0.0001)
    }

    @Test
    fun `healthy MID tier with model loaded scores 0_7`() {
        // 0.5 + 0.2 = 0.7
        val score = PeerLoadScorer().scoreOf(
            candidate(caps = caps(tier = CapabilityTier.MID)),
        )
        assertEquals(0.7, score, 0.0001)
    }

    @Test
    fun `LITE tier gets no tier bonus`() {
        // 0.5 only
        val score = PeerLoadScorer().scoreOf(
            candidate(caps = caps(tier = CapabilityTier.LITE)),
        )
        assertEquals(0.5, score, 0.0001)
    }

    @Test
    fun `active inferences drop the score by 0_2 each`() {
        val a = PeerLoadScorer().scoreOf(candidate(activeInferences = 1))
        val b = PeerLoadScorer().scoreOf(candidate(activeInferences = 3))
        // 1 * 0.2 = -0.2 ; 3 * 0.2 = -0.6 (still under -1.0 clamp)
        assertEquals(0.9 - 0.2, a, 0.0001)
        assertEquals(0.9 - 0.6, b, 0.0001)
    }

    @Test
    fun `load penalty clamps at -1_0 even with huge load`() {
        val score = PeerLoadScorer().scoreOf(candidate(activeInferences = 99))
        // 0.9 - clamp(99 * 0.2 = 19.8, 1.0) = -0.1
        assertEquals(0.9 - 1.0, score, 0.0001)
    }

    @Test
    fun `queue depth greater than zero adds a flat -0_2`() {
        val score = PeerLoadScorer().scoreOf(candidate(queueDepth = 1))
        assertEquals(0.9 - 0.2, score, 0.0001)
    }

    @Test
    fun `RAM below 512 MiB triggers -0_3 penalty`() {
        val score = PeerLoadScorer().scoreOf(
            candidate(caps = caps(freeRamMb = 256L)),
        )
        assertEquals(0.9 - 0.3, score, 0.0001)
    }

    @Test
    fun `consecutive failures clamp at -1_0`() {
        val a = PeerLoadScorer().scoreOf(candidate(consecutiveFailures = 2))
        val b = PeerLoadScorer().scoreOf(candidate(consecutiveFailures = 50))
        // 2 * 0.1 = -0.2 ; clamp(50 * 0.1, 1.0) = -1.0
        assertEquals(0.9 - 0.2, a, 0.0001)
        assertEquals(0.9 - 1.0, b, 0.0001)
    }

    @Test
    fun `SANDBOXED trust tier gets -0_1 penalty`() {
        val score = PeerLoadScorer().scoreOf(
            candidate(trustTier = TrustTier.LOCAL_SANDBOXED),
        )
        assertEquals(0.9 - 0.1, score, 0.0001)
    }

    @Test
    fun `p50 latency over 500ms scales -0_05 per 100ms above`() {
        val score = PeerLoadScorer().scoreOf(
            candidate(p50LatencyMs = 500 + 1_000L),
        )
        // 1_000ms over ⇒ 10 × 0.05 = -0.5
        assertEquals(0.9 - 0.5, score, 0.0001)
    }

    @Test
    fun `p50 latency above clamp still scores 0_4 floor`() {
        // 50_000 ms over ⇒ 500 * 0.05 = 25 ⇒ clamped at 0.5
        val score = PeerLoadScorer().scoreOf(
            candidate(p50LatencyMs = 50_500L),
        )
        assertEquals(0.9 - 0.5, score, 0.0001)
    }

    @Test
    fun `pick returns null when no candidates`() {
        assertNull(PeerLoadScorer().pick(candidates = emptyList(), sticky = null))
    }

    @Test
    fun `pick returns the highest-scoring candidate`() {
        val scorer = PeerLoadScorer()
        val busy = candidate(
            ip = "192.168.1.21",
            health = health(activeInferences = 3),
        )
        val idle = candidate(
            ip = "192.168.1.22",
            health = health(activeInferences = 0),
        )
        val picked = scorer.pick(listOf(busy, idle), sticky = null)
        assertNotNull(picked)
        assertEquals("192.168.1.22", picked!!.ip)
    }

    @Test
    fun `pick honours sticky pin until 3 consecutive failures`() {
        val scorer = PeerLoadScorer()
        val a = candidate(
            ip = "192.168.1.21",
            health = health(consecutiveFailures = 0, asOfMs = 100L),
        )
        val b = candidate(
            ip = "192.168.1.22",
            health = health(consecutiveFailures = 0, asOfMs = 200L),
        )
        // b scores higher (more recent) but sticky pin holds.
        val pinned = scorer.pick(listOf(a, b), sticky = "192.168.1.21")
        assertEquals("192.168.1.21", pinned!!.ip)

        // Push a over the threshold — pin releases, b wins.
        // The CandidateLoad wrapper mirrors health.consecutiveFailures,
        // so we have to update both the health record and the wrapper
        // field. (The wrapper is what the scorer reads; the health
        // record is what diagnostics emit.)
        val aFailed = a.copy(
            health = a.health.copy(consecutiveFailures = 3),
            consecutiveFailures = 3,
        )
        val released = scorer.pick(listOf(aFailed, b), sticky = "192.168.1.21")
        assertEquals("192.168.1.22", released!!.ip)
    }

    @Test
    fun `pick tiebreaks on health asOfMs when scores are equal`() {
        val scorer = PeerLoadScorer()
        val older = candidate(
            ip = "192.168.1.21",
            health = health(asOfMs = 100L),
        )
        val newer = candidate(
            ip = "192.168.1.22",
            health = health(asOfMs = 200L),
        )
        // Same score (both healthy FULL); newer should win.
        val picked = scorer.pick(listOf(older, newer), sticky = null)
        assertEquals("192.168.1.22", picked!!.ip)
    }

    @Test
    fun `snapshots flow emits a list per pick`() = runTest {
        val scorer = PeerLoadScorer()
        val emitted = mutableListOf<List<PeerLoadScorer.WeightSnapshot>>()
        // Collect on a background coroutine — SharedFlow requires a subscriber
        // before tryEmit drops with NO_SUBSCRIBERS. We use `take(1)` so the
        // collector cancels itself after the first emission, which closes
        // the runTest cleanly without manual job bookkeeping.
        val collectJob = launch {
            scorer.snapshots.take(1).toList(emitted)
        }
        // Let the collector register before emitting.
        kotlinx.coroutines.yield()
        scorer.pick(listOf(candidate()), sticky = null)
        collectJob.join()
        assertEquals(1, emitted.size)
        assertTrue(emitted.first().isNotEmpty())
        assertEquals("192.168.1.20", emitted.first()[0].ip)
    }
}
