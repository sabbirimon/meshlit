package com.meshlit.core.inference.pipeline

import org.junit.Assert.*
import org.junit.Test

class LayerPlacementTest {
    private val mib = 1024L * 1024
    @Test fun asymmetricCapacityGetsCompleteIntegerPlacementWithinEveryBudget() {
        val capacities=listOf(1024*mib,4096*mib)
        val plan=LayerPlacement.allocate(capacities,2048*mib,28,256*mib)
        assertEquals(28,plan.counts.sum()); assertTrue(plan.counts.all { it>0 })
        assertTrue(plan.counts[1]>plan.counts[0])
        assertTrue(plan.estimatedBytes.indices.all { plan.estimatedBytes[it]<=capacities[it] })
        assertEquals(plan,LayerPlacement.allocate(capacities,2048*mib,28,256*mib))
    }
    @Test fun manualCountsCannotDropLayersOrExceedOneWorkersMemory() {
        val capacities=listOf(1024*mib,4096*mib)
        assertThrows(IllegalArgumentException::class.java) {LayerPlacement.allocate(capacities,2048*mib,28,256*mib,listOf(14,14))}
        assertThrows(IllegalArgumentException::class.java) {LayerPlacement.allocate(capacities,2048*mib,28,256*mib,listOf(2,2))}
        assertEquals(listOf(4,24),LayerPlacement.allocate(capacities,2048*mib,28,256*mib,listOf(4,24)).counts)
    }
    @Test fun tinyMemberIsExcludedWithoutRejectingTwoCapableWorkers() {
        val hash="a".repeat(64)
        fun node(id:String,ram:Long)=NodeOffer(id,"x86_64",ram,ram,ClusterNegotiation.REVISION,true,observedAtMs=1000)
        val master=node("master",8*1024*mib).copy(workerAllowed=false,coordinatorAllowed=true,coordinatorModelSha256=hash)
        val offers=listOf(master,node("a",4*1024*mib),node("b",8*1024*mib),node("weak",400*mib))
        val plan=ClusterNegotiation.plan(offers,hash,1024*mib,1000,128*mib,28)
        assertEquals(listOf("a","b"),plan.workers.map {it.nodeId});assertEquals(28,plan.weights.sum())
        assertEquals(plan,ClusterNegotiation.plan(offers.reversed(),hash,1024*mib,1000,128*mib,28))
        assertThrows(IllegalArgumentException::class.java) {ClusterNegotiation.plan(offers,hash,1024*mib,1000,128*mib,28,mapOf("a" to 14,"unpaired" to 14))}
    }
}
