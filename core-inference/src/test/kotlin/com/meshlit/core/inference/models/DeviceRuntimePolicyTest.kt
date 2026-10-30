package com.meshlit.core.inference.models
import org.junit.Assert.*
import org.junit.Test

/** Resource admission contracts, not measured device or inference evidence. */
class DeviceRuntimePolicyTest {
    private val gib=1024L*1024*1024
    private fun resources()=DeviceRuntimeSnapshot("Android",35,"arm64-v8a",12*gib,8*gib,20*gib,false,0,8,true,true,"","","",
        powerSave=false,batteryPercent=80,charging=false,memoryPressure=false)
    @Test fun lowRamAndOldOsChooseConservativeContext(){
        val low=DeviceRuntimePolicy.plan(resources().copy(lowRam=true,totalRam=2*gib,availableRam=gib))
        assertEquals("Light",low.category);assertEquals(512,low.recommendedContext);assertEquals(1,low.cpuThreads)
        val old=DeviceRuntimePolicy.plan(resources().copy(api=24));assertEquals("Light",old.category);assertEquals(2048,old.maxContext)
    }
    @Test fun capableDevicesHaveLargerCapsButNoGpuClaim(){
        val high=DeviceRuntimePolicy.plan(resources());assertEquals(8192,high.maxContext);assertEquals(4096,high.recommendedContext)
        assertTrue(high.nativeTuningSupported);assertTrue(high.reasons.any{it.contains("GPU/NPU")})
    }
    @Test fun severeThermalAndMissingBackendBlockNewWork(){
        assertFalse(DeviceRuntimePolicy.plan(resources().copy(thermal=3)).allowHeavyWork)
        assertFalse(DeviceRuntimePolicy.plan(resources().copy(nativeLocalInstalled=false,runAnywhereInstalled=false)).localInferenceSupported)
        assertFalse(DeviceRuntimePolicy.plan(resources().copy(abi="armeabi-v7a")).localInferenceSupported)
    }
    @Test fun unknownThermalIsReportedAndMemoryIsRechecked(){
        val unknown=DeviceRuntimePolicy.plan(resources().copy(api=28,thermal=null));assertTrue(unknown.reasons.any{it.contains("unavailable")})
        val tiny=DeviceRuntimePolicy.plan(resources().copy(availableRam=300L*1024*1024));assertFalse(tiny.allowHeavyWork)
        assertTrue(tiny.reasons.any{it.contains("Available RAM leaves only")})
        assertFalse(DeviceRuntimePolicy.fits(tiny,100L*1024*1024,0))
    }
    @Test fun liveHealthReducesThreadsAndRecoveredReadingsRestoreOnlyTheDeviceCap(){
        val healthy=resources()
        assertEquals(4,DeviceRuntimePolicy.plan(healthy).cpuThreads)
        assertEquals(3,DeviceRuntimePolicy.plan(healthy.copy(thermal=1)).cpuThreads)
        assertEquals(2,DeviceRuntimePolicy.plan(healthy.copy(thermal=2)).cpuThreads)
        assertEquals(1,DeviceRuntimePolicy.plan(healthy.copy(powerSave=true)).cpuThreads)
        assertEquals(1,DeviceRuntimePolicy.plan(healthy.copy(batteryPercent=15)).cpuThreads)
        assertEquals(1,DeviceRuntimePolicy.plan(healthy.copy(availableRam=2*gib)).cpuThreads)
        assertEquals(2,DeviceRuntimePolicy.plan(healthy.copy(availableRam=3*gib)).cpuThreads)
        assertEquals(4,DeviceRuntimePolicy.plan(healthy).cpuThreads)
        assertEquals(1,DeviceRuntimePolicy.plan(healthy.copy(cpuCores=2)).cpuThreads)
    }
    @Test fun criticalHealthBlocksNewLoadsWithoutTreatingUnknownChargingAsPower(){
        for(device in listOf(resources().copy(thermal=3),resources().copy(thermal=4),
            resources().copy(memoryPressure=true),resources().copy(batteryPercent=5),
            resources().copy(batteryPercent=5,charging=null))) {
            val plan=DeviceRuntimePolicy.plan(device)
            assertFalse(plan.allowHeavyWork)
            assertFalse(DeviceRuntimePolicy.fits(plan,100L*1024*1024,0))
        }
        assertTrue(DeviceRuntimePolicy.plan(resources().copy(batteryPercent=5,charging=true)).allowHeavyWork)
        assertTrue(DeviceRuntimePolicy.plan(resources().copy(batteryPercent=6)).allowHeavyWork)
    }
    @Test fun missingOrInvalidReadingsStayUnknownAndNeverExpandCpuBudget(){
        val unknown=DeviceRuntimePolicy.plan(resources().copy(thermal=null,powerSave=null,batteryPercent=null,
            charging=null,memoryPressure=null))
        assertEquals(2,unknown.cpuThreads)
        assertTrue(unknown.allowHeavyWork)
        assertTrue(unknown.reasons.any{it.contains("Battery level unavailable")})
        assertTrue(unknown.reasons.any{it.contains("memory-pressure status unavailable")})
        assertEquals(2,DeviceRuntimePolicy.plan(resources().copy(thermal=-1,batteryPercent=101)).cpuThreads)
        assertEquals(1,DeviceRuntimePolicy.plan(resources().copy(lowRam=true,thermal=null)).cpuThreads)
    }
    @Test fun kvEstimateRequiresRealTransformerDimensions(){
        assertNull(GgufModelMetadata(null,null,null,null,null,null,null).kvBytes(2048,"f16"))
        val metadata=GgufModelMetadata("llama","Q4_K_M",8192,30,3,64,64)
        assertEquals(47185920L,metadata.kvBytes(2048,"f16"))
        assertTrue(metadata.kvBytes(2048,"q8_0")!!<metadata.kvBytes(2048,"f16")!!)
        assertNull(metadata.copy(architecture="mamba").kvBytes(2048,"f16"))
    }
    @Test fun runtimeRejectsInvalidPrecisionAndUnscaledContext(){
        assertThrows(IllegalArgumentException::class.java){ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,8192,"f16").validate(2048)}
        assertThrows(IllegalArgumentException::class.java){ModelRuntimeOptions(keyCacheType="q4_0").validate()}
        assertThrows(IllegalArgumentException::class.java){ModelRuntimeOptions(contextSize=0).validate()}
    }
    @Test fun hostedCredentialsCannotBeSentToUnapprovedHosts(){
        HostedModelConfig(model="org/model:cheapest").validate()
        HostedModelConfig(endpoint="https://my-model.us-east-1.aws.endpoints.huggingface.cloud/v1/chat/completions").validate()
        listOf("http://router.huggingface.co/v1/chat/completions","https://example.com/v1/chat/completions",
            "https://router.huggingface.co.evil.test/v1/chat/completions","https://router.huggingface.co/v1/chat/completions?secret=x").forEach {url ->
            assertThrows(IllegalArgumentException::class.java){HostedModelConfig(endpoint=url).validate()}
        }
    }
}
