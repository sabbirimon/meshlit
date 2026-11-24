# Desktop terminal and Ghostty SSH

Updated 2026-10-10.

The built-in Terminal runs a human-entered command using the selected shell and
working directory, under the OS account that started Meshlit. It is ready without
an additional command-grant checkbox. It executes only when Run is pressed;
model replies, hooks and MCP cannot call it until a separate tool dispatcher is
implemented. Five-minute deadline, 64 KiB per output stream, real exit code,
Stop and owned-child cleanup are implemented. No interactive PTY is claimed.

Advanced → SSH also offers an optional external Ghostty session. Select installed
Ghostty/OpenSSH and an existing private-key file, use the target/user/port above,
and supply a host public key matching the independently verified SHA256 pin.
No private key value is passed on the command line or copied into Meshlit storage.
OpenSSH uses strict host checking and a private temporary known-hosts file,
disables ambient SSH config, forwarding, proxies and local commands. Ghostty
uses literal `-e` argv with no shell expansion, default config disabled and no
clipboard read/write. Close the Ghostty session to end it; Meshlit's chat Stop
cannot control an independent terminal window.

[Ghostty](https://github.com/ghostty-org/ghostty) is MIT licensed. Its official
[configuration reference](https://ghostty.org/docs/config/reference) documents
`-e` direct arguments and `config-default-files=false`. Installed Ghostty 1.3.1
was detected on this Intel Mac. No interactive SSH handshake has been qualified
for this new handoff. Windows GUI support/embedded libghostty and full PTY integration
remain separate work; upstream API/platform availability is not Meshlit proof.

The alternate [usetrmnl/terminus](https://github.com/usetrmnl/terminus) repository
is a Ruby/Hanami TRMNL e-paper device server, not an SSH terminal. It was reviewed
as a reference and not imported into this terminal module.

Agents/full-auto terminal use are requested and tracked in
[security and runtime contracts](SECURITY_AND_RUNTIME.md). The human panel does
not silently elevate root/admin; OS authentication requires an interactive flow.
