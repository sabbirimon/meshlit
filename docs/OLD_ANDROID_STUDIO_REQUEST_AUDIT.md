# Older Android Studio request and plan audit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-10-23 against the separate `mllm` checkout's `PLAN.md`, `REQUESTS.md` and `TODO.md`. These files are historical requests and implementation notes from a different working tree. Their “done” labels do not prove that the current Meshlit APK contains or qualifies the same behavior. This table names the current gate to close before marking a request transferred.

| Older request | Current Meshlit evidence | Remaining gate |
| --- | --- | --- |
| R-01/06/09/19 settings, active model and popup regressions | Current Settings and Models have separate screens and the Android debug build compiles. | Recheck navigation, loaded-model truth, popup contrast and long text on both phones. |
| R-02/03 voice spectrum and animated icons | Pairing and Device radar now draw an active scan sweep. No current `MicSpectrumVisualizer` or reusable `AnimatedIcon` implementation was found. | Port only if live audio input level and accessible motion controls can be verified on device. |
| R-04 palettes and theme motion | Current Appearance settings provide palettes, fonts and animation control. | Test light/dark, large system text and reduced motion across all screens. |
| R-05/11/18 model download, import and progress | Current model catalog/download code and Models UI exist. | Test authenticated/failed URL, device import, resume/pause/cancel, storage exhaustion, integrity and load on a phone. GitHub/FTP import is a separate trust and provenance path. |
| R-10 agent screen and R-20/22 Auto Pilot repair | Current `AgentSession` has an autopilot continuation mode and the app has an agent UI. The older watchdog/repair executor was not found as a matching current module. | Design explicit observations, owner consent, bounded repair actions, rollback and audit; test cancellation and denied actions. |
| R-12 GitHub code/agent/tool import | Current file, agent and MCP areas are not proof of safe repository installation. | Add reviewed provenance, permissions, sandboxing and an owner approval path before code execution. |
| R-13 file management | A current Files workspace exists. | Test provider import/export, cancellation and hostile paths on device. |
| R-15 power, battery and thermal gauges | Current Resources/monitoring screens and device telemetry exist. | Check live samples, chart updates, units, thermal throttling and background/permission behavior on both phones. |
| R-21 bundled Qwen coder | Current starter APK uses a smaller SmolLM2 model to control install size. | Offer a verified optional coder-model download with hardware and storage admission; do not silently re-bundle a much larger model. |
| R-23 distributed LoRA training | `core-training` has ring/DiLoCo strategy contracts and tests; Fine-tuning describes a paired host job. | Prove a real licensed dataset, compatible training host, multi-device job, adapter output, evaluation, peer loss and cancellation. Contracts are not a completed phone cluster training run. |
| Old TODO #183 personality, skill templates and tuned weights | Current chat personality controls alter instructions. | Add per-model persona/skill templates and separately verified adapter selection; prompt styling is not weight tuning. |
| Old TODO #179 Ghostty terminal | Current Android terminal parser and separate desktop terminal paths exist. | Qualify any Android renderer replacement, dependency/license and NDK build before claiming Ghostty integration. |
| Old PLAN gateway/discovery/trust work | Current app has newer gateway, discovery, trust and pairing paths with different wiring. | Audit real call sites, denial/revocation, hostile peer payloads and end-to-end device behavior instead of copying old branch phase labels. |

The older plan's proposed commit order, phase tag and build status belong to that checkout. Current priority remains: working two-phone pairing and UI, safe model download/load, explicit permission boundaries, then physical multi-device inference and training gates.
