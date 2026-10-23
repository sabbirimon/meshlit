# Local model internet tools and Android control
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-23. Source implementation and acceptance evidence are separate.
See [device qualification](DEVICE_TESTING_2026-10-08.md).

## Local does not mean disconnected

“On-device model” identifies where model inference executes. It does not disable
Android internet connectivity. The chat model can request selected tools through
an experimental bounded JSON planning loop. Web results are returned to the same
on-device model; choosing a local model never silently selects a hosted model.

In Chat, open **+ → Chat options and model routing**, select the
currently loaded local model, and independently enable **Allow web page tools**
or **Allow phone tools**, then Save. Both options default off, including for old
saved conversations. Routed and online conversations cannot use this local loop.

The loop permits eight tool calls and nine model decisions within five minutes when phone tools are selected; otherwise it permits three calls and four decisions within 180 seconds.
It checks cancellation and saved operation policy before/after each step, binds
inference to the originally selected model, rejects remote inference, limits model
output and tool evidence, and allows only selected built-in descriptors. Stop
cancels the running request. Global emergency stop and agent AUTOMATION/INFERENCE
restrictions also apply. Invalid JSON, unknown actions and unselected tools fail
without dispatch for that step; there is no shell or arbitrary MCP fallback.
Any earlier completed actions are not rolled back by a later failure.

This is not native function-calling compatibility. Use a model trained for JSON
and tool use. The bundled Qwen2.5 0.5B starter improves ordinary chat but is not
qualified for reliable autonomy. In a local task trial, it returned an answer
instead of invoking the requested phone tool; a second prompt chose the wrong
action. Those tests were on the Mac, not physical Android acceptance. Tool contract tests do not establish model
planning quality, successful web retrieval or completed Android tasks.

## Web page retrieval

Selected web tools include `web_search` and `crawl_url`. Configure **Settings → Advanced → Web
crawler** with your own HTTPS Crawl4AI companion, credential and domain allowlist.
The companion is separately installed; enabling a chat option does not enable it
or create a search account. The Brave search integration needs its own API key, Internet switch and agent
grant. Neither path silently starts a companion or creates an account.

The model may request an approved public HTTPS page. The existing crawler enforces
host approval, public-address checks, robots/access restrictions, deadlines and
output limits. URLs and retrieved content go to the selected companion host.
Page/tool content is passed as untrusted evidence, never user instructions or
new permissions. Successful retrieved pages add source URLs; blocked/error pages
do not become citations. Model-written citations still need reader verification.
No live companion is configured for the Samsung acceptance run.

## Phone control and administration

Enable the saved controls in **Settings → Phone agent and autonomy** separately:
Android automation, autonomous delegation, the Android Accessibility service,
and exact allowed package names (or explicitly all supported apps). The chat
option does not grant these permissions. The built-in phone agent uses only the
loaded on-device model and Android service; the optional OpenClaw gateway is not
needed for this path. Existing `android_control` can snapshot,
open, click, type, Back and Home in the actual allowed foreground app. Protected
system surfaces and password input retain their human controls.

**Settings → App permissions** now retains Manage buttons even when permissions
are already granted. Android 11 notifications require no runtime prompt. Other
grants are reported as Granted or Not granted; Android app settings own revocation.

| Capability | Implemented behavior | Limits |
| --- | --- | --- |
| Ordinary app interaction | Existing scoped Accessibility actions exposed to the local chat loop | Requires Android grant, saved scope and suitable model; physical autonomy quality is unqualified |
| APK install request | Human selects a local APK using the document picker; validates/stages a real APK and opens Android installer | OS confirmation and install-source permission; not silent or reported complete |
| App removal request | Opens Android removal UI for exact package name | Human confirmation; agents cannot target own/protected/system packages or escape saved app scope |
| Other app permissions | Opens exact app's Android details screen | Does not set or revoke another app's OS grants itself |
| Device-owner status | Reads actual Android device-owner state | Does not provision management or implement silent administration |
| Wireless ADB | Owner-paired development/testing path from a trusted host | No in-app ADB client/adapter implemented |
| Root administration | Future separately reviewed adapter | Not implemented; root activation remains human-only |

Agents see `android_package_status` and `android_package_request` only when phone
tools are selected. Install requests require the exact APK URI selected by the
human beforehand; model output cannot grant arbitrary storage. Package mutation
requires saved Android autonomy. The app must be foreground for OS confirmation
requests. The loop stops on a pending human confirmation and does not let the
model operate Android's protected installer/settings UI to bypass it.

APK preparation has a 120-second coroutine deadline and checks cancellation
between copy chunks. Human and agent requests both obey the AUTOMATION feature
gate and global emergency stop. Stop cannot undo an installation the human has
already approved in Android. APK staging retains at most four files, each bounded
to 512 MiB, requires storage headroom and removes incomplete copies. Retained installer
files older than 24 hours are removed on a later install request; Android can
reclaim cache sooner. Selecting a new APK replaces the remembered URI approval.
The picker grant can expire. Installation/removal outcomes are not yet tracked
with PackageInstaller callbacks; a submitted intent is not completion evidence.
The Full build declares REQUEST_INSTALL_PACKAGES/REQUEST_DELETE_PACKAGES; the
Play review overlay removes them and disables this administration workflow.

Android's [PackageInstaller contract](https://developer.android.com/reference/android/content/pm/PackageInstaller)
and [device-policy permission API](https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setPermissionGrantState(android.content.ComponentName,%20java.lang.String,%20java.lang.String,%20int))
explain the distinct install, confirmation and management privileges. Accessibility
does not confer ordinary apps' device-owner/root privileges.

## Wireless debugging testing path

On supported Android 11+ devices, the human enables **Developer options → Wireless
debugging** and selects **Pair device with pairing code**. On a trusted computer
with Android platform-tools, run `adb pair PHONE_IP:PAIR_PORT`, enter the code
locally, then `adb connect PHONE_IP:DEBUG_PORT` and `adb devices -l`. Pairing and
debug ports differ. Never publish pairing codes or device serials. Turn off wireless
debugging and remove pairing when no longer needed. OEM availability and network
policy vary. The Samsung SM-A207F host path passed scoped shell checks, a V1 APK
installation and the standalone HyperL GPU suite over TLS wireless ADB. Updated
Meshlit model/UI checks and wireless loss/revocation remain separate gates; see
[the device record](DEVICE_TESTING_2026-10-08.md).

See [Android's wireless debugging guide](https://developer.android.com/tools/adb#wireless).
Meshlit's future ADB adapter needs authenticated pairing, encrypted host keys,
specific device/app/action scopes, revocation, bounded commands, real outcome
checks and emergency cancellation. It must not silently enable debugging,
provision device-owner mode, accept host authorization or gain root.

## Remaining acceptance gates

Test a larger tool-capable local model on actual hardware; configured HTTPS crawler
retrieval and citations; Accessibility grant/action/Stop/revoke on a disposable
allowed app; consented APK install/remove round trips and outcome callbacks;
permission denial and expired SAF grants; wireless pairing/disconnect/revocation;
device-owner/root adapters only on separately approved lab devices. No existing
personal app is uninstalled or its grants changed to manufacture test evidence.
