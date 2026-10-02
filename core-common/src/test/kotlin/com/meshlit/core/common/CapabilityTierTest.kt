package com.meshlit.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 — :core-common JVM smoke test. The release checklist §1
 * gate lists `:core-common:testDebugUnitTest`; before Phase 7 the
 * module had only the [CapabilityMatrixTest] unit test, leaving the
 * `CapabilityTier` enum (which gates dataSync FGS, hardware
 * attestation, eGPU) without JVM coverage. Threshold regressions
 * here would silently disable FULL-tier features on the wrong SDK.
 */
class CapabilityTierTest {

    @Test
    fun `LITE below SDK 34`() {
        assertEquals(CapabilityTier.LITE, CapabilityTier.fromSdkInt(33))
        assertEquals(CapabilityTier.LITE, CapabilityTier.fromSdkInt(24))
        // `minSdk = 23` floor in :core-common — tier still resolves
        assertEquals(CapabilityTier.LITE, CapabilityTier.fromSdkInt(23))
    }

    @Test
    fun `MID between SDK 34 and 35`() {
        assertEquals(CapabilityTier.MID, CapabilityTier.fromSdkInt(34))
        assertEquals(CapabilityTier.MID, CapabilityTier.fromSdkInt(35))
    }

    @Test
    fun `FULL at SDK 36 and above`() {
        assertEquals(CapabilityTier.FULL, CapabilityTier.fromSdkInt(36))
        assertEquals(CapabilityTier.FULL, CapabilityTier.fromSdkInt(99))
    }

    @Test
    fun `dataSync FGS is LITE-blocked`() {
        assertFalse(CapabilityTier.LITE.allowsDataSyncForegroundService)
        assertTrue(CapabilityTier.MID.allowsDataSyncForegroundService)
        assertTrue(CapabilityTier.FULL.allowsDataSyncForegroundService)
    }

    @Test
    fun `hardware attestation and eGPU are FULL-only`() {
        for (tier in listOf(CapabilityTier.LITE, CapabilityTier.MID)) {
            assertFalse(
                "tier=$tier should not allow hardware attestation",
                tier.allowsHardwareBackedAttestation,
            )
            assertFalse(
                "tier=$tier should not allow eGPU",
                tier.allowsEgpuToggle,
            )
        }
        assertTrue(CapabilityTier.FULL.allowsHardwareBackedAttestation)
        assertTrue(CapabilityTier.FULL.allowsEgpuToggle)
    }
}