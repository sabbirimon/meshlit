package com.meshlit.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.config.ConfigKey
import com.meshlit.core.config.ConfigRepository
import com.meshlit.core.config.ConfigSchema
import com.meshlit.core.config.SchemaResult
import com.meshlit.core.config.schemaError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed [ConfigRepository] for the production app.
 *
 * Splits the `:core-config` interface from the Android-specific
 * persistence layer — the same seam
 * `com.meshlit.core.mcp.UserMcpServerStore.Persistence` uses for
 * `UserMcpServerStore`. Tests inject an `InMemoryConfigRepository`.
 *
 * **Schema validation:** values are coerced through
 * [ConfigSchema.validate] on write. Invalid values short-circuit
 * before reaching DataStore and return
 * [MeshlitResult.Failure] of [schemaError]. The DataStore entry is
 * unchanged.
 */
class DataStoreConfigRepository(
    private val context: Context,
) : ConfigRepository {

    private val log = logger("DataStoreConfigRepository")

    override fun get(key: ConfigKey<String>): String? = runCatching {
        runBlocking { context.configDataStore.data.first()[stringKey(key.name)] }
    }.getOrNull()

    override fun getInt(key: ConfigKey<Int>): Int? = get(ConfigKey(key.name))?.trim()?.toIntOrNull()
        ?: key.default

    override fun getBool(key: ConfigKey<Boolean>): Boolean? {
        val raw = get(ConfigKey(key.name))?.trim()?.lowercase() ?: return key.default
        return when (raw) {
            "true", "1" -> true
            "false", "0" -> false
            else -> key.default
        }
    }

    override fun <E : Enum<E>> getEnum(key: ConfigKey<E>, values: Array<E>): E? {
        val raw = get(ConfigKey(key.name)) ?: return key.default
        return values.firstOrNull { it.name == raw } ?: key.default
    }

    override suspend fun set(
        key: ConfigKey<String>,
        value: String,
    ): MeshlitResult<Unit> {
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
                    return MeshlitResult.Failure(schemaError(result.reason))
                }
            }
        }
        return try {
            context.configDataStore.edit { it[stringKey(key.name)] = value }
            log.info("config.set", "config value written", mapOf("key" to key.name))
            MeshlitResult.Success(Unit)
        } catch (t: Throwable) {
            log.error("config.set.fail", "DataStore write failed", t, mapOf("key" to key.name))
            MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Resource("config.set_fail"))
        }
    }

    override fun flow(key: ConfigKey<String>): Flow<String?> =
        context.configDataStore.data.map { it[stringKey(key.name)] }

    override fun snapshot(): Map<String, String> = runCatching {
        runBlocking {
            context.configDataStore.data.first().asMap()
                .mapNotNull { (k, v) -> if (k is Preferences.Key<*>) k.name to v.toString() else null }
                .toMap()
        }
    }.getOrElse { emptyMap() }

    private fun stringKey(name: String) = stringPreferencesKey(name)
}

/** App-wide DataStore <Preferences> for the bootstrap config. */
private val Context.configDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "meshlit_config")

/**
 * Tiny `runBlocking` shim. Synchronous reads of a `Flow.first()` are
 * the existing pattern in this codebase — see
 * `SettingsRepository.ragModeFlowNow()`. Used here so the
 * non-suspend `get` / `snapshot` accessors can stay non-suspend
 * without forking the API.
 */
private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
