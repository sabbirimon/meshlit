# Meshlit review and implementation plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-10-06 against Meshlit `ff0cd771c12f3daa63cb8fe45e1476245ab7b01b`.
Historical baseline review. Later implementations and proofs are recorded in
[PROGRESS.md](../PROGRESS.md), [current state](architecture/current-state.md) and
[third-party notices](../THIRD_PARTY_NOTICES.md). The findings below must be
rechecked against current source; they are not an up-to-date bug count.

## Product direction

Keep RunAnywhere on-device inference as the normal app experience. A Linux VM
is an optional tool environment, started by a person or an explicitly authorized
agent only when a task needs it. Root is never a prerequisite for local inference.
These tools help with development, local automation, document collection and
network troubleshooting; they do not improve model quality by themselves.
Running QEMU on a phone adds substantial memory, battery and startup costs.

## Implemented in this checkout

| Capability | Implementation | Practical boundary |
| --- | --- | --- |
| Default inference | Existing RunAnywhere Android SDK preserved | Model installation/device support still required |
| Root/rootless methods | APP, ROOT, ROOT_CHROOT, PROOT, BUBBLEWRAP, VM_SSH plans in core-sandbox | Installed executables/kernel support required; no silent fallback |
| Full VM | QEMU on-demand controller, ephemeral disk, bounded resources, foreground lifecycle and console | No QEMU binary or guest image bundled; boot not device-tested |
| Agent VM use | vm_status/start/wait/stop/exec tools and local tool continuation | Start/stop/guest execution require user opt-in; no agent root tool |
| Desktop | Optional QEMU graphics and loopback VNC viewer handoff | Guest needs a desktop; external VNC app required |
| Terminal | Sessions batch command execution, bounded output and timeout | No interactive PTY/SSH terminal emulator added |
| Networking | Interface/DNS reports and authorized single-host TCP checks | No subnet scanning, exploit tools or packet capture |
| Artifacts | SHA-256 checked, staged file import with explicit replacement | Not a guest installer or archive extractor |
| Crawler | Authenticated HTTPS app bridge plus optional Crawl4AI companion | Host-side Python/Chromium; access controls respected |
| Browser assistant | Local model proposes DOM actions; person approves each step | Original Android implementation; limited DOM, no full extension port |
| llama.cpp fork | Pinned optional source and standalone host build script | Existing SDK remains the Android inference backend |

Both Cloud screens expose VM controls, crawler settings and browser assistant.
Both Sessions screens expose terminal commands. No VM starts at application boot.
See [runtime setup](runtime-and-sandbox.md), [browser/llama integration](runanywhere-browser-and-llama.md)
and [crawler setup](../companions/crawler/README.md).

## Findings and next improvements

1. **Central tool authorization:** `McpToolRegistry.invoke` does not itself enforce
   the declared resource permission gate. New VM/crawler tools enforce their own
   opt-ins. Connect the shared gate consistently to every dispatch path, with
   tests that deny unapproved local execution and outbound requests.
2. **Existing web fetch:** `WebFetchMcpTools` validates the initial URL but needs
   redirect, private-address and cancellation handling review. The new crawler
   bridge is a separate implementation, not a fix to every old fetch entry point.
3. **Existing shell tool:** draining stdout/stderr after waiting for exit can
   deadlock on large output. The new runtime runner drains both while running;
   migrate older shell entry points to the same bounded execution contract.
4. **Native capability delivery:** package ABI-specific executables correctly,
   detect kernel/SELinux constraints and add real device coverage. A configured
   path is not proof that Android will execute a binary.
5. **Honest documentation:** older WebTools settings are ephemeral despite text
   suggesting persistence; MCP server documentation describes networking beyond
   the subprocess transport currently implemented. Audit these against code.
6. **Inference boundaries:** the direct JNI `LlamaCppInferenceEngine` is a stub;
   the existing SDK-backed path is the working integration. Do not market the
   standalone host build as new Android JNI support.
7. **SSH boundaries:** core-ssh configuration is not a complete SSH client.
   VM_SSH currently delegates to a separately installed OpenSSH executable.
8. **Agent execution:** the existing AgentPromptRunner still uses the configured
   model API endpoint. Added local tool continuation does not make that runner
   an on-device planner. The new browser assistant uses local inference.
9. **Lifecycle and performance:** measure VM start, stop, process cleanup,
   low-memory/thermal stop, background restrictions and VNC on named phones.
   Move VM execution to a dedicated native service if resource isolation requires it.
10. **UX:** replace path-based terminal setup with a verified capability catalog,
    Android Storage Access Framework imports and guided guest installation after
    packaging native artifacts. Add interactive PTY only with proper native
    process groups and cancellation, not an unbounded shell wrapper.

## Stryker reuse and licensing

Stryker supplied product ideas: engine selection, root/rootless Linux, VM state,
desktop handoff and diagnostics. No Stryker source or assets were copied.
Stryker is GPLv3; copying its implementation into an Apache-only distribution
requires a deliberate licensing decision and compliance review. The RunAnywhere
browser extension retains Apache-2.0 and its third-party NOTICE; the llama fork
retains MIT. See the upstream license files and
[Apache's GPL compatibility explanation](https://www.apache.org/licenses/GPL-compatibility.html).

## Release requirements

JVM/fake-engine tests are not proof of a working Android VM. Before advertising
turnkey Linux, ship verified binaries/guest images and test root denial, missing
binaries, namespace denial, guest SSH host keys, VNC, cancellation, orphan cleanup,
foreground-service behavior and power budgets on physical devices. A PRoot or
chroot environment is not a strong security boundary for hostile agent code.

## Follow-up source review and reuse decision (2026-10-07)

IMON authorized reuse of Stryker or other open-source components when useful.
Reviewed Stryker revision `d7c60f13ed950c9f982e48d46dc06b4a7a62e362`, specifically
[QemuInstaller](https://github.com/sabbirimon/strykerapp-v6.5/blob/d7c60f13ed950c9f982e48d46dc06b4a7a62e362/app/src/main/java/com/zalexdev/stryker/engine/QemuInstaller.java),
[QemuDownloader](https://github.com/sabbirimon/strykerapp-v6.5/blob/d7c60f13ed950c9f982e48d46dc06b4a7a62e362/app/src/main/java/com/zalexdev/stryker/ota/QemuDownloader.java) and
[VmProbe](https://github.com/sabbirimon/strykerapp-v6.5/blob/d7c60f13ed950c9f982e48d46dc06b4a7a62e362/app/src/main/java/com/zalexdev/stryker/engine/VmProbe.java).
This review imported no source, assets, native binaries or tools into Meshlit.

Useful design patterns for the next runtime phase:

| Pattern observed | Meshlit integration requirement |
| --- | --- |
| Named installer stages and byte progress | Publish actual download/extract/verify state through typed commands and UI; never mark a missing runtime ready. |
| Bundled/offline artifacts and a remote manifest | Pin provenance, architecture, license, size and digest before accepting installation. Cache verified artifacts for offline provisioning. |
| Temporary decompression then rename | Bound expanded size and storage, support cancellation, and preserve the last verified artifact on failure. |
| QEMU CPU and io_uring probes | Use bounded executable probes; acceptance of command options is separate from guest boot, SSH, VNC and physical-device proof. |
| Separate missing-artifact/offline/failed outcomes | Return observable, actionable failure codes without raw credentials or misleading automatic fallback. |

Do not reproduce unchecked fallback behavior: the reviewed downloader can return
empty checksums, and the installer warns without necessarily rejecting the result.
Meshlit's runtime catalog must require an independently trusted digest or signature
for every remote executable and guest artifact. Root, architecture labels and file
existence alone do not prove a working VM.

Prefer established upstream components at an explicit integration boundary: QEMU
for full guests, PRoot for compatible rootless userlands, OpenSSH or the existing
JSch transport for authenticated SSH, and distro-maintained images/packages.
Each distributed component needs its own license, notices, provenance and
corresponding-source obligations checked. An independent optional GPL companion
may be appropriate; separating processes alone does not automatically settle
whether a combined product is subject to GPL. Direct Stryker imports require a
compatible distribution licensing decision, not an Apache-only relabel.

Next implementation scope is the verified artifact catalog and installer, followed
by a real Android guest boot/SSH/VNC test. Distribution installers, root probes,
Security Lab tooling and Metasploit remain planned rather than bundled or proven.
See [distribution/tool plan](LINUX_DISTRIBUTIONS_AND_LAB_TOOLS.md) and
[hardware/Security Lab plan](SECURITY_LAB_AND_HARDWARE_ACCESS.md).
