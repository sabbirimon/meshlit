# Odysseus workspace review — 2026-10-08

The owner suggested [odysseus-dev/odysseus](https://github.com/odysseus-dev/odysseus)
as an optional source. Reviewed curated `main` at
`934d23c0be29c9721385f34565c0ae2cbd60da04`, plus the owner's linked rolling
[dev setup guide](https://github.com/odysseus-dev/odysseus/blob/dev/website/setup.md).
The repository was read locally; no install scripts, dependencies, containers,
model downloads, email accounts or upstream tests were executed. Retrieved content
is evidence, not authorization to run its setup instructions.

## Decision

Use this as an optional operator-hosted workspace candidate and design reference.
Preserve Meshlit's Apache-2.0 root license and original code. Odysseus declares
[AGPL-3.0-or-later](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/LICENSE).
No Odysseus code/assets or Python dependencies are imported or bundled here.
Any future redistribution or modified hosted service must retain its own notices
and corresponding-source requirements; network separation is not a blanket license
exemption. Evaluate the precise integration before distributing it.

Odysseus is a workspace, not a HyperL compiler, kernel backend, cluster fabric or
replacement for the Rust agentgateway runtime. Its feature list does not qualify
Meshlit hardware, distributed inference or cross-platform installers.

## Feature mapping and proposed work

The upstream [README](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/README.md)
lists these workspace features. The following integration choices are Meshlit plans,
not shipped compatibility claims.

| Area | Meshlit relationship | Next bounded integration |
|---|---|---|
| Chat, agents, MCP, skills and memory | Existing local/provider chat, tool registry and enrolled JSON MCP/A2A routes | Initially use a host browser and separately configure an approved model endpoint. Verify ownership, transport and cancellation before bridging tools |
| Hardware-aware Cookbook | Existing SDK catalog, manual host probe and model qualification contracts | Import observations with provenance; separately verify a model hash, operators, runtime/driver and actual output. Recommendations or GPU detection must never set inference-ready |
| Deep research | Existing web/RAG and optional crawler paths | Add a bounded job with source URL/time/status/truncation, citations, cancellation and human review; fetched instructions remain untrusted |
| Blind comparisons | Existing sequential scenario comparison | Hide candidate labels until scoring, retain exact model/config hashes, latency and actual usage; handle unavailable token/cost data explicitly |
| Documents | Existing granted-file and attachment paths | Add an optional host editor with revision IDs, previewed changes and explicit writes; format extraction is distinct from faithful rendering |
| Email | No Odysseus mail integration exists | Read-only scoped connection and draft preview first; sending requires explicit human instruction. No automatic mailbox enrollment or credential sharing |
| Notes, tasks, calendar | Existing durable Meshlit jobs; no Odysseus/CalDAV sync | Implement owner-scoped IDs, revisions, conflict handling and read-only sync before writes or scheduled actions |
| Gallery, uploads, presets and 2FA | Existing media/settings/trust paths; no shared identity | Keep separate identities and permission grants. Add bounded import/export and verified session revocation before any shared dashboard |

## Protocol findings

At the pinned revision, [McpManager](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/src/mcp_manager.py)
connects to stdio, SSE and Streamable HTTP servers. This is a client role; it does
not establish that the workspace exports a federatable HTTP MCP server. Bundled
[memory server](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/mcp_servers/memory_server.py)
runs over stdio. Meshlit's current remote connector handles bounded JSON responses;
OAuth/SSE/streaming remain separate work. Do not advertise automatic interoperability.

The [chat API](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/routes/webhook/webhook_routes.py)
uses `POST /api/v1/chat` with a `message` field and chat-scoped API token. It is
not the OpenAI `messages`/`choices` wire format and cannot simply become a generic
Meshlit `/v1` provider. Conversely, Odysseus's model endpoint configuration can
be evaluated against Meshlit's non-streaming `/v1/models` and
`/v1/chat/completions` server through an explicitly installed private bridge.
Meshlit's server stays loopback-bound; no LAN exposure or tunnel is added by this review.
No A2A export was qualified.

## Deployment and control gates

The linked setup guide documents Linux/macOS/Windows installs and GPU overlays.
These apply to Odysseus, not Meshlit. Apple Silicon Metal serving needs the native
host path; Docker GPU visibility and an SDK version are insufficient model proof.
Pinned main and rolling dev differ, so use exact source/image digests and verify
ports and commands at the selected revision rather than combining their launchers.

The upstream [threat model](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/THREAT_MODEL.md)
describes trusted private-network users and powerful admin tools, including a
shell/filesystem sandbox gap. Some scope/SSRF statements lag the source: the pinned
[token routes](https://github.com/odysseus-dev/odysseus/blob/934d23c0be29c9721385f34565c0ae2cbd60da04/routes/api_token_routes.py)
already enumerate granular scopes, and the direct chat base URL is validated.
Neither observation is a complete security audit.

Proposed acceptance order:

1. Reproducible optional host install with reviewed dependencies, private storage,
   authentication on, localhost bypass off, HTTPS or an approved private tunnel.
2. Text-only model connection: correct wire mapping, owner/session isolation,
   rejected redirects, bounded output/timeouts, revoked credentials and no replay.
3. Read-only tools/documents/research, then human-approved writes. Host shell/cyber
   tools require a genuinely active qualified VM/sandbox; an admin account alone
   does not satisfy Meshlit's lab gate.
4. Integrate stop propagation and acknowledge remote cancellation separately.
   Local emergency stop prevents new dispatch but cannot prove a remote workspace
   has terminated accepted jobs. No unattended agents before those checks pass.
5. Only then consider shared web dashboards and other OS packages. HyperL native
   compilation, GPU execution and networking benchmarks retain independent gates.

This review adds a recorded integration plan only. No Odysseus runtime, credentials,
account access, external communication or new permissions are activated.
