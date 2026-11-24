# Meshlit Security Lab companions

Original Python 3 tools; no upstream cyber suite is bundled. Install inside an
owner-provisioned Linux VM, not on the Android host or ordinary SSH machine.
Android lab operations require VM_SSH and SSH_READY on rooted and non-rooted
phones. PRoot/APP/root chroot are not sufficient isolation gates.

## Guest setup

Copy these two scripts into your dedicated guest. Provision the marker and wrappers
as a human guest administrator (root inside guest is independent of phone root):

```
install -m 755 meshlit_cyber.py /usr/local/bin/meshlit-cyber
install -m 755 meshlit_packages.py /usr/local/bin/meshlit-packages
touch /etc/meshlit-lab-owned
```

Use a non-root guest account for pip. Python venv support, timeout, base64 and SSH
must be installed before use. Distro package managers need a separately configured
root guest account and explicit per-operation guest-root approval. No automatic
sudo escalation. Never create the marker on a production/shared host.

## Analysis

`meshlit-cyber probe` reports installed binaries, not adapter readiness/version proof.
`meshlit-cyber run --tool apk_inventory --input /owned/app.apk --sha256 DIGEST`
returns APK ZIP/DEX metadata, not a malware verdict/signature/manifest decoder.
`pcap_summary` validates bounded classic-PCAP records; PCAPNG is unavailable.
`tshark` requires independently installed Wireshark CLI and returns count-only
metadata for at most 10,000 packets. No payload export or automatic capture.
`sleuthkit` requires installed `fsstat` and reports bounded raw filesystem-image
metadata only. No offset selection, recovery or Autopsy GUI integration. External
processes have output/deadline bounds and group cleanup on failure.
Input hashes are verified before/after analysis. App assessment grants additionally
restrict exact artifact, expiry, operation and agent permission.

Other earlier tools (Androguard, Quark, Objection/Frida, Drozer, Metasploit,
Havoc, Magisk, Atomic Red Team, Autopsy/advanced Sleuth Kit recovery and a qualified dynamic sandbox)
remain explicitly planned adapters, not functioning integrations.

## Packages

App Settings -> Lab packages uses `meshlit-packages` for list/install/uninstall.
Supported installed managers: pip, apt, apk, dnf, pacman. Repo package names,
SHA-256 checked guest files or exact HTTPS artifact URLs are accepted.
Phone storage import transfers a bounded package into a unique guest /tmp file
through the existing pinned loopback guest SSH command path. It computes a digest;
that is integrity evidence, not publisher authenticity. Verify publisher provenance.
Another device can supply a manually approved HTTPS artifact or copied file.

pip uses ~/.local/share/meshlit-lab/venv; wheel imports supported. Distro formats:
.deb, .apk, .rpm, .pkg.tar.zst. Architecture/version/dependencies must match guest.
No signature bypass flags. Dependencies use guest repository/index configuration;
review that configuration before install. Web artifact redirects are rejected.
128 MiB transfer/artifact limit. Uninstall takes the installed package name and does
not promise removal of configuration, dependencies, evidence or staged imports.
Clean staged files manually; ephemeral QEMU changes disappear on stop; explicit persistent mode retains disk writes.

Package scripts execute inside guest. VM must be qualified before hostile packages;
network isolation and CPU/RAM budgets come from the existing runtime configuration.
Guest networking is restricted by default; Lab packages exposes a human-only outbound opt-in while VM is stopped. Apply at next start, then renew session grants.
App commands use a 50-second guest timeout/5-second kill grace; remote SSH cancellation
is not proof every descendant or package transaction is rolled back. Inspect guest
state before retry. Automatic agent package installation remains unimplemented.

Tests: `python3 -m unittest discover -s companions/cyber/tests -v`.

Sleuth Kit CLI reference: https://sleuthkit.org/sleuthkit/man/fsstat.html

The APK ships these original scripts as data. Lab packages can install/update them
through an explicit human guest-root action in an already ready VM (requires pinned
root SSH, sha256sum/base64 and /usr/local/bin). No sudo or agent setup delegation.
A failure may leave partial updates. Python/venv/timeout/third-party tools are still
separate guest dependencies. Persistent mode retains writes; abrupt Stop may need
guest filesystem recovery, so shut down inside the guest before stopping when needed.
