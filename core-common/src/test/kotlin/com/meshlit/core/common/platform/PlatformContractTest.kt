package com.meshlit.core.common.platform
import org.junit.Assert.*
import org.junit.Test
class PlatformContractTest {
    @Test fun opticalAndRdmaNamesDoNotGrantWorkingAdapter(){
        val fiber=FabricObservation("owned",FabricKind.ETHERNET_IP,"optical",100,true,100000000000L)
        assertFalse(fiber.usableForIp(200));assertTrue(fiber.copy(ipReachabilityVerified=true).usableForIp(200))
        val rdma=fiber.copy(kind=FabricKind.ROCE,ipReachabilityVerified=true);assertFalse(rdma.usableForNativeFabric(200));assertFalse(rdma.usableForIp(200))
        assertTrue(rdma.copy(nativeAdapterQualified=true).usableForNativeFabric(200));assertFalse(rdma.copy(nativeAdapterQualified=true).usableForNativeFabric(99999))
    }
    @Test fun newOsAndRootDoNotAutomaticallyUnlockCapabilities(){
        val capability=PlatformCapability("ai.compute.cpu",null,true,true,100)
        val host=PlatformAdvertisement(family=PlatformFamily.LINUX,osRevision="observed",abi="aarch64",rootAvailable=true,capabilities=listOf(capability))
        assertTrue(host.enabledCapabilities(200).isEmpty());assertEquals(setOf("ai.compute.cpu"),host.copy(capabilities=listOf(capability.copy(adapterRevision="ai-cpu/1"))).enabledCapabilities(200))
    }
}
