# Blue Team and mobile packet-analysis roadmap

Requested by IMON. Companion assessment adapters below are **reviewed plans**, not
installed or executed integrations. See the pinned revisions and licenses in
[security-tool-sources.json](security-tool-sources.json), and the shared
[assessment/authorization design](ANDROID_SECURITY_TOOL_INTEGRATIONS.md).

| Component | Useful role | Meshlit boundary and acceptance |
| --- | --- | --- |
| [Quark Engine](https://github.com/ev-flow/quark-engine) | Android APK behavior/rule analysis | Verified APK digest and pinned rule set; owner-installed GPL-3.0 host companion. Retain rule evidence and uncertainty; a rule match is not conclusive malware attribution. |
| [Androguard](https://github.com/androguard/androguard) | Manifest/DEX inspection and Android reverse engineering | Apache-2.0 host toolkit; validate the installed version and optional parser/native/MCP dependencies. Start with static analysis of Meshlit's own APK; patching/instrumentation are separate grants. |
| [Cuckoo](https://github.com/cuckoosandbox/cuckoo) | Behavior-analysis sandbox reference | The linked legacy repository is archived; its actual LICENSE is GPL-3.0. Do not automatically install it or assume current Android/OS support. Qualify a maintained supported sandbox and guest before creating an adapter. |
| [Atomic Red Team](https://github.com/redcanaryco/atomic-red-team) | Reproducible tests for validating security detections | MIT test collection. Execute only an explicitly selected, reviewed test on an owned compatible host, with prerequisites, cleanup and a time budget. Not every test is Android compatible or harmless. |
| [Autopsy](https://github.com/sleuthkit/autopsy) | Case/evidence management and forensic reports | Desktop forensic companion; upstream identifies Apache-2.0 application licensing with separate dependency licenses. Import approved case/report metadata, not a fake in-app port. |
| [The Sleuth Kit](https://github.com/sleuthkit/sleuthkit) | Volume/filesystem image analysis | Host CLI/library adapter with mixed component licenses, including IPL/CPL/Apache. Verify exact binary/component notices, image formats and acquisition permissions; do not assume root exposes decrypted Android data. |

## Implementation order

1. Original assessment records and encrypted evidence/report references.
2. Static APK/manifest/DEX jobs on a qualified host, initially Meshlit itself.
3. Mobile PCAP handoff/import and approved desktop Wireshark analysis.
4. Read-only forensic image/report processing with evidence provenance.
5. Detection-test jobs with per-test authorization and verified cleanup.
6. Dynamic sandbox jobs only after proving guest isolation, reset and resource limits.

Each job records the input digest, acquisition source/authorization, tool and
ruleset versions, real start/end time, outcome, findings and untested coverage.
Hash evidence at ingestion and after transfer; keep original artifacts read-only,
work on copies and retain an access/change ledger. Raw artifacts may contain
personal data and secrets; generic OpenTelemetry exports must remain metadata-only.
Do not call the current bounded audit journal a complete chain-of-custody system.

Sandbox execution runs on a dedicated capable host/VM with no production keys,
cluster credentials or ordinary task-memory mounts. Outbound traffic must follow
the explicit assessment policy. Treat analyzed files, generated HTML reports and
test definitions as untrusted inputs. Never automatically run code found in an APK,
report or downloaded test. Verify cleanup/revert independently rather than trusting
an exit code or self-reported success.

## Mobile Wireshark-style workflow

Meshlit has a limited local classic-PCAP record viewer and a PCAPdroid external
bridge. The legacy built-in VPN service is disabled: it routed packets into a TUN
but did not forward them, so it could interrupt connectivity. Both its manifest
and start handler now fail closed; the UI directs users to the companion. This is not the full Wireshark protocol
engine, stream reassembly or display-filter language. Prefer the established
[PCAPdroid companion](https://github.com/emanuele-f/PCAPdroid) for mobile capture;
its rootless path uses Android VPN consent and can conflict with another active VPN.
Root mode requires independently available/approved root and is not automatically
requested by Meshlit.

The reviewed [PCAPdroid API](https://github.com/emanuele-f/PCAPdroid/blob/66f7b3514540c20add8655a74dfee93d00a9563b/docs/app_api.md)
uses the explicit CaptureCtrl activity, action extras and an activity-result caller
for consent. Legacy START_CAPTURE/STOP_CAPTURE action strings in Meshlit were not
the documented API. The source correction preserves user approval, reports cancellation
and defaults to Meshlit's own package, root off, TLS interception off, bounded PCAP
file output and no external collector. PCAPdroid remains separately installed;
no GPL library/source is relabeled as Apache or bundled by this handoff.

The mobile preview source now has bounded file/packet budgets, off-main-thread import and
classic PCAP big/little-endian microsecond/nanosecond support. Unsupported PCAPNG,
truncation and budget limits must be visible. Large captures and detailed protocol
analysis belong in [desktop Wireshark](https://www.wireshark.org/docs/wsug_html_chunked/ChapterIntroduction.html).
A future authenticated host analysis adapter can return a bounded sanitized result;
do not expose a raw capture listener on the internet. PCAPdroid's paid features
must not be advertised as free Meshlit entitlements.

Packet payloads and key logs can contain secrets. Captures stay in local/explicitly
granted files; sending them to a host, cloud model or third party needs separate
user approval. TLS decryption is not implied by capturing encrypted packets.
Wi-Fi monitor mode requires actual chipset/driver/interface support; Magisk alone
cannot create it. Live capture/API and Wireshark host tests remain acceptance gates.

Play Review omits Meshlit's device-wide VPN service and direct companion capture
controls. Manual analysis of a user-selected file is a separate capability. Keep
[Play distribution gates](PLAY_DISTRIBUTION.md) explicit.

The bridge/parser correction requires the final build and malformed/endian/budget
tests; actual PCAPdroid installation/capture and desktop Wireshark remain unproven.

The legacy connectivity bug was found during this source review; the final frozen
build must include the disabled service. No traffic-preserving built-in VPN backend
or full Wireshark engine is claimed.
