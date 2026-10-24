package com.meshlit

import com.meshlit.core.common.control.ManagedFeature

/** Compile-time channel policy. Core qualification is recorded separately from packaging. */
object BuildProfile {
    val coreCandidate = BuildConfig.CORE_CANDIDATE
    val name = if (coreCandidate) "Core candidate" else "Experimental"
    val features = if (coreCandidate) setOf(ManagedFeature.INFERENCE, ManagedFeature.MODEL_TRANSFERS, ManagedFeature.FILES)
        else ManagedFeature.entries.toSet()
    private val coreRoutes = setOf("chat", "models", "monitor", "settings", "appearance", "crypto", "files", "ide",
        "search", "personalization", "behavior", "operations", "device", "resources", "logs", "notifications", "legal", "help", "about")
    fun routeAllowed(id: String) = !coreCandidate || id in coreRoutes
    fun requireChat(options: com.meshlit.chat.ChatOptions) {
        check(!coreCandidate || (options.onlineProfileId == null && options.routeId == null && !options.usesLocalTools && options.colibriMode=="OFF" && !options.colibriAgentAllowed)) {
            "Core candidate supports local chat without experimental tools or online routes"
        }
    }
}
