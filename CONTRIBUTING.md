# Contributing to Meshlit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Meshlit's priority is verifiable local inference and genuine phone layer execution.
Good contributions include reproducible Android/model reports, native worker and
recovery contracts, accessibility improvements and real owner-controlled adapter
checks. Read AGENTS.md, AGENT_BUILD.md, PROGRESS.md and BUILD_MILESTONES.md first.
Setup questions, feature ideas and usability feedback are also welcome. Describe
the device or use case and the behavior you expected; code is not required.

## Help test on real hardware

We welcome testing help across many kinds of hardware and networks. Useful
coverage includes low-memory and newer ARM64 Android phones, x86_64 emulators,
two or more physical phones for pairing, Intel/AMD/Apple desktop hosts, and
systems with vendor GPU/NPU drivers. Diverse screen sizes, accessibility
settings, thermal conditions, local networks and VPNs also matter. For small
IoT or edge devices, report the actual storage, sensor, routing or tool role;
do not infer that they can run transformer layers.

Choose one narrow workflow: install, download/checksum/load/generate/unload,
photo provider setup, voice, a permission grant and revocation, pairing, a
worker failure, or an optional host adapter. State whether the result was a
physical-device run, emulator run, desktop run or source/unit check. A failure
report is as valuable as a success report when it is reproducible.

Include these details in a [new issue](https://github.com/sabbirimon/meshlit/issues/new/choose):

| Field | What to report |
| --- | --- |
| Build | Release tag or commit SHA; V1/V2 flavor and debug/release signature type |
| Hardware | Device and chipset/CPU, RAM, Android/API or desktop OS, ABI, relevant GPU/NPU driver |
| Model | Exact file/revision, format, quantization, size and SHA-256 when testing inference |
| Setup | Local/online route, backend, context and cache settings, network type and number of peers |
| Steps | Short numbered reproduction, expected result and observed result |
| Evidence | Sanitized screenshot/log, elapsed time or measured counters; mark unavailable values as unknown |
| Resource use | Observed battery, power, temperature and device age when available; explain how each was measured |

Remove account IDs, tokens, private prompts, precise locations and unrelated
device data before sharing. The [device-testing record](docs/DEVICE_TESTING_2026-10-08.md)
shows how to separate measured behavior from remaining gates.

## Working on a change

1. Open an issue describing the concrete problem, expected behavior and evidence.
2. Fork/clone this repository and use a focused branch. Build with pinned toolchains;
   prepare the verified real starter asset before APK assembly.
3. Preserve core interfaces, explicit consent, encrypted credentials and unknown
   values. A device role, catalog result or mocked test must not become a fake
   successful runtime status in production.
4. Add meaningful tests for changed contracts/failure paths. Build both app flavors
   and run fatal lint. Device/native/power checks need named hardware evidence.
5. Submit a pull request with what changed, why, how it was verified and remaining
   limits. Do not include credentials, model weights, APKs or build directories.

Use the bug report template for Android ABI/OS/app version, exact model source,
context/backend/cache settings and a minimal reproduction. Redact prompts and
credentials before attaching logs. A screenshot demonstrates layout, not compute
performance. Do not claim phone sharding from desktop worker evidence.

## Useful first contributions

- Reproduce a small model load/download issue on actual owned hardware.
- Improve text scaling, contrast or keyboard inset behavior with before/after bounds.
- Exercise an existing SSH/OpenClaw/cloud adapter on an owner-controlled host.
- Test archive failure/cleanup with a granted Android document provider.
- Improve a specific guide recipe after verifying the corresponding control.

These are contribution suggestions, not assertions that matching issues have been
created or labeled. Ask maintainers before large runtime/license changes. Preserve
upstream licenses/NOTICE and source pins. Report security issues privately according
to SECURITY.md; avoid publishing secrets or exploit payloads in issue comments.
