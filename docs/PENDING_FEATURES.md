# Pending and unfinished Meshlit work
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-23. This is a current delivery checklist, not a claim that a visible menu implements its backend. Detailed Android and Studio evidence lives in [PROGRESS](../PROGRESS.md), [TODO](../TODO.md), and the [Desktop feature tracker](DESKTOP_FEATURE_TRACKER.md).

## First: prove the device experience

- Qualify the current QR scan, save, verify, connect, reject, renewal notification and pairing history on Samsung and Xiaomi. Retest failed and concurrent requests, expiration, revocation, owner offline, IPv4 and IPv6 endpoints, and live status without leaving the screen.
- Finish screen-by-screen Android layout review on both phones, including larger fonts, landscape, light and dark palettes, long names/IDs and dialogs. Build/lint success alone does not prove every screen fits.
- Make local device discovery and device category identification reliable across Wi-Fi, BLE and IPv6; general LAN inventory is separate from Meshlit peer advertisements and is not implemented.
- After mutual pairing approval, exchange fresh authenticated capability and load offers on both phones, then add an owner-controlled Auto / On / Off policy for using approved peer resources. Automatic discovery currently defaults on while the app is visible, but it does not automatically choose a peer for model work or grant access to its sensors, tools or device controls.
- Add an Ansible-like Meshlit fleet configuration plan: select approved devices and typed settings, preview each device's change, bind the approved plan hash and permissions, apply with per-device status, record partial failures and offer a separately reviewed recovery plan. Existing host-side Ansible design and pinned SSH are not an implemented Meshlit fleet controller; agents need their own scoped delegation and cannot silently apply scripts to the whole cluster.
- Validate Radar's saved/live indexed dots, pulse, device table, identifier toggle and animation on both phones. Samsung rendered the new offline Natural Earth map and opt-in OpenStreetMap tile layer. The map also has local-only current-position centering and an optional IMON HTTPS public-IP lookup; its server URL and a public IP must be supplied by the user, so no remote lookup has been accepted yet. Both V1 and V2 debug flavors use the modern UI, while the separately restricted Core Candidate and desktop editions need their own acceptance. The global host map uses owner-set approximate coordinates shared only inside one joined or hosted relay; it is not a public worldwide directory, GPS tracker or verified proof of origin. Add consented cross-relay directory federation and stronger host status reporting before claiming global search.
- Demonstrate two independent phones executing one model across actual layers, including oversized weights, throughput, worker loss and safe retry. Saved devices and job forwarding are not this proof.
- Build monetary-grade metering and privacy-gated reward proofs. The local wallet opt-in, durable self-signed receipt totals and live process counters are now visible in Contribution measurements, but energy, bytes, requester confirmation and cross-signed proof sharing are absent. Do not spend, rent, exchange, publish raw logs or auto-enroll participants from these local credits.

## Android product gaps

- Finish durable multi-device mesh conversations: participants, rooms, message sync, offline queue, history, delivery receipts and safe device removal. Current peer chat is narrower.
- Complete remote relay/reverse tunnel and automatic transport negotiation across LAN, VPN and Internet; test NAT/firewall behavior and mixed local/remote clusters. The built-in relay does not yet carry native layer RPC.
- Add confirmed inbound IPv6 hosting, advertisement and firewall coverage. Manual/QR endpoint formatting can accept IPv6 URLs, but the current inference listener binds IPv4 by default.
- Add robust QR lifecycle: permanent versus expiring codes, image save/share/delete, rotation and invalidation, camera/live/gallery tests, and clear pending-request queue controls.
- Complete compatible model catalog, hardware admission, verified download/import/load quality checks, routing and meaningful local/remote model choices on both phones. Distinguish sequential routing from layer sharding.
- Finish agent/MCP/hook authority and real parallel execution acceptance: per-tool scopes, expiry, revocation during work, approval for sensitive actions, durable audit and recovery. Background control remains bounded by Android permissions and owner choices.
- Expand user-selected RAG sources, connectors, visual input, live camera and voice with tested permissions, provenance and privacy indicators. Training/fine-tuning needs a compatible external runtime; Android autograd training is not implemented.
- Qualify battery, thermal, resource and CPU charts under real load, power policy, diagnostics and repair/rollback, plus accessibility and settings persistence across upgrades.

## Studio, server and container gaps

- Port Android device discovery, QR/trust management, groups, mesh chat, layer-worker enrollment, native RPC controller and dashboard with independent desktop/server tests.
- Finish agent/MCP/A2A/hooks, durable permission controller, credential vault, audit, autonomy stop/revoke, RAG/memory/voice and cloud adapters. Current menus are not equivalent to these backends.
- Finish model download resume and entitlement checks, native Ollama/LM Studio lifecycle, full terminal/SSH workflows, clean package installation and longer running stability tests.
- Add qualified container/VM/Kubernetes control, firewall, resource/power metering, recovery/failover and replicated task/KV journals. Linux, Windows and Apple Silicon packages require their own runtime checks.

## Rewards and release gates

- Move from local activity credits to measured service units, reciprocal receipts, offline reconciliation, replay/collusion checks and dispute handling before balances can represent monetary value.
- Complete payment/legal/processor work before purchases, rental, exchange or payouts.
- Production signing, privacy/dependency checks, clean-target install, physical fleet tests and release artifact verification remain open. Experimental debug builds must be labelled accordingly.
