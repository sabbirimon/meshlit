package com.meshlit.core.federation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Single canonical JSON codec for every federation endpoint.
 *
 * Configuration choices:
 *
 *  - `ignoreUnknownKeys = true` — clients and servers may add fields
 *    in a minor-version bump; older peers must still parse the wire.
 *  - `encodeDefaults = false` — keep payloads small. Optional fields
 *    that equal their default value are elided.
 *  - `prettyPrint = false` — federation traffic is machine-to-machine;
 *    bytes matter more than readability.
 *  - `explicitNulls = false` — `null` fields are omitted from the
 *    output. Wire consumers must use the absence of a field as the
 *    "use the default" signal, which matches the `encodeDefaults`
 *    choice.
 *
 * The codec is intentionally permissive on parse (for forward compat)
 * and strict on construction (every type is `data class` with
 * non-nullable required fields). Tests in
 * `FederationCodecTest` pin both directions.
 */
object FederationCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
        explicitNulls = false
        coerceInputValues = false
    }

    // ---- encode side ----

    inline fun <reified T> encode(value: T): String = json.encodeToString(value)

    inline fun <reified T> encodeToElement(value: T): JsonObject =
        json.encodeToJsonElement(value) as JsonObject

    // ---- decode side ----

    inline fun <reified T> decode(payload: String): Result<T> = runCatching {
        json.decodeFromString<T>(payload)
    }

    /** Decode a [FederationError] payload from a response body. If the
     *  payload isn't valid JSON, returns [FederationError.internalError]
     *  with the parse message — the caller still gets a typed result. */
    fun decodeError(payload: String): FederationError =
        decode<FederationError>(payload).getOrElse { parseError ->
            FederationError.internalError("malformed error body: ${parseError.message}")
        }
}