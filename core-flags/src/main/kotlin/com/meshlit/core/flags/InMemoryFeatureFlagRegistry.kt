package com.meshlit.core.flags

import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [FeatureFlagRegistry] for tests and the preview surface.
 *
 * The cache is a `Map<String, Boolean>` keyed by [FeatureFlag.name].
 * Unknown flags (`get` on a name that was never registered) return
 * `false` — see the [FeatureFlagRegistry.get] contract.
 *
 * The `Persistence` interface lets the DataStore-backed production
 * impl in `:app` (see
 * `com.meshlit.flags.DataStoreFeatureFlagRegistry`) plug in
 * without touching this class. Tests pass [InMemoryFlagPersistence].
 *
 * **Fix 3:** persistence values are stored as `Boolean`, not as
 * `String`. The original `FeatureFlagRegistryImpl` schema mixed
 * `stringPreferencesKey` with a `Boolean` value, which compiled
 * fine but exploded at runtime with `ClassCastException` the first
 * time the flag was read. This class encodes the fix in the
 * `Persistence` interface — production impls must persist as
 * `Boolean`.
 */
class InMemoryFeatureFlagRegistry(
    private val persistence: Persistence = InMemoryFlagPersistence(),
    initial: List<FeatureFlag> = DefaultFlags.ALL,
) : FeatureFlagRegistry {

    private val log = logger("InMemoryFeatureFlagRegistry")
    private val mutex = Mutex()

    @Volatile
    private var registered: Map<String, FeatureFlag> = initial.associateBy { it.name }

    private val cache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    override fun get(name: String): Boolean = cache.value[name] ?: registered[name]?.default ?: false

    override fun flow(name: String): Flow<Boolean> =
        cache.map { it[name] ?: registered[name]?.default ?: false }

    override suspend fun set(name: String, value: Boolean) = mutex.withLock {
        // Persist (no-op for unknown flag names intentionally — see
        // the contract). We touch the cache regardless so the in-
        // memory view stays consistent.
        cache.value = cache.value + (name to value)
        // Best-effort persistence — failures are logged, not thrown.
        runCatching { persistence.write(name, value) }.onFailure { t ->
            log.error(
                "feature_flag.persist_fail",
                "failed to persist feature flag",
                t,
                mapOf("name" to name, "value" to value),
            )
        }
    }

    override fun snapshot(): Map<String, Boolean> {
        val defaults = registered.associate { it.key to it.value.default }
        return defaults + cache.value
    }

    override fun list(): List<FeatureFlag> = registered.values.toList()

    override suspend fun load() = mutex.withLock {
        val persisted = runCatching { persistence.read() }.getOrElse {
            log.error("feature_flag.load_fail", "feature flag load failed", it)
            emptyMap()
        }
        // Persisted values override the defaults, but unknown
        // persisted names are kept (so re-adding a flag down the
        // line restores its previous value).
        cache.value = (registered.associate { it.key to it.value.default } + persisted)
        log.info(
            "feature_flag.loaded",
            "feature flags loaded",
            mapOf(
                "registered" to registered.size,
                "persisted" to persisted.size,
                "overrides" to persisted.filter { (k, _) -> k in registered }.size,
            ),
        )
    }

    /** Add a [FeatureFlag] to the registry after construction.
     *  Used by tests; production code should rely on
     *  [DefaultFlags.ALL]. */
    fun register(flag: FeatureFlag) {
        registered = registered + (flag.name to flag)
    }

    /**
     * In-memory persistence backend. Stores entries as `Boolean`
     * — matching the production DataStore impl per Fix 3.
     */
    class InMemoryFlagPersistence(
        initial: Map<String, Boolean> = emptyMap(),
    ) : FeatureFlagRegistry.Persistence {
        @Volatile private var current: Map<String, Boolean> = initial.toMap()
        override suspend fun read(): Map<String, Boolean> = current.toMap()
        override suspend fun write(name: String, value: Boolean) {
            current = current + (name to value)
        }
    }
}
