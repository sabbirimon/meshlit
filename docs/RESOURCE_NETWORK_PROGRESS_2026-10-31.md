# Open resource network: source checkpoint and continuation
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-31. Repository: `meshlit-unified`. Branch:
`checkpoint/desktop-agents-settings-20261031`; previous checkpoint `5a2e97c`.
The owner requested save-all with 8% weekly allowance shown. Do not infer a live
balance or exact token count. No further scope expansion is needed to resume.

## Product direction preserved

Everyone can participate, regardless of company size. Intended resources include
compute, cache/storage, bandwidth and sensors, for Meshlit and compatible external
clients. Local/WAN single devices, multiple devices/clusters and mixed deployments
are in scope. USB-C, Ethernet/fiber/LAN, Wi-Fi, Bluetooth/radio, NFC, sound/light/QR
are possible carriers, subject to actual adapters and hardware evidence.
See [complete architecture and acceptance gates](OPEN_RESOURCE_NETWORK.md).

A capable desktop may coordinate workers with independent limits. Approved mobile
or other nodes should eventually take over safely after failure. Role election,
resource admission, communication routing, replica commits and model execution
are distinct. Automatic failover is not claimed from planner or journal tests.

## Implemented source in this increment

| Area | Actual change | Important boundary |
| --- | --- | --- |
| Model/shard admission | Exact storage ranges, cumulative per-peer disk/RAM, invalid-size bounds, SDK slice rejection | Storage/prototype plans are not native layer execution |
| Active Android native pipeline | `-ngl all` replaces99; immutable per-launch admission; post-lock age/group/grant checks | Physical oversized/WAN execution remains unqualified |
| Pairing | Full signed-proof replay window and issuer-identity session invalidation; self requests rejected | No new radio/Bluetooth/USB drivers |
| Peer load distribution | Fair selection among fresh trusted healthy peers; bounded forwarding hops; no implicit reconnect replay | No measured reservations/GPU guarantee; upgraded peers required |
| External participation | Authenticated scoped resource description, persisted finite request allowance and MCP model/token checks | No payments, resource rental or capacity guarantee |
| Studio | Protected external-resource inspector with Stop/Clear, bounded HTTPS/loopback requests and no redirects | Client/catalog feature; not a desktop native cluster controller |
| Sync/communication | Alternative paths for same replica; ADAPTIVE/PRIORITY modes, observed cooldown/latency, bounded read fallback | Ambiguous writes sent once; no extra votes from alternate paths |
| Recovery visibility | Secret-free path request counters and outcomes | Session observations, not usage/earnings or automatic resume |
| Corruption handling | Strict exact transfer size, mandatory full SHA, bounded refetch, verified partial resume with trusted chunk digests, old output retained | Missing integrity metadata blocks legacy cluster acquisition; no arbitrary memory repair |
| Health-aware work | New-load native CPU caps from observed thermal/power/battery/memory; severe conditions block new heavy loads | Existing owner caps preserved; active contexts/workers are not migrated |
| Optional protection | Saved local prompt-pattern heuristic; separate owner delegation and scoped agent toggle | Not antivirus; mandatory identity/integrity/permission controls remain |

## Validation record

Final source tests passed: **1,192 tests** —323 core-inference,150 core-MCP,
17 core-trust,55 core-federation,12 desktop-engine,278 Android per flavor and
79 Studio. No failures/errors/skips in these suites. Both Android debug variants
assembled before the final optional guardrail controls; final assemblies are
running at this save point. The replica rejoin test now checks cooldown with an
injected clock, then proves real socket catch-up. An earlier missing import was
fixed before the successful core run. [Machine-readable evidence](desktop/evidence/resource-network-20261031.json).

Full lint, portable desktop packaging, installed-device voice/health behavior,
physical pairing and LAN/WAN oversized-model execution were not qualified here.
No app installed, release published, paid account used or public listener started.
Prior upstream advisories remain unresolved; no malware/zero-day-free claim.

## Where to resume

1. Read this checkpoint, then `OPEN_RESOURCE_NETWORK.md`, `USER_MANUAL.md` and
   `SMALL_MODEL_MAINTENANCE_GUIDE.md`. Existing earlier voice/packaging gates remain.
2. Run final missing lint/device checks before treating an APK as qualified.
3. Implement/qualify the desktop native coordinator separately; it currently
   shares planning sources, not a complete app-owned distributed executor.
4. Add owner-enforced reservations and hardware/software health observations with
   hysteresis; never exceed a worker's cap or treat unknown telemetry as healthy.
5. Qualify single/LAN/WAN execution before hierarchical cluster orchestration.
6. Complete fenced coordinator takeover, compatible committed-state recovery and
   idempotent external effects before automatic compute resume.
7. Add and qualify carrier adapters and real external buyer metering/settlement.
   Prompt patterns are only one layer; real malware scanning/isolation is separate.

Verification commands (JDK21 + configured Android SDK):

```sh
python3 scripts/prepare-bundled-model.py
./gradlew --no-parallel --max-workers=2 :core-inference:testDebugUnitTest :core-mcp:testDebugUnitTest :core-trust:testDebugUnitTest :core-federation:testDebugUnitTest :desktop-engine:test
./gradlew --no-parallel --max-workers=2 :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
./gradlew --no-parallel --max-workers=2 :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
node --test companions/studio-runtime/extension/*.test.cjs
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
```

Logs: `/tmp/meshlit-resource-final-tests.log`,
`/tmp/meshlit-resource-app-checkpoint.log`, `/tmp/meshlit-resource-save-checks.log`,
`/tmp/meshlit-resource-studio-tests.log`. Logs are local; commit only reviewed
summary evidence, not generated APKs, credentials, models or runtime caches.
