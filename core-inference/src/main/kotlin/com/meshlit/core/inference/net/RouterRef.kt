package com.meshlit.core.inference.net

/**
 * Minimal routing interface the embedded [InferenceHttpServer] calls
 * before serving locally. The interface lives in `:core-inference` so
 * the server module doesn't have to depend on `:app` (the `:app`
 * module owns the router implementation, the peer registry, and the
 * DataStore wiring).
 *
 * The server calls [decideFor] once per `POST /v1/infer`. The
 * returned [RouterDecision] tells the server whether to serve
 * locally or delegate to a peer via the [Forwarder] interface.
 *
 * Why an interface and not a concrete class:
 *  - Lets the server be unit-tested with a stub router.
 *  - Keeps `:core-inference` free of `:app`-only concerns (DataStore,
 *    settings UI, BuildConfig).
 *  - Phase 2 replaces the `:app` MiniRouter with a richer
 *    orchestrator-side router; the contract here won't change.
 */
fun interface RouterRef {
    suspend fun decideFor(
        request: InferRequest,
        hints: RequestHints?,
    ): RouterDecision
}

/**
 * Capability hints parsed from the `X-Meshlit-Hints` request header.
 * The header format is `key=value,key=value`, e.g.
 * `role=brain,gpu=true`. Missing or malformed headers fall back to
 * the defaults.
 *
 * Lives next to the router interface (not in `:app`) because both
 * sides — the server that parses the header and the `:app` client
 * that emits it — need to agree on the shape.
 */
data class RequestHints(
    val role: String = "brain",
    val needsGpu: Boolean = false,
) {
    /**
     * Serialize to the wire header value.
     */
    fun toHeaderValue(): String = buildString {
        append("role=").append(role)
        if (needsGpu) append(",gpu=true")
    }

    companion object {
        /**
         * Parse `role=brain,gpu=true` into a [RequestHints]. Returns
         * the default when [raw] is null or blank.
         */
        fun parse(raw: String?): RequestHints {
            if (raw.isNullOrBlank()) return RequestHints()
            var role: String? = null
            var needsGpu = false
            raw.split(',').forEach { kv ->
                val (k, v) = kv.substringBefore('=').trim() to kv.substringAfter('=', "").trim()
                when (k.lowercase()) {
                    "role" -> role = v.ifBlank { role }
                    "gpu" -> needsGpu = v.equals("true", ignoreCase = true) ||
                        v == "1" || v.equals("yes", ignoreCase = true)
                }
            }
            return RequestHints(
                role = role ?: "brain",
                needsGpu = needsGpu,
            )
        }
    }
}

/**
 * Output of [RouterRef.decideFor]. The server honors this:
 *  - [Where.LOCAL] → run on the local coordinator.
 *  - [Where.FORWARD] → open a remote client to [peerBaseUrl] via the
 *    injected [Forwarder] and pipe the peer's SSE stream back to the
 *    original caller.
 */
data class RouterDecision(
    val where: Where,
    val peerBaseUrl: String? = null,
    val reason: String = "",
) {
    enum class Where { LOCAL, FORWARD }

    companion object {
        fun local(reason: String = "local-capable"): RouterDecision =
            RouterDecision(Where.LOCAL, reason = reason)

        fun forward(peerBaseUrl: String, reason: String): RouterDecision =
            RouterDecision(Where.FORWARD, peerBaseUrl = peerBaseUrl, reason = reason)
    }
}

/**
 * Proxy used by the server when [RouterDecision.where] is FORWARD.
 * Lives in `:core-inference` so the server module compiles without
 * `:app`. The `:app` module supplies the real implementation
 * (ForwardingProxy) — that one knows how to open a Ktor HTTP client
 * and pipe SSE back through the server's outgoing channel.
 */
fun interface Forwarder {
    /**
     * Forward [request] to [peerBaseUrl] and stream each token event
     * back to the caller via [onToken]; final outcome via [onDone].
     * Implementations must catch network errors and return a
     * `Result.failure(...)` so the server can fall back to local.
     */
    suspend fun forwardAndStream(
        peerBaseUrl: String,
        request: InferRequest,
        hints: RequestHints?,
        onToken: suspend (InferTokenEvent) -> Unit,
        onDone: suspend (InferDoneEvent) -> Unit,
        onError: suspend (InferErrorEvent) -> Unit,
    ): Result<Unit>
}