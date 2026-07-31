# Build Guide — Meshlit (Android Device Cluster & Local-Agent Orchestrator)

A phone-cluster app that discovers nearby (and optionally remote) Android devices,
assigns each one a role, and runs local LLM inference, MCP tool servers, agents,
training jobs, and a managed VPN mesh across them — with wired/wireless LAN,
direct Wi-Fi, encrypted tunnel, and cellular WAN transports.

This guide is written to be followed by an autonomous coding agent (Claude Code /
"full autopilot") one phase at a time. Each phase ends with something that
actually runs, so progress stays checkable instead of turning into an
unshippable pile of infrastructure.

> **Meshlit** (Mesh + lit). Tagline: "Many phones. One mind."

---

## 0. Core design principles (do not violate these)

These decisions came out of scoping discussion and exist specifically to stop
scope creep from turning into unbuildable features. Treat them as constraints,
not suggestions.

1. **Data-parallel, not tensor-parallel. But MoE-shard federation is OK.**
   Never split one model's layers or activations across phones over the
   network — each node runs a complete model of *its* slice. The router
   distributes independent jobs across nodes. Real inter-phone bandwidth
   (1–2 GB/s best case over USB, far less over Wi-Fi) cannot support
   cross-device tensor operations at usable latency. **However**, for
   *Mixture-of-Experts* architectures (most modern frontier models),
   coarse-grained shard federation IS feasible: each node holds the
   complete weights for a subset of experts, and a lightweight router
   dispatches tokens to the right shard. This is *not* tensor-parallel;
   it's expert-routing at the token boundary. See §2.7 "Frontier model
   federation" for the worked approach.
2. **Role suggestions are advisory, not hard locks.** Warn and let the user
   override. Hard-locking invites sideloaded workarounds and blocks
   legitimate stress-testing.
3. **The network is unreliable by default.** Phones sleep, leave Wi-Fi range,
   get backgrounded, or die. Every job dispatch needs a timeout, retry, and
   dead-node eviction path from day one — don't retrofit this later.
4. **Security scales with trust tier, not a single global flag.** LAN nodes,
   temporary/untrusted local nodes, and WAN nodes each get a different auth
   burden. See `skills/cluster-trust-security/SKILL.md`.
5. **Ship static thresholds before adaptive ones.** Hardcoded battery/thermal
   cutoffs first. Adaptive tuning (Phase 5) only gets built once there's real
   usage data to learn from — building it earlier means tuning against
   nothing.
6. **Don't promise what the platform can't do.** No silent ADB provisioning
   of non-rooted devices, no programmatic bypass-charging control (no public
   AOSP API for it), no true multi-hop mesh routing in v1. State these as
   known limits in-app rather than half-implementing them.
7. **Every user-visible feature respects the user's data.** Inference,
   embeddings, file transfer, and model import can move large amounts of
   data. Metered connections, mobile data, and per-device quotas get
   explicit consent flows — never silent usage. Every byte a user pays for
   is a byte they should have approved.
8. **On-device by default; opt-in for everything else.** Training, embeddings,
   and prompt guardrails all run locally. There's no telemetry endpoint in
   v1 — `Settings → Privacy` should be able to confirm this without
   scanning a config file. Anything that would leave the device must be a
   visible, toggleable action with a clear label.
9. **Choices are user-driven, not platform-imposed.** When several
   technically valid approaches exist (Tailscale vs WireGuard vs relay;
   LAN SSH vs SSH-off; bypass-charging manual toggle vs not; trust-tier
   auto-paired vs always-explicit-paired; on-device LoRA vs cross-node
   cooperative training; etc.), Meshlit surfaces them as **options in
   the UI**, not silent defaults. The user picks; Meshlit shows the
   trade-offs and the consent/permission state clearly. This is how the
   app stays out of the way of power users while remaining safe for
   non-technical ones.
10. **Conflicting constraints are resolved by user choice, not by
    relaxing the constraint.** Examples baked into the design:
    - The "no public SSH" rule stays. Cluster SSH (Phase 5) is a real,
      useful feature bound to LAN/Tailscale. If a user wants public SSH,
      they use Termux — Meshlit doesn't try to compete or compromise.
    - The "no programmatic bypass-charging" rule stays. The UI surfaces
      the manual toggle on supported OEMs as a recommendation, never
      tries to automate it.
    - "No multi-hop mesh routing" stays. Long-distance nodes use the
      tunnel (Tailscale/WG) or the relay — the user picks which.
      Single-hop is the discovery rule, not the connectivity rule.
    - "Static thresholds before adaptive" stays. Even when Phase 5
      ships the EWMA, the user can pin a device to static thresholds in
      Settings.

---

## 1. Architecture overview

```
┌──────────────────────────────────────────────────────────┐
│  UI (Jetpack Compose)                                     │
│  Devices · Cluster · Jobs · Models · Files · Sessions ·   │
│  Network · Users · Settings · Guardrails                  │
└───────────────────────────┬────────────────────────────────┘
                             ▼
┌──────────────────────────────────────────────────────────┐
│  Orchestration Core (Kotlin)                               │
│  Discovery (NSD/mDNS, Wi-Fi Direct/Aware, Tailscale/WG)   │
│  Router/Load Balancer · Node Registry · Trust/Auth ·       │
│  Job Queue · Policy Rules Engine · Heartbeat/Staleness     │
│  Capability Probe · Role Assignment · Topology View        │
└───────────────────────────┬────────────────────────────────┘
                             ▼
┌──────────────────────────────────────────────────────────┐
│  Native / Service Layer                                    │
│  llama.cpp (NDK/C++) · Embedded Ktor HTTP/SSE server ·     │
│  MCP tool handlers · Foreground Service wrappers ·        │
│  LoRA fine-tune runner · SSH/PTY session host ·            │
│  Local firewall (per-port allow-list) · WireGuard/Tailscale│
└──────────────────────────────────────────────────────────┘
```

Every phone runs the same APK. "Master" and "worker" are runtime states, not
separate builds — see Phase 2.

---

## 2. Phased roadmap

### Phase 0 — Scaffolding
**Goal:** empty but structured project that builds and installs.

- Kotlin multi-module Gradle project (see §4 for module layout).
- Compose UI shell with bottom navigation: Devices · Jobs · Models · Settings.
- Brand assets (adaptive launcher icon, Meshlit color palette).
- CI-less local build verified on at least one physical device.

**Exit criteria:** app installs, launches, shows the empty Devices screen
without crashing, branded with Meshlit identity.

---

### Phase 1 — Core loop: 2 devices, 1 job type
**Goal:** prove the hardest technical risk (a background inference service
that actually survives) before building anything else on top of it.

- One phone runs a `dataSync`-type Foreground Service hosting llama.cpp via
  NDK, exposing a local HTTP endpoint.
- A second device (or the same phone's UI) sends one prompt over LAN HTTP and
  displays the response.
- No discovery yet — hardcode the IP for this phase.
- Implement `onTimeout()` handling for the FGS 6-hour/24h cap (Android 15+)
  even though you won't hit it yet — build the restart path now, not later.

**Exit criteria:** a prompt sent from Device A produces a real generated
response from Device B, and the service survives the app being backgrounded
for 10+ minutes.

---

### Phase 2 — Multi-node LAN, roles, auto-scaling
**Goal:** the actual cluster — discovery, role assignment, routing.

- NSD/mDNS discovery so new nodes appear automatically on the same Wi-Fi.
- Capability probe on join: RAM, CPU, thermal, GPU/NPU delegate availability.
- Role suggestion (Brain / Tool node / Monitor) from the capability probe —
  advisory per principle 2 above.
- **Benchmark-on-join:** run a fixed 30-second inference job the moment a
  node connects and record tokens/sec + thermal delta. Feeds Phase 5.
- Simple router: dispatch to the least-busy node with a matching role.
- Master/worker as a runtime toggle on a single APK.
- Heartbeat every few seconds; hard staleness cutoff (no heartbeat in 15s →
  mark unavailable, evict from routing).
- Store-and-forward job queue with retry/backoff — assume nodes vanish
  mid-job.
- Topology view: a 2D node graph showing the cluster.

**Exit criteria:** add a third phone to the same Wi-Fi with zero manual
config and it appears, gets a role, and starts receiving jobs.

---

### Phase 3 — Trust tiers, mesh transports, first real workloads
**Goal:** move off "everyone on my home Wi-Fi" and prove workloads that
actually benefit from clustering.

- Trust tiers per `skills/cluster-trust-security/SKILL.md` (local-trusted /
  local-untrusted-sandboxed / WAN).
- Transport abstraction layer: NSD as fallback, Wi-Fi Aware attempted first
  where supported, Wi-Fi Direct for ad-hoc. Single-hop only.
- **Local firewall** (`core-firewall`): per-port packet filter for the
  inference / MCP server ports — accept from trust-tier allow-list, rate-limit
  per source IP, default deny.
- **User accounts** (`core-users`): local profiles, per-user model library
  and job history, optional biometric gate.
- **File management** (`core-files`): in-app file browser scoped to
  app-accessible dirs, copy/move between cluster nodes using the shared
  transport, with per-user quota.
- **Security guards** (`core-guardrails`): prompt-injection detector,
  jailbreak phrase filter for outbound tool calls, PII redaction, output
  token cap per job, "locked-down" mode disabling script-execution tools.
  All processing local.
- **Model import**: HuggingFace URL parsing, Ollama model registry parsing,
  direct URL, local file picker, and import from another cluster node.
  Checksum-verified, with explicit consent for metered connections.
- **Shared model cache**: one node with a GGUF already downloaded serves it
  to others over LAN instead of every phone re-downloading.
- **Distributed embedding/RAG indexing** as the first real multi-node
  workload.
- MCP tool server on a second role class (filesystem, web search, etc.),
  exposed over HTTP/SSE.
- Declarative per-device policy rules ("only accept jobs if battery > 50%
  and charging").

**Exit criteria:** a batch of documents gets embedded noticeably faster
across 3 nodes than on 1, a chat session can call out to an MCP tool
running on a different phone, the local firewall blocks a connection from
an unlisted IP, and a model can be imported from a HuggingFace URL with
checksum verified.

---

### Phase 4 — WAN / encrypted tunnel / long-distance roles
**Goal:** optional long-distance nodes for async, latency-tolerant roles
only.

- **Tailscale integration** as a peer-discovery and WAN transport option
  (configurable: official Tailscale or self-hosted Headscale). Auth key
  flow lives in-app; node's Tailscale IP becomes its `NodeId` for routing.
- **WireGuard fallback** for users who don't want a Tailscale account:
  manual key + endpoint config, single tunnel per node.
- TLS + per-device signed tokens for every WAN-reachable node — Tailscale
  already gives us encrypted transport; tokens gate *role capability* on top.
- Keep the small relay service (VPS) as a fallback for nodes that can't run
  Tailscale/WireGuard (e.g. locked-down corporate devices).
- Separate job class with its own timeout/SLA for WAN nodes: DB/cache,
  async MCP tools, slow batch agents, *training jobs* — never the live
  inference "Brain" role.
- Explicit opt-in per node with a data-usage warning gated on
  `ConnectivityManager.isActiveNetworkMetered()`.

**Exit criteria:** a WAN-connected phone on cellular can serve as a
persistent key-value store, async MCP tool, or *training worker* for the
local cluster, reached over Tailscale (or WireGuard), with a visible
latency/SLA distinct from LAN nodes.

---

### Phase 4.5 — Public-side gateway & federated cluster

**Goal:** anyone running Claude Code, an OpenAI client, an MCP client, or
their own LLM/agent framework can address a Meshlit cluster as if it
were a single inference provider. The cluster's phones become a
federated backend for the public side.

- **Public gateway node** (`core-inference` + `:app`): any cluster node
  can be promoted to "public gateway" mode. In this mode it exposes an
  OpenAI-compatible HTTP API:
  - `POST /v1/chat/completions` (with SSE streaming)
  - `POST /v1/embeddings`
  - `GET  /v1/models`
  - `POST /v1/completions` (legacy)
  - `GET  /v1/cluster/status` (cluster topology for the public client)
- **Bearer-token auth.** Per-device tokens the user generates from the
  Meshlit UI. Each token is scoped: allowed-models, rate-limit, allowed
  trust tiers, expiry. Tokens are revocable from inside the app. The
  gateway logs every token use (caller IP, model, latency, status).
- **Federated dispatch.** A request from the public side flows: gateway
  → orchestrator → picker (capability + role match) → worker node(s).
  Tokens stream back through the gateway to the client. The gateway
  presents the cluster as a single model — the client never sees the
  sharding.
- **Bidirectional roles on a single device.** A single device can be
  gateway + worker simultaneously, or worker + client (consuming
  another cluster's gateway). No "master/worker" hard split. Roles are
  per-traffic-direction, not per-device.
- **Compatible with the AI ecosystem:** Claude Code, Cursor, LangChain,
  LlamaIndex, the OpenAI Python SDK, the Anthropic SDK, MCP clients,
  raw `curl`. Anything that speaks the OpenAI HTTP API works.
- **Reachability:** the gateway is reachable over Tailscale or the
  relay. Never directly public. Token-theft blast radius is contained
  by scoping.
- **HiveMind gossip** (see §2.7/§7.12): public-facing nodes broadcast
  their existence + token policy to the rest of the cluster so the
  orchestrator knows which node the public client should land on.

**Exit criteria:** a laptop running Claude Code, configured with a
Meshlit gateway URL and a bearer token, can complete a real coding task
that uses the cluster's inference — and the result is observably faster
(or at least not slower) than a single-phone baseline. The flow can be
followed end-to-end in logs.

---

### Phase 5 — SSH sessions, fine-tuning, adaptive scheduling, polish
**Goal:** use the data Phase 2 started collecting; round out the product.

- **SSH-style session management** (`core-ssh`): cluster-internal remote
  shell to a node, but NOT exposed to the public internet — bound to LAN /
  Tailscale interfaces only, gated by the same trust tier as the MCP server.
  Publickey auth (key fingerprint exchanged at pairing time, same as MCP).
  PTY over the cluster WebSocket. `adb shell`-style for power users, but
  governed by the trust policy so it can be revoked per tier.
- **On-device fine-tuning** (`core-training`): LoRA/QLoRA on a user-supplied
  dataset, runs on the most-capable node in the cluster. Pick a base model
  from the library, pick a dataset (uploaded file or user-supplied JSONL),
  set epochs / LR, monitor progress. Export: GGUF for inference,
  safetensors adapter for portability, Meshlit bundle (model + adapter +
  metadata). All on-device, all opt-in.
- Exponentially-weighted moving average per (node, job-type) pair replacing
  the static benchmark-on-join number as history accumulates.
- Threshold auto-tuning (simple hill-climbing on false-positive demotions).
- Local-by-default performance logs; opt-in, aggregated (chipset + RAM
  bucket level, not raw per-device) sharing only.
- Config export/import (JSON) for topology, roles, trust tokens, firewall
  rules, and user profiles.
- Quick-start templates: "RAG assistant cluster," "coding agent cluster,"
  "private fine-tune pool."
- Home-screen widget / quick-settings tile for cluster status and join
  toggle.

**Exit criteria:** the router visibly makes better decisions after a few
days of real usage than it did on day one, a user can SSH into a LAN node
with a public key, and a user can fine-tune a small base model on a
cluster node and export the result as a working GGUF.

---

## 2.5 Repository layout & version control

```
.
├── PROGRESS.md               - Running journal of state, decisions, next steps
├── docs/                     - Extended documentation
│   ├── architecture.md       - Diagrams and design rationale
│   ├── decisions/            - One ADR per non-obvious decision
│   └── journal/              - Time-stamped progress snapshots
├── app/                      - Compose UI, MainActivity, foreground service starters
├── core-*/                   - One Gradle module per concern (see §4)
├── gradle/libs.versions.toml - Single source of truth for dependency versions
├── settings.gradle.kts       - Module wiring + repo config
└── build.gradle.kts          - Top-level Gradle config
```

**Version control convention:**
- Each `git commit` is one logical change. Don't bundle a Phase across
  multiple commits with a single commit at the end.
- Each phase boundary gets a tagged commit (`git tag phase-0-done`,
  `phase-1-done`, …) so the agent picking up next can `git checkout` the
  last stable point if a phase goes wrong.
- **`PROGRESS.md` is updated at every phase boundary and at every
  material decision change** — not every commit. It is the journal;
  the build guide is the spec.
- **Each material non-obvious decision** gets a one-line entry in
  `PROGRESS.md`'s decision log. If it warrants an ADR (Architectural
  Decision Record), promote it to `docs/decisions/NNNN-title.md`.
- **Progress snapshots** are saved as `docs/journal/YYYY-MM-DD-phase-N.md`
  when a phase closes or when the session is paused mid-phase, so an
  agent picking up later doesn't have to reconstruct state from git log.
- Commit messages follow Conventional Commits format:
  `feat(scope):`, `fix(scope):`, `chore(scope):`, `docs(scope):`.
- Use git branches for each phase: `phase/0-scaffolding`,
  `phase/1-core-loop`, etc. `main` always points at the last completed
  phase's tag.

---

## 2.6 Frontend vs backend & language choices

The app is a hybrid: it ships as an Android APK (Kotlin) but it has to
play well with the broader AI tooling ecosystem. The choices in §3 are
deliberate:

- **Kotlin (primary):** the app, the orchestration, all cluster logic.
  Idiomatic coroutines, no raw threads, full Android tooling support.
- **C++ (NDK):** llama.cpp for inference. Already a battle-tested,
  continuously updated inference engine. We don't try to write our own
  inference loop; we wrap llama.cpp.
- **Python (embeddable, optional):** for power-user notebooks / data
  tooling. Two paths:
  - **Chaquopy** in `:app` for users who want a Python REPL in the app.
  - **ONNX Runtime** in `:core-inference` for some lightweight models
    where llama.cpp isn't the right engine.
  Python is NOT used for the inference hot path — that stays in
  llama.cpp's C++ via JNI.
- **Protobuf / OpenAI wire format / MCP JSON-RPC:** wire formats for
  cluster traffic, public-side traffic, and tool calls respectively.
  Reuse what the AI ecosystem already speaks.
- **JSON for config** (topology, roles, trust, profiles). Trivial
  tooling support, easy to inspect.

The public gateway exposes the **OpenAI HTTP API** as the public
contract — chosen because Claude Code, Cursor, the OpenAI SDK, LangChain,
MCP clients, and the rest of the ecosystem already speak it. We don't
invent a new protocol.

---

## 2.7 Frontier model federation: how Meshlit holds "part of" a frontier model

The user-facing promise of Meshlit is "many phones, one mind." For
**dense** models that means data-parallel: each phone holds the full
model, jobs are distributed. The constraint is bandwidth — there's no
way to share a single 7B-paramer's activations across phones in real time.

For **Mixture-of-Experts** architectures (most modern frontier models —
Mixtral, DBRX, GPT-4-class, Llama-4-MoE, Qwen-MoE, DeepSeek-MoE), there
is a *different* federation that is feasible on phones:

```
                         Public client
                              │
                              ▼
                    ┌───────────────────┐
                    │ Gateway node      │
                    │ (token auth)      │
                    └─────────┬─────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
        ┌─────────┐     ┌─────────┐     ┌─────────┐
        │ Shard 0 │     │ Shard 1 │     │ Shard 2 │
        │ experts │     │ experts │     │ experts │
        │  0..15  │     │ 16..31  │     │ 32..47  │
        └─────────┘     └─────────┘     └─────────┘
        phone A         phone B         phone C
```

**The flow:**

1. Gateway receives a prompt, tokenizes it.
2. A lightweight router on the gateway runs only the model's
   `router.gate.*` weights (small, ~50 MB), and produces per-token
   expert-id assignments.
3. Tokens get batched and dispatched to the right shard — each phone
   runs llama.cpp on **its slice of experts only** for those tokens.
4. Outputs come back to the gateway, which stitches them into the
   response stream and ships SSE to the public client.

**Why this works:**
- Inter-token bandwidth is **tiny** — a 4-byte expert id per token, not
  activations. 1k tokens = 4 KB of dispatch, not 1k × hidden_size × 2
  bytes of activations.
- Each phone holds only the expert weights for its slice. A 8×7B MoE
  model with 8 experts-per-token: each phone can hold 1-2 of those
  experts' worth of weights.
- llama.cpp supports MoE architectures natively; the shard is just
  "a model with a subset of experts."

**Why it doesn't work for dense models:** there's no router, so every
token needs every layer's activation. The constraint stays. MoE is
*the* architectural feature that makes phone-cluster federation of
frontier models realistic.

**Module ownership:**
- `:core-inference` — the shard model manager, expert-id dispatch.
- `:core-orchestration` — the gateway-side router and stitcher.
- `:core-discovery` — advertises "I hold shard N of model X" so the
  gateway knows which node to call.

This is the same HiveMind coordination as Phase 4.5: each node
publishes what it has, the gateway composes. The user can run dense
models the old way (data-parallel, one model per node) and MoE models
the new way (shards across nodes) and the public API is identical.

**Validation:** Phase 4.5's exit criteria apply — public client hits
the gateway, real frontier-class model produces real tokens, the
sharding is invisible to the client.

---

## 2.8 Token model: allocation, not monetization

**Goal:** the token exists to allocate scarce shared resources across the
mesh, not to monetize users. Growth comes from making hosting attractive,
not from gating features. Premium exists for power users; the default
experience is free forever.

**Three tiers, all free by default:**

| Tier | Who | Cost | What you get |
|---|---|---|---|
| **Meshlit Spark** | Anyone, no signup | Free | Own-phone local inference. Unlimited. |
| **Meshlit Mesh** | Opt-in by toggling "share idle compute" | Free | + access to public mesh + earns tokens while hosting |
| **Meshlit Mind** | Power users / devs / teams | Free during growth phase | + higher rate limits, custom model hosting, cluster API |

A Spark user has the full app. Tokens only matter when reaching beyond
your own phone.

**Earning rates (always ≥ cost to the earner):**

| Behavior | Reward |
|---|---|
| Idle + plugged in + Wi-Fi | +5 tokens/hour |
| Actively serving a job | +1 token / 1k tokens generated for the requester |
| Hosting a non-default model (13B+, embed, specialized) | +50% bonus |
| Participating in MoE shard federation | 2× multiplier |
| Low-connectivity region (provides redundancy) | 1.5× multiplier |
| Running a public MCP server | +1 token / 100 tool invocations |

**Onboarding bias (deliberately extreme toward hosting):**

- Download app: 0 tokens.
- Toggle "share idle compute" once: +500 tokens.
- Host overnight for 7 consecutive days: +2,000 one-time bonus.
- Friend joins and hosts ≥1 hour: both get +200.
- Submit a chipset/eGPU/peripheral entry accepted into the public DB: +100.
- First 1,000 hosts in a new region get a $10 local gift card (Amazon /
  Flipkart / JD / Taobao). This is paid marketing, not token economics.

**De-prioritization, not exclusion.** When the mesh is busy:

| User state | Position in queue |
|---|---|
| Mesh user, has tokens | First |
| Mesh user, no tokens | Wait, eventually served |
| Spark user | Last, but still served if the mesh has surplus |
| Mind user | Fastest lane, capped so it can't crowd out others |

**What tokens are NOT:**

- Not a cryptocurrency. No blockchain, no token contract, no DEX listing,
  no "Meshlit to USD" rate.
- Not tradeable on secondary markets. Tokens are not an asset class.
- Not required to use the app. Spark is fully featured.
- Not a stake / governance token. Voting is by node reputation (Phase 5).

**Implementation (Phase 4.5, build before public gateway ships):**

- `core-users` owns `TokenBalance`, `TokenLedger` (append-only),
  `HostingRewards` engine.
- Each node runs a `HostingRewardCalculator` that publishes its idle-
  credit and per-job serving credit into the cluster gossip.
- Anti-cheat lives in `core-firewall` + `core-guardrails`: detect fake
  "serving" claims, throttle earning on suspicious nodes, rate-limit
  earning per device (one phone = one node, ever).
- The home screen shows a single chip: "127 tokens — host to earn more."
  The token UI is never the center of the product. Hosting is.

**Marketing framing (use this language, not "earn tokens"):**

- "Your old phone can be a free AI server."
- "Run a 70B model across your friends' phones for free."
- "Your data never leaves your network."
- "Be part of the largest decentralized AI cluster in your country."

Tokens are invisible infrastructure that makes the network work. The
growth message is the value of the collective, not the value of a
credit balance.

### 2.8.1 Measurable resources that earn credits

Everything credited must be measurable. If we can't prove the
contribution happened from the receiving end, no credit is issued.

**Compute (the main category):**

| Resource | Measurement | Rate |
|---|---|---|
| CPU time | `Process.getElapsedCpuTime()` + heartbeat | +1 / 10 min served |
| On-device GPU time | llama.cpp `ggml_time_us()` matmul portions | +3 / 10 min (scarce) |
| eGPU time | ROCm / nvprof / Vulkan timestamps | +8 / 10 min (very scarce) |
| NPU time | vendor SDK telemetry | +4 / 10 min (premium) |
| RAM headroom | KB available for KV cache during serve | +1 / GB-hour |
| Storage read | per-job GGUF throughput | +1 / 5 GB served |
| Storage write | import + checkpoints | +0.5 / 5 GB |

**Network:**

| Resource | Measurement | Rate |
|---|---|---|
| Ingress | `TrafficStats` per interface | +1 / GB |
| Egress | `TrafficStats` per interface | +2 / GB (egress is scarcer) |
| WAN relay | Tailscale/WG accounting | +5 / GB WAN |
| Tunnel uptime | handshake freshness | +10 / hour |
| NSD advertisements | beacon counter, rate-limited | +1 / 1000 beacons |

**Specialty services:**

| Resource | Measurement | Rate |
|---|---|---|
| Embedding generation | per-token counter | +2 / 1k embed tokens |
| MCP tool invocations | server logs | +1 / 100 calls |
| Image generation (SD/FLUX) | per-image, resolution-bucketed | +20 / SD-1.5 image, +200 / FLUX-12B |
| Voice transcription (Whisper) | audio-seconds processed | +50 / hour of audio |
| Vision (LLaVA-style) | per-image | +10 / image |
| Custom LoRA training | per-step on someone else's data | +5 / step |
| MoE expert hosting | per-expert-id served | +10 / 1k tokens routed through this node's experts |

**Auxiliary (lower value, but real):**

| Resource | Measurement | Rate |
|---|---|---|
| Heartbeat uptime | gossip presence | +0.5 / hour online |
| Topology mapping | new peers brought into cluster | +5 / unique peer |
| Chipset DB entry | merged community submission | +100 one-time |
| Translation / docs | merged PR | +50 / PR |
| Vouched-onboarding | referred friend active 7 days | +50 one-time |
| Bug-report-with-fix | confirmed issue → merged fix | +200 one-time |

### 2.8.2 NOT creditable (no proof of impact)

- "I posted about Meshlit on Twitter" — no impact measurement.
- "I bought a new phone to run this" — up-front cost ≠ ongoing contribution.
- "I beta-tested the app" — unless structured bug reports with logs
  lead to a merged fix.
- "I introduced Meshlit to my company" — indirect attribution.
- "I keep my node plugged in 24/7" — covered by the +5/hour idle
  baseline; don't double-count.

### 2.8.3 Premium token packages (when Mind tier actually pays)

The growth phase is free. When Mind goes paid, the conversion has to
satisfy **"buying can never be cheaper than earning."** A user hosting
24/7 generates roughly +120 tokens/day, ~+3,600/month. So:

| Package | Price | Tokens | Effective rate |
|---|---|---|---|
| Starter (free, always) | $0 | 100 on signup + all earning | — |
| Hobbyist | $5/month | 5,000/month + earning | ~$1 / 1k tokens |
| Pro | $20/month | 25,000/month + 1.5× earning multiplier | ~$0.80 / 1k tokens |
| Team | $100/month | 150,000/month + custom model slots + 2× multiplier | ~$0.67 / 1k tokens |

### 2.8.4 Per-device earning cap (fairness floor)

Soft cap; past the cap the node still serves, but the marginal credit
rate drops to 0.1× until the next day.

| Node type | Daily earning cap |
|---|---|
| Phone (single SoC) | 200 / day |
| Phone + eGPU | 500 / day |
| Laptop / desktop peer | 800 / day |
| Server peer | 2,000 / day |
| MoE shard node | uncapped |

### 2.8.5 Anti-exploitation checks

- Earning claim with `TrafficStats` showing 0 bytes egress → flagged.
- MCP invocation claims with empty server logs → flagged.
- "Served 1M tokens" claim with empty `loadedModels` → flagged.
- One phone = one earning limit, by hardware-fingerprint-derived node ID.
- Buying a second phone doesn't double earning.

---

## 3. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| UI | Jetpack Compose (Material 3) | node topology, dashboards |
| Language | Kotlin | app + orchestration core |
| Discovery (LAN) | NSD (`NsdManager`) | always-available fallback |
| Discovery/transport (mesh) | Wi-Fi Aware (`WifiAwareManager`), Wi-Fi Direct fallback | feature-gated |
| VPN tunnel | Tailscale (or Headscale) + WireGuard fallback | Phase 4 |
| WAN relay transport | WebSocket or MQTT over TLS | small VPS, fallback only |
| Local HTTP/SSE server | Ktor | embedded per-node |
| Inference engine | llama.cpp via NDK (C++) | GGUF, quant-aware loading |
| Fine-tune | llama.cpp finetune + safetensors via NDK or pure-Kotlin | Phase 5 |
| Background execution | Foreground Service + WorkManager | see `skills/android-foreground-services/SKILL.md` |
| MCP tool servers | Native FGS + Ktor HTTP/SSE | see `skills/mcp-server-android/SKILL.md` |
| SSH/session host | Apache MINA SSHD or `cryptlib` over Ktor | LAN/Tailscale bound only |
| Firewall | Per-port accept-list + rate limit at listening socket | not iptables |
| Local storage | Room + DataStore (encrypted for users/secrets) | — |
| File transfer | Resumable chunked HTTP over the cluster transport | — |
| Guardrails | On-device regex + token-cap + PII regex; no external calls | — |
| Auth (users) | BiometricPrompt → in-app keystore | no cloud account |

---

## 4. Suggested module layout

```
app/                    - Compose UI, navigation, MainActivity, foreground-service starters
core-orchestration/     - node registry, router, job queue, policy engine, topology
core-discovery/         - NSD, Wi-Fi Direct/Aware, Tailscale/WireGuard, WAN relay client
core-trust/             - trust tiers, token issuance/validation, publickey pairing
core-users/             - local profiles, biometric unlock, per-user quotas
core-inference/         - llama.cpp JNI bindings, model manager, benchmark-on-join
core-mcp/               - MCP server implementations, tool handlers
core-training/          - LoRA fine-tune runner, dataset loaders, export pipeline
core-files/             - file browser, cluster transfer, model-cache sync
core-ssh/               - SSHD embedded in FGS, publickey auth, PTY over Ktor
core-firewall/          - per-port accept-list, rate-limit, default-deny
core-guardrails/        - prompt injection / jailbreak / PII filters, output caps
core-tunnel/            - Tailscale/WireGuard integration, tunnel lifecycle
core-common/            - shared models, capability probe, logging, sealed result types
```

Keep `core-inference`, `core-mcp`, `core-training`, `core-ssh`,
`core-firewall`, and `core-guardrails` independent of `core-orchestration` —
the router should depend on interfaces, not concrete engines, so a future
swap doesn't ripple through the whole app.

---

## 5. Per-phase validation checklist

Before moving to the next phase, confirm:

- [ ] Runs on a **physical device**, not just an emulator (thermal/battery
      behavior doesn't emulate).
- [ ] Survives the app being backgrounded for 15+ minutes without the
      service dying silently.
- [ ] A node going dark mid-job (airplane mode toggle is a good test) is
      detected within the staleness window and evicted, not queued forever.
- [ ] Nothing added in this phase silently defeats a principle in §0.
- [ ] Any new network exposure respects the trust tier in
      `skills/cluster-trust-security/SKILL.md` — nothing added in this phase
      opens a public port without TLS + signed-token auth.
- [ ] Any new data movement (model import, file transfer, training sync) is
      opt-in with a metered-connection warning where relevant.

---

## 6. Known hard platform limits (state these in-app, don't fight them)

- **No public bypass-charging API.** Detect and recommend; don't promise
  automatic control. Only some OEM gaming phones expose a manual toggle.
- **ADB over USB requires manual on-device authorization** per device, every
  time it's a new "control device" — cannot be scripted around.
- **`dataSync`/`mediaProcessing` foreground services cap at 6h/24h** on
  Android 15+ targets. Design for restart, not indefinite runtime.
- **No true multi-hop mesh routing in this build.** Single-hop peer
  discovery plus an encrypted tunnel (Tailscale/WireGuard) for long-distance;
  don't build phone-to-phone-to-phone relay.
- **No public SSH.** The SSH/session host in Phase 5 binds to LAN and
  Tailscale interfaces only, gated by trust tier. A standalone SSHD on a
  public port is explicitly out of scope — the existing no-public-SSH rule
  from CLAUDE.md is unchanged and Phase 5 SSH is *not* a workaround for it.
- **No iptables.** The "firewall" is a per-port application-level filter at
  the listening-socket layer. Genuine packet filtering needs root. State
  this in `Settings → Network → Firewall`.

---

## 7. Feature-area playbooks (added with the v1.1 expansion)

These short playbooks describe how the new feature areas plug into the
existing phases. They are *plans*, not commitments to ship them all — each
must respect principles in §0 before being marked done.

### 7.1 User management

- Local profiles only. Each profile owns: nickname, model library entries,
  job history, per-role default policy, and an optional biometric unlock
  gate. Encrypted with the Android keystore.
- "Single-user mode" (default) hides profile-switching entirely; the device
  is "the user's phone."
- "Multi-user mode" (for shared-pool deployments) lets a single device
  advertise multiple profiles to the cluster, each with their own trust
  posture. A profile is *not* a separate Android user — it's a role within
  one user account, scoped to Meshlit.

### 7.2 Cluster & device management

- Topology view (Phase 2): a 2D graph with this device at the center,
  discovered nodes arranged by transport (LAN ring, WAN ring, etc.).
- Per-device detail screen: hostname, role(s), transport in use, current
  RAM headroom, peak RAM during last inference, current thermal status,
  current job, kill/pause actions, "make primary" (promote to master),
  forced re-benchmark, and "remove from cluster."
- Cluster-wide toggles: auto-accept new nodes (default off — explicit
  pairing always required), default role policy, telemetry opt-in.

### 7.3 Network management

- Live transport view per node: which transport is currently carrying
  traffic, fallback chain that's been tried, current RTT estimate.
- IP allowlist editor for the `LOCAL_SANDBOXED` trust tier (Phase 3).
- Per-port firewall rule editor (Phase 3, see §7.4).
- One-tap network reset: clears stale NSD registrations, restarts Wi-Fi
  Aware sessions, refreshes Tailscale/WireGuard endpoints.
- Connectivity diagnostics: ping, DNS, gateway check, mDNS query for the
  service type, log recent dropped packets.

### 7.4 Local firewall

- Per-port rules on the inference and MCP server ports:
  - Accept from trust-tier allow-list.
  - Rate-limit per source IP (token-bucket, configurable tokens/sec).
  - Default deny anything else.
- Rules are stored as `FirewallRule(nodeId?, port, action, sourceMatcher,
  rateLimit?, ttl?)`.
- Audit log of accepted / dropped / rate-limited requests, viewable in
  Settings → Firewall.
- This is **not** iptables. State that in-app. Phones without root can't
  install a system-wide firewall; the Meshlit firewall only protects ports
  Meshlit itself listens on.

### 7.5 Security guards / guardrails

- Prompt-injection detection: regex + simple heuristics for known
  injection patterns ("ignore previous instructions," "system:", etc.).
  Flag, don't block — show a warning to the user and log the prompt.
- Jailbreak phrase filter for outbound tool calls: same approach.
- PII redaction toggle: a regex-based redaction on model output before
  display (emails, phone numbers, IBANs, etc.). Toggle per user profile.
- Output token cap per job: prevents a runaway model from blocking a node.
- "Locked-down" mode: disables the script-execution MCP tool entirely.
- All filtering runs on-device. No external API calls. No telemetry of
  blocked prompts.

### 7.6 SSH / session management

- Embedded SSHD inside the foreground service, NOT on a public port.
- Bound to LAN interfaces (`192.168.x.x`, `10.x.x.x`) and the
  Tailscale/WireGuard interface only.
- Publickey auth: key fingerprint is exchanged at pairing time, stored in
  the same `DeviceTrustPolicy` as the MCP and HTTP tokens.
- PTY over the cluster's existing Ktor WebSocket transport — no new port
  to manage.
- Session log on the host node (caller, timestamp, commands run, status).
- The "no public SSH" rule from CLAUDE.md and §6 still applies: the
  session host is *only* reachable from a node that has already passed the
  trust-tier pairing. This is a cluster-internal feature, not a remote-
  access backdoor.
- **User choice:** SSH is opt-in per device, default off. A non-technical
  user who never opens `Sessions` never sees a listening SSH port. A
  power user turns it on, picks the trust tiers that may connect, and
  gets a full remote shell — the system never silently enables it.

### 7.7 File management

- In-app file browser scoped to app-accessible directories (per-user home,
  shared model cache, dataset folders).
- Browse, copy, move between cluster nodes over the shared transport.
  Resumable chunked transfer; checksum-verified at the end.
- File-type recognition: GGUF, safetensors, JSON config, log files, plain
  text. Each gets a dedicated icon and a "use with" action (e.g. "Load as
  model", "Open as dataset").
- Per-user folder quota (default 2 GB; configurable).
- Per-transfer progress with pause/resume.

### 7.8 Model import sources

| Source | Mechanism | Notes |
|---|---|---|
| HuggingFace URL | Resolve tree, fetch GGUF files | user must provide HF token if repo is private |
| Ollama registry | Parse `ollama://` URL, fetch manifest | |
| Direct URL | HTTP/HTTPS download | must end in `.gguf`, `.bin`, or known extension |
| Local file picker | SAF (Storage Access Framework) | large files use SAF streaming |
| Another cluster node | Existing shared cache + chunked transfer | quickest path for offline clusters |

- All downloads: SHA-256 checksum verification, partial download resume,
  size pre-flight against the node's free space.
- Explicit consent for metered connections
  (`ConnectivityManager.isActiveNetworkMetered()`) — show an
  estimated cost warning.

### 7.9 Custom training / fine-tuning (LoRA + export)

- LoRA / QLoRA fine-tune of small base models on-device. Runs on the most
  capable node in the cluster (chosen by capability probe + current
  load).
- Dataset input: JSONL (instruction format), text folder (one file per
  document), CSV (with prompt/completion columns).
- Configuration: epochs, LR, batch size, rank, alpha.
- Live progress: loss curve, current epoch, ETA, memory headroom.
- Pause / resume / cancel supported.
- Export formats:
  - **GGUF** for inference, loaded by llama.cpp on any cluster node.
  - **safetensors** adapter for portability with other tools.
  - **Meshlit bundle** (`.meshlit`): a tar with the GGUF, the adapter,
    the dataset hash, training config, and a manifest. Importable on
    any cluster node.

### 7.10 Tailscale / WireGuard tunnel

- Tailscale: in-app auth-key flow, node's Tailscale IP becomes its
  cluster `NodeId` over WAN. Headscale (self-hosted coordination server)
  supported via custom control URL.
- WireGuard: manual config — private key, public key, endpoint, allowed
  IPs. Single tunnel per node. Quick-config from a QR code.
- Replaces the Phase 4 plain-relay path with an encrypted mesh where
  available; relay remains as fallback for nodes that can't tunnel.
- Tailscale/WireGuard state is shown in the Network screen alongside
  NSD/Wi-Fi Aware/Wi-Fi Direct.
- Always opt-in per node — VPN installation shouldn't be silent.
- **User choice:** the Network screen offers four transport options as
  toggleable cards — *NSD/LAN*, *Wi-Fi Direct*, *Tailscale*, *WireGuard*
  — each with its own consent flow (Tailscale wants an auth key, WG
  wants a config, etc.). The relay is a fifth option for nodes behind
  firewalls that block everything else. Nothing is enabled until the
  user explicitly turns it on. If a node later roams to a different
  network, the previously-enabled transports come up automatically but
  the *capability scope* still requires the user to re-confirm on first
  re-join — defense against silent privilege changes.

---

## 8. Known limits of this expanded scope

To stay honest about what's feasible:

- **Fine-tuning is bounded by RAM.** A 7B base model in Q4 (~4 GB) plus
  a LoRA adapter plus optimizer state won't fit on a 6 GB-RAM phone. The
  training UI must refuse to start a job it can't fit and propose
  quantization. This is why the spec runs training on "the most capable
  node in the cluster" — phone-cluster training is a small-model +
  cooperative-memory operation, not a frontier-model operation.
- **HuggingFace private repos need a token.** Storing it locally is fine
  (Android keystore), but the user has to obtain it. Document this.
- **Tailscale on a work-profile / MDM-locked phone** will fail. The
  fallback (WireGuard or plain relay) covers that case; the UI explains
  why.
- **"Export custom training models" can only export what was trained
  locally.** Re-exporting a model that was downloaded and never trained
  is just "copy the GGUF," which already exists.
- **The local firewall is per-listening-socket, not system-wide.** Phones
  can't install a real packet filter without root. State this.
- **No code signing of models.** A user can import a model from anywhere
  and we can't know if it's poisoned. Treat unverified models as
  untrusted input — same as any other file the user chose to download.