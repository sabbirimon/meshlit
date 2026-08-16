package com.meshlit.core.trust

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrustStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun cleanup() {
        LocalTrustPolicy.reset()
    }

    @Test
    fun in_memory_store_round_trip() {
        val store = InMemoryTrustStore()
        val node = NodeId("node-A")
        val policy = DeviceTrustPolicy(
            nodeId = node.value,
            trustTier = TrustTier.LOCAL_TRUSTED,
            allowedRoles = setOf(ClusterRoleTag.BRAIN),
            tokenExpiryMs = null,
            publicKeyFingerprint = null,
        )
        val upserted = store.upsert(policy)
        assertTrue(upserted is MeshlitResult.Success)
        assertEquals(policy, store.policyFor(node))
        assertEquals(listOf(policy), store.list())
    }

    @Test
    fun in_memory_store_revoke_removes_entry() {
        val store = InMemoryTrustStore()
        val node = NodeId("node-B")
        store.upsert(
            DeviceTrustPolicy(
                nodeId = node.value,
                trustTier = TrustTier.LOCAL_SANDBOXED,
                allowedRoles = emptySet(),
                tokenExpiryMs = 1_000L,
                publicKeyFingerprint = null,
            )
        )
        val revoked = store.revoke(node)
        assertTrue(revoked is MeshlitResult.Success)
        assertNull(store.policyFor(node))
    }

    @Test
    fun file_backed_store_persists_across_reopen() {
        val dir = tmp.newFolder("trust")
        val first = FileBackedTrustStore(dir)
        val node = NodeId("node-C")
        first.upsert(
            DeviceTrustPolicy(
                nodeId = node.value,
                trustTier = TrustTier.WAN,
                allowedRoles = setOf(ClusterRoleTag.BRAIN, ClusterRoleTag.TOOL),
                tokenExpiryMs = 9_999L,
                publicKeyFingerprint = "fp:abc",
            )
        )
        // Second instance reads the same dir — must see the policy.
        val second = FileBackedTrustStore(dir)
        val restored = second.policyFor(node)
        assertNotNull(restored)
        assertEquals(TrustTier.WAN, restored!!.trustTier)
        assertEquals("fp:abc", restored.publicKeyFingerprint)
    }

    @Test
    fun effective_tier_picks_strictest() {
        val trusted = DeviceTrustPolicy("a", TrustTier.LOCAL_TRUSTED, emptySet(), null, null)
        val sandboxed = DeviceTrustPolicy("b", TrustTier.LOCAL_SANDBOXED, emptySet(), null, null)
        val wan = DeviceTrustPolicy("c", TrustTier.WAN, emptySet(), null, null)
        assertEquals(TrustTier.LOCAL_TRUSTED, effectiveTier(trusted, trusted))
        assertEquals(TrustTier.LOCAL_SANDBOXED, effectiveTier(trusted, sandboxed))
        assertEquals(TrustTier.LOCAL_SANDBOXED, effectiveTier(sandboxed, trusted))
        assertEquals(TrustTier.WAN, effectiveTier(trusted, wan))
        assertEquals(TrustTier.WAN, effectiveTier(wan, sandboxed))
    }

    @Test
    fun local_trust_policy_singleton_round_trip() {
        assertNull(LocalTrustPolicy.current())
        val policy = DeviceTrustPolicy(
            nodeId = "self",
            trustTier = TrustTier.LOCAL_TRUSTED,
            allowedRoles = setOf(ClusterRoleTag.BRAIN),
            tokenExpiryMs = null,
            publicKeyFingerprint = null,
        )
        val prev = LocalTrustPolicy.set(policy)
        assertNull(prev)
        assertEquals(policy, LocalTrustPolicy.current())
        assertEquals(TrustTier.LOCAL_TRUSTED, LocalTrustPolicy.currentTier())
        assertEquals(NodeId("self"), LocalTrustPolicy.currentNodeId())
    }
}
