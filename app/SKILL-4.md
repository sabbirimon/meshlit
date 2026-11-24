---
name: cluster-trust-security
description: The role-based trust-tier security model for cluster nodes — how auth, tokens, and exposure differ between same-Wi-Fi trusted nodes, temporary/untrusted local nodes, and WAN/cellular nodes reachable over the open internet. Use this for any task touching authentication, SSH, tokens, the WAN relay, or exposing any port/service to another node — this determines how much trust burden a given task needs, and getting the tier wrong is a real security gap, not a style choice.
---

# Cluster Trust Tiers

The single biggest risk in this project isn't the AI/inference code — it's
treating a phone reachable over the open internet (any WAN/cellular node)
with the same casual trust as a phone on your home Wi-Fi. Use three tiers,
not one global flag.

## Tier 0 — Local, trusted (same Wi-Fi, paired once)

- Implicit trust after a one-time pairing step (e.g., SSH key exchange, or
  a simpler pre-shared token generated at pairing time).
- No per-request auth overhead needed — this is the normal path for a
  user's own device pool on their own network.
- Still: log connections and job dispatches. "Trusted" means low friction,
  not unmonitored.

## Tier 1 — Local, untrusted (e.g., a guest's phone joining temporarily)

- Sandboxed role only — read-only status/monitor role by default, no
  filesystem, shell, or model-management access.
- Revocable, short-lived token issued at join time, not a permanent
  pre-shared key.
- Every capability this tier can access should be an explicit allow-list,
  not "everything except an explicit deny-list."

## Tier 2 — WAN / cellular (reachable over the open internet)

- Full TLS required — no plaintext local-network shortcuts here, because
  this traffic leaves the LAN.
- Signed, per-request or short-lived tokens, not a static pre-shared key —
  a cellular node's credentials are more exposed by nature of being
  internet-reachable at all.
- Capability-scoped explicitly per node ("this node may only serve as an
  MCP tool for web-search, nothing else") — never grant a WAN node the same
  role set a local node gets by default.
- **Never expose a raw SSH port or an MCP server directly to the internet.**
  All WAN traffic goes through the relay service (small VPS or cloud
  endpoint under your control) over a persistent authenticated
  WebSocket/MQTT-TLS connection. The relay is the only thing with a public
  IP; individual phones never are.
- Explicit opt-in per node, with a data-usage warning gated on
  `ConnectivityManager.isActiveNetworkMetered()` — a WAN node syncing model
  weights or verbose logs over someone's cellular plan without clear
  consent is a real harm to avoid, not just a UX nicety.

## Data model

Store trust configuration as a per-device policy object, not a single
boolean:

```
data class DeviceTrustPolicy(
    val nodeId: String,
    val trustTier: TrustTier,        // LOCAL_TRUSTED, LOCAL_SANDBOXED, WAN
    val allowedRoles: Set<ClusterRole>,
    val tokenExpiry: Instant?,       // null for Tier 0's long-lived pairing
)
```

This is what lets a single device carry different effective trust depending
on how it's currently connected (e.g., a phone that's normally Tier 0 on
home Wi-Fi but should drop to WAN-tier scoping if it ever connects in over
cellular instead).

## What NOT to do

- Don't build a single global "cluster password" — it collapses all three
  tiers into one, and a leaked credential then grants WAN-level exposure to
  everything.
- Don't skip TLS for WAN traffic "for now" — retrofit is much harder than
  building it in from the start of Phase 4, and there's no safe intermediate
  state for internet-reachable device control.
- Don't build real distributed consensus (Raft-style leader election) for
  master failover in v1 — it's a lot of complexity for a handful of
  devices. If the master node disappears, pause the cluster and let the
  user manually promote another node; automatic leader election is a
  reasonable v2 feature once the manual flow is proven.

## Testing

For any change touching this area, verify: a Tier 1 (sandboxed) node
genuinely cannot reach a Tier 0-only capability (don't just check the UI
hides the option — confirm the backend rejects the request). For Tier 2,
verify that killing the relay connection actually prevents the WAN node
from being routed to, rather than falling back to some other undefended
path.
