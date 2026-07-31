package com.meshlit.ui.screens.settings

/**
 * Searchable index of every leaf-level setting in the Settings
 * panel. The hub screen uses [search] to filter by free-text query
 * and deep-links to the parent [SettingsCategory] on tap.
 *
 * Phase 1 ships the index — every entry is labelled, described, and
 * grouped. Phase 2 will add a `tag` so in-app action buttons can be
 * rendered inline (e.g. "Toggle" right in the search result).
 *
 * Adding a new setting:
 *  1. Add an entry to [entries].
 *  2. Pick the right [SettingsCategory].
 *  3. Add keywords a user would search for (lowercase, no spaces).
 * Search is case-insensitive substring match across label, description,
 * and keywords.
 */
object SettingsSearchIndex {

    data class Match(
        val category: SettingsCategory,
        val label: String,
        val description: String,
        /** Stable id so Phase 2 can route the user straight to the
         *  specific toggle (e.g. "high_contrast") rather than just the
         *  category screen. */
        val tag: String,
    )

    private val entries: List<Match> = listOf(
        // ---- DEVICE ----
        Match(
            SettingsCategory.DEVICE,
            label = "Device name",
            description = "How this device appears to the rest of the cluster",
            tag = "device.name",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Role assignment",
            description = "Brain (compute), Tool (MCP), Monitor (relay), or Auto-suggest",
            tag = "device.role",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Trust tier",
            description = "Local trusted, local sandboxed, or WAN — controls what peers may do on this device",
            tag = "device.trust_tier",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Chipset override",
            description = "Manually pick a chipset if auto-detect was wrong",
            tag = "device.chipset",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "External GPU (eGPU)",
            description = "AMD, NVIDIA, Intel, Moore Threads, Biren, Huawei Ascend, Cambricon, etc.",
            tag = "device.egpu",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "eGPU backend",
            description = "Vulkan, OpenCL, CUDA, ROCm, CANN, MLU — pick which driver to use",
            tag = "device.egpu_backend",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Peripherals",
            description = "USB host devices, hubs, eGPUs, storage",
            tag = "device.peripherals",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Host OS",
            description = "Detected runtime — Android, Linux x86, Waydroid, ChromeOS ARC, Android-x86, Genymotion/Bluestacks, Anbox",
            tag = "device.host_os",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Host ABI",
            description = "arm64-v8a, x86_64, x86, riscv64 — drives SIMD path selection in llama.cpp",
            tag = "device.host_abi",
        ),
        Match(
            SettingsCategory.DEVICE,
            label = "Desktop eGPU backend",
            description = "CUDA / ROCm / oneAPI / Vulkan — picked automatically when running on Linux x86_64",
            tag = "device.desktop_backend",
        ),

        // ---- THEME ----
        Match(
            SettingsCategory.THEME,
            label = "Accent color",
            description = "Meshlit Violet, Cyan, Teal, Sky, Indigo, Rose, Amber, Emerald, Fuchsia, Slate",
            tag = "theme.accent",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Base palette",
            description = "Midnight, Dusk, Dawn, Paper (light), Coffee, Ocean, Forest",
            tag = "theme.palette",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Theme mode",
            description = "Follow system, Always light, Always dark, Auto by time of day",
            tag = "theme.mode",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Font scale",
            description = "Resize all text — 0.85x to 1.5x",
            tag = "theme.font_scale",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Display density",
            description = "Compact, default, comfortable — 0.85x to 1.3x",
            tag = "theme.density",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Animations",
            description = "Disable for battery / accessibility",
            tag = "theme.animations",
        ),
        Match(
            SettingsCategory.THEME,
            label = "High contrast",
            description = "Deeper accent colors, stronger outlines",
            tag = "theme.high_contrast",
        ),
        Match(
            SettingsCategory.THEME,
            label = "Reset theme to defaults",
            description = "Clear all theme and display overrides",
            tag = "theme.reset",
        ),

        // ---- NOTIFICATIONS ----
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Cluster node",
            description = "Persistent notification while this device is hosting inference or MCP tools",
            tag = "notif.fgs",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Inference complete",
            description = "Notify when a prompt you sent has finished generating",
            tag = "notif.inference_complete",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Peer topology",
            description = "Other cluster nodes joining or leaving",
            tag = "notif.peer",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Job failed",
            description = "Inference or training job crashed or hit an error",
            tag = "notif.job_failed",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Brain battery low",
            description = "Primary BRAIN device is about to drop out on battery",
            tag = "notif.brain_battery",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Model imported",
            description = "A new model finished downloading or importing",
            tag = "notif.model_import",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Security alert",
            description = "Jailbreak attempts, signature mismatches, unknown peer auth",
            tag = "notif.security",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Tunnel state",
            description = "Tailscale / WireGuard up, down, reconfigured",
            tag = "notif.tunnel",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Public token used",
            description = "An external caller used one of your bearer tokens",
            tag = "notif.token_used",
        ),
        Match(
            SettingsCategory.NOTIFICATIONS,
            label = "Training milestone",
            description = "Fine-tune step, checkpoint, loss plateau",
            tag = "notif.training",
        ),

        // ---- CLUSTER ----
        Match(
            SettingsCategory.CLUSTER,
            label = "Cluster name",
            description = "Name visible to other peers on the same network",
            tag = "cluster.name",
        ),
        Match(
            SettingsCategory.CLUSTER,
            label = "Discovery",
            description = "NSD / LAN / Wi-Fi Direct / Tailscale / WireGuard / relay",
            tag = "cluster.discovery",
        ),
        Match(
            SettingsCategory.CLUSTER,
            label = "Tailscale",
            description = "Tailscale VPN tunnel for cross-network reachability",
            tag = "cluster.tailscale",
        ),
        Match(
            SettingsCategory.CLUSTER,
            label = "WireGuard",
            description = "WireGuard tunnel — manual config or QR onboarding",
            tag = "cluster.wireguard",
        ),
        Match(
            SettingsCategory.CLUSTER,
            label = "WAN relay",
            description = "Reach cluster over cellular / public Wi-Fi via trusted relay",
            tag = "cluster.wan_relay",
        ),
        Match(
            SettingsCategory.CLUSTER,
            label = "Local firewall",
            description = "Per-port incoming connection policy",
            tag = "cluster.firewall",
        ),

        // ---- MODELS ----
        Match(
            SettingsCategory.MODELS,
            label = "Default quantization",
            description = "Q4_K_M, Q5_K_M, Q8_0, F16 — pick the default for new downloads",
            tag = "models.default_quant",
        ),
        Match(
            SettingsCategory.MODELS,
            label = "Auto-download",
            description = "Allow Meshlit to fetch missing models from a peer automatically",
            tag = "models.auto_download",
        ),
        Match(
            SettingsCategory.MODELS,
            label = "Model storage path",
            description = "Where downloaded GGUF files live on disk",
            tag = "models.path",
        ),
        Match(
            SettingsCategory.MODELS,
            label = "HuggingFace mirror",
            description = "Endpoint used when importing from HuggingFace",
            tag = "models.hf_mirror",
        ),

        // ---- ACCOUNT ----
        Match(
            SettingsCategory.ACCOUNT,
            label = "Tier",
            description = "Spark (free), Mesh (hosting rewards), Mind (early access)",
            tag = "account.tier",
        ),
        Match(
            SettingsCategory.ACCOUNT,
            label = "Public bearer tokens",
            description = "OpenAI-compatible tokens for external callers",
            tag = "account.tokens",
        ),
        Match(
            SettingsCategory.ACCOUNT,
            label = "Audit trail",
            description = "Who dispatched work to your cluster and when",
            tag = "account.audit",
        ),

        // ---- PERFORMANCE ----
        Match(
            SettingsCategory.PERFORMANCE,
            label = "CPU threads",
            description = "Threads allocated to llama.cpp — default = physical cores - 2",
            tag = "perf.cpu_threads",
        ),
        Match(
            SettingsCategory.PERFORMANCE,
            label = "GPU layers",
            description = "How many transformer layers to offload to GPU / eGPU",
            tag = "perf.gpu_layers",
        ),
        Match(
            SettingsCategory.PERFORMANCE,
            label = "Batch size",
            description = "Tokens processed per inference batch",
            tag = "perf.batch_size",
        ),
        Match(
            SettingsCategory.PERFORMANCE,
            label = "Thermal policy",
            description = "Throttle, pause, or step-down on temperature threshold",
            tag = "perf.thermal",
        ),
        Match(
            SettingsCategory.PERFORMANCE,
            label = "Memory ceiling",
            description = "Hard cap on resident memory usage for inference",
            tag = "perf.mem_ceiling",
        ),

        // ---- PRIVACY ----
        Match(
            SettingsCategory.PRIVACY,
            label = "Trust tiers",
            description = "LOCAL_TRUSTED, LOCAL_SANDBOXED, WAN — peer access policy",
            tag = "privacy.trust",
        ),
        Match(
            SettingsCategory.PRIVACY,
            label = "Key rotation",
            description = "How often to rotate per-peer shared keys",
            tag = "privacy.key_rotation",
        ),
        Match(
            SettingsCategory.PRIVACY,
            label = "Telemetry",
            description = "Anonymous usage metrics shared with Meshlit foundation",
            tag = "privacy.telemetry",
        ),
        Match(
            SettingsCategory.PRIVACY,
            label = "Prompt guardrails",
            description = "Jailbreak filters, profanity, PII detection",
            tag = "privacy.guardrails",
        ),

        // ---- ABOUT ----
        Match(
            SettingsCategory.ABOUT,
            label = "Version",
            description = "App version, build number, git SHA",
            tag = "about.version",
        ),
        Match(
            SettingsCategory.ABOUT,
            label = "Open-source licenses",
            description = "Apache 2.0, MIT, BSD components used by Meshlit",
            tag = "about.licenses",
        ),
        Match(
            SettingsCategory.ABOUT,
            label = "Third-party",
            description = "llama.cpp, ggml, MCP SDK, llama.cpp-server, ONNX runtime",
            tag = "about.thirdparty",
        ),
        Match(
            SettingsCategory.ABOUT,
            label = "Acknowledgements",
            description = "HuggingFace, Ollama, OpenAI, Anthropic, Mistral, Meta",
            tag = "about.acknowledgements",
        ),

        // ---- DEVELOPER ----
        Match(
            SettingsCategory.DEVELOPER,
            label = "Verbose logs",
            description = "Trace / debug level for all subsystems",
            tag = "dev.verbose_logs",
        ),
        Match(
            SettingsCategory.DEVELOPER,
            label = "Sample rate",
            description = "How often to capture metrics / traces",
            tag = "dev.sample_rate",
        ),
        Match(
            SettingsCategory.DEVELOPER,
            label = "Debug overlay",
            description = "Show FPS, memory, and routing decisions on screen",
            tag = "dev.overlay",
        ),
        Match(
            SettingsCategory.DEVELOPER,
            label = "Mock cluster",
            description = "Spin up a virtual cluster of 1-5 nodes for testing",
            tag = "dev.mock_cluster",
        ),
        Match(
            SettingsCategory.DEVELOPER,
            label = "Force kill inference",
            description = "Immediately stop the foreground service — debug only",
            tag = "dev.force_kill",
        ),
    )

    /**
     * Returns the entries whose label, description, or any keyword
     * substring contains [query] (case-insensitive). Empty query
     * returns all entries. Result order is stable — the same as
     * [entries] — so the user gets consistent results across keystrokes.
     */
    fun search(query: String): List<Match> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return entries
        return entries.filter { m ->
            m.label.lowercase().contains(q) ||
                m.description.lowercase().contains(q) ||
                m.tag.lowercase().contains(q)
        }
    }

    /** All indexed settings — used by the hub screen to render an
     *  "every setting" view, and to seed the Phase 2 in-app actions. */
    fun all(): List<Match> = entries
}
