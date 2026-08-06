package com.meshlit.core.cloudmcp.agent

import kotlinx.serialization.Serializable

/**
 * On-device capability that an autonomous Meshlit agent can request.
 *
 * Each capability maps to:
 *  - An Android runtime permission (granted by the user via the
 *    system prompt; we cannot bypass this).
 *  - A user-facing master toggle in Settings → Cloud → Agent
 *    capabilities (default off — zero-trust posture).
 *  - One or more `agent_*` MCP tools that surface to the LLM only
 *    when the capability is enabled + permission granted.
 *  - A per-action confirmation dialog. Even with the master toggle
 *    on, every individual tool call requires explicit user consent
 *    unless the user has explicitly allowlisted the action's target
 *    (e.g. "always allow SMS to +15551234567").
 *
 * **Why per-capability + per-action:**
 *  A single "AI can do anything" switch is unsafe. Different
 *  capabilities have different blast radii — `agent_location_get`
 *  leaks coarse position but is reversible; `agent_sms_send` is a
 *  one-way message to a real person; `agent_storage_write` is
 *  potentially destructive. Each capability carries its own
 *  permission, its own allowlist model, and its own risk label so
 *  the user makes an informed choice.
 *
 * **Lifecycle:**
 *  The capability surface lives in `:core-cloud-mcp/agent/`. Tools
 *  are registered into `ToolRegistry` only when (masterToggle == ON
 *  AND runtime permission is granted). Disabling the toggle
 *  immediately removes the tools so the LLM never sees them.
 */
@Serializable
enum class AgentCapability(
    val tag: String,
    val title: String,
    val description: String,
    val riskLabel: Risk,
    /** Android permission required at runtime. `null` = no perm. */
    val permission: String?,
) {
    Camera(
        tag = "camera",
        title = "Camera",
        description = "Capture single photos. Useful for vision tasks, document scan, "
            + "OCR seeding, or letting the agent describe what it sees.",
        riskLabel = Risk.MEDIUM,
        permission = android.Manifest.permission.CAMERA,
    ),
    Microphone(
        tag = "microphone",
        title = "Microphone",
        description = "Capture short audio clips. Useful for STT seeding, "
            + "voice-memo transcription, ambient listening.",
        riskLabel = Risk.MEDIUM,
        permission = android.Manifest.permission.RECORD_AUDIO,
    ),
    Location(
        tag = "location",
        title = "Location",
        description = "Read the device's last-known GPS fix. Useful for "
            + "location-aware queries (\"what's the weather near me?\").",
        riskLabel = Risk.MEDIUM,
        permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
    ),
    DataState(
        tag = "data_state",
        title = "Network state",
        description = "Tell the agent whether the device is on Wi-Fi, cellular, "
            + "or offline. No data leaves the phone — it's a ConnectivityManager "
            + "query.",
        riskLabel = Risk.LOW,
        permission = null,
    ),
    Call(
        tag = "call",
        title = "Calls (dial only)",
        description = "Open the dialer with a pre-filled number. The agent "
            + "NEVER places a call directly — the user still has to hit the "
            + "green button. Use this when the agent needs to start a call "
            + "after confirming the number with the user.",
        riskLabel = Risk.LOW,
        permission = null,
    ),
    Sms(
        tag = "sms",
        title = "SMS",
        description = "Send an SMS to a number on the user's allowlist. "
            + "Recipients must be approved one at a time — there is no "
            + "wildcard allowlist. Default = no recipients.",
        riskLabel = Risk.HIGH,
        permission = android.Manifest.permission.SEND_SMS,
    ),
    Storage(
        tag = "storage",
        title = "Storage",
        description = "Read and write files inside a directory tree the user "
            + "grants via the system file picker. The agent can never read "
            + "outside the granted tree.",
        riskLabel = Risk.HIGH,
        permission = null,
    );

    /** Per-capability risk label. Drives the confirmation dialog copy. */
    enum class Risk { LOW, MEDIUM, HIGH }

    companion object {
        fun fromTag(tag: String): AgentCapability =
            entries.firstOrNull { it.tag == tag }
                ?: error("unknown capability: $tag")
    }
}