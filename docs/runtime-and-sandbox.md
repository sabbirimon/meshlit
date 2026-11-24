# Optional runtime, terminal and VM

The default remains RunAnywhere in the Android app process. The following tools
are opt-in and do not automatically download or launch a guest.

## Execution methods

- APP: batch programs with Meshlit's Android UID. This shares app permissions;
  it is not filesystem isolation.
- ROOT: explicit `su` invocation, requiring `--allow-root` for each human command.
- ROOT_CHROOT: explicit root invocation and a supplied root filesystem. Chroot
  changes path visibility; root remains powerful on the host.
- PROOT: supplied executable/rootfs for rootless userspace path translation.
  PRoot does not provide a kernel security boundary.
- BUBBLEWRAP: supplied executable/rootfs, read-only root, private namespaces,
  workspace bind and private networking. Android kernels may deny namespaces.
- VM_SSH: verified host-key OpenSSH into a loopback guest, with a supplied key.
  No automatic acceptance of host keys or remote public listener.

Missing binaries and unsupported capabilities fail explicitly. On Android 10+
[execution from writable app storage is restricted](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).
An APK needs install-time, ABI-correct native packaging or a separately designed
host integration. Copying QEMU/PRoot/SSH into filesDir and chmod is not a delivery
solution. No native runtime installers are included in this change.

## Human terminal

Open Sessions → Terminal. These are bounded batch commands with quote support,
not a PTY or general shell grammar. Pipes, variable expansion and redirection
require explicitly invoking a shell; they are never inferred.

```text
runtime status
runtime use app
runtime exec /system/bin/id
runtime use root /system/bin/su
runtime exec --allow-root /system/bin/id
runtime use proot /installed/path/proot /path/to/rootfs
runtime use root_chroot /system/bin/su /path/to/rootfs
runtime use bubblewrap /installed/path/bwrap /path/to/rootfs
net interfaces
net dns example.com
net tcp example.com 443 --authorized
artifact verify /accessible/path/guest.qcow2 EXPECTED_SHA256
artifact import /accessible/path/guest.qcow2 guest.qcow2 EXPECTED_SHA256
```

Replace example paths with actual accessible, trusted artifacts. The Android app
cannot read arbitrary other-app files. Artifact import uses an expected SHA-256,
staging and atomic replacement; it does not execute or unpack the input. TCP
checks are limited to 16 explicit ports on one authorized host and retain up to
20 JSON reports. DNS resolution depends on the platform resolver and may not
respond immediately to coroutine cancellation.

## Full VM on demand

Provide a trusted QEMU executable and a bootable QCOW2 guest, matched to the
chosen architecture. ARM direct boot also needs the correct kernel and optionally
initrd; its configured root device is /dev/vda. Guest images need preconfigured
SSH keys, host keys and, for VNC, a graphical desktop. Provisioning those is an
operator step, not performed by the app.

```text
vm configure /installed/path/qemu-system-x86_64 /accessible/path/guest.qcow2 x86_64
vm resources 512 1
vm desktop on
runtime ssh /installed/path/ssh guest /accessible/path/id_ed25519 /accessible/path/known_hosts 2222
vm start
vm wait
runtime exec /usr/bin/uname -a
vm console
vm vnc
vm stop
```

QEMU uses software emulation, an ephemeral snapshot, restricted user networking,
SSH forwarding on 127.0.0.1:2222 and optional VNC on 127.0.0.1:5901. Snapshot
changes are discarded on stop. Restricted networking prevents general outbound
guest internet; use a preprovisioned image. VNC opens an installed external
viewer; Meshlit does not implement a VNC renderer. Loopback does not protect
against another application on the same device.

A foreground service maintains the VM, stops on severe thermal/low-memory state
or a 30-minute session limit and provides a Stop notification. RAM checks reserve
256 MiB beyond the guest budget. Service restart does not boot a VM. These
controls need physical-device verification, including manufacturer background
restrictions. See [Android special-use foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
and [QEMU invocation](https://www.qemu.org/docs/master/system/invocation.html).

## Agent activation

The Cloud VM card or `vm agent on` explicitly authorizes agent VM start, stop,
readiness checks and guest commands. It defaults off. Model arguments cannot
change installed binary/image paths or request root. `vm_exec` uses only VM_SSH
and requires an SSH-ready guest; selecting a human root backend does not grant
agent root. An SSH banner confirms service availability, not authentication or
successful command execution. Turn this authorization off after a task.

The process runner bounds output and time and propagates cancellation. It stops
the direct child process, but does not guarantee rooted descendants are killed.
Native process-group handling and OS isolation are prerequisites for running
untrusted shell programs safely. Do not treat APP, PRoot or root chroot as a
security sandbox for arbitrary generated code.

## Rooted-device extension roadmap

GitHub Full retains the implemented root/root-chroot runtime plans, rootless
compatibility and optional VM controls. Physical rooted-device/VM execution is
still unverified. Potential additions are capability-probed Linux namespaces,
cgroup quotas, native process-group supervision/descendant cancellation, richer
read-only thermal/driver diagnostics, and KVM detection only where `/dev/kvm`,
kernel/SELinux policy and owner permission allow it. Root does not create kernel
features or grant a working vendor GPU/NPU backend. Keep these as planned rather
than operational controls until actual hardware and cancellation tests pass.
Play distribution needs an independently reviewed capability/permission surface;
its narrower manifest alone does not certify every retained runtime integration.

## Explicit guest package networking (2026-10-07)

Guest networking remains restricted by default. Human `vm network on|off` or Lab packages controls can configure outbound user-mode networking only while stopped. Next startup applies it; inbound SSH/VNC forwarding remains loopback. Guest VM isolation is distinct from outbound network policy. Recreate session-bound lab grants after restart.


## Persistent disks and verified setup import (2026-10-07 continuation)

`vm storage ephemeral|persistent` changes mode while stopped. Default ephemeral
mode includes QEMU `-snapshot`; persistent mode omits it and writes to the supplied
qcow2. Lab packages exposes the same explicit human controls. Root access is still
independent, and agent activation cannot change disk policy. Stop is not a rollback
for persistent writes; keep an owned disposable image/backup.

Lab packages also imports opaque disk/kernel/initrd artifacts from storage or exact
HTTPS with supplied SHA-256 and exact size. Maximum 8 GiB, thirty-minute deadline,
64 MiB storage headroom, no redirects/credentials/query URLs, no unpack or execution.
Imports are serialized, atomic and remove incomplete staging. Compatible Android
QEMU/native packaging, trusted distro catalogues and actual guest acceptance remain
separate work. Imported Linux executables are not automatically Android-compatible.

## SSH node and agent revocation (2026-10-09)

Experimental [SSH node commands](SSH_NODE.md) can reach the configured guest with per-key control/execution scopes. Agent activation uses committed, default-off consent; revocation cancels waits/commands and stops agent-started VMs. Global VM policy is checked in session maintenance. Human app diagnostic commands are fixed to APP regardless of the selected root backend. The Core candidate removes the VM/SSH services and blocks their operation gates. None of these controls bundles or qualifies QEMU/guest artifacts.
