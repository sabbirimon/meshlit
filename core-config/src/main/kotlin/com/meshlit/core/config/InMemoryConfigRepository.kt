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

    override fun get(key: ConfigKey<String>): String? = state.value[key.name]

    override fun getInt(key: ConfigKey<Int>): Int? =
        state.value[key.name]?.trim()?.toIntOrNull() ?: key.default

    override fun getBool(key: ConfigKey<Boolean>): Boolean? =
        when (state.value[key.name]?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            null -> key.default
            else -> key.default
        }

    override fun <E : Enum<E>> getEnum(key: ConfigKey<E>, values: Array<E>): E? {
        val raw = state.value[key.name] ?: return key.default
        return values.firstOrNull { it.name == raw } ?: key.default
    }

    override suspend fun set(
        key: ConfigKey<String>,
        value: String,
    ): MeshlitResult<Unit> = mutex.withLock {
        val schema = key.schema
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
}
