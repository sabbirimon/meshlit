# Android security tool and root integration plan

Requested by IMON on 2026-10-07. **Source review and implementation roadmap.**
None of these tools has been installed, executed, copied or bundled into this beta.
Pinned review revisions and license evidence are in [security-tool-sources.json](security-tool-sources.json).
The current runtime/root boundary remains [runtime-and-sandbox.md](runtime-and-sandbox.md).

| Reference | Practical fit | Integration boundary and licensing |
| --- | --- | --- |
| [Android Pentesting Checklist](https://github.com/Hrishikesh7665/Android-Pentesting-Checklist) | Inputs for a Meshlit assessment template covering storage, logs, permissions, exported components, WebView, cryptography and network data | Reference/link only: no license was detected in the reviewed repository. Write original checks and evidence criteria; do not redistribute its text as if licensed. |
| [Objection](https://github.com/sensepost/objection) | Frida-backed runtime examination of an owned/test app | Owner-installed host companion, explicit target package and matching instrumentation capability. GPL-3.0; review Frida and platform packaging separately. Rootless instrumentation requires a compatible prepared test app/runtime, not blanket access to other apps. |
| [Drozer](https://github.com/ReversecLabs/drozer) | Android application/IPC exposure assessment | Python/Java host console plus separately installed Android agent. Reviewed root LICENSE is BSD-3-Clause with component exceptions; GitHub's NOASSERTION metadata was not sufficient. Verify the separate agent's license/version too. Upstream notes a rewritten beta and unavailable/crashing custom-agent construction. |
| [Havoc](https://github.com/HavocFramework/Havoc) | Advanced post-exploitation/C2 lab reference, not a general Android runtime | GPL-3.0. Initial integration should import operator-selected lab reports/session metadata from an isolated host. No payload builder, persistence, listener or implant is included in Meshlit. Review current server/client security before any further integration. |
| [Magisk](https://github.com/topjohnwu/Magisk) | User-managed root broker for a compatible owned Android device | GPL-3.0. Meshlit can use the existing human-only ROOT/ROOT_CHROOT plans through installed `su`; no Magisk source/module is bundled. Root availability, grant, SELinux/kernel features and working commands are separate observations. |

These are different roles: Magisk supplies an optional privilege broker; Objection
uses instrumentation; Drozer tests Android app/IPC behavior; Havoc is a host lab
framework. They do not supply transformer sharding, shared phone RAM, KVM support,
strong isolation or universal rootless access. An external-process boundary does
not automatically settle GPL obligations for a combined distribution.

## First implementation milestone: assessment records

Create an original, versioned assessment schema with owner, target APK digest,
package, device/OS/API, scope, tool/adapter version, time window, technique grants,
and concurrency/time/output budgets. A check has `not-run`, `running`, `passed`,
`finding`, `blocked`, `cancelled` or `failed` status. Retain evidence references,
severity rationale and remediation/retest status; an unavailable tool is not a pass.

Start with Meshlit's own Full/Play Review artifacts and an owner-controlled test
app. Cover exported components and permissions, backup policy, credential and
collector-secret handling, local log/export privacy, WebView origin/bridge rules,
TLS endpoint/host-key validation and first-use/optional-grant separation. Root and
emulator detection are policy choices, not vulnerabilities by their absence alone.
Follow threat-model evidence rather than automatically applying every checklist label.

Use typed `security.assessment.create/status/cancel/report` contracts only after
implementing the backend. Human and agent permissions must be independent.
Install/enroll/modify app binaries, attach instrumentation, enable a device agent,
change root policy or run lab actions only within the recorded grant. Keep these
planned operations out of the live command catalog until implemented and tested.

## Companion adapters and evidence

Prefer a maintained, owner-installed Python environment or verified container on
a dedicated host, controlled through authenticated SSH/private transport. Pin
versions/digests and record capabilities before accepting a job. Do not treat a
user-supplied command string as an approved assessment template. A Drozer agent or
Frida endpoint should not become a public unauthenticated cluster service.

Bound output, time and descendants; Stop must reach the companion process. Raw
assessment data may contain credentials, app memory and personal information:
keep it in separately approved encrypted artifacts, with a sanitized metadata
summary for Meshlit auditing. Export JSON/Markdown reports with hashes, tool versions,
actual checks, unresolved coverage and retest results. Never feed hostile report
text or tool output back as agent instructions.

Acceptance requires actual owned-target tests, permission denial, target mismatch,
revocation, timeout/cancellation, cleanup, secret redaction and independent report
verification. No live target, rooted-device proof or successful engagement is claimed
by the current unit/build evidence.

## Magisk device setup boundary

Follow only the [official Magisk installation guide](https://topjohnwu.github.io/Magisk/install.html)
and official project downloads. The guide requires an unlocked bootloader and a
matching boot/init_boot/recovery image; Samsung has a separate installation path.
Do not reuse another phone's patched image. Meshlit must not silently unlock,
flash, wipe, install modules or change SELinux policy from an AI/web suggestion.
These are separate device-specific actions after the exact hardware/firmware,
backup/recovery path and owner authorization are known.

Future UI: root broker identity (where verifiable), `su` presence, actual approved
`id` result, grant revoked/denied state, kernel/SELinux restrictions, and a clear
human-only operation boundary. Request root only when an explicit action needs it.
The current APK does not root a phone or automatically install Magisk.

## Distribution and handoff

GitHub Full can later expose reviewed lab adapters after their implementation and
hardware tests. Keep the Play build surface independently reviewed and do not
assume removing manifest permissions makes offensive tooling store eligible.
Read [Security Lab roadmap](SECURITY_LAB_AND_HARDWARE_ACCESS.md) and
[Play gates](PLAY_DISTRIBUTION.md). Preserve notices and corresponding-source
obligations for any redistributed components. No remote licenses were changed
for the projects linked above.
