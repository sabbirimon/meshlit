package com.meshlit.flags

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.common.logger
import com.meshlit.core.flags.DefaultFlags
import com.meshlit.core.flags.FeatureFlag
import com.meshlit.core.flags.FeatureFlagRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * DataStore-backed [FeatureFlagRegistry] for the production app.
 *
 * **Fix 3 (review):** values are stored as `Boolean` via
 * `booleanPreferencesKey(name)`. The original `FeatureFlagRegistryImpl`
 * used `stringPreferencesKey(name)` and assigned a `Boolean` to the
 * same key — the code compiled cleanly but exploded at runtime with
 * `ClassCastException` the first time the flag was read. The fix
 * pins the type in the key factory; tests cover the `Persistence`
 * interface contract that production impls must write a `Boolean`.
 */
class DataStoreFeatureFlagRegistry(
    private val context: Context,
    initial: List<FeatureFlag> = DefaultFlags.ALL,
) : FeatureFlagRegistry {

    private val log = logger("DataStoreFeatureFlagRegistry")

    @Volatile
    private var registered: Map<String, FeatureFlag> = initial.associateBy { it.name }

    override fun get(name: String): Boolean {
        val raw = runCatching {
            runBlocking { context.featureFlagDataStore.data.first()[booleanKey(name)] }
        }.getOrNull()
        return raw ?: registered[name]?.default ?: false
    }

    override fun flow(name: String): Flow<Boolean> =
        context.featureFlagDataStore.data.map { prefs ->
            prefs[booleanKey(name)] ?: registered[name]?.default ?: false
        }

    override suspend fun set(name: String, value: Boolean) {
        try {
            context.featureFlagDataStore.edit { it[booleanKey(name)] = value }
            log.info("feature_flag.set", "feature flag written", mapOf("name" to name, "value" to value))
        } catch (t: Throwable) {
            log.error("feature_flag.set.fail", "DataStore write failed", t, mapOf("name" to name))
        }
    }

    override fun snapshot(): Map<String, Boolean> {
        val defaults = registered.associate { it.key to it.value.default }
        val persisted = runCatching {
            runBlocking {
                context.featureFlagDataStore.data.first().asMap()
                    .mapNotNull { (k, v) ->
                        if (k is Preferences.Key<*>) k.name to (v as? Boolean ?: false)
                        else null
                    }
                    .toMap()
            }
        }.getOrElse { emptyMap() }
        return defaults + persisted
    }

    override fun list(): List<FeatureFlag> = registered.values.toList()

    override suspend fun load() {
        // The production getter is already a "load on demand" read
        // from DataStore, so there's no separate cache to populate.
        // Calling load() is a no-op for symmetry with the in-memory
        // impl, and is what the bootstrap coordinator invokes.
        val snap = snapshot()
        log.info(
            "feature_flag.load",
            "feature flags loaded",
            mapOf("count" to snap.size),
        )
    }

    /** Register an extra flag at boot. Mostly for tests. */
    fun register(flag: FeatureFlag) {
        registered = registered + (flag.name to flag)
    }

    private fun booleanKey(name: String) = booleanPreferencesKey(name)
}

/** App-wide DataStore <Preferences> for feature flags. */
private val Context.featureFlagDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_feature_flags")
