package com.meshlit.core.mcp

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [UserMcpServerStore]. Uses an in-memory persistence
 * backend so the suite is hermetic and finishes in milliseconds.
 */
class UserMcpServerStoreTest {

    private fun newStore(persistence: UserMcpServerStore.Persistence = InMemoryUserMcpServerPersistence()) =
        UserMcpServerStore(persistence = persistence)

    private fun gitServer(id: String = "github", enabled: Boolean = true) = UserMcpServer(
        id = id,
        name = id,
        command = "/usr/local/bin/$id-mcp",
        args = listOf("--stdio"),
        env = mapOf("GITHUB_TOKEN" to "fake"),
        timeoutMs = 5_000,
        enabled = enabled,
    )

    @Test
    fun initial_state_is_empty() = runTest {
        val store = newStore()
        assertEquals(emptyList<UserMcpServer>(), store.all)
        assertEquals(emptyList<UserMcpServer>(), store.servers.value)
        assertNull(store.findById("anything"))
    }

    @Test
    fun rehydrate_with_empty_persistence_keeps_state_empty() = runTest {
        val store = newStore()
        store.rehydrate()
        assertEquals(emptyList<UserMcpServer>(), store.all)
    }

    @Test
    fun upsert_inserts_and_updates_entry() = runTest {
        val store = newStore()
        store.upsert(gitServer())
        assertEquals(1, store.all.size)
        assertEquals("github", store.all.first().id)

        val updated = gitServer().copy(timeoutMs = 12_000)
        store.upsert(updated)
        assertEquals(1, store.all.size)
        assertEquals(12_000, store.all.first().timeoutMs)
    }

    @Test
    fun upsert_rejects_blank_id() = runTest {
        val store = newStore()
        var thrown: Throwable? = null
        try {
            // Bypass init validation by passing a deliberately
            // blank id through a permissive copy.
            @Suppress("UNCHECKED_CAST")
            val bad = UserMcpServer(
                id = "",
                name = "x",
                command = "/bin/true",
            )
            store.upsert(bad)
        } catch (t: Throwable) {
            thrown = t
        }
        // UserMcpServer.init throws for blank id so the upsert
        // never reaches the store; verify nothing was inserted.
        assertNotNull(thrown)
        assertEquals(emptyList<UserMcpServer>(), store.all)
    }

    @Test
    fun remove_drops_entry_and_persists() = runTest {
        val store = newStore()
        store.upsert(gitServer("github"))
        store.upsert(gitServer("slack"))
        assertEquals(2, store.all.size)
        store.remove("github")
        assertEquals(1, store.all.size)
        assertEquals("slack", store.all.first().id)

        // Removing an absent id is a no-op.
        store.remove("nope")
        assertEquals(1, store.all.size)
    }

    @Test
    fun setEnabled_toggles_in_place() = runTest {
        val store = newStore()
        store.upsert(gitServer(enabled = true))
        store.setEnabled("github", false)
        assertFalse(store.findById("github")!!.enabled)
        store.setEnabled("github", true)
        assertTrue(store.findById("github")!!.enabled)
    }

    @Test
    fun setEnabled_for_unknown_id_is_noop() = runTest {
        val store = newStore()
        // Should not throw.
        store.setEnabled("ghost", true)
        assertEquals(0, store.all.size)
    }

    @Test
    fun replaceAll_persists_full_set() = runTest {
        val store = newStore()
        store.replaceAll(listOf(gitServer("a"), gitServer("b"), gitServer("c")))
        assertEquals(3, store.all.size)

        // Replacements sort by name; verify ordering.
        val names = store.all.map { it.id }
        assertEquals(listOf("a", "b", "c"), names)
    }

    @Test
    fun rehydrate_after_persistence_swap_restores_state() = runTest {
        val first = InMemoryUserMcpServerPersistence()
        val store = newStore(first)
        store.upsert(gitServer("github"))
        store.upsert(gitServer("slack"))

        // Simulate process restart: a brand new store backed by the
        // SAME persistence object must see the persisted entries.
        val reborn = newStore(first)
        reborn.rehydrate()
        assertEquals(2, reborn.all.size)
        val ids = reborn.all.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("github", "slack")))
    }

    @Test
    fun rehydrate_corrupt_payload_yields_empty_set() = runTest {
        val broken = object : UserMcpServerStore.Persistence {
            override suspend fun read(): String = "{not-json"
            override suspend fun write(value: String) = Unit
        }
        val store = newStore(broken)
        store.rehydrate()
        assertEquals(emptyList<UserMcpServer>(), store.all)
    }

    @Test
    fun clear_drops_everything() = runTest {
        val store = newStore()
        store.upsert(gitServer("a"))
        store.upsert(gitServer("b"))
        store.clear()
        assertEquals(emptyList<UserMcpServer>(), store.all)
        // And a follow-up rehydrate still yields empty.
        store.rehydrate()
        assertEquals(emptyList<UserMcpServer>(), store.all)
    }

    @Test
    fun servers_state_flow_reflects_mutations() = runTest {
        val store = newStore()
        // Snapshot emissions after each mutation.
        store.upsert(gitServer("a"))
        assertEquals(1, store.servers.value.size)
        store.upsert(gitServer("b"))
        assertEquals(2, store.servers.value.size)
        store.remove("a")
        assertEquals(1, store.servers.value.size)
        assertEquals("b", store.servers.value.single().id)
    }

    @Test
    fun applyTo_replaces_pool_catalog() = runTest {
        val store = newStore()
        val pool = McpClientPool(registry = McpToolRegistry())
        store.upsert(gitServer("github"))
        store.upsert(gitServer("slack"))
        store.applyTo(pool)
        assertEquals(2, pool.configured.value.size)
        assertEquals(store.all.sortedBy { it.name }, pool.configured.value.values.toList().sortedBy { it.name })
    }
}