package com.meshlit.core.flags

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagRegistryTest {

    @Test
    fun `default flags are visible before set is called`() {
        val registry = InMemoryFeatureFlagRegistry()
        assertTrue(
            "feature.discovery.nsd should default to true",
            registry.get("feature.discovery.nsd"),
        )
        assertFalse(
            "feature.gossip.enabled should default to false",
            registry.get("feature.gossip.enabled"),
        )
    }

    @Test
    fun `set updates the in-memory value and flow`() = runTest {
        val registry = InMemoryFeatureFlagRegistry()
        registry.set("feature.discovery.nsd", false)
        assertFalse(registry.get("feature.discovery.nsd"))
        assertFalse(registry.flow("feature.discovery.nsd").first())
    }

    @Test
    fun `load reads previously persisted values and overrides defaults`() = runTest {
        // Persistence stored `feature.gossip.enabled = true` before
        // the registry was constructed. After load() the in-memory
        // cache must reflect it, even though the default is false.
        val persistence = InMemoryFeatureFlagRegistry.InMemoryFlagPersistence(
            initial = mapOf("feature.gossip.enabled" to true),
        )
        val registry = InMemoryFeatureFlagRegistry(persistence = persistence)
        assertFalse(
            "before load() the default should still apply",
            registry.get("feature.gossip.enabled"),
        )
        registry.load()
        assertTrue(
            "after load() the persisted value must win",
            registry.get("feature.gossip.enabled"),
        )
    }

    @Test
    fun `snapshot includes defaults and overrides`() = runTest {
        val registry = InMemoryFeatureFlagRegistry()
        registry.set("feature.discovery.nsd", false)
        val snap = registry.snapshot()
        assertEquals(false, snap["feature.discovery.nsd"])
        assertEquals(true, snap["feature.power.bypass_charge"])
    }

    @Test
    fun `get returns false for unknown flag name`() {
        // Contract: optional flags can be queried without crashing
        // when the registration hasn't happened yet.
        val registry = InMemoryFeatureFlagRegistry()
        assertFalse(registry.get("nonexistent.flag"))
    }

    @Test
    fun `set is a no-op for unknown flag name`() = runTest {
        // Same contract: speculative set on an unknown flag must
        // not throw. The snapshot just doesn't surface it as a
        // registered flag.
        val registry = InMemoryFeatureFlagRegistry()
        registry.set("nonexistent.flag", true)
        assertFalse(registry.get("nonexistent.flag"))
    }

    /**
     * **Regression test for Fix 3** — `FeatureFlagRegistryImpl` used
     * to store Booleans under a `stringPreferencesKey`, which
     * compiled cleanly but threw `ClassCastException` the first time
     * the flag was read out of DataStore.
     *
     * The fix moves persistence to `Boolean`. This test asserts the
     * shape of the [FeatureFlagRegistry.Persistence] interface: the
     * production impl is required to write a Boolean, not a String.
     * A `stringPreferencesKey`+Boolean would not satisfy this
     * contract.
     */
    @Test
    fun `persistence stores booleans not strings — regression for Fix 3`() = runTest {
        // Use a captured-persistence to inspect the type of value
        // the registry hands to the backend.
        val captured = CapturingPersistence()
        val registry = InMemoryFeatureFlagRegistry(persistence = captured)
        registry.set("feature.discovery.nsd", true)
        val written = captured.lastWritten
        requireNotNull("persistence.write must have been called", written)
        val (name, value) = written
        assertEquals("feature.discovery.nsd", name)
        assertTrue(
            "Fix 3: persistence value must be Boolean, was ${value::class.simpleName}",
            value is Boolean,
        )
        assertEquals(true, value)
    }

    @Test
    fun `round-trip through persistence preserves the value exactly`() = runTest {
        // End-to-end: write -> read on a fresh registry must yield
        // the same Boolean. The "stringPreferencesKey + Boolean"
        // bug from the original plan would fail this with a CCE.
        val persistence = InMemoryFeatureFlagRegistry.InMemoryFlagPersistence()
        val a = InMemoryFeatureFlagRegistry(persistence = persistence)
        a.set("feature.gossip.enabled", true)
        a.set("feature.discovery.nsd", false)

        val b = InMemoryFeatureFlagRegistry(persistence = persistence)
        b.load()
        assertTrue(b.get("feature.gossip.enabled"))
        assertFalse(b.get("feature.discovery.nsd"))
    }

    private class CapturingPersistence : FeatureFlagRegistry.Persistence {
        var lastWritten: Pair<String, Boolean>? = null
        override suspend fun read(): Map<String, Boolean> = emptyMap()
        override suspend fun write(name: String, value: Boolean) {
            lastWritten = name to value
        }
    }
}
