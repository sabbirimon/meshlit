package com.meshlit.ui.v2.screens

import com.meshlit.core.bootstrap.BootstrapPhase
import com.meshlit.core.bootstrap.BootstrapReport
import com.meshlit.core.bootstrap.BootstrapSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the v2 `BootstrapScreen` phase mapping helpers
 * (`phaseProgress`, `currentPhaseLine`). The v2 Bootstrap screen
 * maps the live `BootstrapSnapshot` to a progress fraction 0..1
 * and a short phase label.
 *
 * The mapping is deterministic — these tests pin every phase to
 * a known fraction so the splash bar reads correctly:
 *   - `null` snapshot (initial state) → 0.05
 *   - `Config` → 0.20
 *   - `Probe` → 0.40
 *   - `Role` → 0.55
 *   - `Registry` → 0.70
 *   - `Services` → 0.85
 *   - (no `Complete` phase in the enum; report-empty sentinel)
 */
class BootstrapViewModelTest {

    @Test
    fun `null snapshot maps to 0_05`() {
        assertEquals(0.05f, phaseProgress(null), 0.001f)
    }

    @Test
    fun `Config phase maps to 0_20`() {
        assertEquals(0.20f, phaseProgress(snapshotWith(BootstrapPhase.Config)), 0.001f)
    }

    @Test
    fun `Probe phase maps to 0_40`() {
        assertEquals(0.40f, phaseProgress(snapshotWith(BootstrapPhase.Probe)), 0.001f)
    }

    @Test
    fun `Role phase maps to 0_55`() {
        assertEquals(0.55f, phaseProgress(snapshotWith(BootstrapPhase.Role)), 0.001f)
    }

    @Test
    fun `Registry phase maps to 0_70`() {
        assertEquals(0.70f, phaseProgress(snapshotWith(BootstrapPhase.Registry)), 0.001f)
    }

    @Test
    fun `Services phase maps to 0_85`() {
        assertEquals(0.85f, phaseProgress(snapshotWith(BootstrapPhase.Services)), 0.001f)
    }

    @Test
    fun `currentPhaseLine returns Initialising when null`() {
        assertEquals("Initialising", currentPhaseLine(null))
    }

    @Test
    fun `currentPhaseLine returns phase name and node id prefix`() {
        val line = currentPhaseLine(
            snapshotWith(BootstrapPhase.Services, nodeId = "abcdef1234567890"),
        )
        assertEquals("Services · nodeId abcdef12…", line)
    }

    private fun snapshotWith(
        phase: BootstrapPhase,
        nodeId: String = "node-7f3a",
    ): BootstrapSnapshot {
        val entry = BootstrapReport.Entry(
            phase = phase,
            outcome = BootstrapReport.Outcome.Ok,
            durationMs = 10L,
        )
        return BootstrapSnapshot(
            nodeId = nodeId,
            flags = emptyMap(),
            role = null,
            report = BootstrapReport(entries = listOf(entry)),
        )
    }
}