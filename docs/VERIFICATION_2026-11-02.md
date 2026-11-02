# Verification and performance — 2026-11-02
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

This is evidence for the current working tree based on d4361ab, not a claim that every advertised function works on every device. No source publication or release was performed.

## Agent conversation qualification checkpoint

Current actual-task-example source passed core-mcp 185 tests, desktop-device 29, desktopApp 88 (one fixture skip), and both latest Android app suites 326 each, with no failures. Both APKs, the test APK and desktop distribution built. Both final Android lints passed with zero errors in the 14m 46s final check. One existing 30 ms HookEngine wait test failed under concurrent compilation; both complete app suites passed on rerun with two Gradle workers. The timing-sensitive test was not changed.

Real model-driven forward delegation passed on Samsung SM-A207F with Qwen3 0.6B and on Intel Mac with Qwen2.5 1.5B/context 4096: an actual peer task, correlated receiver reply and correct final arithmetic answer. See [Samsung proof](desktop/evidence/agent-dialogue-samsung-passed-20261102.json) and [Studio proof](desktop/evidence/agent-dialogue-studio-passed-20261102.json). Earlier zero-handoff and wrong-answer attempts remain failed evidence. Current-model [reverse workflow qualification failed](desktop/evidence/agent-dialogue-studio-reverse-failed-20261102.json): the model requested self-delegation and the runtime rejected it before dispatch. Shared unit tests prove reverse routing/correlation contracts, not model workflow quality or physical cross-device agent acceptance.

The Samsung SDK [adversarial grammar probe](desktop/evidence/samsung-structured-decoding-20261102.json) passed, but no production structured agent path was enabled. Agent conversation currently delegates model-analysis tasks; it does not grant phone/file/cloud actions. After the final installs/instrumentation, Samsung Accessibility was cycled off/on through the real system consent UI. Final model restoration succeeded for `url-824902c942c3cc73`; phone control and service binding are on. Actual open/snapshot/search-button click/input-field click/type/back/home checks passed. A fresh request sent from the Chat UI returned “Phone control: enabled. Android Accessibility: connected.” See [final phone state](desktop/evidence/phone-control-final-20261102.json) and [capability inventory](desktop/evidence/samsung-agent-capabilities-20261102.json). The composer Send accessibility click failed, so its observed button was tapped with ADB for that manual UI test; the search controls are the qualified click/type targets.

The fresh Chat-screen check found an Android 11 foreground-service timeout for fast tool replies. Completion now travels through a generation-correlated service command, allowing foreground promotion before shutdown; stale completion cannot stop a newer generation. The [real-device regression](desktop/evidence/chat-service-lifecycle-samsung-20261102.json) passed three immediate start/finish main-looper cycles and the delayed crash guard.

The final [production Chat-controller qualification](desktop/evidence/chat-agents-samsung-20261102.json) passed in 25.113 s: phone status invoked `android_control_status`; DuckDuckGo search invoked `web_search` and attached ten real source receipts; explicit device inventory invoked only `remote_nodes` and surfaced the existing “Remote commands are off” gate. No device/SSH grants changed, no device command was sent and no remote reachability was inferred. The previous device query made zero calls and only returned an identity message; it remains [failed evidence](desktop/evidence/chat-agents-before-device-fix-20261102.json). Explicit selected remote inventory now uses its real read handler directly, with the same registry identity and grant checks. Chat's stale Brave-required hint was corrected: DuckDuckGo needs no API key; Brave requires one when selected.

[Artifact hashes and current test scope](desktop/evidence/agent-chat-final-build-20261102.json) record the dirty-tree build, physical gates and remaining limitations. The phone was handed back for separately requested human Tailscale onboarding after restoration; later onboarding is outside this backend checkpoint.

## Current continuation: CPU, resource dashboard, teams and connected capabilities

The tables below retain the earlier voice/streaming checkpoint. Subsequent source changes require fresh build/lint and device evidence; earlier counts are not the final acceptance for the current tree.

A fresh unlocked Samsung sweep measured SDK hints 1, 2, 3, 4, 6 and 8, one warmup and five short requests per profile. Median wall times were 7.319, 3.847, 2.732, 2.225, 1.737 and 2.206 seconds respectively. Six workers were best for this specific prompt/model/context; eight took about 27% longer. Real app CPU averaged roughly 2.68 logical-core equivalents at six and 2.93 at eight, including UI/GC/profiling. This is shared OS scheduling, not reserved cores. Actual SDK token counts remain unknown. The initial locked-phone 3–8 attempts were limited to two by the background guard and are excluded from the comparison. See [raw profiles and limits](desktop/evidence/multicore-20261102/summary.json).

The newly packaged native local engine passed five real 16-token generations plus cancellation/recovery on the Samsung. The first native test requested six per-request workers but did not record the load-time thread policy; it is retained as native functionality evidence only, not a qualified six-worker native benchmark. Its app-only CPU/PSS counters exclude the native child. A subsequent [native six-worker load-policy profile](desktop/evidence/multicore-20261102/samsung-native-performance-6.json) passed with five actual 16-token generations: median request 1.867 s, first text 0.420 s, native-reported median 10.58 tokens/s, cancellation join 11.2 ms and successful recovery. Per-request hints do not retune this server after loading. The [final native profile](desktop/evidence/multicore-20261102/samsung-native-final-6.json) passed again (median 1.926 s) and the bounded same-UID fallback observed the real child CPU time, RSS and threads. App-only CPU counters remain explicitly separate from the child snapshots; their PSS must not be presented as total native-engine memory.

Current workbench Node suite: 90 passing tests, including automation deadline/revocation cleanup and delayed-status Stop races. Packaged-Python runtime suite: 21 passed; crawler policy/service suite: nine passed in its existing crawler environment. Both app unit suites: 311 passed each. The combined core/shared/desktop build and both lints passed (zero errors, 410 warnings and 19 hints); the final phone monitor/test corrections also passed both app unit suites, both APK builds and both lints. Studio real private IPC passed live CPU/RAM/process readings, saved workload policy and invalid-mode rejection, all 268 role templates, locked-team rejection and owned-model Stop; GUI access remains unqualified. The portable Studio candidate completed signing and verification, then passed all 21 runtime tests using its own bundled Python. Real packaged planner/worker/reviewer runs passed at contexts 1024 and 4096, including a cold 4096 start; each returned the correct arithmetic result. An earlier cold team failed while build/runtime checks were active; the later passes do not establish reliability under sustained host resource pressure. macOS ignored the initial Java preference-folder flags: the original workload values were restored and verified, and later tests used an in-memory PreferencesFactory. Final Samsung mode/warning/draft/dashboard and editor/settings/permission checks passed, as did all 12 HyperL recipes at 3/257/4096/8192 elements. Startup/awake test settings were restored, with Performance/six retained as the owner-authorized measured choice. See the [final scope and artifact hashes](desktop/evidence/multicore-20261102/final-verification.json). OpenViking/Artemis/Strata live services are unconfigured; remote WAN live testing remains deferred by the owner. Fleet manifests/logical allocation are foundations, not proof of a shared global RAM/GPU pool.

## Earlier verified scope

| Area | Result |
| --- | --- |
| Android libraries | 1,031 passed, four fixture-dependent skips, zero failures across 32 Android library modules; core-peer-security adds three passing JVM tests. core-orchestration has no unit cases. |
| Android app | Both flavors built; 309 unit tests per flavor pass after the voice preparation change. Both final lints passed with zero errors; the updated V2 APK is installed on Samsung. Three startup/Stop cycles and the owner-confirmed spoken after-test passed. |
| Final Android lints | Both passed with zero errors, 396 warnings and 19 hints per flavor. |
| Studio | 127 JVM tests passed, one physical SSH fixture skipped, including four streaming tests. 79 workbench Node tests and 21 Python/runtime/packaging tests passed. |
| Crawler | Nine policy/service tests passed in its existing environment; no live Chromium claim. |
| Inventory | 98 features, 38 operations and 40 modules validate. The [settings audit](ANDROID_SETTINGS_AUDIT.md) traces all 73 routes to source; this is separate from physical execution of every setting. |

On Samsung SM-A207F / Android 11, settings categories/search, voice navigation, rewards/network navigation, chat tool settings, search settings, chat presentation, response rendering, network observations, encrypted usage/credential transactions, encrypted knowledge imports/grant separation, HyperL native CPU/storage, and the native GibberLink codec passed. The owner enabled HyperL manually. Its editor then passed CPU execution, Metal/Vulkan source export, invalid-input/budget rejection, permissions-screen controls, and Monitor navigation after correcting the test's scrolling. Codec PCM tests do not prove over-air phone-to-phone delivery.

Offline Whisper/Piper recognition, synthesis, playback and concurrent local LLM residency passed. The owner confirmed the audible correct answer during a separately cued real microphone turn. An earlier background-audio capture is excluded from human-voice acceptance. No microphone audio was saved.

## Performance measurements

| Measurement | Observed result |
| --- | --- |
| Studio first visible text, before streaming | 14.04 s and 6.50 s; one text event per answer |
| Studio first visible text, final streaming candidate | 2.09 s and 0.41 s; 105 and 89 incremental text events |
| Intel Studio CPU baseline | Mean 4.49 output tokens/s over two trials |
| Intel Studio AVX2/FMA/F16C | Mean 11.50 output tokens/s over two trials |
| Samsung SDK cold load | 6.89 s; requested context 512, effective native context is not exposed by the SDK |
| Samsung short request | Mean 1.78 s, median 1.75 s over three measured trials after one warmup |
| Samsung first text callback | Mean 1.09 s, median 1.06 s |
| Samsung cancellation | 128 ms to join, followed by successful generation |
| Samsung SDK process PSS | Approximately 805 MiB during the short-request trials |
| Samsung separate native engine | Unavailable: the APK has no executable native-local runtime for this ABI |

The Studio latency trials use the same synthetic prompt and a fresh SDK session for each trial, with one local engine shared within each run. Sampling is not deterministic and answer lengths differ, so these observations establish earlier visible text, not an end-to-end throughput speedup. The CPU comparison uses matched deterministic 128-token requests in baseline/AVX2/AVX2/baseline order. AVX2 selection already existed; that comparison is a configuration benchmark, not a new code speedup. Neither machine was isolated from all OS/background activity. Android SDK token counts are unknown; text callbacks were not reported as tokens.

The confirmed Samsung voice turn took 19.061 s preparing speech adapters, 13.191 s listening (including owner wait), 4.245 s transcribing, 23.710 s thinking, 2.043 s preparing output, and 2.326 s speaking/cleanup. Capture-end to playback-start was 29.998 s. Model/chat preparation dominated the response delay. These phase timings are distinct from the short synthetic model benchmark.

## Changes and remaining checks

Studio now forwards local model text as it arrives. Budget admission still precedes model dispatch; completion and usage chunks plus DONE wait for durable settlement. Hosted provider handling remains on the existing response path. Tests cover early delivery, incomplete/error/oversized streams, unknown usage, and cancellation after streamed text. The final packaged candidate passed the latency rerun and real usage/budget restart qualification after the terminal-chunk settlement refinement. Signature verification passed.

Android now begins listening after recognition preparation while its offline output model loads as a structured child task. Memory admission still includes both models. Playback awaits output readiness; Stop and preparation failure release the turn and await native initialization before cleanup. Three new unit tests and three physical startup/Stop cycles passed. The owner then confirmed a correct audible answer in the visible voice test window. Startup preparation was 10.557 s versus 19.061 s before. Capture-end to playback-start was 28.438 s versus 29.998 s before; the thinking stage remains 24.100 s. Automatic startup trials reached Listening in 9.003 s, then 3.265 s and 3.181 s in the same process after adapter unloads (OS/runtime caches remain warm). Immediate Stop took 7.7–8.6 s while in-progress native output creation finished and was released. This improves time to listening; it does not eliminate model reply latency. The initial after-attempt with no speech above threshold is excluded from conversation acceptance.

The relocated Studio candidate passed minimal-environment SSL/SDK imports, signature verification, two real local chats, usage/budget restart persistence, wrong-passphrase rejection, and 12 real native HyperL recipes. Native packaging now relocates library identities and explicitly supplied OpenSSL dependencies while retaining notices and rejecting arbitrary external dependencies.

Open gates: desktop visual inspection (computer-control access denied); physical Studio-to-phone SSH/pairing/reconnect (no approved fixture configured); live DuckDuckGo search (upstream verification challenge); external paid/provider accounts, optional companions, additional phones, GPU/root/VM/peripheral workflows. The legacy UI audit reports six checks: three files with plain flow collection (one counted twice), plus two stale assertions about the settings wrapper. These are not six demonstrated failures of the modern UI.

## Artifacts and reproducibility

- Android: `app/build/outputs/apk/meshlitV2/debug/app-meshlitV2-arm64-v8a-debug.apk` and the corresponding V1/universal APKs.
- Studio: `/Users/code/Documents/Codex/output/Meshlit Studio Verified 2026-11-02/Meshlit Studio.app`; Intel macOS, ad-hoc signed, not notarized. Optional service runtimes remain separate.
- [Voice after-change evidence](desktop/evidence/live-voice-conversation-after-20261102.json), [startup/Stop samples](desktop/evidence/samsung-voice-startup-after-20261102.json), and [final Studio latency](desktop/evidence/studio-first-text-final-20261102.json).
- [Session evidence](desktop/evidence/resume-20261102.json), [library counts](desktop/evidence/android-library-tests-20261102.json), [voice baseline](desktop/evidence/live-voice-conversation-20261102.json), [Samsung SDK benchmark](desktop/evidence/samsung-sdk-benchmark-20261102.json), [CPU comparison](desktop/evidence/studio-cpu-benchmark-20261102.json).
- Use `companions/studio-runtime/benchmark_latency.py --runtime /absolute/runtime.json --output /absolute/results.json` with the packaged Python to measure actual SDK first-text latency in a disposable local usage vault.
- `DeviceCoreBenchmarkTest` accepts `benchmarkEngine=runanywhere` or `llama-native-local`. Run engines in separate processes with startup loading temporarily paused, restoring the original setting afterward. The initial mixed UI/engine runs hit memory admission; failed attempts are not timing samples. Failed benchmark reports now preserve the primary error and partial samples instead of hiding them behind teardown errors.

Original Samsung startup-loading, USB-awake and temporary chat options were restored. The owner’s HyperL opt-in is retained. No credentials or microphone recordings were exported.
