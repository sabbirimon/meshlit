package com.meshlit.core.training.config

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads a [DistributedConfig] from a JSON string or file system path.
 *
 * Rejects unknown schema versions with a typed
 * [MeshlitError.Invalid] so the caller can show a clear "you need app
 * update" instead of a silent miscompute. The wire is v1 only — when
 * we bump to v2 we keep v1 as a read-only fallback for two minor
 * versions, then drop it.
 */
object DistributedConfigLoader {

    private val json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
        isLenient = false
    }

    /**
     * Parse a JSON string. Returns a typed [MeshlitResult] so callers
     * don't need to catch exceptions across module boundaries.
     */
    fun fromJson(text: String): MeshlitResult<DistributedConfig> = try {
        MeshlitResult.Success(json.decodeFromString(DistributedConfig.serializer(), text))
    } catch (e: SerializationException) {
        MeshlitResult.Failure(
            MeshlitError.Invalid(
                tag = "cluster.trainer.config_parse:${e.message?.take(120)}",
                cause = e,
            )
        )
    } catch (e: IllegalArgumentException) {
        // require() in init { ... } lands here.
        MeshlitResult.Failure(
            MeshlitError.Invalid(
                tag = "cluster.trainer.config_invalid:${e.message?.take(120)}",
                cause = e,
            )
        )
    }

    /**
     * Read + parse from a file path. Returns [MeshlitError.Invalid] on
     * missing file so the caller can fall back to the default config.
     */
    fun fromFile(path: String): MeshlitResult<DistributedConfig> = try {
        fromJson(java.io.File(path).readText(Charsets.UTF_8))
    } catch (e: java.io.FileNotFoundException) {
        MeshlitResult.Failure(
            MeshlitError.Invalid(
                tag = "cluster.trainer.config_missing:$path",
                cause = e,
            )
        )
    } catch (e: java.io.IOException) {
        MeshlitResult.Failure(
            MeshlitError.Network(
                tag = "cluster.trainer.config_io:${e.message?.take(120)}",
                cause = e,
            )
        )
    }

    /** Serialize a config to pretty-printed JSON. Used by `training plan`. */
    fun toJson(cfg: DistributedConfig): String =
        json.encodeToString(DistributedConfig.serializer(), cfg)

    /** Default config (= [DistributedConfig] with all defaults). */
    fun defaultConfig(): DistributedConfig = DistributedConfig()
}
