package com.meshlit.core.cloudmcp.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AccessibilityServiceStatus] — covers the sealed
 * class's three states.
 */
class AccessibilityServiceStatusTest {

    @Test
    fun disabled_is_singleton() {
        assertEquals(AccessibilityServiceStatus.Disabled, AccessibilityServiceStatus.Disabled)
    }

    @Test
    fun missing_is_singleton() {
        assertEquals(AccessibilityServiceStatus.Missing, AccessibilityServiceStatus.Missing)
    }

    @Test
    fun enabled_carries_service_name() {
        val s = AccessibilityServiceStatus.Enabled(
            serviceName = "com.meshlit/.core.cloudmcp.android.MeshlitAccessibilityService",
        )
        assertEquals(
            "com.meshlit/.core.cloudmcp.android.MeshlitAccessibilityService",
            s.serviceName,
        )
    }

    @Test
    fun enabled_distinguishes_by_service_name() {
        val a = AccessibilityServiceStatus.Enabled("a")
        val b = AccessibilityServiceStatus.Enabled("b")
        assertNotEquals(a, b)
    }

    @Test
    fun disabled_does_not_equal_missing() {
        assertTrue(AccessibilityServiceStatus.Disabled != AccessibilityServiceStatus.Missing)
    }
}