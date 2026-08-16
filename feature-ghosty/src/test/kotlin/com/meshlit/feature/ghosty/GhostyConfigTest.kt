package com.meshlit.feature.ghosty

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for [GhostyConfig] + [GhostyConfigStore] using an
 * in-memory [DataStore]. Real persistence is exercised end-to-end
 * by the `:app` Robolectric tests.
 */
class GhostyConfigTest {

    @Test
    fun default_config_is_safe() {
        val c = GhostyConfig.DEFAULT
        assertFalse(c.enabled)
        assertTrue(c.autoShow)
        assertEquals(0.85f, c.opacity, 0.001f)
        assertEquals(56, c.bubbleSizeDp)
        assertEquals(GhostyConfig.Position.BottomEnd, c.position)
        assertEquals("", c.hotWord)
    }

    @Test(expected = IllegalArgumentException::class)
    fun opacity_above_one_throws() {
        GhostyConfig.DEFAULT.copy(opacity = 1.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun opacity_below_zero_throws() {
        GhostyConfig.DEFAULT.copy(opacity = -0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bubbleSize_too_small_throws() {
        GhostyConfig.DEFAULT.copy(bubbleSizeDp = 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bubbleSize_too_big_throws() {
        GhostyConfig.DEFAULT.copy(bubbleSizeDp = 200)
    }

    @Test
    fun store_round_trip() = runBlocking {
        val data = InMemoryDataStore()
        val store = GhostyConfigStore(data)
        // Defaults.
        val initial = store.current()
        assertEquals(false, initial.enabled)

        store.update { it.copy(enabled = true, opacity = 0.5f, bubbleSizeDp = 64, position = GhostyConfig.Position.TopStart, hotWord = "hey ghosty") }
        val updated = store.current()
        assertTrue(updated.enabled)
        assertEquals(0.5f, updated.opacity, 0.001f)
        assertEquals(64, updated.bubbleSizeDp)
        assertEquals(GhostyConfig.Position.TopStart, updated.position)
        assertEquals("hey ghosty", updated.hotWord)

        // No-op transform does not lose data.
        store.update { it }
        assertEquals(0.5f, store.current().opacity, 0.001f)
    }

    @Test
    fun store_persists_unknown_position_to_default() = runBlocking {
        val data = InMemoryDataStore()
        data.setRaw("ghosty_position", "MadeUpCorner")
        val store = GhostyConfigStore(data)
        val c = store.current()
        assertEquals(GhostyConfig.Position.BottomEnd, c.position)
    }

    @Test
    fun config_flow_emits_when_changed() = runBlocking {
        val data = InMemoryDataStore()
        val store = GhostyConfigStore(data)
        // First emission is the default.
        assertEquals(false, store.config.first().enabled)
        store.update { it.copy(enabled = true) }
        assertEquals(true, store.config.first().enabled)
    }
}

/**
 * Bare-bones in-memory [DataStore] for tests. We expose a
 * `setRaw(key, value)` hook that the position-fallback test uses
 * to inject a known-bad value. In production the store is real.
 */
private class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val prior = state.value
        val next = transform(prior)
        state.value = next
        return next
    }

    fun setRaw(key: String, value: Any) {
        val updated = state.value.toMutablePreferences().apply {
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> this[androidx.datastore.preferences.core.stringPreferencesKey(key)] = value
                is Boolean -> this[androidx.datastore.preferences.core.booleanPreferencesKey(key)] = value
                is Float -> this[androidx.datastore.preferences.core.floatPreferencesKey(key)] = value
                is Int -> this[androidx.datastore.preferences.core.intPreferencesKey(key)] = value
            }
        }.toPreferences()
        state.value = updated
    }
}

/** Mirror of [GhostyController] logic — confirms the state flow
 *  toggling. We don't need the full service here. */
class GhostyControllerTest {

    @Test
    fun enable_then_disable_round_trips_persistence() = runBlocking {
        val data = InMemoryDataStore()
        val store = GhostyConfigStore(data)
        // initial state
        assertFalse(store.current().enabled)
        store.update { it.copy(enabled = true) }
        assertTrue(store.current().enabled)
        store.update { it.copy(enabled = false) }
        assertFalse(store.current().enabled)
    }
}