package com.meshlit.disco

import com.meshlit.core.common.NodeId
import com.meshlit.core.discovery.DiscoveryCoordinator
import com.meshlit.core.discovery.DiscoveryTransport
import com.meshlit.core.discovery.LocalPeerDescriptor
import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.trust.InMemoryTrustStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the trust/transport seams added to PeerRepository
 * by the v2 Scan screen redesign.
 *
 * Scope:
 *  - `forget` revokes the peer's trust entry AND evicts them from
 *    the coordinator's peer map.
 *  - `setTransportEnabled` flips the BLE transport on/off in the
 *    coordinator and the repo's `uiState.transportsEnabled`.
 *  - The `usbTether` field passes through whatever the provider
 *    returns.
 *
 * Out of scope: BLE advertiser / scanner platform behaviour is
 * covered by the on-device pass; the BLE transport itself is
 * covered by `core-discovery` tests.
 */
class PeerRepositoryTrustTest {

    /** Transport whose `start()` returns a pre-completed Job so the
     *  test never hangs. */
    private class NoopTransport(override val name: String) : DiscoveryTransport(name) {
        override fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job {
            val j = Job()
            j.complete()
            return j
        }
        override fun stop() {}
    }

    private val trustStore = InMemoryTrustStore()
    private val nsd = NoopTransport("nsd")
    private val ble = NoopTransport("ble")
    private val coordinator = DiscoveryCoordinator(listOf(nsd, ble))
    private lateinit var repo: PeerRepository
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

    private fun self() = LocalPeerDescriptor(
        nodeId = "self",
        host = "192.168.1.10",
        port = 8080,
        tierTag = "local_trusted",
        fingerprint = "fp:self",
    )

    private fun adv(
        nodeId: String = "node-A",
        host: String = "192.168.1.20",
        port: Int = 8080,
        tier: String = "local_trusted",
        transport: String = "nsd",
        fingerprint: String = "fp:abc",
    ) = PeerAdvertisement(
        nodeId = nodeId, host = host, port = port, tier = tier,
        fingerprint = fingerprint, transport = transport,
    )

    @Before
    fun setUp() {
        // Seed the trust store with a policy so we can verify
        // revoke happens.
        trustStore.upsert(
            com.meshlit.core.trust.DeviceTrustPolicy(
                nodeId = "node-A",
                trustTier = com.meshlit.core.trust.TrustTier.LOCAL_TRUSTED,
                allowedRoles = emptySet(),
                publicKeyFingerprint = "fp:abc",
            ),
        )
        repo = PeerRepository(
            coordinator = coordinator,
            localIPPrefixProvider = { listOf("192.168.1.") },
            clusterFingerprintsProvider = { emptySet() },
            trustStore = trustStore,
            usbTetherProvider = { null },
        )
        repo.start(scope, self())
    }

    @After
    fun tearDown() {
        runBlocking {
            repo.stop()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        }
    }

    @Test
    fun `forget revokes the trust entry and evicts the peer`() = runTest {
        coordinator.ingest(adv())
        assertNotNull("peer must be in coordinator before forget", coordinator.peers.value["node-A"])
        repo.forget("node-A")
        assertNull("peer must be evicted from coordinator", coordinator.peers.value["node-A"])
        assertNull(
            "trust entry must be revoked",
            trustStore.policyFor(NodeId("node-A")),
        )
    }

    @Test
    fun `forget is idempotent on missing peer`() {
        // No peer ingested — calling forget must be a no-op rather
        // than crash. The seed policy for `node-A` from setUp()
        // remains in the trust store since we never asked to
        // revoke it.
        val sizeBefore = trustStore.list().size
        repo.forget("does-not-exist")
        assertEquals(sizeBefore, trustStore.list().size)
    }

    @Test
    fun `setTransportEnabled flips BLE in the coordinator state`() = runTest {
        repo.setTransportEnabled("ble", enabled = false)
        assertEquals(listOf("nsd"), coordinator.transports.map { it.name })
        val state = repo.uiState.first()
        assertTrue("'nsd' must remain enabled", state.transportsEnabled.contains("nsd"))
        assertFalse("'ble' must be off in state", state.transportsEnabled.contains("ble"))

        repo.setTransportEnabled("ble", enabled = true)
        assertEquals(listOf("nsd", "ble"), coordinator.transports.map { it.name })
        val stateAfter = repo.uiState.first()
        assertTrue("'ble' must be back on", stateAfter.transportsEnabled.contains("ble"))
    }

    @Test
    fun `setTransportEnabled on unknown name is a no-op`() = runTest {
        repo.setTransportEnabled("wifi_aware", enabled = true)
        assertEquals(listOf("nsd", "ble"), coordinator.transports.map { it.name })
    }

    @Test
    fun `usbTether Info passes through from the provider`() = runTest {
        val tether = UsbTetherInfo(host = "192.168.42.42", mtu = 1500, linkMbps = 480)
        val repoWithUsb = PeerRepository(
            coordinator = coordinator,
            localIPPrefixProvider = { listOf("192.168.1.") },
            clusterFingerprintsProvider = { emptySet() },
            trustStore = trustStore,
            usbTetherProvider = { tether },
        )
        repoWithUsb.start(scope, self())
        val state = repoWithUsb.uiState.first()
        assertEquals(tether, state.usbTether)
    }
}