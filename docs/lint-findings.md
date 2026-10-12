# Final beta lint evidence (2026-10-07)
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Both Full flavors: **0 fatal/errors, 348 warnings, 17 hints each**.
Play Review: **0 fatal/errors, 351 warnings, 17 hints**. Fatal/error gating stays
enabled; no baseline or blanket suppression was introduced. Explicit optional
telephony in the Review overlay resolves its SMS-removal lint false positive.
Final evidence: `../meshlit-validation/release-final-validation.log` and generated
app XML/HTML reports. Earlier records below remain historical. This is static
analysis evidence, not store approval or physical UX proof.

# Latest lint evidence (2026-10-06)

Final cloud/browser/files/fonts/help continuation passes full lint in both flavors:
**0 errors, 337 warnings, 17 hints per flavor**. `abortOnError=true` remains enabled.
See `../meshlit-validation/final-bounded-browser-validation.log` and the generated
app reports. Earlier counts below are historical; physical UX/release gates remain.

# Lint findings and follow-up

Current sequential native/checkpoint source passed fatal-gate lint with **0 errors,
334 warnings and 17 hints per flavor**. `abortOnError=true` now blocks errors.
Final Cloud/vault source requires a new run, recorded when complete in PROGRESS.md.
The tables below are historical findings; they are not current remaining errors.

Historical full lint completed on 2026-10-06 with 59 errors, 322 warnings and 16 hints per flavor. The Gradle configuration has `abortOnError=false`; task success does not mean a clean report. Follow-up reports contain 51 errors, 322 warnings and 16 hints per flavor. Final environment/media lint completed with **51 errors, 330 warnings and 17 hints per flavor**. Its task succeeded in 17m 47s because abortOnError is false. The subsequent bounded buffered-GGUF parser follow-up passed its tests/build but was not followed by another full lint run. See `../../meshlit-validation/lint-summary-2026-10-06.json`.

## Corrections made after the first report

- Replaced pipeline Process.isAlive with exitValue-based checks compatible with API 24.
- Guarded SOC_MODEL in the device probe and its fallback profile; older Android uses HARDWARE.
- Guarded network MTU access, removed the system-only VPN uses-permission, declared camera/telephony optional and removed duplicate location permissions.

## Remaining initial-report findings

These entries come from lint, not a device failure. Confirm the failure path and the applicable API/permission/format before changing behavior. Most belong to retained legacy screens. The corrected entries above are excluded below.

| Rule | Source | Line | Finding |
| --- | --- | --- | --- |
| MissingPermission | `app/src/main/kotlin/com/meshlit/agent/LocationDispatcher.kt` | 60 | Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException` |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 202 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 204 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 213 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 216 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 379 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 384 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 393 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 400 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt` | 405 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/settings/BundledModelCard.kt` | 79 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/v2/screens/DeviceInfoScreen.kt` | 150 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/v2/screens/DeviceInfoScreen.kt` | 162 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/v2/screens/DeviceInfoScreen.kt` | 171 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 817 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 824 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 827 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 830 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 954 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 1031 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 1035 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/help/FeedbackScreen.kt` | 169 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/help/FeedbackScreen.kt` | 175 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 156 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 315 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 322 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 349 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 356 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 375 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 382 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 407 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 414 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 433 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 440 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/disco/QrPairingSheet.kt` | 96 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/disco/QrPairingSheet.kt` | 103 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/disco/QrPairingSheet.kt` | 106 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/disco/QrPairingSheet.kt` | 109 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/StructuredScreen.kt` | 128 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/StructuredScreen.kt` | 167 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/VisionScreen.kt` | 157 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/VoiceScreen.kt` | 320 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/VoiceScreen.kt` | 355 | Querying resource values using LocalContext.current |
| LocalContextGetResourceValueCall | `app/src/main/kotlin/com/meshlit/ui/screens/VoiceScreen.kt` | 417 | Querying resource values using LocalContext.current |
| StringFormatInvalid | `app/src/main/kotlin/com/meshlit/ui/screens/settings/BundledModelCard.kt` | 105 | Format string '`models_bundled_installed`' is not a valid format string so it should not be passed to `String.format` |
| StringFormatInvalid | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 858 | Format string '`files_open_failed`' is not a valid format string so it should not be passed to `String.format` |
| StringFormatInvalid | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 873 | Format string '`files_open_failed`' is not a valid format string so it should not be passed to `String.format` |
| StringFormatInvalid | `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt` | 888 | Format string '`files_open_failed`' is not a valid format string so it should not be passed to `String.format` |
| StringFormatInvalid | `app/src/main/kotlin/com/meshlit/ui/screens/VoiceScreen.kt` | 355 | Format string '`llm_output_saved`' is not a valid format string so it should not be passed to `String.format` |
| StringFormatMatches | `app/src/main/kotlin/com/meshlit/ui/screens/DevicesScreen.kt` | 832 | Suspicious argument type for formatting argument #1 in `devices_qr_scan_failed_code`: conversion is `s`, received `int` (argument #2 in method call) (Did you mean formatting character `d`, 'o' or `x`?) |
| StringFormatMatches | `app/src/main/kotlin/com/meshlit/disco/QrPairingSheet.kt` | 111 | Suspicious argument type for formatting argument #1 in `devices_qr_scan_failed_code`: conversion is `s`, received `int` (argument #2 in method call) (Did you mean formatting character `d`, 'o' or `x`?) |

## Core-module follow-up

Partial analysis also reports pre-existing issues in ActivationPacket API calls, voice permission handling, legacy built-in shell/client process handling, notifications and WebFetch. App report counts do not include every core-module incident. Reuse the bounded runtime/downloader contracts instead of adding suppressions or inventing permission grants.

Priorities: check actual permission failure/cancellation; guard unsupported Android APIs; fix resource formatting; migrate retained Compose resource reads; rerun lint and device checks. Keep physical-device validation distinct from static reports.
