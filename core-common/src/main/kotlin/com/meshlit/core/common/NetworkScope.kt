package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * Where the local app is allowed to reach out for inference.
 *
 * The user can flip between these scopes without restarting the app;
 * the coordinator picks the next available backend accordingly. The
 * scope is a *filter*, not a destination — the actual address comes
 * from a [RemoteEndpoint] when [CUSTOM] is selected, otherwise the
 * scope implies a default routing strategy.
 *
 *  - [LOCAL]      — only phones advertising on this LAN / mDNS. The
 *                   bundled engine runs here if a model fits on the
 *                   device, otherwise the request fails fast.
 *  - [INTERNET]   — public OpenAI-compatible endpoints (cloud LLM
 *                   APIs). Requires an HTTPS base URL and an API key
 *                   stored in DataStore as a secret reference (never
 *                   committed to the APK).
 *  - [VPN]        — addresses reachable only over a WireGuard /
 *                   Tailscale mesh. Used when the user wants their
 *                   private GPU box / home server without exposing it
 *                   to the public internet.
 *  - [GROUP]      — a named "selective group" — only peers that the
 *                   user has explicitly approved (via QR pairing or
 *                   manual URL paste) participate in routing. This is
 *                   the privacy-preserving default.
 *  - [CUSTOM]     — fully manual: user pastes one or more URLs, picks
 *                   one as "active". Useful for CDN / FTP / private
 *                   CDN-fronted endpoints where the protocol isn't
 *                   standard HTTPS.
 *
 * The enum is `Serializable` so it can travel on the wire when peers
 * negotiate. Default is [GROUP] so first-run users get a
 * privacy-preserving configuration without doing anything.
 */
@Serializable
enum class NetworkScope {
    LOCAL,
    INTERNET,
    VPN,
    GROUP,
    CUSTOM;

    val displayLabel: String
        get() = when (this) {
            LOCAL -> "Local only"
            INTERNET -> "Internet"
            VPN -> "VPN / Tailscale"
            GROUP -> "Selective group"
            CUSTOM -> "Custom endpoint"
        }

    val shortLabel: String
        get() = when (this) {
            LOCAL -> "Local"
            INTERNET -> "Internet"
            VPN -> "VPN"
            GROUP -> "Group"
            CUSTOM -> "Custom"
        }

    val isPublic: Boolean
        get() = this == INTERNET

    companion object {
        val Default: NetworkScope = GROUP
    }
}

/**
 * A remote inference endpoint the user has explicitly approved.
 *
 * Stored as JSON inside DataStore (one [RemoteEndpoint] per line, or
 * a serialized list). The shape is intentionally minimal: we just
 * need enough to construct an [okhttp3.Request] against `/v1/infer`.
 *
 * - [name]    — display label shown in the Devices tab and the
 *               network-scope chip.
 * - [baseUrl] — base URL; must be HTTPS unless [allowInsecure] is
 *               true (LITE / dev / private-network use cases).
 * - [apiKey]  — optional bearer token. Empty for meshlit-to-meshlit
 *               connections that use the public-key handshake.
 * - [protocol] — wire protocol. Currently always `meshlit-sse`
 *               (matches the existing [com.meshlit.core.inference.net]
 *               SSE server). `openai-compatible` is reserved for the
 *               INTERNET scope where Meshlit talks to OpenAI / vLLM
 *               / Ollama / etc. via the OpenAI Chat Completions API.
 *               `raw-ftp` and `raw-cdn` are reserved for the CUSTOM
 *               scope where the user wants to fetch a model file
 *               directly without going through any inference server.
 * - [lastSeenMs] — last successful `/v1/health` round-trip; used to
 *                 sort the endpoint picker so reachable endpoints
 *                 float to the top.
 * - [trusted] — true once the user has approved this endpoint
 *               (via QR code scan, manual paste, or one-tap
 *               "trust this device"). Untrusted endpoints show up
 *               dimmed in the picker.
 */
@Serializable
data class RemoteEndpoint(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val protocol: EndpointProtocol = EndpointProtocol.MESHLIT_SSE,
    val allowInsecure: Boolean = false,
    val trusted: Boolean = false,
    val lastSeenMs: Long = 0L,
    val addedAtMs: Long = 0L,
    val notes: String = "",
) {
    fun isReachable(): Boolean = lastSeenMs > 0L
}

@Serializable
enum class EndpointProtocol {
    /** Meshlit-to-Meshlit SSE stream over `/v1/infer`. */
    MESHLIT_SSE,

    /** OpenAI-compatible Chat Completions (`/v1/chat/completions`). */
    OPENAI_COMPATIBLE,

    /** Raw FTP fetch — used to pull a GGUF straight from an FTP host
     *  in the CUSTOM scope. No inference happens remotely. */
    RAW_FTP,

    /** Raw CDN / HTTP(S) download of a GGUF model file. No
     *  inference happens remotely. */
    RAW_CDN,

    /** Custom user-defined protocol (e.g. WebSocket, gRPC). The
     *  user wires the adapter in settings. */
    CUSTOM;
}
