# Current regressions, limitations and verification
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Current validation boundary — 2026-10-24

The paused build45 source fixes and unresolved cases are summarized in [the checkpoint](docs/PAUSED_CHECKPOINT_2026-10-24.md). Publication preserves this work; final voice regressions, both flavor build/lint and physical device checks remain open. Earlier successful builds below apply only to their named revisions.


## Active reliability findings — 2026-10-24

- **Source review fixes / device retest pending:** Live voice no longer treats
  ordinary Android silence as a fatal error, claims Listening during transcription,
  discards short qualified speech at timeout, truncates phrase captions to three
  lines, or releases AudioTrack before its meter child finishes. Regression tests
  and physical validation are recorded separately in PROGRESS.md.
- **Observed isolated probe:** Samsung native SmolVLM identified a square but
  omitted color and took 82.686 s. In-app offline photo Chat remains unwired.

- **Observed:** Qwen3 0.6B fast-mode Samsung reply gave 32 for 17+25. Explicit reasoning returned 42 after 38.4 s. Preserve both results; do not claim model-quality acceptance.
- **Source fix / device retest pending:** Provider setup Back loses origin; recommendation Load button does not indicate loaded state; native Qwen completion path omitted ChatML roles.
- **Source fix / final qualification pending:** RAG had no durable ingestion; remote RAG used a placeholder domain; HyperL displayed reference-only memory admission in native mode.
- **Source fix / retest pending:** Qwen3 now suppresses incomplete reasoning text and strips the template closing marker before saving/displaying its final answer.
- **Open:** SDK counters are not authoritative; physical live speech/image understanding and full device-layout sweep remain gates.

## Current photo lifecycle correction — 2026-10-24

The old photo path ran in a screen coroutine and then required a second text-model request. Source now uses the chat controller and displays the selected vision provider answer directly, without requiring a local text model. Stop, timeout, interrupted history and provider-change handling are explicit. Real provider and spoken offline voice acceptance remain open. The build43 V2 debug CI stage passed, but its separate Core Candidate stage hit the job timeout.

## Current build41 device observations — 2026-10-23

Samsung build42 showed an ordinary “hi” sent through the local tool planner whenever Web/Phone grants were enabled; the bundled small model returned an unrelated refusal. Source now requires an apparent tool request before using that planner, with a regression test for greetings and explicit commands. The voice mic opened a modal and auto-started; source now uses inline manual Start/Stop controls, a live input level/speech indicator, and a clear no-speech timeout. Photo selection opened a blocking dialog and could not show a usable model choice when no vision profile existed; source now uses an inline card with visible provider/model choices and a single Send photo action, or a setup link. Physical Samsung voice and provider vision response checks remain open; the bundled text model cannot process pixels.

Build43 Samsung still returned “I'm sorry, but I can't assist with that” to “sing a song” with Web and Phone tools off. This is a separate small-model response quality issue; the new tool-routing rule does not resolve it. A larger compatible local model or a configured provider requires its own device and account qualification.

Samsung DocumentsUI lists the copied Whisper/Piper ZIPs but did not return a selection during ADB taps. The import action now uses Android's one-time content picker; choosing Samsung My Files returned content URIs and both packs passed Meshlit's strict manifest/path/checksum validation on Samsung. A Piper sample returned to Stopped without a UI error; audible output and a complete recognition → chat → synthesis turn remain unverified. The ZIPs are in both phones' Downloads for manual Xiaomi import.

Samsung SM-A207F and Xiaomi M2101K7AI both accepted the V2 debug APK through ADB after the earlier Xiaomi USB policy block was cleared. Samsung dashboard showed discovery AUTO and scanning; Pause changed to OFF and cleared observed entries. The OFF state now labels its control “Resume automatic discovery.” Xiaomi chat displayed an invented human identity from the small bundled model, prompting an explicit Meshlit identity instruction; a new reply still needs to be checked. The SDK did not supply trustworthy token counts or speed, so Chat reports them unavailable. A pairing path that previously persisted trust after a failed handshake is changed to fail closed in source; physical adversarial pairing remains an open acceptance gate. No remote cluster takeover resistance is claimed solely from unit tests.

Updated 2026-10-10. Older bug IDs, hypotheses and screenshots are preserved in docs/history/BUGS-before-studio-2026-10-10.md. They are not automatically current open bugs. Feature backlog belongs in TODO.md and the desktop tracker.

| Item | State | Evidence / next verification |
| --- | --- | --- |
| Desktop system-role serialization rejected configured system prompt | Fixed | Serializer contracts and actual offline generation pass |
| HyperL dylib own install name pointed at development path | Fixed; packaging gate retained | Relocatable unchanged-source builder; all 12 CPU recipes/precise reduction and recovered payload hashes pass |
| Trimmed packaged Java omitted OSHI/JNA logging | Fixed | Full-classpath jdeps modules included; both recovered launchers generate and PKG monitor/render pass without external Java |
| DMG create/mount device unavailable in build session | Build path corrected; mount acceptance open | File-based HFS→UDZO plus hdiutil verification and exact extracted payload checks pass; no OS-mounted/clean-target proof |
| Benchmark warm-up violated shared minimum output budget | Fixed harness | 64-token warm-up; four real matched trials pass; aborted run contributes no timing data |
| q4_0 key cache gave poor arithmetic response | Held out of normal desktop settings | f16/q8_0 exposed; no general model-quality certification; Q4_K_M weights are a different setting |
| Combined Android lint/build stalled under concurrent load | Serial retry passes | One worker/in-process compiler; both flavors zero errors, 374 warnings each; core inference 7 warnings |
| Windows Ghostty argument test assumed POSIX absolute paths | Fixture corrected; 30 local desktop cases and Windows host CI pass at c1f2d60 | Native temp-directory absolute paths retain pin/literal/relative-path rejection assertions; no Ghostty process launched or Windows port claimed |
| Existing app warnings and historical cold-start/ANRs | Open acceptance/cleanup | Latest APK device tests required; prior build/start observations retain their dates |
| Build41 Xiaomi install blocked by device policy | Resolved for debug install | Earlier `INSTALL_FAILED_USER_RESTRICTED` on M2101K7AI was cleared; current V2 debug APK installs through ADB. MIUI still blocks ADB tap injection, limiting autonomous screen traversal. |
| “Use as primary” did not route requests | Honest UI label applied; routing open | The saved active endpoint ID had no reader in the model router. Devices now says “Mark as preferred” and explains this is a device-list preference until routing is implemented. |
| Radar sweep missing in visible device flow | Source fix; physical animation check pending | Device pairing now embeds radar and radar uses a bright 3-second rotating wedge only while discovery is active and animations are enabled. OFF disables the effect. |
| Build41 Android lint warnings | Open cleanup; no lint errors | V1 and V2 lint tasks pass with 378 warnings and 18 hints each, mostly unused resources and KTX suggestions; no claim of warning-free UI |
| Forced process loss/orphan recovery, memory/leak/long-run behavior | Unqualified | Shutdown hooks and explicit unload are implemented; stress/crash evidence still needed |
| Physical multi-device oversized model, native placement and failover | Open implementation/acceptance | Planner/loopback evidence is insufficient for physical fleet qualification |

No fabricated total bug count or project completion percentage. Report each reproducible issue with exact source/artifact, device/backend, expected/actual result, sanitized logs and its regression test or acceptance gate.
