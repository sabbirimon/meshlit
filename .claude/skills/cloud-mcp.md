---
name: cloud-mcp
description: Conventions for the :core-cloud-mcp module — multi-cloud MCP agent + RAG.
tools: Read
---

# /cloud-mcp — Conventions for the cloud-MCP module

## What lives in `:core-cloud-mcp`

- **SSE + JSON-RPC transport.** Hand-rolled `SseParser` (no
  `okhttp3.sse` — consistent with `RemoteInferenceClient`).
- **`McpEvent` sealed class.** Surface every observable signal the
  agent-loop UI consumes.
- **`ToolRegistry`.** Process-wide merge of every connected
  provider's tools. The agent loop reads `toolRegistry.ordered()`
  and passes the list to `NaraRouterClient.chatCompletions`.
- **Per-provider `CloudMcpSession`.** Owns `connect()`,
  `handshake()`, and `callTool()`. Lifecycle is one-shot — caller
  holds the scope.
- **`CloudMcpCoordinator`.** Process-wide facade. Holds the
  sessions map + the `events` `SharedFlow`. Add a `tryEmit` helper
  on the coordinator when callers need to inject synthesized
  events (the `SharedFlow` interface doesn't expose `tryEmit`).
- **`CloudMcpForegroundService`.** Long-lived SSE service. Mirrors
  `InferenceForegroundService`'s `WakeLock` lifecycle. Required
  manifest entry:

  ```xml
  <service
      android:name="com.meshlit.core.cloudmcp.CloudMcpForegroundService"
      android:foregroundServiceType="dataSync"
      android:exported="false" />
  ```

- **`NaraRouterClient`.** OpenAI-compatible streaming LLM client.
  Default base URL: `https://router.bynara.id`. Default model:
  `NaraRouterModel.Default` (DeepSeek V4 Flash — 5M tokens/day
  free tier).
- **RAG.** `RagMode` (Local / Remote / Auto / Ask) +
  `RagBackendSelectionPolicy` (pure logic, no I/O) +
  `LocalRagStore` (in-memory stub for v1; Room + sqlite-vss
  follow-up) + `RemoteRagStore` (talks to the provider's MCP
  server).

## Security

- All provider tokens live in `CloudCredentialStore` (in
  `:core-trust`). Backed by `EncryptedSharedPreferences` + Android
  Keystore (AES256/GCM).
- Never put a credential in a `ProviderConfig` field — store the
  `credentialRef` (e.g. `aws-prod/token`) and resolve at connect
  time via `credentialStore.get(ref)`.

## UI conventions

- `RagIndicatorChip` lives in `:app` (not `:core-cloud-mcp`) to
  avoid a circular dep on `:app/ui/components/RaPillChip`.
- `McpEvent.ToolCall` requires `callId` — never emit without it.
  The LLM uses it to correlate `tool_calls` requests with the
  eventual `ToolResult`.
- `Cloud` is a `drawerOnly` `TopLevelDestination` — the bottom
  bar stays at 9 items.

## Things explicitly out of scope

- OAuth2 + AWS-IAM auth flows (BearerToken ships first).
- Pinecone / Qdrant / Milvus native SDKs (use provider MCP).
- Multi-session Agent Terminal history.
- Room + sqlite-vss wiring (KSP is a follow-up).
