package com.meshlit.core.common

/**
 * The three role classes a node can serve. Per BUILD_GUIDE §0 principle 2
 * these are *advisory* — a user can override a node's role at any time
 * via Settings. The router warns but does not block role mismatches.
 */
enum class ClusterRole(val tag: String) {
    /** Runs LLM inference. Heaviest: needs a GGUF model, RAM, NPU/GPU is a plus. */
    BRAIN("brain"),

    /** Runs MCP tool handlers: filesystem, web search, etc. */
    TOOL("tool"),

    /** Observes cluster health: thermal, battery, heartbeat, logs. */
    MONITOR("monitor");

    companion object {
        fun fromTag(tag: String): ClusterRole? =
            entries.firstOrNull { it.tag == tag }
    }
}
