# Linux distributions and optional lab tools

Requested by IMON. **Provisioning roadmap; not an implemented one-click distro or
Metasploit installer.** Current runtime commands accept compatible installed
binaries/root filesystems and preprovisioned QEMU guests. Read runtime-and-sandbox.md
and SECURITY_LAB_AND_HARDWARE_ACCESS.md for actual boundaries and test gaps.

## Intended profiles

| Profile | Source and intended role | Compatibility gate |
|---|---|---|
| Alpine | [Official downloads](https://alpinelinux.org/downloads/); lightweight rootfs or VM | Distinguish mini-rootfs from a bootable virtual image; match architecture, ABI and guest kernel |
| Kali / Kali Purple | [Official images](https://www.kali.org/get-kali/) and [verification guide](https://www.kali.org/docs/introduction/download-official-kali-linux-images/); security lab/defensive tooling | Device-specific ARM images are not universal phone/QEMU images; select a tested generic guest or appropriate rootfs |
| Parrot | [Official downloads](https://parrotsec.org/download/); security/development profiles, including QEMU formats | Select the correct edition/architecture; verify publisher resource requirements and disk format before download |
| Ubuntu | [Official cloud images](https://cloud-images.ubuntu.com/); general Linux development/server tools | Provision SSH identity/keys and a compatible kernel/firmware/boot path; cloud image availability is not boot proof |
| RHEL | [Official developer downloads](https://developers.redhat.com/products/rhel/download); authorized enterprise environment | Owner supplies applicable entitlement/subscription and verified image; do not redistribute credentials or assume unrestricted licensing |
| Custom Linux | Owner-provided rootfs or QCOW2 with provenance | Exact architecture, digest, license, executable and boot requirements must be supplied and verified |

These are profiles to implement, not claims that every distribution boots on every
phone. Containers/PRoot/chroot share the host kernel; a QEMU guest has its own
kernel. Rootless PRoot does not provide raw device privileges or VM isolation.
QEMU uses software emulation today; KVM is a separate capability-probed roadmap.
Heavy security desktops/tools compete with model inference for RAM, storage,
CPU, battery and thermal budget. Choose a smaller environment or an approved
SSH-connected lab host when the phone cannot reserve adequate resources.

## Installation lifecycle to implement

1. Resolve an official, versioned artifact and retain source/architecture/license,
   SHA-256 and signature provenance. No arbitrary web installation scripts.
2. Present real download size, available storage/RAM, extraction headroom, network
   policy and boot prerequisites. Check actual free resources again before launch.
3. Resume verified downloads, stream to staging, validate checksum/signature, and
   atomically install. Reject unsafe archive paths and clean failed partial output.
4. Provision guest identity, pinned SSH host key, narrowly scoped credentials and
   optional desktop/VNC. Verify authenticated readiness and an actual guest command.
5. Offer explicit ephemeral or persistent disk modes with snapshots/backups and
   reviewed migrations. **Current QEMU uses ephemeral `-snapshot`: changes are lost
   when stopped.** Do not advertise persistent tool installations on that path.
6. Make networking explicit. **Current restricted guest networking does not allow
   general outbound package installation.** Preprovision tools or design an
   approved bounded network policy before claiming live guest package installs.
7. Honor human/agent activation policies, scopes, thermal limits, timeouts, process
   supervision and Stop. Store/export evidence outside an ephemeral guest when needed.

## Tools

Metasploit Framework is a possible optional guest/approved-host tool, with its
[official installation documentation](https://docs.rapid7.com/metasploit/installing-the-metasploit-framework).
Other tools can be added through reviewed distribution packages or host adapters
with declared versions/licenses and actual setup checks. Meshlit currently does
not bundle or automatically install Metasploit, Kali or Parrot. Shell support is
not proof that a package works, that raw sockets are available, or that a security
engagement succeeded. Red/Blue Team target permissions and budgets apply regardless
of where a tool executes. Keep these extensions distinct from the Play edition's
independently reviewed distribution rules.

## Acceptance evidence

Record real guest boot, architecture, authenticated SSH command, optional VNC,
package version, offline/online install behavior, persistent/ephemeral disk behavior,
low-memory/thermal stop and resource cleanup on the target device. Tool invocations
need an owned lab, exact authorization, cancellation and credential-redaction checks.
No distribution images or third-party packages were downloaded or executed as part
of this documentation change.
