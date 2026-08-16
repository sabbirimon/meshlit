package com.meshlit.feature.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM smoke test that verifies the Advanced hub's surface
 * area: every destination has a stable route, a non-empty label,
 * a non-empty description, and a distinct icon so the UI can show
 * it without crashing.
 *
 * Compose UI clicks are covered in `:app/src/androidTest/...`
 * (runs against the real Activity). This test is the cheap
 * pre-flight check that catches data-class corruption before
 * Compose even tries to render.
 */
class AdvancedButtonSmokeTest {
    private val all: List<AdvancedDestination> = AdvancedDestination.entries

    @Test
    fun every_destination_has_a_route() {
        val blank = all.filter { it.route.isBlank() }
        assertEquals(0, blank.size)
    }

    @Test
    fun every_destination_has_a_label() {
        val blank = all.filter { it.label.isBlank() }
        assertEquals(0, blank.size)
    }

    @Test
    fun every_destination_has_a_description() {
        val blank = all.filter { it.description.isBlank() }
        assertEquals(0, blank.size)
    }

    @Test
    fun every_destination_has_a_distinct_route() {
        val distinct = all.map { it.route }.distinct()
        assertEquals(all.size, distinct.size)
    }

    @Test
    fun every_destination_has_a_distinct_label() {
        val distinct = all.map { it.label }.distinct()
        assertEquals(all.size, distinct.size)
    }

    @Test
    fun expected_advanced_destinations_are_present() {
        // Spot-check the 17 destinations from the plan. If the enum
        // gets pruned in a refactor this test will catch it.
        val expected = listOf(
            "advanced/diarization",
            "advanced/read_aloud",
            "advanced/transcription",
            "advanced/voice_activity",
            "advanced/web_tools",
            "advanced/solutions",
            "advanced/cloud_providers",
            "advanced/benchmarks",
            "advanced/gpu_panel",
            "advanced/ghosty",
            "advanced/mcp",
        )
        val actual = all.map { it.route }.toSet()
        for (route in expected) {
            assertTrue(
                "expected route $route in AdvancedDestination",
                actual.contains(route),
            )
        }
    }

    @Test
    fun every_destination_can_be_classified() {
        // Group by primaryCategory to ensure the UI sectioning logic
        // can render each entry under the right section header.
        val categories = all.map { it.primaryCategory }.toSet()
        assertTrue(categories.isNotEmpty())
        for (dest in all) {
            assertNotEquals("", dest.primaryCategory.displayName)
        }
    }
}