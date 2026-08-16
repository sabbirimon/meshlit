package com.meshlit.core.flags

import kotlinx.coroutines.flow.Flow

/**
 * Process-wide registry for feature flags.
 *
 * The registry knows about every flag in [DefaultFlags.ALL] (plus any
 * extras registered at boot time via [register]). Reads always return
 * the current value — defaults if nothing has been written yet.
 *
 * The DataStore-backed production impl persists values via
 * [Persistence] — the seam the `:app` module fills in. Tests pass an
 * [InMemoryFlagPersistence] (see [InMemoryFeatureFlagRegistry]).
 */
interface FeatureFlagRegistry {

    /**
     * Read the current value of [name]. Returns the registered
     * default when the flag is unknown, which lets callers query
     * optional flags without crashing on a missing registration.
     */
    fun get(name: String): Boolean

    /** Live updates for [name]. Emits the current value followed by
     *  every subsequent change. */
    fun flow(name: String): Flow<Boolean>

    /**
     * Persist a new value for [name]. No-op when [name] is unknown
     * to the registry (so callers can speculatively set flags that
     * were registered late).
     */
    suspend fun set(name: String, value: Boolean)

    /** Snapshot of every known flag, for diagnostics + the bootstrap
     *  log line. */
    fun snapshot(): Map<String, Boolean>

    /** List the registered flags (name, default, description). */
    fun list(): List<FeatureFlag>

    /**
     * Hot-load every persisted value into the in-memory cache.
     * Called once on app start, after [register] has finished.
     */
    suspend fun load()

    /** Persistence seam — the `:app` module plugs a DataStore impl
     *  in here so `:core-flags` stays JVM-testable. */
    interface Persistence {
        suspend fun read(): Map<String, Boolean>
        suspend fun write(name: String, value: Boolean)
    }
}
