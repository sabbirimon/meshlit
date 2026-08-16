package com.meshlit.core.cloudmcp.rag

/**
 * User-selected RAG retrieval mode. Persisted via
 * `SettingsRepository` and surfaced as a chip on every
 * RAG-backed surface (Cloud Hub, Agent Terminal, Settings → RAG).
 *
 *  - [Local] — always use the on-device Room + sqlite-vss store.
 *    Cheapest; covers runbooks + recent infra history; no
 *    network needed.
 *  - [Remote] — always query the configured remote vector DB
 *    (Pinecone / Qdrant / Milvus) via the provider's MCP server.
 *    Best recall; requires network + valid token.
 *  - [Auto] — try local first; fall back to remote on cache miss
 *    or if the local store has fewer than
 *    [RagBackendSelectionPolicy.AUTO_LOCAL_MIN_DOCS] documents.
 *  - [Ask] — emit a [RagPermissionRequest] for every retrieval;
 *    the user confirms each call via a dialog. Defaults to
 *    local on confirmation but the choice persists per-call.
 */
enum class RagMode {
    Local,
    Remote,
    Auto,
    Ask,
}