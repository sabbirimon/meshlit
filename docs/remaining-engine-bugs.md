# Remaining model-engine correctness gates
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-07. Real bundled generation now passes on the API35 x86_64
emulator; this document records unresolved correctness work, not fake failures.

Evidence update 2026-10-09: build 37 subsequently passed real Samsung Galaxy A20s
model download/hash/load/generate/unload and UI checks on 2026-10-08, recorded in
`DEVICE_TESTING_2026-10-08.md`. Older emulator-only statements below describe their
original observation date. They do not negate that phone evidence or qualify the
new build 38, Core candidate, SSH node or VM paths. No ADB device is currently
connected for the new source revision; sustained/resource/cluster/privacy gates
remain open.

## Stream accounting corrected; SDK native usage still unavailable

The app no longer counts text deltas as tokens or truncates generation after a
number of callbacks. Native max_tokens/stop options bound generation. Missing
terminal completion fails, and unavailable usage is null in result/UI/wire types.

Pinned RunAnywhere 0.20.12 final metadata still reports 3 completion tokens for
`Hello, hello, hello.`, previously measured as 6 tokens by its native backend.
Its public stream result does not establish whether counts are measured or
estimated. Those SDK usage figures are suppressed; do not use them for billing
or native throughput. A future SDK change must expose measured counters with
provenance and agree with actual backend evidence before this gate is removed.

The standalone native adapter reads tokens_predicted, tokens_evaluated,
timings.cache_n and decode throughput from the native completion response.
Context/path checks and real native checkpoint reuse now have emulator evidence.
Read `native-checkpoints.md` and the latest PROGRESS entry.

## Physical-device and resource gates

- Real Android generation currently has emulator evidence; physical-phone
  boot/load/generate/unload, thermal, battery and low-memory tests remain.
- Native two-worker proof is desktop-only. Genuine oversized-model execution
  across physical phones remains a separate required gate.
- Compatible VLM/native speech model availability and real capture/feed inference
  must be tested before reporting media analysis as functional on a given device.
- SDK-managed context and unsupported acceleration controls must remain explicit;
  a metadata training limit is not an applied runtime context or usable NPU.
- Replicated journals, fenced claims, portable KV and automatic restart are
  future implementations; current local job persistence is insufficient.

See PROGRESS.md and the layer/recovery plan for the broader acceptance sequence.

## SDK development telemetry attempts observed (2026-10-07)

A real bundled chat generation during UI validation logged attempted POSTs to
`https://dev.runanywhere.local/api/v2/sdk/telemetry/llm`, ending in DNS failures.
The inference adapter's comment that DEVELOPMENT disables telemetry is contradicted
by this runtime observation. Evidence is in
`../../meshlit-validation/reference-ui/sdk-telemetry-observation.log` (PID 10603).
No successful transmission or payload contents were established. Do not describe
this SDK configuration as a demonstrated zero-network/telemetry opt-out.

Follow-up: review the exact 0.20.12 native HTTP/event integration; implement a
supported, default-disabled telemetry boundary independent of environment URLs;
then prove generation/download/import still work and no telemetry HTTP callback
or connection is attempted. The bridge lifecycle in newer cloned source is an
internal API and differs from the pinned AAR; do not patch it blindly or use a
broken URL as an opt-out. Native standalone CPU inference is a separate backend.
This UI continuation records the bug; it does not claim to have corrected it.

## Legacy capture connectivity (2026-10-07)

The built-in VPN capture read TUN packets without forwarding and could interrupt
network access. Its service is now disabled in the app manifest and fails closed
without creating a TUN. The capture UI directs users to the separately installed
PCAPdroid companion. Final validation must cover the merged manifests and verify
this unavailable state; live companion capture remains unproven.

## Beta cold-start observation (2026-10-07)

Two normal V1 launches on API35 x86_64 emulator-5580 were killed by Android
with “failed to complete startup” while full Gradle lint ran on the same host.
The audit instrumentation itself passes (1 test, 9.343 seconds). No cold-start
fix or causal attribution is established. Once Gradle stopped, a normal cold
launch succeeded in 7.406 seconds and the final Models screen showed the real
bundled starter loaded. This establishes a successful emulator launch, not
a physical startup latency target; physical startup remains untested. Evidence:
`../meshlit-validation/beta-startup-during-build.log`.
