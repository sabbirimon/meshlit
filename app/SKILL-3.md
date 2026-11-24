---
name: mcp-server-android
description: How to run a Model Context Protocol (MCP) compatible tool server natively on Android as a foreground service exposing HTTP/SSE endpoints — covers exposing device capabilities (filesystem, web search, shell execution) as MCP tools, transport patterns, and why this differs from a normal REST API. Use this for any task that turns a phone into an MCP tool node, an agent node, or exposes a device capability to the rest of the cluster.
---

# MCP Tool Servers on Android

Turns a "Tool node" role phone into something the "Brain" node (or an
external agent framework) can call for filesystem access, web search, shell
script execution, or any other capability — without requiring Termux or
Node.js on-device.

## Transport pattern

- Run as a native Android Foreground Service (see
  `android-foreground-services/SKILL.md` for the runtime-cap and wakelock
  rules — an MCP server is exactly the kind of long-lived background work
  those rules govern).
- Embed a lightweight HTTP server (Ktor) inside the service, exposing
  MCP-style endpoints over HTTP with Server-Sent Events (SSE) for streaming
  results back to the caller.
- Bind to the local network interface only by default. Do not bind to
  `0.0.0.0` and assume that's safe — see the trust-tier requirements below
  and in `cluster-trust-security/SKILL.md` before exposing this to anything
  beyond the local trusted network.

## Mapping device capabilities to MCP tools

Each tool this node exposes should be a discrete, scoped handler — not a
generic "run arbitrary shell command" endpoint:

- **Filesystem tool**: scoped to specific app-accessible directories, never
  arbitrary device storage. Explicit allow-list of paths, not a blanket
  grant.
- **Web search / scraping tool**: runs the request on-device (uses that
  node's network connection, which matters for WAN nodes on cellular — see
  the data-usage warning in `cluster-trust-security/SKILL.md`).
- **Script execution tool**: highest-risk capability — gate behind the
  strictest trust tier, and consider whether it's needed at all before
  building it. A compromised or misconfigured node with script-execution
  exposed to the network is a real liability, not a hypothetical one.

## Why this isn't "just a REST API"

- The calling node (Brain) needs to discover *which* tools a given node
  exposes dynamically — implement a capability-listing endpoint that
  returns the node's available MCP tools, so the router/agent framework can
  match jobs to nodes without hardcoding tool-to-device mappings.
- Results should stream via SSE for anything that isn't instant (a
  multi-second web scrape, a large file read) — the calling node's UI
  shouldn't block on a synchronous HTTP response for tool calls that take
  real time.
- Tool servers need the same heartbeat/staleness handling as inference
  nodes — a "Tool node" going dark mid-call should time out and get evicted
  from the routing table exactly like an inference node would.

## Security — read this before exposing anything

An MCP tool server is a remote-code-execution-adjacent surface by design (it
executes actions on request from another device). Do not expose it without:
- Trust-tier-appropriate auth (see `cluster-trust-security/SKILL.md`) — a
  local-trusted node can have a lighter-weight pre-shared key; anything
  reachable over WAN needs full TLS + signed per-request tokens.
- Never bind an MCP server's port to a public/WAN-reachable interface
  directly — WAN-reachable tool nodes route through the relay (Phase 4),
  never a directly exposed port.
- Log every tool invocation (caller, tool, timestamp, result status) locally
  on the node — this is both a debugging aid and a basic audit trail if a
  trust boundary is ever crossed unexpectedly.

## Testing

Verify tool servers under realistic conditions: the calling node on a
different Wi-Fi band, the tool node's screen off, the tool node mid-charge
with a policy rule that might otherwise demote it. A tool call that silently
fails when the phone's screen turns off is a background-execution bug — see
`android-foreground-services/SKILL.md` — not an MCP protocol bug.
