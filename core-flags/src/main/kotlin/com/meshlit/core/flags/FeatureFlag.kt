package com.meshlit.core.flags

/**
 * A single feature flag declaration.
 *
 * @property name Stable identifier — also the DataStore key. Must
 *  match `[a-z0-9_.]+` so the persistence layer can use it as a
 *  key without escaping.
 * @property default The value the registry returns before any
 *  explicit `set` is called.
 * @property description Human-readable explanation. Surfaced in the
 *  Settings → Advanced → Feature Flags screen so users (and tests)
 *  know what each flag toggles.
 */
data class FeatureFlag(
    val name: String,
    val default: Boolean,
    val description: String,
) {
    init {
        require(name.matches(NAME_PATTERN)) {
            "FeatureFlag.name must match ${NAME_PATTERN.pattern}: $name"
        }
    }

    private companion object {
        val NAME_PATTERN = Regex("^[a-z0-9_.]+$")
    }
}
