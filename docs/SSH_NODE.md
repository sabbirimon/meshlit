# Bidirectional SSH app nodes

Experimental Android 8/API 26+ inbound path, separate from the existing pinned
outbound SSH client. Neither path installs a layer worker. SSH encryption and
identity are separate from LLM selection, model locality and VM isolation.

## Phone as server

Settings → SSH nodes and connections → SSH into this app node.

1. Generate an ECDSA P-256 or RSA 3072+ key on the client. Keep the private key on
   that client. Copy only its one-line `.pub` key into Meshlit.
2. Enrol a username/key and explicit scopes. Agent identities default to status
   only, cannot receive APP_EXEC, and need separate VM activation plus global
   SSH/VM/automation grants where applicable. Permission and runtime setup changes
   remain human-only. Stop the listener before changing/revoking keys.
3. Bind loopback or one currently assigned private IPv4 address, port 1024–65535.
   Start explicitly. The app does not open router ports or start on boot. LAN can
   work without Internet; remote access needs an owner-configured private VPN or
   private network. Public/wildcard addresses are rejected.
4. Independently verify the displayed SHA256 server fingerprint on the client.
   Use ordinary SSH exec, for example `ssh -T -p 2223 meshlit@PHONE_IP 'status'`.
   The first connection's displayed key is not automatic trust approval.

Commands are strict, bounded JSON, not a login shell. Examples:

```json
{"action":"VM_STATUS"}
{"action":"VM_START"}
{"action":"VM_WAIT"}
{"action":"VM_EXEC","argv":["/usr/bin/uname","-a"]}
{"action":"VM_STOP"}
{"action":"APP_EXEC","argv":["/system/bin/id"]}
```

STATUS/VM_STATUS need STATUS; start/wait/stop need VM_CONTROL; guest commands need
VM_EXEC. APP_EXEC is human-only and restricted to installed Android `id`, `uname`,
`uptime`, `df` diagnostics. No shell, SFTP/SCP, forwarding, public listener,
password authentication or remote root/configuration/permission API is exposed.
A human key is powerful within its grants: do not give it to an agent.

The persistent P-256 host identity and key grants use Android Keystore-backed
credential storage. No private key is returned in the UI, protocol or ordinary
configuration export. Clearing data loses the identity and clients must verify
its replacement. Compromised/unlocked devices remain outside this protection.

Limits: 32 enrolled keys, four concurrent connections (including unauthenticated
handshakes), two channels per connection, four active commands, 15-second auth,
five auth attempts per connection, 60-second idle/command limits, 128 KiB reply
bound, 30-minute human-started listener lease. Resource pressure, global Stop and
notification Stop terminate the local listener. Disconnect/Stop cancels registered
work; completed or remote actions are not undone. This is bounded availability,
not a denial-of-service resistance certification.

## Phone as client and agents

Existing outbound profiles require an independently verified server fingerprint.
Passwords/private keys or exact-purpose vault references remain encrypted. Editing
or removing a profile cancels its active registered calls. No accept-new behavior
or key-value export is introduced. Outbound calls remain 60 seconds and 64 KiB per
stdout/stderr stream; closing SSH is not proof a remote process was killed.

Enable Agent identity access on an outbound profile and select allowed **node
actions**. `ssh_nodes` exposes delegated IDs/action names, and `ssh_node_request`
sends strict JSON to an already enrolled Meshlit node. The same remote key must
have matching scopes there. Agent raw shell commands are rejected. Generic OS SSH
commands on Windows/Unix remain explicit human commands; Meshlit does not infer a
remote shell dialect or automatically enable Windows OpenSSH.

## VM and sandbox boundary

`runtime-and-sandbox.md` describes existing APP, root/root-chroot, PRoot,
Bubblewrap and optional QEMU. APP uses Meshlit's UID and is not additional filesystem
isolation. PRoot/chroot are not strong arbitrary-code security boundaries. Agents
use only the configured verified-key VM guest, never selected human root paths.
The VM remains off and agent activation false by default. Its saved opt-in now
uses committed persistence; revocation cancels agent waits/commands and stops an
agent-started VM. Global VM/agent revocation is rechecked during maintenance.

QEMU, an ABI/OS-compatible install-time executable and a trusted bootable guest
are still prerequisites. These are not bundled or downloaded/executed by SSH.
Missing artifacts fail explicitly. A process start/SSH banner is not an
authenticated guest command or a working desktop. Guest outbound networking and
persistent disks use explicit human policies; Stop is not rollback for persistent
writes. Native device/guest tests and privileged descendant containment remain
open. A local SSH host test does not establish Android VM or Internet readiness.

## Implementation and dependency

Pinned Apache MINA SSHD 2.20.0 (sshd-core/common), Apache-2.0. Its maintained
[embedding guide](https://github.com/apache/mina-sshd/blob/master/docs/server-setup.md)
describes identity/authentication/command configuration and warns that process
shells share the server UID. Its [Android notes](https://github.com/apache/mina-sshd/blob/master/docs/android.md)
explicitly do not certify Android compatibility. Meshlit therefore keeps this
adapter experimental and API 26+; actual Android/OEM crypto, NIO, foreground and
background tests are acceptance gates. It uses platform/JCE P-256/RSA, SHA2
signatures/MACs and restricted KEX/ciphers, with no downloaded executable.

In Chat → Conversation settings, the default-off **VM and SSH node tools** option
exposes these registered tools to the on-device planner. Per-chat selection never
creates delegation. Web/phone tools are deselected when enabling node tools to
keep the descriptor set bounded; imported/restored option combinations are also
validated. Three tool calls and a 180-second conversation-tool deadline remain.
The tiny starter model is not qualified for reliable tool planning; invalid JSON
fails before dispatch rather than being interpreted as a shell command.
