package com.meshlit.core.gpu
import org.junit.Assert.*
import org.junit.Test
class HyperLClusterPlannerTest {
    private fun node(id:String)=HyperLNodeExecutionProfile(id,"Linux:aarch64","NEON",HyperLTarget.LLVM_CPU,"1","1","a".repeat(64),"f32",100,true,true,true,true,1.2,true)
    @Test fun compatibleQualifiedNodesAreOnlyTurboCandidates(){
        val result=HyperLClusterPlanner.plan(listOf(node("one"),node("two")),true,200)
        assertEquals(HyperLClusterMode.TURBO_CANDIDATE,result.mode);assertFalse(result.executionActivated)
        assertEquals(HyperLClusterMode.STABILITY,HyperLClusterPlanner.plan(listOf(node("one")),false,200).mode)
    }
    @Test fun mixedUnknownThermalNoBenchmarkOrStaleAlwaysUseStability(){
        val candidates=listOf(node("two").copy(instructionSet="SVE"),node("two").copy(thermalHealthy=null),node("two").copy(measuredSpeedup=null),node("two").copy(turboAdapterImplemented=false),node("two").copy(qualified=false))
        candidates.forEach{assertEquals(HyperLClusterMode.STABILITY,HyperLClusterPlanner.plan(listOf(node("one"),it),true,200).mode)}
        assertEquals(HyperLClusterMode.STABILITY,HyperLClusterPlanner.plan(listOf(node("one"),node("two")),true,40000).mode)
    }
}
