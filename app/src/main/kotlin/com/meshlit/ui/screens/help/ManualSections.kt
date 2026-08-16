package com.meshlit.ui.screens.help

/**
 * Static catalogue of every "section" the user manual exposes.
 * Each section is a typed data class so the [UserManualScreen]
 * LazyColumn can render them uniformly without reflection.
 *
 * Sections follow a single shape:
 *   - title      — display name in the section list
 *   - intent     — one-line purpose
 *   - useCase    — when the user would want this
 *   - configSteps — bullet list of "how to configure"
 *   - troubleshooting — bullet list of common failure modes + remedy
 *
 * Steps and trouble entries can carry an [onClick] that the screen
 * executes (e.g. "Open Models" → navigate to the Models screen).
 * Pure prose entries leave it null.
 *
 * Adding a new section is a single line in [all] — every other
 * surface (Tour screen, Settings → About rows, Feedback body
 * pre-fill) reads from this catalogue.
 */
data class ConfigStep(
    val title: String,
    val body: String,
    val ctaLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

data class TroubleEntry(
    val title: String,
    val body: String,
)

sealed class ManualSection {
    abstract val title: String
    abstract val intent: String
    abstract val useCase: String
    abstract val configSteps: List<ConfigStep>
    abstract val troubleshooting: List<TroubleEntry>

    data object Devices : ManualSection() {
        override val title = "Devices"
        override val intent = "Find nearby Meshlit nodes and pair with them over Wi-Fi / Bluetooth / QR."
        override val useCase = "You want to run inference on a more powerful peer (laptop, another phone) and have this device act as a thin client."
        override val configSteps = listOf(
            ConfigStep("Make sure both devices are on the same network", "Or pair with QR for cross-network pairing."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("No peers appear", "Confirm Wi-Fi is on and that location is granted (Android 13+ requires it for nearby-wifi scans)."),
            TroubleEntry("QR scanner won't open", "Update Play Services — the scanner is bundled in the Play Services module, not the app APK."),
        )
    }

    data object Jobs : ManualSection() {
        override val title = "Jobs"
        override val intent = "Run an inference job against the active model on a Local / Remote / Cluster target."
        override val useCase = "Default screen — open it whenever you want to prompt the model and read the answer."
        override val configSteps = listOf(
            ConfigStep("Pick a dispatch mode", "Top-bar tabs: Local (this device), Remote (a paired peer), Cluster (first trusted peer via ClusterDispatch)."),
            ConfigStep("Pick a model", "The compact model picker in the top bar filters by what's already downloaded + the active dispatch's capabilities."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Job won't start", "Tap the identity badge — if the model column says 'missing', open Models and download."),
            TroubleEntry("Stop button is grey", "It isn't — long-press to cancel a streaming response."),
        )
    }

    data object Voice : ManualSection() {
        override val title = "Voice"
        override val intent = "Speak a prompt, hear a spoken reply, save / import / export the audio."
        override val useCase = "Hands-free interaction with the model."
        override val configSteps = listOf(
            ConfigStep("Grant microphone permission", "Required once on first use; revocable from Settings → Apps → Meshlit."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("No audio output", "Check that a media volume is above zero — Voice uses STREAM_MUSIC."),
        )
    }

    data object Agent : ManualSection() {
        override val title = "Agent"
        override val intent = "Multi-turn agentic loop that can call tools (MCP) and on-device capabilities (camera / mic / GPS / SMS / storage)."
        override val useCase = "Tasks that need the model to act — read a file, send a message, fetch GPS, capture a photo."
        override val configSteps = listOf(
            ConfigStep("Enable the capabilities you want", "Settings → Cloud → Agent capabilities."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Tool call denied", "The dispatcher always requires a per-action confirmation unless the target is on your allowlist."),
        )
    }

    data object Models : ManualSection() {
        override val title = "Models"
        override val intent = "Browse, download, and switch the on-device language model."
        override val useCase = "Pick a smaller model for speed or a larger one for quality; pick an NPU-capable one if your device has an NPU."
        override val configSteps = listOf(
            ConfigStep("Pick an engine", "Tags show NPU / llama.cpp / Easy / Fast / Balanced / Heavy."),
            ConfigStep("Wait for the download", "Speed, ETA, % complete, MB downloaded, MB total render under the row."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Download stuck at 0%", "The CDN is likely down — cancel and retry. The link will resume from the last byte range."),
        )
    }

    data object Structured : ManualSection() {
        override val title = "Structured"
        override val intent = "Prompt the model with a JSON schema and read structured output back."
        override val useCase = "Extracting entities, filling forms, building typed records."
        override val configSteps = listOf(
            ConfigStep("Pick a schema", "Pre-set schemas (calendar event, contact, todo) or paste your own JSON Schema."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Model returns prose instead of JSON", "Lower temperature to 0.0 and switch to a 'balanced' or 'heavy' preset."),
        )
    }

    data object Vision : ManualSection() {
        override val title = "Vision"
        override val intent = "Send an image to the multimodal model and ask a question about it."
        override val useCase = "OCR, scene description, identifying objects."
        override val configSteps = listOf(
            ConfigStep("Pick or capture an image", "Photo picker or in-app capture."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Image too large", "Vision auto-resizes; if it still fails, the model lacks vision — switch to a multimodal preset."),
        )
    }

    data object Catalog : ManualSection() {
        override val title = "Catalog"
        override val intent = "Browse the full RunAnywhere model registry."
        override val useCase = "Discover new models; pull info / status / delete affordances on each row."
        override val configSteps = listOf(
            ConfigStep("Pull to refresh", "Resyncs the catalog from the upstream registry."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Catalog empty", "Check internet; the catalog is served from the RunAnywhere CDN."),
        )
    }

    data object Advanced : ManualSection() {
        override val title = "Advanced"
        override val intent = "Power-user surface — runtime switching, engine config, experimental flags."
        override val useCase = "When you want to change the inference engine or try a new runtime."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = emptyList<TroubleEntry>()
    }

    data object Files : ManualSection() {
        override val title = "Files"
        override val intent = "Browse internal storage + SAF volumes; copy / move / delete / share."
        override val useCase = "Manage the model library, log exports, screenshots, anything on the SAF volume the user granted."
        override val configSteps = listOf(
            ConfigStep("Grant directory access", "Pick a SAF tree once; the persistable URI is stored."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Empty directory", "Make sure the SAF tree was granted at the root, not a sub-folder."),
        )
    }

    data object Sessions : ManualSection() {
        override val title = "Sessions"
        override val intent = "Persistent shell sessions over the local sandbox."
        override val useCase = "When you want to run a real terminal session inside Meshlit."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = listOf(
            TroubleEntry("Session won't open", "Confirm the foreground service is running — pull down the notification shade."),
        )
    }

    data object Cluster : ManualSection() {
        override val title = "Cluster"
        override val intent = "Aggregate metrics across every paired Meshlit node."
        override val useCase = "When you want to see queue depth, success rate, tokens/sec across the cluster."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = emptyList<TroubleEntry>()
    }

    data object Network : ManualSection() {
        override val title = "Network Monitor"
        override val intent = "Inspect Meshlit's own HTTP traffic + optionally capture device-wide packets."
        override val useCase = "Debug Remote / Cluster inference calls; trace slowdowns; export .pcap files."
        override val configSteps = listOf(
            ConfigStep("Enable tracing", "Settings → Tracing → mode = Local or Otel."),
            ConfigStep("Opt-in to device capture", "Network monitor → Start device capture — the system prompts for the VPN profile consent."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Capture file is empty", "TLS bodies are not captured without a user-installed MITM CA; only handshake metadata is recorded by default."),
            TroubleEntry("Can't open .pcap in Wireshark", "Meshlit writes standard libpcap — open it directly in Wireshark desktop or in PCAPdroid."),
        )
    }

    data object Users : ManualSection() {
        override val title = "Users"
        override val intent = "Manage users on this node (capability tier + audit log)."
        override val useCase = "Multi-user setups where different profiles have different roles."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = emptyList<TroubleEntry>()
    }

    data object Settings : ManualSection() {
        override val title = "Settings"
        override val intent = "Hub for theme, notifications, cluster, models, account, performance, privacy, developer, about."
        override val useCase = "When you want to change how Meshlit behaves."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = emptyList<TroubleEntry>()
    }

    data object Cloud : ManualSection() {
        override val title = "Cloud"
        override val intent = "Manage cloud providers (AWS / DO / Azure / GCP / Custom) + tool adapters (web search / HTTP / browser / Android automation)."
        override val useCase = "Configure the LLM endpoint, RAG, MCP servers, and the on-device capabilities exposed to the agent loop."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = listOf(
            TroubleEntry("Provider won't save", "Each provider has its own credential store key — paste the API key once; it's encrypted at rest."),
        )
    }

    data object Tracing : ManualSection() {
        override val title = "Tracing"
        override val intent = "Global tracer that records every endpoint, feature, model, and interaction."
        override val useCase = "When the model is slow or the agent loop is misbehaving and you want to know what's happening under the hood."
        override val configSteps = listOf(
            ConfigStep("Pick a mode", "Off (default), Local (log buffer only), or Otel (push to Grafana / Tempo / any OTLP endpoint)."),
            ConfigStep("Set the OTLP endpoint", "Paste the OTLP/gRPC URL + optional headers (Grafana Cloud uses Basic auth)."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Spans aren't appearing in Grafana", "Check the Authorization header — Grafana Cloud uses `<instance_id>:<api_token>` base64-encoded."),
            TroubleEntry("Too many spans", "Bump the sample rate above 1."),
        )
    }

    data object Feedback : ManualSection() {
        override val title = "Send Feedback"
        override val intent = "Open a pre-filled GitHub Issue on the project's repo."
        override val useCase = "Reporting a bug or requesting a feature — automatically attaches logs and the device profile."
        override val configSteps = listOf(
            ConfigStep("Override the repo slug if needed", "Default: meshlit/meshlit-android. Edit in Settings → About → Feedback repo."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Submit fails with no browser", "Install any browser — the URL is a standard https://github.com/.../issues/new?labels=... link."),
        )
    }

    data object AgentCapabilities : ManualSection() {
        override val title = "Agent Capabilities"
        override val intent = "Master toggles for the on-device capabilities (camera, mic, GPS, network state, dialer, SMS, storage) that the agent loop can invoke."
        override val useCase = "When you want the agent to act on your behalf — read a file, send an SMS, capture a photo."
        override val configSteps = listOf(
            ConfigStep("Flip the toggle", "Settings → Cloud → Agent capabilities."),
            ConfigStep("Grant the runtime permission", "Each capability shows its own grant button."),
            ConfigStep("Allowlist targets", "SMS recipients, storage tree URIs. Per-action confirmation still fires for high-risk packages."),
        )
        override val troubleshooting = listOf(
            TroubleEntry("Agent can't dial / SMS / read storage", "The capability is off or the per-target allowlist doesn't include the recipient / tree."),
        )
    }

    data object Sync : ManualSection() {
        override val title = "Sync"
        override val intent = "Re-fetch the RunAnywhere model catalog so Meshlit sees new models + updated tags."
        override val useCase = "Drawer → Sync (or Catalog → pull to refresh). Surfaces a Toast with the count of resynced models."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = listOf(
            TroubleEntry("Sync reports 0 models", "The CDN may be down — retry after a minute."),
        )
    }

    data object Boost : ManualSection() {
        override val title = "Boost"
        override val intent = "Toggle inference boost — raises inference thread priority and prefers the NPU / GPU engine when available."
        override val useCase = "When you want lower-latency responses at the cost of higher battery / thermal load."
        override val configSteps = emptyList<ConfigStep>()
        override val troubleshooting = listOf(
            TroubleEntry("No visible speedup", "Boost is a hint, not a guarantee — on devices without an NPU the only effect is thread priority."),
        )
    }

    companion object {
        /** Display order — used by the manual's LazyColumn + the tour. */
        val all: List<ManualSection> = listOf(
            Devices, Jobs, Voice, Agent, Models, Structured, Vision, Catalog,
            Advanced, Files, Sessions, Cluster, Network, Users, Settings, Cloud,
            Tracing, Feedback, AgentCapabilities, Sync, Boost,
        )
    }
}