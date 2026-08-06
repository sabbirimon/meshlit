package com.meshlit.core.observability

/**
 * Log source taxonomy. Used by [LogBuffer] and the in-app
 * `LogScreen` to group entries by *where* they came from,
 * independent of the SLF4J tag which is more fine-grained.
 *
 * The [fromTag] rule is intentionally simple — substring match
 * against a stable prefix per source. Tag conventions in this
 * project already follow `module.submodule.event` (see
 * `core-common`'s MeshlitLogger KDoc), so the prefix match is
 * reliable.
 */
enum class LogSource {
    APP,
    NETWORK,
    INFERENCE,
    AGENT,
    SYSTEM;

    companion object {
        /** The "no filter" value used by the LogScreen dropdown. */
        val ALL_FILTER = "All"

        /**
         * Derive a [LogSource] from a logger tag. Falls back to [APP]
         * when no rule matches (most user-visible UI state lives
         * in the `:app` module today).
         *
         * Tag → source rules (substring match, case-sensitive):
         *  - contains `Inference` → [INFERENCE]
         *  - contains `Router`, `PeerHealth`, `Tunnel`, `Discovery`,
         *    `Firewall`, `Orchestrat`, `MiniRouter` → [NETWORK]
         *  - contains `Agent`, `Mcp`, `McpServer` → [AGENT]
         *  - starts with `core.` or contains `core` → [SYSTEM]
         *  - everything else → [APP]
         */
        fun fromTag(tag: String): LogSource {
            if (tag.isEmpty()) return APP
            if (tag.contains("Inference", ignoreCase = false)) return INFERENCE
            if (tag.contains("Router", ignoreCase = false) ||
                tag.contains("PeerHealth", ignoreCase = false) ||
                tag.contains("Tunnel", ignoreCase = false) ||
                tag.contains("Discovery", ignoreCase = false) ||
                tag.contains("Firewall", ignoreCase = false) ||
                tag.contains("Orchestrat", ignoreCase = false)
            ) {
                return NETWORK
            }
            if (tag.contains("Agent", ignoreCase = false) ||
                tag.contains("Mcp", ignoreCase = false)
            ) {
                return AGENT
            }
            if (tag.startsWith("core.") || tag.startsWith("core-")) return SYSTEM
            return APP
        }

        /** Stable display label used by the LogScreen dropdown. */
        fun label(source: LogSource): String = when (source) {
            APP -> "App"
            NETWORK -> "Network"
            INFERENCE -> "Inference"
            AGENT -> "Agent"
            SYSTEM -> "System"
        }
    }
}
