package com.meshlit.core.inference.models

import org.junit.Assert.*
import org.junit.Test

class CompactModelRecommendationsTest {
    private val gib=1024L*1024*1024
    private fun device(total:Long=12,available:Long=10,storage:Long=20)=DeviceRuntimeSnapshot(
        "Android",33,"arm64-v8a",total*gib,available*gib,storage*gib,false,0,8,true,true,"test","test","unknown")
    @Test fun weakAndStrongDevicesGetDifferentFirstChoices() {
        assertEquals("qwen3-small",CompactModelRecommendations.assess(device(3,2)).first().model.id)
        assertEquals("qwen3-balanced",CompactModelRecommendations.assess(device(6,5)).first().model.id)
        assertEquals("qwen3-capable",CompactModelRecommendations.assess(device()).first().model.id)
        assertEquals("qwen3-small",CompactModelRecommendations.assess(device(),true).first().model.id)
    }
    @Test fun CurrentPressureCannotBeConfusedWithTotalRam() {
        val advice=CompactModelRecommendations.assess(device(12,1))
        assertTrue(advice.any{it.hardwareFit});assertTrue(advice.none{it.readyNow})
        assertTrue(advice.first().reason.contains("unload"))
    }
    @Test fun unsupportedHotAndStorageLimitedDevicesAreNeverReady() {
        for(d in listOf(device().copy(abi="armeabi-v7a"),device().copy(thermal=4),device(storage=0))) {
            assertTrue(CompactModelRecommendations.assess(d).none{it.readyNow})
        }
        assertTrue(CompactModelRecommendations.assess(device().copy(cpuCores=2)).filter{it.hardwareFit}.all{it.model.tier==0})
    }
    @Test fun publishedDownloadsArePinnedAndIncludeSpaceForRuntimeCopy() {
        for(a in CompactModelRecommendations.assess(device())) {
            assertTrue(a.model.sha256.matches(Regex("[a-f0-9]{64}")))
            assertTrue(a.model.revision.matches(Regex("[a-f0-9]{40}")))
            assertFalse(a.model.url.contains("/main/"));assertTrue(a.storageNeeded>a.model.bytes*2)
        }
    }
}
