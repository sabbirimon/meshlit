# HyperL alpha.6 in Meshlit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Settings → HyperL libraries. Read the separate offline licence/notices and enable
alpha.6 explicitly. This installs no additional executable and makes no network
request. HyperL Community and Enterprise License 1.0 covers newly covered module
rights; Meshlit's root Apache licence and earlier Apache HyperL grants remain.
Personal/developer/research and below-threshold organizations remain free. Large
entities have the licence's single six-month production trial; paid terms are
agreed separately with sabbirimon@gmail.com. The app has no licensing telemetry,
activation/payment server or automatic invoice.

## CPU and precision

Choose **Kotlin reference** or **Native C99**, review a recipe/JSON and the memory
budget, then Validate and Run CPU. The pinned C99 ABI is built by NDK
28.2.13676358 for arm64-v8a, armeabi-v7a, x86 and x86_64 with strict warnings,
no floating-point contraction and a 16 KiB maximum ELF page size. Selection is
explicit and session-local; a native load/revision failure does not silently
substitute another backend. Native admission includes JNI snapshot overhead.
Both implementations share the bounded hyperl/1 contract and original ordered
f32 graph sums. Stop/Operations revocation polls inside native execution.

**Precise sum** operates on exactly one editor input vector. It uses compensated
binary64 accumulation and one finite f32 conversion, separately as precise-sum/1.
For `[16777216,1,-16777216]`, ordered f32 sum gives 0 and precise sum gives 1.
This utility does not change a graph or chat model's semantics.

Output reports actual elapsed wall time including JNI copies. It does not claim
a speedup, GPU execution, model tokens/s or concurrent cluster throughput. Existing
Metal/Vulkan source generation remains source export, not Android GPU execution.

## Encrypted datasets (Android 8/API 26+)

Use the Android document picker to **Import and encrypt**. Content streams directly
into 4 MiB AES-256-GCM authenticated chunks and a typed HLM2 manifest. The app caps
this workspace at four datasets of 64 MiB each; standalone 4 TiB format bounds are
not advertised as tested phone capacity. App-private no-backup storage retains a
random 32-byte key per dataset. Keys are not exported, exposed to the model/agents,
or backed up automatically; these raw private key files are not Android Keystore
hardware protection. App clearing/uninstall loses both dataset and key.

Select a dataset to verify every authenticated chunk and whole-file hash. Rotate
key makes a new encrypted dataset with a fresh key/identity/nonces and retains the
original; no intermediate plaintext file is created for rotation. Four entries
leave no rotation slot until the user removes one. Removal has a confirmation.

**Export plaintext** first verifies to private staging, then writes to the chosen
provider document. Exported content is no longer encrypted. SAF providers do not
guarantee atomic output publication: a cancelled/failed provider write is deleted
when supported, otherwise the user must remove its empty/partial document. Private
staging is cleaned on ordinary completion/failure; crash cleanup is best-effort,
not secure erasure or power-loss durability. Provider IO may block despite a
coroutine deadline. No remote backup/key recovery or automatic memory spill exists.

CPU actions have a 10-second deadline; dataset operations a 60-second deadline,
one active operation, the human HyperL feature gate and global emergency stop.
This UI does not register a new agent/MCP tool or change the local chat engine.

## Reproducible checks

- `python3 scripts/check-hyperl-port.py` preserves the old Apache port.
- `python3 scripts/check-hyperl-alpha6.py` checks pinned imported bytes, adapted
  bytes, identical offline licence assets and the Kotlin/C contract constants.
- `:core-hyperl:testDebugUnitTest` tests reference/precision and authenticated
  malformed datasets, wrong keys, quota, tampering, legacy reads and rekey.
- Explicit `-PhyperlHostLibraryDir=/absolute/verified/host/path` enables real host
  JNI tests after building **this** module's cpu.c/android_jni.c with host JNI
  headers. Host execution does not establish Android installation/device success.
- `:app:testMeshlitV2DebugUnitTest` covers the app controller/gates and workspace
  lifecycle. Both app flavors must assemble and lint without errors.
- `HyperLNativeAndroidTest` exercises packaged JNI and dataset storage on API 26+
  devices. `WorkbenchUiDeviceTest` requires prior human enablement of HyperL's
  optional terms; it does not accept agreements automatically.

The new Android build and tests do not imply full neural networks, NPU/CUDA/ROCm,
transparent KV migration or automatic cluster healing. See the supplied-paper
[review](../architecture/EVALUATION_REVIEW_2026_10_09.md) for measured follow-up gates.

## Measurement and admission continuation — 2026-10-24

Current source adds **Measure CPU** beside Run CPU. It executes the chosen real backend once to warm up, then five times under the same ten-second deadline, one-operation capacity and emergency-stop gate. It reports measured samples, median and p95, output count, graph/output SHA-256 and device/ABI. Every repeated output hash must match. With five samples p95 is the slowest sample; it is not a long-running percentile or cross-device benchmark. Parsing and hashing are outside the measured samples; runtime admission and JNI copies remain included. No automatic backend switch or GPU/NPU speed claim is made.

Validate now includes JNI input/output snapshots when Native C99 is selected, matching the additional execution admission check. Estimates still do not reserve memory. App reference/admission tests passed in the first continuation run; final build and actual Android native measurement remain pending. See ../../PROGRESS.md for the evolving validation record. Imported HyperL module bytes and separate licence terms are unchanged by these app-wrapper edits.
