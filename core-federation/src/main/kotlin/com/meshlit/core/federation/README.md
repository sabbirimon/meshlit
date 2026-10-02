# `:core-federation` — versioned peer-to-peer federation

Phase 6 of the master plan (see `lucky-greeting-crown.md` §6).
This module owns the **wire-level** federation protocol — the JSON
codec, the `/v1/*` endpoints, and the `protocol_version` handshake
that lets two Meshlit devices on the same LAN talk to each other
without corrupting state across app versions.

## Wire contract (ADR-008)

```
Content-Type: application/json
TLS 1.3 with mTLS (no TLS < 1.3, no PSK-only, no plaintext)

X-Meshlit-Protocol-Version: <packed int, currently 10000 = 1.0.0>
X-Meshlit-Node-Id: <stable node id>
X-Meshlit-Key-Fingerprint: <sha256 of long-term pub key>
X-Meshlit-Session-Token: <opaque token from peer.hello>
```

### Version negotiation

| Local major | Remote major | Outcome |
|---|---|---|
| 1 | 1 | accept; use `min(local, remote)` as negotiated floor |
| 1 | 2 | reject with `426 Upgrade Required` + `version_mismatch` |
| 1 | 0 | reject with `426` + `version_mismatch` (no version header is invalid) |

The major version bump is reserved for **breaking** wire changes.
Additive fields stay within `1.x` — older peers ignore them via
`ignoreUnknownKeys = true` in the codec.

### Endpoints

| Method | Path | Purpose | Endpoint constant |
|---|---|---|---|
| POST | `/v1/peer.hello` | handshake, exchange keys + capabilities, return session token | `FederationEndpoints.PEER_HELLO` |
| POST | `/v1/health.capability` | lightweight capability probe (no session state) | `FederationEndpoints.HEALTH_CAPABILITY` |
| POST | `/v1/inference.dispatch` | stream one inference request (chunked JSON) | `FederationEndpoints.INFERENCE_DISPATCH` |
| POST | `/v1/agent.handoff` | accept a signed session handoff token | `FederationEndpoints.AGENT_HANDOFF` |
| POST | `/v1/model.shardRanges` | GGUF shard layout (Slice 4 / Phase 7) | `FederationEndpoints.MODEL_SHARD_RANGES` |

### Error model

Every non-2xx response is a typed [FederationError] JSON object:

```json
{
  "kind": "version_mismatch",
  "message": "Incompatible protocol version: local=1.0.0 remote=2.0.0",
  "details": { "local_major": "1", "remote_major": "2", "remote": "20000" }
}
```

`kind` values are sentinel constants on [FederationError]:
`version_mismatch`, `unknown_peer`, `trust_denied`,
`unsupported_feature`, `model_unavailable`, `dispatch_busy`,
`dispatch_cancelled`, `invalid_request`, `internal_error`. Clients
MUST treat unknown kinds as fatal `internal_error`.

### Streaming `/v1/inference.dispatch`

- Client sends one `InferenceDispatchRequest` JSON body.
- Server responds with `200 OK` + `Content-Type: application/x-ndjson`.
- Body is one `TokenChunk` JSON object per line.
- Stream terminates after the chunk whose `finishReason` is non-null.
- After that chunk the server MAY close the connection.

## File map

| File | Purpose |
|---|---|
| `FederationProtocol.kt` | version constants + `negotiate()` |
| `FederationEndpoints.kt` | path constants |
| `FederationError.kt` | typed error model + sentinel constants |
| `FederationCodec.kt` | JSON codec (single canonical `Json {}` instance) |
| `FederationClient.kt` | OkHttp-backed typed client |
| `PeerHello.kt` | handshake request/response |
| `InferenceDispatch.kt` | dispatch request + `TokenChunk` |
| `HandoffToken.kt` | signed agent-session handoff |
| `CapabilityExchange.kt` | lightweight probe + shard layout |
| `CapabilityMatrixDto.kt` | wire representation of `CapabilityMatrix` |

## Dependencies (in topological order)

```
:core-federation
  → :core-common
  → :core-trust
  → okhttp + kotlinx-coroutines + kotlinx-serialization
```

## Tests

- `FederationProtocolVersioningTest` — pinned version negotiation matrix.
- `FederationCodecTest` — round-trip JSON for every wire type + forward-compat.
- `FederationClientWireTest` — MockWebServer end-to-end:
  - protocol-version header sent on every request
  - 200 responses decoded
  - 4xx / 5xx decoded as typed `FederationError`
  - streaming chunks parsed line-by-line
  - connection failures mapped to `internal_error`

Android instrumentation tests for full TLS 1.3 + mTLS handshake on
two physical arm64-v8a devices are planned for the Phase 6 device
gate (plan §6.5) — blocked on the user's device being available
unlocked.

## Security

- TLS 1.3 only; cipher suite enforced by the platform default.
- mTLS: both peers present a cert. The trust store is the
  Android Keystore-backed one from `:core-trust`. Cached trust
  decisions live in `SharedPreferences` (encrypted by the
  Keystore master key).
- Session tokens are opaque, single-peer, expiring (default
  24 h). Revocation = `POST /v1/peer.hello` again to get a new
  one; the old token is invalidated atomically.
- Public keys and tokens are NEVER logged. Logs record only
  `peerId`, `keyFingerprint`, and the negotiated protocol version.
