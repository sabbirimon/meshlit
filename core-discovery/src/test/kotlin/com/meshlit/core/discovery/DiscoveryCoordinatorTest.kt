package com.meshlit.core.discovery

import com.meshlit.core.common.NodeId
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryCoordinatorTest {

    /** Transport whose `start()` returns a pre-completed Job — no
     *  long-lived coroutines, so the test never hangs. */
    private class NoopTransport(override val name: String) : DiscoveryTransport(name) {
        var started = false
        var stopped = false
        override fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job {
            started = true
            val j = Job()
            j.complete()
            return j
        }
        override fun stop() { stopped = true }
    }

    @Test
    fun ingest_dedupes_same_nodeId_same_transport() {
        val coord = DiscoveryCoordinator(listOf(NoopTransport("nsd")))
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted", transport = "nsd"))
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted", transport = "nsd"))
        assertEquals(1, coord.peers.value.size)
        assertEquals("192.168.1.20", coord.peers.value["node-A"]!!.host)
    }

    @Test
    fun ingest_keeps_first_seen_transport_when_conflict() {
        val coord = DiscoveryCoordinator(listOf(NoopTransport("nsd")))
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted", transport = "nsd"))
        coord.ingest(adv("node-A", "10.0.0.5", 9090, "wan", transport = "wifi_aware"))
        // Second emission is dropped — we keep the nsd record.
        assertEquals("192.168.1.20", coord.peers.value["node-A"]!!.host)
        assertEquals("nsd", coord.peers.value["node-A"]!!.transport)
    }

    @Test
    fun ingest_with_ttl_zero_evicts() {
        val coord = DiscoveryCoordinator(listOf(NoopTransport("nsd")))
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted"))
        assertNotNull(coord.peers.value["node-A"])
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted", ttlSec = 0))
        assertNull(coord.peers.value["node-A"])
    }

    @Test
    fun stop_clears_state_and_calls_transport_stop() {
        val transport = NoopTransport("nsd")
        val coord = DiscoveryCoordinator(listOf(transport))
        val job = kotlinx.coroutines.Job()
        val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.Unconfined)
        coord.start(scope, self())
        assertTrue(transport.started)
        coord.ingest(adv("node-A", "192.168.1.20", 8080, "local_trusted"))
        assertEquals(1, coord.peers.value.size)
        coord.stop()
        assertTrue(transport.stopped)
        assertEquals(emptyMap<String, PeerAdvertisement>(), coord.peers.value)
        job.cancel()
    }

    @Test
    fun parses_fingerprint_and_tier() {
        val adv = adv("node-X", "10.0.0.5", 9090, "wan", fingerprint = "fp:abc", ttlSec = 30)
        assertEquals("fp:abc", adv.fingerprint)
        assertEquals(30, adv.ttlSec)
        assertEquals(com.meshlit.core.trust.TrustTier.WAN, adv.trustTierOrDefault())
        assertEquals(NodeId("node-X"), adv.nodeIdTyped())
    }

    /** A no-op scope that accepts launch calls and completes them
     *  immediately. Used only to verify that `start` calls each
     *  transport exactly once. */
    private fun self() = LocalPeerDescriptor(
        nodeId = "self", host = "192.168.1.10", port = 8080,
        tierTag = "local_trusted", fingerprint = "fp:self"
    )

    private fun adv(
        nodeId: String,
        host: String,
        port: Int,
        tier: String,
        fingerprint: String = "",
        ttlSec: Int = 60,
        transport: String = "nsd",
    ) = PeerAdvertisement(
        nodeId = nodeId, host = host, port = port, tier = tier,
        fingerprint = fingerprint, ttlSec = ttlSec, transport = transport,
    )
}
