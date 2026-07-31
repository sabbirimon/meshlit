package com.meshlit.core.common

import java.util.UUID

/**
 * Identifier for a node in the cluster. Stable per device across sessions
 * (derived from a key in the Android keystore), so the same phone
 * keeps the same id even if the user uninstalls and reinstalls Meshlit.
 *
 * Format: a UUIDv4 string. Trust tokens and policy objects are keyed by
 * this id.
 */
@JvmInline
value class NodeId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Generate a fresh id — used once at first launch and stored. */
        fun fresh(): NodeId = NodeId(UUID.randomUUID().toString())
    }
}
