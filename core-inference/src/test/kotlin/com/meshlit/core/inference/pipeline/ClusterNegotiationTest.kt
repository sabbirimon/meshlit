package com.meshlit.core.inference.pipeline
import org.junit.Test
import org.junit.Assert.*
class ClusterNegotiationTest {
    private val hash="a".repeat(64)
    private fun node(id:String,ram:Long=4L*1024*1024*1024)=NodeOffer(id,"arm64-v8a",ram,ram,ClusterNegotiation.REVISION,true,observedAtMs=1000)
    @Test fun masterElectionIsDeterministicAndWeightsFollowMemory(){
        val a=node("a").copy(coordinatorAllowed=true,coordinatorModelSha256=hash)
        val b=node("b").copy(coordinatorAllowed=true,coordinatorModelSha256=hash)
        val nodes=listOf(a,b,node("c",8L*1024*1024*1024))
        val plan=ClusterNegotiation.plan(nodes,hash,100000000,1000)
        assertEquals("a",plan.coordinatorId);assertEquals(plan,ClusterNegotiation.plan(nodes.reversed(),hash,100000000,1000))
        assertTrue(plan.weights[1]>plan.weights[0])
    }
    @Test fun hotStaleUnapprovedAndWrongRevisionNodesCannotHost(){
        val master=node("master").copy(workerAllowed=false,coordinatorAllowed=true,coordinatorModelSha256=hash)
        val bad=listOf(node("hot").copy(thermalStatus=3),node("stale").copy(observedAtMs=-30001),
            node("denied").copy(workerAllowed=false),node("old").copy(runtimeRevision="old"))
        assertTrue(runCatching{ClusterNegotiation.plan(listOf(master,node("ok"))+bad,hash,100000000,1000)}.isFailure)
    }
    @Test fun contextKvAndLayerGranularityCanRejectOtherwiseFittingWeights(){
        val master=node("m").copy(coordinatorAllowed=true,coordinatorModelSha256=hash)
        val offers=listOf(master,node("a"),node("b"))
        val accepted=ClusterNegotiation.plan(offers,hash,100000000,1000,kvBytes=10000000,blocks=32)
        assertEquals(10000000L,accepted.estimatedKvBytes)
        assertTrue(accepted.estimatedWorkerBytes.zip(accepted.workers).all{(bytes,worker)->bytes<=ClusterNegotiation.budget(worker)})
        assertThrows(IllegalArgumentException::class.java){ClusterNegotiation.plan(offers,hash,100000000,1000,kvBytes=8L*1024*1024*1024,blocks=32)}
    }
    @Test fun coordinatorCannotBeElectedWithoutSchedulingMemory(){
        val master=node("m",400L*1024*1024).copy(workerAllowed=false,coordinatorAllowed=true,coordinatorModelSha256=hash)
        assertThrows(IllegalArgumentException::class.java){ClusterNegotiation.plan(listOf(master,node("a"),node("b")),hash,100000000,1000,blocks=32)}
    }
    @Test fun untrustedOffersCannotOverflowMemoryArithmetic(){
        val master=node("m").copy(coordinatorAllowed=true,coordinatorModelSha256=hash)
        assertThrows(IllegalArgumentException::class.java){ClusterNegotiation.plan(listOf(master,node("a",Long.MAX_VALUE),node("b")),hash,100000000,1000)}
    }
    @Test fun insufficientMemoryIsRejected(){
        val master=node("m").copy(coordinatorAllowed=true,coordinatorModelSha256=hash)
        assertTrue(runCatching{ClusterNegotiation.plan(listOf(master,node("a"),node("b")),hash,20L*1024*1024*1024,1000)}.isFailure)
    }
}
