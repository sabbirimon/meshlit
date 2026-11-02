package com.meshlit.core.inference
import com.meshlit.core.inference.models.*
import org.junit.Assert.*
import org.junit.Test
class AdaptiveCpuPolicyTest {
    private val normal=CpuConditions(8,0,false,70,true,false,true)
    @Test fun headroomAndOwnerLimitsApplyOnEverySize() {
        assertEquals(4,AdaptiveCpuPolicy.decide(normal,CpuMode.BALANCED,6).threads)
        assertEquals(6,AdaptiveCpuPolicy.decide(normal,CpuMode.PERFORMANCE,6).threads)
        for(cores in 1..16){val d=AdaptiveCpuPolicy.decide(normal.copy(cores=cores),CpuMode.PERFORMANCE,6);assertTrue(d.threads<=maxOf(1,cores-d.headroom))}
        assertEquals(3,AdaptiveCpuPolicy.decide(normal,CpuMode.PERFORMANCE,3).threads)
    }
    @Test fun livePressureAndUnknownReadingsLowerTheBudget() {
        for(c in listOf(normal.copy(saver=true),normal.copy(lowMemory=true),normal.copy(battery=5,charging=false),normal.copy(thermal=2),normal.copy(thermal=3),normal.copy(battery=null)))
            assertEquals(1,AdaptiveCpuPolicy.decide(c,CpuMode.PERFORMANCE,6).threads)
        assertEquals(2,AdaptiveCpuPolicy.decide(normal.copy(foreground=false),CpuMode.PERFORMANCE,6).threads)
        assertEquals(1,AdaptiveCpuPolicy.decide(normal,CpuMode.BATTERY_SAVER,6).threads)
    }
    @Test fun reductionIsImmediateAndRecoveryMustRemainStable() {
        val w=CpuRecoveryWindow(100)
        assertEquals(4,w.apply(4,0));assertEquals(1,w.apply(1,1))
        assertEquals(1,w.apply(4,2));assertEquals(1,w.apply(4,101));assertEquals(4,w.apply(4,102))
        assertEquals(2,w.apply(2,103));assertEquals(2,w.apply(6,104));assertEquals(2,w.apply(3,150));assertEquals(2,w.apply(3,249));assertEquals(3,w.apply(3,250))
    }
    @Test fun overdriveKeepsGuardsAndPlusOverridesOnlySelectedFactors() {
        assertEquals(8,AdaptiveCpuPolicy.decide(normal,CpuMode.OVERDRIVE,4).threads)
        val stressed=normal.copy(thermal=3,saver=true,lowMemory=true,foreground=false,battery=4,charging=false)
        assertEquals(1,AdaptiveCpuPolicy.decide(stressed,CpuMode.OVERDRIVE,4).threads)
        assertEquals(8,AdaptiveCpuPolicy.decide(stressed,CpuMode.OVERDRIVE_PLUS,4,CpuOverrides(true,true,true,true,true)).threads)
        assertEquals(1,AdaptiveCpuPolicy.decide(stressed,CpuMode.OVERDRIVE_PLUS,4,CpuOverrides(false,true,true,true,true)).threads)
    }
    @Test fun explicitOwnerChangeCanResetRecoveryWithoutDisablingPressureCaps() {
        val window=CpuRecoveryWindow()
        assertEquals(2,window.apply(2,0));assertEquals(2,window.apply(8,1))
        window.reset();assertEquals(8,window.apply(8,2));assertEquals(1,window.apply(1,3))
        assertEquals(1,window.apply(8,4));assertEquals(8,window.apply(8,30004))
    }
    @Test fun hostAdapterMayReportMissingSignalsWithoutFabricatingThem() {
        val missing=CpuConditions(8,null,null,null,null,false,true,unknownAsPressure=false)
        val allowed=AdaptiveCpuPolicy.decide(missing,CpuMode.BALANCED,4)
        assertEquals(4,allowed.threads)
        assertTrue(allowed.reason.contains("unavailable"))
        assertEquals(1,AdaptiveCpuPolicy.decide(missing.copy(lowMemory=true),CpuMode.OVERDRIVE,8).threads)
    }
}
