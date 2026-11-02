# Android multicore performance plan — 2026-11-02
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Owner direction: improve performance while preserving CPU capacity for Android and other apps. The original baseline and sequence are retained below; implemented changes and measurements are recorded here separately.

## Implemented continuation

Shared adaptive policy now offers Balanced, Battery saver, Performance, Overdrive and Overdrive+. Balanced retains two logical cores of scheduling headroom and caps at four; Performance caps at six with the same headroom; Overdrive permits all cores while retaining scheduling guards; Overdrive+ allows separately selected pressure overrides and a persistent warning. Model memory admission and OS protections remain independent. Explicit owner changes reset the recovery window; automatic pressure recovery waits 30 seconds.

SDK chat, structured and vision requests pass supported native thread hints. Speech and HyperL share the bounded heavy-work gate; speech's internal worker count remains SDK-managed. Native local servers/RPC workers apply thread policy on load/start and require restart for a changed mode. HyperL parallelizes bounded elementwise work at 8192 elements and keeps ordered reductions serial.

Unlocked Samsung SM-A207F profiles verified hints 1/2/3/4/6/8. Five requests after one warmup produced median wall times of 7.319/3.847/2.732/2.225/1.737/2.206 seconds. Six was best for this short synthetic request. All-core Overdrive was slower and remains optional. These are request timings rather than full voice-turn latency; the SDK did not report actual token counts. Locked-phone attempts limited by the background guard are excluded from the comparison. See [evidence and limitations](desktop/evidence/multicore-20261102/summary.json).

The native ARM64 runtime is now built and packaged. A first real native generation/cancel/recovery pass succeeded, but its load thread hint was not recorded; the final six-worker load-policy profile and actual owned-child CPU/RSS/thread snapshots also passed. Sustained thermal tests, representative long chats and repeat real microphone turns remain open acceptance gates.

Studio now uses the same workload policy through owner-enabled controls, reports actual process/RAM/CPU readings and an independent Overdrive+ status warning. Saved native engine tuning is preserved until adaptive control is enabled. macOS thermal/power-saver signals unavailable to this adapter are explicitly reported as unavailable. Final packaged IPC, runtime, native local team and signature checks passed. GUI acceptance remains distinct and unqualified.

## Original baseline evidence

Samsung SM-A207F reports CPUs 0–7 present and online. Its current APK uses RunAnywhere; the separate native-local executable is absent. Meshlit's RunAnywhere load/generation and offline speech wrappers do not pass explicit thread counts. Actual SDK native worker concurrency has not been measured; eight available cores and an app's total thread count are not measurements of eight-core inference.

AccelerationPreferences and its slider permit 1–4 native threads. DeviceRuntimePolicy selects one for Light devices (including reported usable RAM below 4 GiB), with additional memory/power/thermal limits. PipelineHost passes that cap to the separate native server. It does not control the installed SDK. The confirmed voice turn still spends about 24 seconds in chat preparation/generation; short synthetic prompts finish much faster. Profile representative conversation prompts, not just short generation.

## Implementation sequence

1. Add opt-in local profiling for model load, prompt processing, decoding, recognition and synthesis. Sample per-thread CPU time and total process CPU utilization, available memory, temperature/thermal state, and UI responsiveness. Include native child processes where present. Record configured versus observed worker counts separately; never infer tokens from text callbacks.
2. Inspect the pinned SDK's actual native thread defaults and supported controls. Wire explicit counts through supported APIs, or qualify a maintained native bridge/backend if controls are missing. Clearly label unsupported settings; do not present the separate native slider as SDK control.
3. Benchmark 1, 2, 3, 4 and 6 compute workers on this eight-core phone, with five measured repetitions after warmup, representative short and long chat histories, identical weights/context/output limits and a fixed seed where supported. Measure first text, total latency, actual native token rate when available, temperature, memory and foreground responsiveness. Include repeated voice turns and sustained runs; exclude owner waiting time from processing comparisons.
4. Implement a shared adaptive CPU budget across text, recognition, synthesis and other heavy Meshlit work. Start with at most four heavy compute workers; allow up to six only when measurements show a benefit and thermal, battery and foreground conditions permit it. Leave at least two logical cores' worth of CPU capacity as headroom. A worker cap is not exclusive core reservation: Android still schedules all processes. Do not pin threads to guessed core IDs or claim reserved cores without an actual supported mechanism.
5. Schedule each stage separately: microphone/audio playback and UI stay responsive; prompt processing and decoding may use different measured thread counts. Overlapping speech preparation must share the global heavy-work budget instead of creating competing full-size pools. Retain bounded queues and one active text generation; adding simultaneous generations is not a substitute for speeding one request.
6. Step down under thermal pressure, battery saver, low battery, memory pressure or background operation. Retain existing severe-thermal/critical-battery and load-admission blocks. Separate CPU scheduling policy from RAM admission rather than granting more memory implicitly. Use hysteresis and a cooldown before raising counts again; apply changes only at boundaries the backend safely supports.
7. Expose Auto/Balanced, Battery saver and Performance modes with actual backend support, configured counts, observed activity and reasons for throttling. Performance still respects headroom and system limits. Persist owner choices and migrate configuration import/export validation together with the slider and backend controls.

## Acceptance

Adopt a faster configuration only if representative median and tail latency improve without unacceptable UI/audio interference, memory failures, answer regressions or sustained thermal throttling. Verify Stop, cancellation, failed initialization, preference persistence and recovery. Build and lint both Android variants, run affected policy/adapter tests, then repeat physical speech and inference checks on Samsung. Keep old/new benchmark evidence and rollback settings. Do not promise proportional speedup from core count alone.

Related evidence: [verification and performance](VERIFICATION_2026-11-02.md).
