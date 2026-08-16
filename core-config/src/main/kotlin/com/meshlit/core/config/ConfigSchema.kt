package com.meshlit.core.config

/**
 * Validates a candidate value against an expected shape before it
 * lands in the [ConfigRepository]. Schemas are pure functions over
 * their input — they return either the canonicalised value or a
 * reason it was rejected.
 *
 * A null schema (the default on [ConfigKey]) means "no validation"
 * — useful for keys that are inherently free-form (the device label
 * the user types in Settings, for example).
 */
fun interface ConfigSchema<T : Any> {
    fun validate(candidate: String): SchemaResult<T>

    companion object {
        /** Accept any non-blank string. The canonicalised value is
         *  `candidate` verbatim. */
        val AnyString: ConfigSchema<String> = ConfigSchema { raw ->
            if (raw.isBlank()) SchemaResult.Invalid("must not be blank")
            else SchemaResult.Valid(raw)
        }

        /** Accept anything parseable as an [Int]. */
        val Int: ConfigSchema<kotlin.Int> = ConfigSchema { raw ->
            val parsed = raw.trim().toIntOrNull()
            if (parsed == null) SchemaResult.Invalid("not an int: $raw")
            else SchemaResult.Valid(parsed)
        }

        /** Accept "true"/"false"/"1"/"0" (case-insensitive). */
        val Bool: ConfigSchema<Boolean> = ConfigSchema { raw ->
            when (raw.trim().lowercase()) {
                "true", "1" -> SchemaResult.Valid(true)
                "false", "0" -> SchemaResult.Valid(false)
                else -> SchemaResult.Invalid("not a bool: $raw")
            }
        }

        /** Accept only members of the enum's `name` set. */
        fun <E : Enum<E>> enum(values: Array<E>): ConfigSchema<E> = ConfigSchema { raw ->
            val match = values.firstOrNull { it.name == raw }
            if (match == null) {
                SchemaResult.Invalid("not in ${values.joinToString { it.name }}: $raw")
            } else {
                SchemaResult.Valid(match)
            }
        }
    }
}

sealed interface SchemaResult<out T : Any> {
    data class Valid<T : Any>(val value: T) : SchemaResult<T>
    data class Invalid(val reason: String) : SchemaResult<Nothing>
}
