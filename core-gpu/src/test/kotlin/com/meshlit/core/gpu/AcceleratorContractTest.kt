package com.meshlit.core.gpu

import org.junit.Assert.*
import org.junit.Test

class AcceleratorContractTest {
    @Test fun unifiedMemoryIsNotDoubleCountedAndStaleUnknownIsNotInvented() {
        val host = AcceleratorAdvertisement("owned", ChipVendor.NVIDIA, "GB10", "Linux", "aarch64", AcceleratorLink.SSH,
            memory = listOf(AcceleratorMemoryPool("cpu", "uma0", 1200, 100), AcceleratorMemoryPool("gpu", "uma0", 1000, 100), AcceleratorMemoryPool("external", "discrete1", 2000, 100)))
        assertEquals(3000L, host.knownAllocatableBytes(200)); assertNull(host.knownAllocatableBytes(40000))
        assertNull(host.copy(memory = listOf(AcceleratorMemoryPool("gpu", "uma", null, 100))).knownAllocatableBytes(200))
    }
    @Test fun chipNameAndConnectionCannotGrantInference() {
        val device = AcceleratorAdvertisement("owned", ChipVendor.HUAWEI_ASCEND, "Ascend", "Linux", "aarch64", AcceleratorLink.HTTPS, true, true)
        assertFalse(device.inferenceReady("a".repeat(64), 1000))
    }
    @Test fun modelRuntimeDriverConsentAndExpiryMustAllMatch() {
        val proof = AcceleratorQualification("owned", "a".repeat(64), "b".repeat(64), "driver", "sdk", 100, true, 1234)
        val device = AcceleratorAdvertisement("owned", ChipVendor.AMD, "observed-device", "Linux", "x86_64", AcceleratorLink.HTTPS, true, true, "driver", "sdk", "a".repeat(64), qualification = proof)
        assertTrue(device.inferenceReady("b".repeat(64), 200))
        assertFalse(device.inferenceReady("c".repeat(64), 200)); assertFalse(device.copy(driverRevision = "changed").inferenceReady("b".repeat(64), 200))
        assertFalse(device.copy(ownerApproved = false).inferenceReady("b".repeat(64), 200)); assertFalse(device.inferenceReady("b".repeat(64), 100000000))
        assertFalse(device.inferenceReady("b".repeat(64), 50))
    }
}
