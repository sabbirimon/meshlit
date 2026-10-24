package com.meshlit.pipeline

import com.meshlit.core.inference.pipeline.ClusterNegotiation
import com.meshlit.core.inference.pipeline.NodeOffer
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Test

class PipelinePeerApprovalTest {
    private val worker = NodeOffer("worker-1", "arm64-v8a", 2L shl 30, 4L shl 30,
        ClusterNegotiation.REVISION, workerAllowed = true)

    @Test fun requiresDistinctApprovedCompatibleWorker() {
        requirePairableWorker(worker, "coordinator")
        assertThrows(IllegalArgumentException::class.java) { requirePairableWorker(worker, "worker-1") }
        assertThrows(IllegalArgumentException::class.java) { requirePairableWorker(worker.copy(nodeId = "bad id"), "coordinator") }
        assertThrows(IllegalArgumentException::class.java) { requirePairableWorker(worker.copy(workerAllowed = false), "coordinator") }
        assertThrows(IllegalArgumentException::class.java) { requirePairableWorker(worker.copy(runtimeRevision = "old"), "coordinator") }
    }

    @Test fun pairingQrUsesReachableHostAndLeavesGroupChoiceToCoordinator() {
        val peer = PipelinePeer("worker.example", 50551, "a".repeat(64), "b".repeat(64), group = "friends")
        val decoded = LayerPairingQr.decode(LayerPairingQr.encode(peer))
        org.junit.Assert.assertEquals("worker.example", decoded.host)
        org.junit.Assert.assertEquals("default", decoded.group)
        assertEquals("aaaa-aaaa-aaaa",LayerPairingQr.identityCode(peer.fingerprint))
        assertThrows(IllegalArgumentException::class.java) { LayerPairingQr.encode(peer.copy(host = "127.0.0.1")) }
        assertThrows(IllegalArgumentException::class.java) { LayerPairingQr.encode(peer.copy(host = "https://worker.example")) }
    }

    @Test fun liveAssessmentBlocksIncompatibleWorkersAndReportsResourcePressure() {
        val peer=PipelinePeer("worker.example",50551,"a".repeat(64),"b".repeat(64))
        val blocked=assessWorker(peer,worker.copy(workerAllowed=false),"coordinator",20)
        assertEquals(100,blocked.riskScore)
        val warm=assessWorker(peer,worker.copy(freeMemoryBytes=800L*1024*1024,thermalStatus=2),"coordinator",350)
        assertEquals(99,warm.riskScore)
        org.junit.Assert.assertTrue(warm.reason.contains("thermal pressure"))
    }
}
