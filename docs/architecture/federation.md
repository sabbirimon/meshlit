# `:core-federation` — Phase 6 architecture

This is the public-facing companion to `lucky-greeting-crown.md` §6.
The implementation lives at `core-federation/src/main/kotlin/com/meshlit/core/federation/`.

## What this module owns

| Surface | What it is |
|---|---|
| Wire codec | `FederationCodec` (single canonical `Json {}`) |
| Version negotiation | `FederationProtocol.PROTOCOL_VERSION = 1_00_00` (1.0.0) |
| Endpoints | `FederationEndpoints` — the five `/v1/*` paths |
| Typed client | `FederationClient` (OkHttp-backed) |
| Error model | `FederationError` + sentinel `kind` constants |

It deliberately does **not** own: the trust store (`:core-trust`),
discovery (`:core-discovery`), or the agent kernel (`:core-agent-memory`).
Those modules call into `:core-federation` for the wire; they own
their own state.

## Versioning (ADR-008)

`protocol_version: int` is packed as `MAJOR*10_000 + MINOR*100 + PATCH`.
Major-version mismatch → refuse handshake (`426 Upgrade Required`).
Minor / patch mismatch → accept, use the smaller value as the negotiated
floor. Old peers ignore unknown fields via `ignoreUnknownKeys = true`.

The current protocol version is `1.0.0`. Bumping major is reserved
for breaking wire changes. Additive fields stay on `1.x`.

## The five endpoints (plan §6.1)

| Method | Path | Caller → Server |
|---|---|---|
| POST | `/v1/peer.hello` | client → server: introduce ourselves + return session token |
| POST | `/v1/health.capability` | client → server: lightweight probe (no session state) |
| POST | `/v1/inference.dispatch` | client → server: stream one inference request |
| POST | `/v1/agent.handoff` | client → server: accept a signed session handoff |
| POST | `/v1/model.shardRanges` | client → server: return GGUF shard layout (Slice 4 / Phase 7) |

Every endpoint requires the `X-Meshlit-Protocol-Version` header. A
peer that sends no version header, or whose major version doesn't
match ours, is refused.

## Streaming inference dispatch

`POST /v1/inference.dispatch` is the only streaming endpoint. The
server responds with `200 OK` + `Content-Type: application/x-ndjson`
and emits one `TokenChunk` JSON object per line. The stream terminates
after the chunk whose `finishReason` is non-null. After that chunk the
server MAY close the connection.

`TokenChunk.finishReason` values are sentinel constants:

- `natural` — the model produced EOS naturally.
- `length` — `maxTokens` reached.
- `stop_sequence` — one of the caller's `stopSequences` was emitted.
- `cancelled` — the caller cancelled mid-stream.
- `error` — the server failed; `error` carries a typed `FederationError`.

## Error model

Every non-2xx response body is a `FederationError`:

```json
{
  "kind": "version_mismatch",
  "message": "Incompatible protocol version: local=1.0.0 remote=2.0.0",
  "details": { "local_major": "1", "remote_major": "2", "remote": "20000" }
}
```

`kind` sentinel constants: `version_mismatch`, `unknown_peer`,
`trust_denied`, `unsupported_feature`, `model_unavailable`,
`dispatch_busy`, `dispatch_cancelled`, `invalid_request`,
`internal_error`. Clients MUST treat unknown kinds as fatal
`internal_error`.

## Security posture

- TLS 1.3 only; cipher suite chosen by the platform default.
- mTLS: both peers present a cert. Trust store is the
  Android Keystore-backed one from `:core-trust`.
- Public keys and tokens are **never** logged. Logs carry only
  `peerId`, `keyFingerprint`, and the negotiated protocol version.
- Session tokens are opaque, single-peer, expiring (default 24 h).
  Revocation = re-handshake.
- Handoff tokens are signed (Ed25519) blobs with an explicit expiry
  and replay-protection via a bounded LRU on the receiver.

## Tests in this drop

| Test file | Pins |
|---|---|
| `FederationProtocolVersioningTest` | version packing, negotiation matrix, refusal on major mismatch |
| `FederationCodecTest` | JSON round-trip for every wire type + forward-compat + default-elision |
| `FederationClientWireTest` | MockWebServer end-to-end: protocol-version header, 200 / 4xx / 5xx decoding, NDJSON stream parse, connection-failure mapping |

33 JVM unit tests, all passing as of the Phase 1 cut.

## Out of scope for v1

- The full TLS 1.3 + mTLS handshake validation on two physical
  arm64-v8a devices (plan §6.5 device gate) — blocked on the user's
  Pixel `R9KN2009CZJ` being unlocked.
- `:core-agent-memory` wiring that calls `submitHandoff(...)` to
  ship a durable session from one peer to another. The wire types
  are stable; the kernel integration is the Phase 2 → Phase 6 join.
- Slice 4 (GGUF sharding) — `model.shardRanges` returns
  `unsupported_feature` from v1 peers, per the documented contract.