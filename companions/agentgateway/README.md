# Official Rust agentgateway companion

Pinned upstream v1.6.0 from the official release API, reviewed 2026-10-07.
`upstream.lock.json` records Linux amd64/arm64 download URLs and GitHub SHA-256
asset digests. These are upstream assets; no binary/source is relabeled as Meshlit.

`python3 fetch.py --architecture arm64 --output /approved/staging/agentgateway`
downloads and verifies an asset; it never starts a listener or installs a package.
Use inside a qualified Linux lab VM or dedicated approved host. Linux binaries
are not Android/Bionic binaries. No Rust Android build is included in this APK.

Configure from the pinned upstream examples/schema, then start with
`agentgateway -f /approved/path/config.yaml`. Exact authentication, TLS, provider
keys, model aliases and MCP/A2A routes belong to that deployment. Keep admin and
metrics listeners private. Do not expose unauthenticated example listeners.

Meshlit inbound loopback port 18893:
- `/mcp`: stateless MCP Streamable HTTP JSON-response subset.
- `/a2a`: A2A 0.3 JSON-RPC model-task subset.
- `/.well-known/agent-card.json`: authenticated capability document.
- `/v1/models`, `/v1/chat/completions`: buffered text routes.
All need the owner's scoped gateway bearer token. A private verified tunnel is
needed for remote access; Android lab VM forwarding does not automatically expose
phone loopback. Preserve authentication on both gateway and phone endpoints.

Meshlit outbound: Online providers -> Custom compatible, exact HTTPS `/v1` base,
gateway token and upstream configured alias. Existing direct Anthropic/Gemini
profiles remain independent. Compatible text routing does not prove tool calling,
Responses, media or every provider's entitlement.

Official sources:
https://github.com/agentgateway/agentgateway/releases/tag/v1.6.0
https://agentgateway.dev/docs/standalone/latest/documentation/quickstart/llm/
https://agentgateway.dev/docs/standalone/latest/documentation/quickstart/mcp/
https://agentgateway.dev/docs/standalone/latest/documentation/agent/a2a/

Host execution unverified here: this Mac is x86_64, upstream supplied Darwin arm64
only in reviewed release; Docker daemon is not running. No Android VM/phone is
attached. YAML deployment schema, live Rust process and provider acceptance remain
separate tests rather than assumptions from Kotlin gateway tests.
