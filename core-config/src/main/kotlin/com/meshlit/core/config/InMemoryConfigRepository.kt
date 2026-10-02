package com.meshlit.core.config

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [ConfigRepository] for unit tests and the Compose preview
 * surface. Thread-safe via a single [Mutex] so it can stand in for the
 * DataStore-backed production impl in any test that doesn't depend on
 * the on-disk format.
 *
 * Persistence interface lets the DataStore impl in `:app` swap in
 * without touching this class — the same seam used by
 * [com.meshlit.core.mcp.UserMcpServerStore.Persistence].
 */
class InMemoryConfigRepository(
    initial: Map<String, String> = emptyMap(),
) : ConfigRepository {

    private val log = logger("InMemoryConfigRepository")
    private val mutex = Mutex()

    /** Backing state. Wrapped in [MutableStateFlow] so [flow] and
     *  [snapshot] are O(1). */
    private val state = MutableStateFlow(initial.toMap())

    /** Per-name schema registry. Populated the first time a key with
     *  a non-null [ConfigKey.schema] is [set] — subsequent writes
     *  through any [ConfigKey] sharing the same `name` validate
     *  against the same schema. Without this registry the test
     *  scenario "first write with schema, second write through a
     *  raw `ConfigKey(name)` that drops the schema" would let
     *  invalid values through silently. */
    private val schemas = mutableMapOf<String, ConfigSchema<String>>()

    override fun get(key: ConfigKey<String>): String? =
        state.value[key.name] ?: key.default

    override fun getInt(key: ConfigKey<Int>): Int? =
        state.value[key.name]?.trim()?.toIntOrNull() ?: key.default

    override fun getBool(key: ConfigKey<Boolean>): Boolean? =
        when (state.value[key.name]?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            null -> key.default
            else -> key.default
        }

    override suspend fun setInt(key: ConfigKey<Int>, value: Int): MeshlitResult<Unit> =
        set(
            ConfigKey<String>(name = key.name, schema = intSchemaAsString),
            value.toString(),
        )

    override suspend fun setBool(key: ConfigKey<Boolean>, value: Boolean): MeshlitResult<Unit> =
        set(
            ConfigKey<String>(name = key.name, schema = boolSchemaAsString),
            value.toString(),
        )

    override fun <E : Enum<E>> getEnum(key: ConfigKey<E>, values: Array<E>): E? {
        val raw = state.value[key.name] ?: return key.default
        return values.firstOrNull { it.name == raw } ?: key.default
    }

    override suspend fun set(
        key: ConfigKey<String>,
        value: String,
    ): MeshlitResult<Unit> = mutex.withLock {
        // Resolve the schema in two passes: first consult the
        // keyed-by-name registry, then fall back to the inline
        // `key.schema` and register it for next time. This lets a
        // raw `ConfigKey(name)` write still be checked against the
        // schema established by the previous typed write — which
        // is what the "set rejects value failing schema" test
        // exercises (it writes the int via `setInt` first, then
        // tries to overwrite with a non-numeric string via a
        // schema-less `ConfigKey`).
        val schema = schemas[key.name] ?: key.schema?.also {
            // Re-key the schema with `<String>` so it lives in the
            // `Map<String, ConfigSchema<String>>` regardless of
            // the original type. The validation semantics are
            // preserved because the underlying `ConfigSchema`
            // interface is purely string-in / result-out.
            @Suppress("UNCHECKED_CAST")
            schemas[key.name] = it as ConfigSchema<String>
        }
        if (schema != null) {
            when (val result = schema.validate(value)) {
                is SchemaResult.Valid -> { /* fall through */ }
                is SchemaResult.Invalid -> {
                    log.warn(
                        "config.set.invalid",
                        "rejecting value for ${key.name}",
                        mapOf("reason" to result.reason),
                    )
                    return@withLock MeshlitResult.Failure(schemaError(result.reason))
                }
            }
        }
        state.value = state.value + (key.name to value)
        log.info(
            "config.set",
            "config value written",
            mapOf("key" to key.name),
        )
        MeshlitResult.Success(Unit)
    }

    override fun flow(key: ConfigKey<String>): Flow<String?> =
        state.map { it[key.name] }

    override fun snapshot(): Map<String, String> = state.value.toMap()

    companion object {
        // The [ConfigSchema] companions are typed (`ConfigSchema<Int>`,
        // `ConfigSchema<Boolean>`) but the [ConfigKey] /
        // [ConfigRepository.set] interface is string-shaped. These
        // trivial adapters re-key the schema so setInt / setBool can
        // push the validation contract through to [set] without
        // making the [ConfigRepository] interface generic over a
        // schema type. Validation semantics are unchanged because
        // [ConfigSchema] is a string-in / result-out function.
        private val intSchemaAsString: ConfigSchema<String> =
            ConfigSchema { raw ->
                when (val r = ConfigSchema.Int.validate(raw)) {
                    is SchemaResult.Valid -> SchemaResult.Valid(raw)
                    is SchemaResult.Invalid -> r
                }
            }

        private val boolSchemaAsString: ConfigSchema<String> =
            ConfigSchema { raw ->
                when (val r = ConfigSchema.Bool.validate(raw)) {
                    is SchemaResult.Valid -> SchemaResult.Valid(raw)
                    is SchemaResult.Invalid -> r
                }
            }
    }
}
