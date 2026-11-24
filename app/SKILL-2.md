---
name: mesh-networking-android
description: Device discovery and local transport options on Android — NSD/mDNS, Wi-Fi Direct, and Wi-Fi Aware (NAN), including feature-gating for devices that don't support Wi-Fi Aware, and why this project uses single-hop discovery instead of true multi-hop mesh routing. Use this for any task involving finding nearby devices, choosing a transport, building the transport-abstraction layer, or debugging why a node isn't showing up in the cluster.
---

# Local Discovery & Mesh Transports on Android

Three real options exist for finding and talking to nearby Android devices
without a cloud round-trip. Use them as tiers with fallback, not as a single
choice — the device pool in a real cluster (mixed OEMs, mixed Android
versions) won't uniformly support the fancier options.

## Tier 1 (always available): NSD / mDNS over regular Wi-Fi

- Uses `NsdManager` to register/discover services on the existing LAN — no
  special hardware feature required, works with every device on the same
  Wi-Fi network/router.
- Highest reliability, best throughput of the three options (bounded by
  normal Wi-Fi, not a peer-to-peer protocol's overhead).
- **Build this first and treat it as the permanent fallback**, even after
  Wi-Fi Direct/Aware are added — every node should register via NSD
  regardless of what other transports it also supports.

## Tier 2 (opportunistic): Wi-Fi Aware (NAN)

- Purpose-built for local discovery + data paths without an access point,
  lower power draw than Wi-Fi Direct.
- **Not universally supported** — check
  `packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)`
  before attempting to use it, and fall back silently to NSD if absent. Do
  not assume every phone in a mixed hardware pool has this.
- Prefer this over Wi-Fi Direct when available — better suited to a
  many-small-devices topology than Wi-Fi Direct's group-owner model.

## Tier 3 (fallback when no AP is present): Wi-Fi Direct

- True peer-to-peer, doesn't need a router — useful for a cluster with no
  shared Wi-Fi network available.
- Group formation (electing a "group owner") can be finicky in practice and
  doesn't scale gracefully past a handful of devices — treat this as a
  fallback for small ad-hoc groups, not the primary transport for a larger
  cluster.

## Transport abstraction layer

Build one interface (`ClusterTransport`) with implementations for each tier,
and a selection policy: attempt Wi-Fi Aware → Wi-Fi Direct → plain NSD/LAN,
per node, based on what that specific device supports. The router and job
queue should never know which transport a given node is actually using —
only the transport layer cares.

```
interface ClusterTransport {
    suspend fun discover(): Flow<NodeInfo>
    suspend fun connect(node: NodeInfo): Connection
    fun supportsFeature(): Boolean
}
```

## What this project explicitly does NOT build

**No multi-hop mesh routing.** True mesh (packets relaying phone → phone →
phone to reach a device outside direct range) is a substantial
distributed-systems project on its own — routing table maintenance, loop
prevention, etc. For a home/local cluster, single-hop discovery covers the
realistic use case (all nodes within Wi-Fi/NAN/Direct range of each other or
of a common access point). If a task description implies building
multi-hop relay, flag it — it's out of scope for this build and should be
reframed as "add a WAN relay node" (Phase 4) instead, which solves the
long-distance case differently and far more simply.

## Debugging checklist when a node doesn't appear

1. Confirm the node's NSD registration actually succeeded (log the
   `RegistrationListener` callbacks, don't assume).
2. Confirm both devices are on the same Wi-Fi subnet if using LAN/NSD —
   guest networks and 5GHz/2.4GHz band isolation on some routers silently
   block mDNS between bands.
3. If testing Wi-Fi Aware, confirm `hasSystemFeature` actually returned true
   on *that specific device* — don't assume based on chipset generation
   alone, it varies by OEM build.
4. Check the heartbeat/staleness logic isn't evicting a node that connected
   successfully but is slow to respond — a slow first response shouldn't
   look identical to "never connected" in the logs.
