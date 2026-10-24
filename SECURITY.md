# Security reporting
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Do not post credentials, private model prompts, exploit payloads or sensitive
network captures in public issues. Use the repository's GitHub private vulnerability
reporting option when available. If it is unavailable, open an issue asking for a
private reporting channel without describing the vulnerability or exposing data.

Meshlit is an experimental Android development build. See PROGRESS.md for verified
behavior and open gates; no production security certification is claimed.

Security-sensitive areas include encrypted credential reuse, saved human/agent
scopes, worker TLS pins, file-provider grants, browser consent and audit exporters.
Local audit metadata is bounded/batched; it is not a signed tamper-proof ledger.
Known vendor SDK telemetry behavior is documented in docs/remaining-engine-bugs.md.
Report affected source version, device/OS/ABI, minimal authorized reproduction and
sanitized evidence. Preserve third-party licenses and report upstream dependency
issues through the relevant upstream project's security channel.
