package com.meshlit.core.config

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.flow.Flow

/**
 * Process-wide config store for the bootstrap layer.
 *
 * Configuration values are stored as **strings** at the backend (both
 * DataStore Preferences and the test [InMemoryConfigRepository] treat
 * everything as `String` keyed by [ConfigKey.name]). Typed reads are
 * the call site's job — use [getString], [getInt], [getBool], or
 * [getEnum] for type-safe access with default fallback.
 *
 * The [get] / [set] APIs return raw strings so callers building
 * typed wrappers don't have to round-trip through the type-safe
 * helpers. Most app code should prefer the typed helpers.
 *
 * Like every other `core-*` interface, this returns
 * [MeshlitResult] on writes (so a corrupt DataStore doesn't crash
 * the process) and synchronous / [Flow] reads (which can't fail in
 * a way the caller can recover from — they just return `null` or
 * the [ConfigKey.default]).
 */
interface ConfigRepository {

    /** Raw string read. Returns the persisted value, or `null` when
     *  nothing has been written and no [ConfigKey.default] is set. */
    fun get(key: ConfigKey<String>): String?

    /** Typed read helpers. Each falls back to [ConfigKey.default]
     *  (when present and castable) and otherwise to `null`. */
    fun getInt(key: ConfigKey<Int>): Int?
    fun getBool(key: ConfigKey<Boolean>): Boolean?

    /** Inline enum read; the string is matched against the enum's
     *  `name`. */
    fun <E : Enum<E>> getEnum(key: ConfigKey<E>, values: Array<E>): E?

    /** Persist a string under [key]. The schema on the key (if any)
     *  is applied first; an invalid value yields
     *  [MeshlitResult.Failure] without writing. */
    suspend fun set(
        key: ConfigKey<String>,
        value: String,
    ): MeshlitResult<Unit>

    /** Convenience: persist an int through [ConfigSchema.Int]. */
    suspend fun setInt(key: ConfigKey<Int>, value: Int): MeshlitResult<Unit> =
        set(ConfigKey(key.name), value.toString())

    /** Convenience: persist a bool through [ConfigSchema.Bool]. */
    suspend fun setBool(key: ConfigKey<Boolean>, value: Boolean): MeshlitResult<Unit> =
        set(ConfigKey(key.name), value.toString())

    /** Live updates for a key. Emits the current value (or the
     *  default) followed by every subsequent change. */
    fun flow(key: ConfigKey<String>): Flow<String?>

    /** Snapshot of every persisted key-value pair, for diagnostics
     *  and the bootstrap log line. */
    fun snapshot(): Map<String, String>
}

/**
 * Schema error returned by [ConfigRepository.set] when the candidate
 * fails validation. Wrapped in [MeshlitError.Invalid] so the failure
 * tag ("config.invalid_value") is stable across call sites.
 */
fun schemaError(reason: String): MeshlitError.Invalid =
    MeshlitError.Invalid("config.invalid_value:$reason")
