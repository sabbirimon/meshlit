package com.meshlit.core.users

import com.meshlit.core.common.MeshlitResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun in_memory_store_starts_with_placeholder_when_empty() {
        val store = InMemoryUserStore()
        assertEquals(MeshlitUserFallback, store.current())
        assertEquals(emptyList<User>(), store.list())
    }

    @Test
    fun in_memory_store_add_and_switch() {
        val store = InMemoryUserStore()
        val first = (store.add("Alice") as MeshlitResult.Success).value
        assertEquals("Alice", first.displayName)
        val second = (store.add("Bob") as MeshlitResult.Success).value
        // Active user is the most recently added.
        assertEquals(second, store.current())
        store.switchTo(first.id)
        assertEquals(first, store.current())
    }

    @Test
    fun in_memory_store_unknown_id_yields_invalid() {
        val store = InMemoryUserStore()
        val r = store.switchTo("nope")
        assertTrue(r is MeshlitResult.Failure)
    }

    @Test
    fun in_memory_store_rename_preserves_id() {
        val store = InMemoryUserStore()
        val u = (store.add("x") as MeshlitResult.Success).value
        store.rename(u.id, "y")
        assertEquals("y", store.current().displayName)
        assertEquals(u.id, store.current().id)
    }

    @Test
    fun file_backed_store_persists_across_reopen() {
        val dir = tmp.newFolder("users")
        val first = FileBackedUserStore(dir)
        val user = (first.add("Charlie") as MeshlitResult.Success).value
        first.rename(user.id, "Charlie Brown")

        val second = FileBackedUserStore(dir)
        val restored = second.current()
        assertEquals("Charlie Brown", restored.displayName)
        assertEquals(user.id, restored.id)
    }
}
