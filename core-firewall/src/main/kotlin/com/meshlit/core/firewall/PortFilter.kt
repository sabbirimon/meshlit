package com.meshlit.core.firewall

import com.meshlit.core.trust.TrustTier

/**
 * Endpoint-aware firewall wrapper. Two responsibilities:
 *
 *  1. Map `LOCAL_SANDBOXED` callers to [Decision.QUARANTINE] when
 *     the endpoint is "read-only" (e.g. `/v1/capabilities`, `/v1/health`,
 *     `/v1/manifest/{modelId}`) — they get a 200, but never write.
 *  2. Forward everything else to the wrapped [policy] unchanged.
 *
 * Endpoints are identified by the path prefix (the first URL segment
 * after `v1/`) so the HTTP layer can call [classifyEndpoint] once per
 * request.
 */
class PortFilter(
    private val policy: FirewallPolicy,
    private val readOnlyEndpoints: Set<String> = DEFAULT_READ_ONLY,
) {

    fun classifyEndpoint(path: String): EndpointKind {
        val segment = path.trimStart('/').removePrefix("v1/").substringBefore('/')
        return if (segment in readOnlyEndpoints) EndpointKind.READ_ONLY else EndpointKind.WRITE
    }

    fun decide(
        remoteAddr: String,
        remoteNodeId: String?,
        remoteTier: TrustTier?,
        endpointPath: String,
    ): Decision {
        val base = policy.decide(remoteAddr, remoteNodeId, remoteTier)
        if (base == Decision.DENY) return Decision.DENY
        if (remoteTier == TrustTier.LOCAL_SANDBOXED &&
            classifyEndpoint(endpointPath) == EndpointKind.WRITE
        ) {
            return Decision.QUARANTINE
        }
        return base
    }

    companion object {
        /**
         * Endpoints that are safe for an unpaired / sandboxed peer to
         * call. The HTTP layer is still responsible for stripping
         * write-side effects from these endpoints when the caller is
         * quarantined — the firewall only decides the *initial* gate.
         */
        val DEFAULT_READ_ONLY: Set<String> = setOf(
            "capabilities",  // /v1/capabilities
            "health",        // /v1/health
            "model",         // /v1/model
            "manifest",      // /v1/manifest/{modelId}
            "runtimes",      // /v1/runtimes
            "handshake",     // /v1/handshake
        )
    }
}

enum class EndpointKind { READ_ONLY, WRITE }
