package com.meshlit.core.config

/**
 * A typed key into the [ConfigRepository]. The [T] bound makes the
 * read/write APIs type-safe at the call site (e.g. `get(IntKey("foo"))`
 * cannot accidentally hand back a string). The [default] is what the
 * repository returns when nothing has been written yet — most callers
 * rely on this rather than checking for `null`.
 *
 * Keys are interned by their string `name`, so two `ConfigKey` instances
 * built with the same `name` resolve to the same DataStore entry
 * regardless of which call site created them.
 */
data class ConfigKey<T : Any>(
    val name: String,
    val default: T? = null,
    val schema: ConfigSchema<T>? = null,
) {
    init {
        require(name.isNotBlank()) { "ConfigKey.name must not be blank" }
    }
}

/**
 * Built-in key factories for the few well-known entries the bootstrap
 * sequence needs. New keys are added here so call sites never have to
 * spell out the string name inline.
 */
object BuiltInConfigKeys {
    /** Stable per-install node identity. Generated and persisted on
     *  first boot, never re-minted. */
    fun nodeId(): ConfigKey<String> = ConfigKey(
        name = "node.id",
        default = null,
    )

    /** Human-readable device label shown in the cluster UI. */
    fun deviceLabel(): ConfigKey<String> = ConfigKey(
        name = "device.label",
        default = null,
    )

    /** Cluster display name (may differ from per-device label). */
    fun clusterName(): ConfigKey<String> = ConfigKey(
        name = "cluster.name",
        default = null,
    )
}
