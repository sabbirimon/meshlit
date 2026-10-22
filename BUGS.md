# Current regressions, limitations and verification
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: cross-platform tracking; feature and device gates remain separate. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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
| Build41 Xiaomi install blocked by device policy | External test gate | `adb install -r` returned `INSTALL_FAILED_USER_RESTRICTED` on M2101K7AI (Android 12); owner must enable/approve USB installation before on-device UI or performance evidence can be claimed |
| Build41 Android lint warnings | Open cleanup; no lint errors | V1 and V2 lint tasks pass with 378 warnings and 18 hints each, mostly unused resources and KTX suggestions; no claim of warning-free UI |
| Forced process loss/orphan recovery, memory/leak/long-run behavior | Unqualified | Shutdown hooks and explicit unload are implemented; stress/crash evidence still needed |
| Physical multi-device oversized model, native placement and failover | Open implementation/acceptance | Planner/loopback evidence is insufficient for physical fleet qualification |

No fabricated total bug count or project completion percentage. Report each reproducible issue with exact source/artifact, device/backend, expected/actual result, sanitized logs and its regression test or acceptance gate.
