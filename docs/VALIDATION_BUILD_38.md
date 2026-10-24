# Build 38 validation — 9 October 2026

This change integrates separately licensed HyperL alpha.6, introduces a separate
Core Candidate package and adds bidirectional scoped SSH node commands with
revocable agent VM grants. It preserves the modern reply UI and ordinary local
model/prompt/history/streaming defaults. Core Candidate is not production qualified.

## Checks and scope

The final affected-app chain passes in **16m18s**: V1/V2 unit tests, APKs, V2
instrumentation compilation, all three lints and the targeted Core boundary test.
The final guide/notice-only packaging refresh passes in **2m04s**. Earlier
packaging and removal-marker lint errors were fixed; fatal lint remains enabled.

| Targeted JVM suite | Cases | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| core-hyperl, real host JNI enabled | 18 | 0 | 0 |
| core-common | 28 | 0 | 0 |
| core-ssh | 11 | 0 | 1 |
| core-mcp | 134 | 0 | 0 |
| core-sandbox | 14 | 0 | 0 |
| core-net | 46 | 0 | 0 |
| core-inference | 266 | 0 | 0 |
| app V1 Experimental | 188 | 0 | 0 |
| app V2 Experimental | 188 | 0 | 0 |
| app V2 Core Candidate boundary | 1 | 0 | 0 |
| **Total** | **894** | **0** | **1** |

893 cases pass. The single skip is the older optional external live-SSH fixture.
Both new SSH tests execute real loopback TCP handshakes and pinned JSch clients:
public-key authentication, wrong key/fingerprint/password denial, action scope,
revocation and Stop cancellation of an authenticated in-flight command. They do
not qualify Android/OEM hosting, a remote device or Internet transport. Only the
single targeted boundary test was selected for Core Candidate, not all app tests.
Core suite evidence was retained after later changes confined to the app.

Actual host C99 contract and 4,096 seeded stress cases pass with AddressSanitizer
and UndefinedBehaviorSanitizer. macOS leak detection was disabled; this is not
leak proof. Real host JNI tests cover this module's compiled implementation.
Android instrumentation for native execution and workbench UI compiles but was
not run. The optional HyperL licence is not accepted automatically by tests.

The nine crawler unit cases pass in 0.242 seconds; no live site/account is implied.
Feature-map validation reports 80 features, 38 operations and 34 modules. Both
HyperL provenance scripts pass. CI now builds host JNI/native contracts and the
Core boundary/static checks; local success does not assert that remote CI passed.

V1/V2 Experimental lint: **zero errors/fatal, 368 warnings and 18 hints each**.
Core Candidate lint: **zero errors/fatal, 371 warnings and 18 hints**. Existing
warnings remain a quality backlog; a zero-error build is not production proof.

## Exact artifacts

| Universal APK | Bytes | SHA-256 |
| --- | ---: | --- |
| meshlit-v1-build38-experimental.apk | 226,268,507 | `3c3a20f5d89d10898d79b1883004cb4ff04864af0f3a8fa33148ece526bcbfb8` |
| meshlit-v2-build38-experimental.apk | 226,268,348 | `8606210188d829cd13f67c1a87760861317ac8a0b6882757498b261acf6435b5` |
| meshlit-v2-build38-core-candidate.apk | 207,521,996 | `db21fe6eac5408814800932a196981a7690beb1fb2e4c417ec208ea49a699845` |

All three APKs contain the full pinned 105,454,432-byte starter model and match its
SHA-256, the HyperL and Apache MINA notices, version 2026-10-09.1 Terms/Privacy,
and current offline guide. Android data/consent/keys are separate between V2
Experimental (`com.meshlit.v2.debug`) and Core (`com.meshlit.v2.production`).
Updated app notices require renewed human acceptance; acceptance never enables
optional permissions. APK signatures are verified developer signatures, not
production signing. ZIP/ELF packaging checks do not prove a 16 KiB device runtime.

Static Core checks pass: debuggable is false; only model-download, local-chat-inference and Room
invalidation services survive the merged manifest; selected disallowed permissions
are absent, and package/label/policy/model/notices agree. All four packaged HyperL
ABIs and all 24 ARM64 native libraries have 16 KiB ELF alignment. Core native bytes
match the validated Experimental package. Shared inactive classes/dependencies
remain packaged: this is not a separate minimal binary or an OS isolation proof.

## Device and production gates

ADB reports no connected device for this build. Nothing was installed, no updated
notice was accepted, and no new Android microphone, VM, thermal, SSH, cluster,
Internet or runtime 16 KiB test passed. The Samsung build-37 model/UI evidence in
[the earlier ledger](DEVICE_TESTING_2026-10-08.md) is historical and does not qualify
build 38. An older Windows PC and Xiaomi phone are owner-identified inventory;
exact configuration/connectivity/capability remains unmeasured.

Before production: resolve the development SDK's documented telemetry behavior
and verify the supported network opt-out; run fresh offline storage/generation,
restart/cancel/low-memory/thermal/accessibility and signing/device-matrix checks.
Then qualify physical two/three-node layer execution/recovery, followed by remote
Internet client authorization/quota/restart testing. See the ordered
[channel/device plan](architecture/PRODUCTION_CHANNELS.md).

Inbound SSH is Experimental/API 26+, private IPv4 or loopback, human-started with a
30-minute lease. It exposes bounded JSON node actions, no login shell, forwarding,
SFTP, password auth, public listener or remote root/permission editor. SSH identity
is encrypted with Keystore-backed storage; HyperL dataset keys are separate raw
private no-backup files, not hardware-protected keys. Agents need independent
chat/global/profile/remote grants and cannot authorize themselves.

VM execution requires a compatible installed executable, trusted bootable guest
and verified guest identity; none is bundled or automatically fetched. APP,
PRoot/chroot and successful process starts are not strong isolation or guest-boot
proof. Stop cancels registered local work but does not undo completed or persistent
remote effects. Read [SSH_NODE.md](SSH_NODE.md),
[ANDROID_ALPHA6.md](hyperl/ANDROID_ALPHA6.md) and the
[evaluation review](architecture/EVALUATION_REVIEW_2026_10_09.md).

Final universal installers and the 1 MiB device-test APK are preserved outside
the source checkout in the local build-38 delivery directory. Only duplicate
ABI-specific generated APKs were removed, reclaiming **1,033,556,098 bytes
(0.963 GiB)**. Source, model assets, private keystores, reports and earlier retained
installers were not removed. Private validation logs/reports are archived outside
the repository; APKs, weights and credentials are excluded from the commit.
