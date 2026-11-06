package com.meshlit.pipeline

import com.meshlit.core.inference.pipeline.ClusterNegotiation
import com.meshlit.core.inference.pipeline.NodeOffer
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
    @Test fun explicitCpuPlacementOffloadsOnlyRemoteLayersAndRequiresFullCoverage() {
        val args=nativePlacementArgs(listOf("127.0.0.1:10001"),listOf(6),18,24)
        assertEquals("6",args[args.indexOf("-ngl")+1])
        assertEquals("RPC0",args[args.indexOf("--device")+1])
        assertEquals("6",args[args.indexOf("--tensor-split")+1])
        assertThrows(IllegalArgumentException::class.java){nativePlacementArgs(listOf("127.0.0.1:10001"),listOf(6))}
        assertThrows(IllegalArgumentException::class.java){nativePlacementArgs(listOf("127.0.0.1:10001"),listOf(7),18,24)}
        assertThrows(IllegalArgumentException::class.java){nativePlacementArgs(emptyList(),emptyList(),24,24)}
    }
    @Test fun singleRemoteAdmissionRequiresUnchangedExplicitCoordinatorParticipation() {
        val admission=PipelineLaunchAdmission(peers.take(1),peers.take(1),1000,18,24)
        admission.requireCurrent(1001,peers.take(1),2,true)
        assertThrows(IllegalArgumentException::class.java){admission.requireCurrent(1001,peers.take(1),2,false)}
        val remoteOnly=PipelineLaunchAdmission(peers,peers,1000)
        assertThrows(IllegalArgumentException::class.java){remoteOnly.requireCurrent(1001,peers,2,true)}
    }

    @Test fun recoveryCanReadmitOnlyTheRemainingVerifiedWorkers() = runBlocking {
        val group = peers + peer("worker-c")
        val scan = scanPipelineOffers(group, allowUnreachable = true) { worker ->
            if (worker == group.first()) throw ConnectException("worker offline")
            offer(worker.host)
        }
        assertEquals(listOf(group.first()), scan.unreachable)
        val coordinator = offer("coordinator").copy(
            workerAllowed = false, coordinatorAllowed = true, coordinatorModelSha256 = "a".repeat(64)
        )
        val placement = ClusterNegotiation.plan(
            scan.verified.map { it.second } + coordinator, "a".repeat(64), 512L * 1024 * 1024, blocks = 24,
        )
        assertEquals(group.drop(1).map { it.host }, placement.workers.map { it.nodeId })
        assertEquals(24, placement.weights.sum())
    }

    @Test fun workerRecoveryDoesNotTreatAnIdentityFailureOrCancellationAsAnOfflineWorker() {
        assertThrows(SSLHandshakeException::class.java) {
            runBlocking { scanPipelineOffers(peers, allowUnreachable = true) { throw SSLHandshakeException("pin changed") } }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking { scanPipelineOffers(peers, allowUnreachable = true) { throw CancellationException() } }
        }
        assertThrows(ConnectException::class.java) {
            runBlocking { scanPipelineOffers(peers, allowUnreachable = false) { throw ConnectException() } }
        }
    }

    private fun offer(id: String) = NodeOffer(
        id, "arm64-v8a", 4L * 1024 * 1024 * 1024, 0, ClusterNegotiation.REVISION, workerAllowed = true,
    )
}
