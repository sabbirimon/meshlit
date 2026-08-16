package com.meshlit.core.config

import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryConfigRepositoryTest {

    private val nodeIdKey = BuiltInConfigKeys.nodeId()
    private val clusterNameKey = ConfigKey<String>(
        name = "cluster.name",
        default = "Meshlit",
    )
    private val maxPeersKey = ConfigKey<Int>(
        name = "cluster.max_peers",
        default = 8,
        schema = ConfigSchema.Int,
    )
    private val gossipEnabledKey = ConfigKey<Boolean>(
        name = "feature.gossip.enabled",
        default = false,
        schema = ConfigSchema.Bool,
    )

    @Test
    fun `get returns null when key absent and no default`() {
        val repo = InMemoryConfigRepository()
        assertNull(repo.get(nodeIdKey))
    }

    @Test
    fun `get returns default when key absent and default present`() {
        val repo = InMemoryConfigRepository()
        assertEquals("Meshlit", repo.get(clusterNameKey))
    }

    @Test
    fun `set then get round-trip yields same value`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.set(nodeIdKey, "abc-123")
        assertEquals("abc-123", repo.get(nodeIdKey))
    }

    @Test
    fun `set then flow emits same value — regression for bootstrap node-id read`() = runTest {
        // This is the exact code path BootstrapCoordinator uses to
        // surface the persisted node id: write once, observe via flow.
        // Without it the gossip protocol sees a fresh identity every
        // restart (Bug #4 in the review).
        val repo = InMemoryConfigRepository()
        repo.set(nodeIdKey, "stable-node-id-9c3a")
        assertEquals("stable-node-id-9c3a", repo.flow(nodeIdKey).first())
    }

    @Test
    fun `typed getInt falls back to default when absent`() {
        val repo = InMemoryConfigRepository()
        assertEquals(8, repo.getInt(maxPeersKey))
    }

    @Test
    fun `typed getInt reads written value`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.setInt(maxPeersKey, 16)
        assertEquals(16, repo.getInt(maxPeersKey))
    }

    @Test
    fun `set rejects value failing schema`() = runTest {
        val repo = InMemoryConfigRepository()
        val res = repo.setInt(maxPeersKey, 16) // first write OK
        assertTrue(res is MeshlitResult.Success)

        // Now overwrite via the raw API with a non-int string. The
        // schema on `maxPeersKey` should reject it.
        val bad = repo.set(ConfigKey(maxPeersKey.name), "not-a-number")
        assertTrue(bad is MeshlitResult.Failure)
        // Original value preserved.
        assertEquals(16, repo.getInt(maxPeersKey))
    }

    @Test
    fun `setBool round-trips true and false`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.setBool(gossipEnabledKey, true)
        assertEquals(true, repo.getBool(gossipEnabledKey))
        repo.setBool(gossipEnabledKey, false)
        assertEquals(false, repo.getBool(gossipEnabledKey))
    }

    @Test
    fun `snapshot returns every persisted value`() = runTest {
        val repo = InMemoryConfigRepository()
        repo.set(nodeIdKey, "id-a")
        repo.setInt(maxPeersKey, 12)
        val snap = repo.snapshot()
        assertEquals("id-a", snap[nodeIdKey.name])
        assertEquals("12", snap[maxPeersKey.name])
    }
}
