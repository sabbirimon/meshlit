package com.meshlit.core.cloudmcp

import kotlinx.serialization.Serializable

/**
 * Per-provider connection configuration. Persisted (sans
 * credential) to Room `cloud_provider_configs`; the credential
 * itself lives in [com.meshlit.core.trust.CloudCredentialStore]
 * (Android Keystore-backed EncryptedSharedPreferences).
 *
 * `baseUrl` is the MCP server endpoint — e.g.
 * `https://mcp.example.com/sse` for AWS, or a user-provided
 * OpenAPI-derived endpoint for the Custom provider.
 *
 * `ragNamespace` lets the agent scope RAG retrievals to one
 * namespace per provider — e.g. AWS docs vs DO docs — so the
 * LLM doesn't see cross-cloud contamination.
 *
 * `openApiSpecUrl` is filled only when the user adds a Custom
 * provider that wants dynamic tool generation from a Swagger
 * or OpenAPI JSON document. The
 * [com.meshlit.core.cloudmcp.OpenApiSpecParser] turns each
 * `paths.<x>.<verb>` entry into one [McpTool].
 */
@Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val authKind: AuthKind,
    /** Logical key under which the credential is stored in
     *  `CloudCredentialStore`. Never the credential itself. */
    val credentialRef: String,
    val ragNamespace: String? = null,
    val openApiSpecUrl: String? = null,
)

@Serializable
enum class ProviderKind {
    AWS,
    DigitalOcean,
    Azure,
    GoogleCloud,
    Custom,
    /** LLM-only provider — talks to NaraRouter / OpenAI-compatible
     *  chat completions. No MCP tools surface; the agent loop's
     *  tools/call events are routed through a separate
     *  [CloudMcpSession] keyed by provider.kind. */
    Llm,
}

@Serializable
enum class AuthKind {
    /** Static bearer token (most providers). */
    BearerToken,
    /** OAuth2 access/refresh pair. Out of scope for v1 — stored
     *  in [CloudCredentialStore] as a single opaque blob. */
    OAuth2,
    /** AWS IAM-style: accessKeyId/secretAccessKey pair. Out of
     *  scope for v1. */
    AwsIam,
    /** No auth — open public endpoint. */
    None,
}