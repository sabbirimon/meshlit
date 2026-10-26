package com.meshlit.workspace

/** Navigation vocabulary only. Each platform supplies its actual capability and observations. */
data class DashboardEntry(val id: String, val title: String, val description: String)

object DashboardDirectory {
    val entries = listOf(
        DashboardEntry("models", "Models", "Choose, import and manage your models"),
        DashboardEntry("router", "Routing", "Choose where a task runs"),
        DashboardEntry("usage", "Usage & costs", "Inspect reported usage and its limits"),
        DashboardEntry("vpn", "VPN & network", "Manage private connections and reachability"),
    )
    val androidBasic = setOf("appearance", "securityprivacy", "notifications", "files", "help", "about")
    val desktopBasic = setOf("appearance", "securityprivacy", "help", "about")
}
