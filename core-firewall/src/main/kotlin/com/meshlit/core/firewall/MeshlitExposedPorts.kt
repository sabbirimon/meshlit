package com.meshlit.core.firewall

/**
 * Catalogue of the surfaces Meshlit exposes on the network. Each
 * port is a self-contained service the user can opt in or out of.
 *
 * Why a catalogue instead of "8080 + whatever":
 *  - The user asked for "more ports" so they can connect a desktop
 *    dashboard, an SSH-style tunnel, a file-sharing session, a
 *    terminal session, and the cluster gateway without having to
 *    reverse-proxy through one monolith.
 *  - Each port can be firewall-gated independently via the
 *    port-layer rules — so opening 8100 (file share) doesn't also
 *    open 8080 (inference) or 8110 (terminal).
 *
 * Wire semantics:
 *  - **MESHLIT_DEFAULT (8080)** — the original SSE inference surface,
 *    `/v1/infer`, `/v1/health`, `/v1/model`, `/v1/capabilities`,
 *    `/v1/manifest`, `/v1/runtimes`, `/v1/handshake`. Always on by
 *    default; never disable this unless you're OK with no remote
 *    inference.
 *  - **MESHLIT_AGENT_BRIDGE (8090)** — the agent control plane:
 *    `/agent/tools/call`, `/agent/tools/list`, OpenAI-compatible
 *    `/v1/chat/completions`. Used by the Agent screen and the cloud
 *    hub. Optional; off by default on phones.
 *  - **MESHLIT_FILE_SHARE (8100)** — `/files/{path}` GET/PUT for
 *    the in-app file manager. Used by the Files screen to read /
 *    write across phones on the LAN. Optional; off by default
 *    because file surfaces are the highest-risk targets.
 *  - **MESHLIT_TERMINAL (8110)** — `/terminal/{id}` WebSocket for
 *    the in-app ghostty terminal. Off by default; gated behind
 *    `feature.cloud.android_automation` style permission.
 *  - **MESHLIT_CLUSTER_GATEWAY (8120)** — `/cluster/peers`,
 *    `/cluster/route`, `/cluster/health`. Used by the cluster
 *    dispatch + forwarding stack. Off by default; only useful when
 *    cluster mode is on.
 *
 * Defaults:
 *  - MESHLIT_DEFAULT is always on (8080).
 *  - Everything else starts OFF. The user opts in per port in
 *    Settings → Network → Exposed ports.
 */
enum class MeshlitExposedPort(
    val tag: String,
    val port: Int,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean,
) {
    MESHLIT_DEFAULT(
        tag = "meshlit-default",
        port = 8080,
        title = "Inference (SSE)",
        description = "Remote inference + health + cluster manifest",
        defaultEnabled = true,
    ),
    MESHLIT_AGENT_BRIDGE(
        tag = "meshlit-agent-bridge",
        port = 8090,
        title = "Agent bridge",
        description = "Tool calling + chat completions for the Agent screen",
        defaultEnabled = false,
    ),
    MESHLIT_FILE_SHARE(
        tag = "meshlit-file-share",
        port = 8100,
        title = "File share",
        description = "Read / write files in the app sandbox across phones",
        defaultEnabled = false,
    ),
    MESHLIT_TERMINAL(
        tag = "meshlit-terminal",
        port = 8110,
        title = "Terminal",
        description = "WebSocket session for the in-app terminal",
        defaultEnabled = false,
    ),
    MESHLIT_CLUSTER_GATEWAY(
        tag = "meshlit-cluster-gateway",
        port = 8120,
        title = "Cluster gateway",
        description = "Peer routing for cluster mode (peers + manifests)",
        defaultEnabled = false,
    );

    companion object {
        /** All known proxies. UI iterates this for the toggles list. */
        val All: List<MeshlitExposedPort> = entries.toList()

        /** Resolve a port number back to its catalogue entry, or
         *  null when the user typed something we don't ship. */
        fun fromPort(p: Int): MeshlitExposedPort? =
            entries.firstOrNull { it.port == p }

        /** Resolve by tag string (used in DataStore JSON). */
        fun fromTag(tag: String): MeshlitExposedPort? =
            entries.firstOrNull { it.tag == tag }
    }
}