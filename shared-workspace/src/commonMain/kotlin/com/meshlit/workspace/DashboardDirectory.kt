package com.meshlit.workspace

/** Navigation vocabulary only. Each platform supplies its actual capability and observations. */
data class DashboardEntry(val id: String, val title: String, val description: String)
data class ManagementSection(val id: String, val title: String, val destinations: List<String>)

object DashboardDirectory {
    /** Shared category vocabulary; clients only expose destinations they actually implement. */
    val management = listOf(
        ManagementSection("models", "Models & compute", listOf("models", "router", "providers", "hyperl", "acceleration")),
        ManagementSection("devices", "Devices & clusters", listOf("device-dashboard", "sharding", "radar", "external")),
        ManagementSection("network", "Network & connections", listOf("network", "vpn", "external-services", "ssh")),
        ManagementSection("health", "Health & resources", listOf("resources", "monitor", "power", "operations")),
        ManagementSection("security", "Credentials & security", listOf("cloud", "securityprivacy", "identity-recovery", "audit")),
        ManagementSection("storage", "Usage & storage", listOf("usage", "files", "rag", "recovery")),
    )
    val entries = listOf(
        DashboardEntry("models", "Models", "Choose, import and manage your models"),
        DashboardEntry("router", "Routing", "Choose where a task runs"),
        DashboardEntry("usage", "Usage & costs", "Inspect reported usage and its limits"),
        DashboardEntry("vpn", "VPN & network", "Manage private connections and reachability"),
    )
    val androidBasic = setOf("appearance", "securityprivacy", "notifications", "files", "help", "about")
    val desktopBasic = setOf("appearance", "securityprivacy", "help", "about")
}
