# Meshlit Core candidate and Experimental

The owner requested production hardening and research features to remain separate,
with offline chat first, multi-device inference/recovery second, and Internet hub
access third. These are acceptance stages, not a production certification.

## Installable separation

`meshlitV2ProductionCandidate` installs as `com.meshlit.v2.production`, labelled
**Meshlit Core Candidate**. `meshlitV2Debug` remains
`com.meshlit.v2.debug`, labelled Meshlit v2, with **Experimental** in its sidebar.
Android keeps each package's settings, chats, consent, models and keys separate.
No migration copies secrets or acceptance between them. Both candidate/debug
artifacts are developer-signed; production signing remains an owner release gate.
V1 can also build the same channels; its older UI is not restored.

The Core candidate permits local inference, model transfers and user-selected file
operations. An immutable admission policy blocks experimental managed operations
even after saved-policy changes or Resume. The modern menu filters unavailable
routes; local chat refuses hosted routes and model-directed tools. Its manifest
removes VM, SSH-node, pipeline, OpenClaw, cloud MCP, web bridge, accessibility and
capture services. Startup omits hooks, agent registration, MCP/discovery and peer
listeners. Core still contains shared inactive classes/dependencies; it is **not**
a separate minimal binary or an OS sandbox for that code. The optional HyperL
module's separate licence is retained in every package that contains it.

## Qualification stages and devices

| Stage | Candidate scope | Required proof before a production claim |
| --- | --- | --- |
| 1. Local chat and storage | Core candidate | Fresh install/update; model hash; real offline generation; large-reply/keyboard/accessibility checks; atomic history/model recovery; restart, cancellation, low-memory/thermal endurance; actual SDK network opt-out; maintained OS/API/ABI matrix; production signing. |
| 2. Layer inference and recovery | Experimental until qualified | Two/three physical nodes, pinned transport, output equivalence, fresh capability evidence, memory and sustained thermal measurements, disconnect/revoke/rejoin, committed compatible KV state, worker fencing, duplicate-action prevention. Host tests alone do not qualify phones. |
| 3. Internet client hub | Experimental until qualified | LAN and private Internet transport, invitation/identity/scoped token expiry/revocation, per-client quotas, replay/authorization tests, actual remote client traffic, restart/Stop, logs without secret values, documented exposure and incident/update procedure. |

Category is an inventory label, never evidence of executable capabilities:

| Devices | Permitted contribution after owner approval and capability proof |
| --- | --- |
| Android phones/tablets | Compatible local text runner; qualified layer workers; explicit storage, camera/audio/sensor/tool adapters. Phone model/Android/ABI/RAM must be measured. |
| Windows/Linux/macOS PCs and servers | Clients; installed SSH/tool/node companions; compatible measured compute workers. OS, RAM, CPU/accelerator and actual runtime determine eligibility. Windows SSH availability and shell syntax cannot be assumed. |
| HarmonyOS NEXT phones/tablets/2in1 | Separate native ArkTS 26.0.0 source preview. Host-client source only; no qualified HAP/NDK/local LLM or physical device yet. English/简体中文 and visual settings are independent of Android APKs. |
| Raspberry Pi/other SBCs | Clients/storage/tools or measured compatible CPU workers; no automatic accelerator claim. |
| NAS/router/switch/firewall | Only the storage, routing, monitoring or tool interfaces actually installed and approved; enrolment does not install an LLM runtime. |
| Microcontrollers/sensors/radios | Explicit sensors/actuation/relay adapters; never transformer workers merely because they join. |
| Browser clients | Authenticated hub client; not native layer execution without a separately implemented runner. |

The owner identified this Mac, Samsung Galaxy A20s, an older Windows PC and one
Xiaomi phone. Exact Windows/Xiaomi configuration and current connections remain
unknown. No device is invented, enrolled, or marked qualified from this list.

The RunAnywhere development SDK's observed telemetry attempt remains a production
privacy gate (`remaining-engine-bugs.md`, `PRIVACY_POLICY.md`). Selecting local
weights or this build channel does not establish zero network traffic.

## Multiplatform preview boundary

Build 39 begins an incremental KMP contract/parser extraction and experimental
Compose desktop GUI/JVM CLI. It does not promote the app, port Android services
or qualify a new platform. Native HarmonyOS NEXT is a separate 26.0.0 ArkTS/NDK
source target. No NEXT SDK/device is available; its local runtime explicitly
reports unavailable. See ../MULTIPLATFORM_NEXT.md for exact source versus proof.
