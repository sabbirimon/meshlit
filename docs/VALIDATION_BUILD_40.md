# Build 40 validation — 9 October 2026

Build 40 adds compact Studio styling, shared Kotlin workspace contracts, a desktop
host client, native HarmonyOS NEXT project source, optional Colibri hosting,
GibberLink with an English transcript, offline cryptography, and scoped peer/node
tools. These features retain Experimental boundaries. Core Candidate remains a
separate Android package with its earlier local-operation restrictions. The
existing local model, prompt, history and normal output defaults remain intact.

## Executed checks

| Targeted JVM suite | Cases | Failures/errors/skips |
| --- | ---: | ---: |
| shared-workspace | 4 | 0 |
| desktopApp | 9 | 0 |
| core-gibberlink, actual host JNI | 2 | 0 |
| core-mcp | 137 | 0 |
| core-inference | 271 | 0 |
| app V1 Experimental | 191 | 0 |
| app V2 Experimental | 191 | 0 |
| **Total** | **805** | **0** |

This is an affected-suite count, not a claim that every repository test reran.
The first combined unit/APK/lint chain passed in 56m54s. The final Kotlin source,
instrumentation compilation, three Android lints, desktop tests and native Mac
image chain passed in 22m15s. The final policy/package refresh passed in **18m52s**, synchronizing offline
notices, running the new pre-build policy guard and explicitly retaining the EC
TLS provider in the desktop runtime.
Fatal lint is enabled. Experimental lint has zero errors/fatal, 374 warnings and
18 hints per flavor; Core Candidate has zero errors/fatal, 377 warnings and 18
hints. These warnings remain a quality backlog.

The actual ggwave JNI codec passes generated audible PCM round trips, silence,
bounds and stale-handle checks on this Mac. Four Android ABIs are compiled and
packaged. This is codec proof, not microphone/speaker, acoustic-range or two-phone
communication proof. The native Android codec, resource-language and existing
reply/token settings instrumentation compile; none has run on build 40 yet.

The actual Compose/Skiko renderer passes an offscreen workspace check. The bundled
Intel macOS launcher passes trusted ECDHE TLS 1.2 and bearer authentication against
a loopback model-metadata fixture and rejects an untrusted certificate, with no
external Java on PATH. This fixture does not generate a model response. Desktop
client tests cover real TCP responses, bounded SSE, cancellation and failure
contracts. Windows/Linux packaging, signed distribution and an external model
host remain unqualified.

The browser peer fixture passes signed manual pairing, rejection of an altered
description, direct ICE connectivity, Unicode text, replay rejection and closure
on this Mac. Android uses an app-owned WebView, but that packaged path has not
been exercised. No long-distance/NAT traversal, TURN relay, remote cluster command
or independent-device success is inferred from the browser fixture. Received peer
text remains untrusted and never becomes an executable command automatically.

Pinned Colibri source compiles its CPU executable, answers its help command and
passes 59 registry checks. No Colibri checkpoint, generation, GPU execution or
phone-cluster memory pooling was tested. The Android HTTP adapter has five real
MockWebServer contract tests. Off/On/Auto and agent switching remain independent
of host enrollment and secrets.

Feature-map validation passes for 88 areas, 38 durable operations and 37 modules.
Both HyperL provenance checks pass. NEXT validation passes 11 strict project JSON
files and source-boundary checks. DevEco/26.0.0 SDK, ArkTS/NDK compilation, HAP
signing, simulator and physical NEXT hardware are unavailable; no NEXT binary or
local runtime success is claimed.

## Exact corrected Android artifacts

| Universal APK | Bytes | SHA-256 |
| --- | ---: | --- |
| meshlit-v1-build40-experimental.apk | 227,027,223 | `38035e0093d194d2edaade861f0a7a78479c964689fac709ec96c89f962fa773` |
| meshlit-v2-build40-experimental.apk | 227,027,249 | `c37f0067e4c456f540aaa14c1027426544a87e0257cbe598ecedc69fb6d2c778` |
| meshlit-v2-build40-core-candidate.apk | 208,273,589 | `41d39e3cb1a9dc4301bd483a24f14de659e1bbc613f46c64d151ceef97dc13d3` |

All three manifests report versionCode 40. Developer signatures verify against
the retained signing identity, certificate SHA-256
`1e9e5cf146c9a4cb7035d79b1eced1c0b298b0ca0d9080a887c8ab085f1ddf60`.
They contain the exact pinned 105,454,432-byte starter model, current help, peer
page and separate HyperL/SSH/Colibri/ggwave notices. Four-ABI HyperL and ggwave ELF
alignment, all 25 ARM64 native libraries and ZIP 16 KiB packaging checks pass.
Core native bytes match Experimental. This does not qualify a 16 KiB device
runtime or production signing.

An initial build-40 package inspection caught stale offline Terms/Privacy assets.
That failure is preserved in the private validation ledger. Corrected packages
above contain byte-identical version **2026-10-09.2** documentation/assets. The new
`verifyOfflinePolicies` pre-build task checks both copies and the agreement-store
version so this mismatch fails future builds. Updated notices require renewed
human acceptance; tests do not check consent boxes or grant optional access.

Static Core checks pass: its label/package are separate, debuggable is false,
only the expected three services survive, forbidden permissions are absent, and
model/notices/policies match. Inactive shared classes/dependencies remain packaged;
Core Candidate is not an OS sandbox or a minimal independent binary.

Verified universal installers and the device-test APK are retained outside the
checkout with SHA-256 checksums. Removing nine preliminary build-39 installers
and duplicate generated ABI splits reclaimed 1,694,822,255 bytes. Removing four
regenerable native/asset merge-copy directories after packaging reclaimed another
3,447,212,051 bytes: **5,142,034,306 bytes (4.79 GiB)** total. Compiled classes/dex,
the bundled desktop image, build-38 fallback, source, model, private keys and
validation evidence remain. Gradle recreates removed payload copies when needed.

## Device and production gates

Samsung was requested over USB but remains absent from ADB. The current ADB log
reports macOS USB interface denial; the owner was asked to restart ADB in normal
Terminal and approve any phone-side debugging prompt. No build-40 APK installation,
renewed notice acceptance, phone screenshot, microphone/speaker, live voice, model
generation, SSH/VM command or two-device test is recorded. Earlier Samsung model,
voice and UI evidence remains historical.

Before production, resolve the model SDK's documented development telemetry/network
opt-out; qualify lifecycle, storage, cancel/restart, low-memory, thermal,
accessibility, supported device categories, signing and remote authorization.
Then qualify physical multi-node inference/recovery and Internet clients. User
and agent grants default off and cannot authorize themselves. Browser WebRTC is
peer messaging and bounded node actions, not distributed model-layer transport.

See [production channels](architecture/PRODUCTION_CHANNELS.md),
[Colibri](COLIBRI.md), [GibberLink](GIBBERLINK.md),
[peer/cryptography boundaries](P2P_AND_CRYPTO.md), and
[desktop/NEXT](MULTIPLATFORM_NEXT.md) for setup and explicit limits.
