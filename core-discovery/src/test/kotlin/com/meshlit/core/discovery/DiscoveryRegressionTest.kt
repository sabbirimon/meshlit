package com.meshlit.core.discovery
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class DiscoveryRegressionTest {
    private class Transport(override val name: String) : DiscoveryTransport(name) {
        var starts = 0
        override fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job { starts++; return Job().apply { complete() } }
        override fun stop() {}
    }
    private val self = LocalPeerDescriptor("0c123456-1234-1234-1234-123456789012", "192.168.1.10", 8080, "local_sandboxed", "")
    @Test fun restartPreservesTransportsAndDisabledSelection() {
        val nsd = Transport("nsd"); val ble = Transport("ble"); val coordinator = DiscoveryCoordinator(listOf(nsd, ble))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        coordinator.start(scope, self); coordinator.setTransportEnabled("ble", false); coordinator.stop()
        coordinator.start(scope, self); assertEquals(2, nsd.starts); assertEquals(1, ble.starts)
        coordinator.setTransportEnabled("ble", true); assertEquals(2, ble.starts)
        coordinator.stop(); scope.cancel()
    }
    @Test fun togglesWorkBeforeStartAndSelfIsNotDiscovered() {
        val ble = Transport("ble"); val coordinator = DiscoveryCoordinator(listOf(ble))
        coordinator.setTransportEnabled("ble", false); coordinator.setTransportEnabled("ble", true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined); coordinator.start(scope, self)
        coordinator.ingest(PeerAdvertisement(self.nodeId, self.host, self.port, "local_sandboxed", "", transport = "ble"))
        assertTrue(coordinator.peers.value.isEmpty()); coordinator.stop(); scope.cancel()
    }
    @Test fun lossFromDifferentTransportDoesNotEvictLivePeer() {
        val c = DiscoveryCoordinator(emptyList())
        val peer = PeerAdvertisement("B", "192.168.1.20", 8080, "local_sandboxed", "", transport = "nsd")
        c.ingest(peer); c.ingest(peer.copy(transport = "ble", ttlSec = 0))
        assertEquals(peer, c.peers.value["B"])
    }
    @Test fun blePayloadFitsLegacyFrameAndRoundTripsIpv4PortAndUuid() {
        val packet = BlePeerPacket.encode(self)
        assertEquals(24, packet.size)
        val parsed = BlePeerPacket.decode(packet)!!
        assertEquals(self.nodeId, parsed.nodeId); assertEquals(self.host, parsed.host); assertEquals(8080, parsed.port)
        assertEquals("local_sandboxed", parsed.tier); assertEquals("", parsed.fingerprint)
        assertNull(BlePeerPacket.decode(ByteArray(23)))
    }
}
