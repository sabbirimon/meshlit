package com.meshlit.pipeline

import org.junit.Assert.*
import org.junit.Test

class PipelineLaunchPolicyTest {
    private fun peer(host:String)=PipelinePeer(host,50551,"a".repeat(64),"b".repeat(64))
    private val peers=listOf(peer("worker-a"),peer("worker-b"))

    @Test fun modelsAbove99BlocksRequestAllNativeLayersIncludingOutput() {
        for(weights in listOf(listOf(64,64),listOf(512,512))) {
            val args=nativePlacementArgs(listOf("127.0.0.1:10001","127.0.0.1:10002"),weights)
            assertEquals("all",args[args.indexOf("-ngl")+1])
            assertEquals("layer",args[args.indexOf("--split-mode")+1])
            assertEquals(weights.joinToString(","),args[args.indexOf("--tensor-split")+1])
        }
        assertEquals(listOf("--device","none","-ngl","0"),nativePlacementArgs(emptyList(),emptyList()))
    }

    @Test fun expiredOrClockReversedAdmissionCannotLaunchAfterWaitingForEngine() {
        val admission=PipelineLaunchAdmission(peers,peers,1000)
        admission.requireCurrent(31_000,peers,2)
        assertThrows(IllegalArgumentException::class.java) {admission.requireCurrent(31_001,peers,2)}
        assertThrows(IllegalArgumentException::class.java) {admission.requireCurrent(999,peers,2)}
    }

    @Test fun revokedWorkersGroupOrReducedCapacityRequireNewNegotiation() {
        val admission=PipelineLaunchAdmission(peers,peers,1000)
        assertThrows(IllegalArgumentException::class.java) {admission.requireCurrent(1001,peers.take(1),2)}
        assertThrows(IllegalArgumentException::class.java) {admission.requireCurrent(1001,peers.map{it.copy(group="other")},2)}
        assertThrows(IllegalArgumentException::class.java) {admission.requireCurrent(1001,peers,1)}
    }
}
