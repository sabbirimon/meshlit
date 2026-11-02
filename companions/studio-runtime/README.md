# Studio upstream runtime qualification
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Meshlit will reuse a full Code OSS workbench and the Google Antigravity agent
SDK rather than implement another editor or agent loop. This directory contains a development workbench extension, SDK host and Kotlin
usage bridge, alongside the original preflight tooling. It is not a standalone
installer or a completed device operator.

## Verified inputs

- [VSCodium](https://github.com/VSCodium/vscodium/releases/tag/1.143.17183)
  1.143.17183: Intel macOS workbench archive. Preserve its upstream licences,
  notices and third-party attribution when packaging. Use Open VSX; do not assume
  Microsoft's Marketplace or proprietary extensions may be redistributed.
- [Antigravity SDK](https://github.com/google-antigravity/antigravity-sdk-python)
  0.1.22: Apache-2.0 Python SDK with a platform-specific compiled runtime.
  The wheel metadata declares Alpha. Its compiled runtime and dependencies need
  their own redistribution review before shipping. The SDK is not the IDE UI.

`upstreams.lock.json` pins official artifact URLs, sizes and SHA-256 digests.
All published SDK wheel platforms are recorded, but only Intel macOS preflight
was run. No upstream code or binaries are copied into this repository.

In an isolated Python 3.12 environment with the pinned SDK installed, run:

```sh
python companions/studio-runtime/qualify.py --cache /absolute/artifact-cache
```

The tool verifies cached artifacts, imports the actual SDK, constructs a config
with zero tools/subagents/retries, and checks workbench archive components.
It starts no agent, contacts no model and does not launch or replace the GUI.
Config construction is not proof that runtime policy enforcement works.

## Integration sequence

1. Package the actual workbench with Meshlit branding and a Meshlit extension;
   retain editor tabs, search, Git, terminal, debugging and extension management.
   Keep the existing Studio usable until migration is qualified.
2. Connect independent chat/session controllers to the existing model and usage
   authority. Each chat owns history, selected model, cancellation and approvals.
   Local inference shares a bounded queue, not duplicate model processes.
3. Route Antigravity's OpenAI-compatible requests through a purpose-bound local
   Meshlit bridge. Every request, retry, compaction and delegated request must
   reach durable budget admission and settlement. Do not give the SDK raw vendor
   credentials or rely solely on a pre-turn hook. SDK hooks document a gap for
   connection-initiated turns. No spending-cap promise follows from SDK budgets.
4. Reuse existing enrollment, SSH, MCP and Android Accessibility/OpenClaw paths
   for the operator. Advertise only probed capabilities after pairing. Recheck
   device/scope authorization at execution, propagate Stop/revocation, and retain
   per-device action receipts. No unrestricted control merely from installation.
5. Add workflow profiles (coding, research, files, device diagnostics) as small
   capability sets. Remove unused tools from model context to reduce tokens.
   Keep Plan read-only; expose edit review and bounded Autopilot independently
   from permission level. Reuse provider dashboards rather than recreate them.

## Current development evidence and limits

The real Intel Mac workbench opened Meshlit Home plus two independent chat
panels, a JavaScript editor and the built-in Git extension. A separate genuine
bundled Qwen request completed through the SDK and usage broker (165 input,
6 output tokens). Native SDK tests cover independent sessions and Stop using
synthetic model responses; MCP fixtures cover approval and denial. External
providers, physical phones and complete IDE UI migration remain unqualified.

`prepare_workbench.py` creates a separate development app and retains upstream
notices. It reuses existing local SDK/JDK/Studio dependency jars/model resources;
it is not a portable release build. `--sign-identity` accepts an installed signing
identity; its default is ad-hoc. No command installs a certificate or changes
Keychain permissions. Ad-hoc rebuilds can cause new Keychain consent prompts.
Keychain protects editor secrets; the separate Meshlit usage passphrase unlocks
the existing usage vault. Cancelling an OS prompt must not trigger plaintext
credential storage. Production Apple signing/notarization remains pending.

Home, Chat, Workspace, Coding agent, Models, MCP directory, Usage and Settings
are available through the Meshlit activity icon. The directory lists configured
connectors; it does not claim automatic Git installation or tool discovery.
Sessions are transient, with four open chats and a shared local inference queue.
Older device/VPN/vendor dashboard views are not migrated. Android and paired
phone control are requested follow-ups; the pinned SDK has no Android wheel.
A hosted real workbench and on-device Linux/code-server are distinct candidates,
not implemented mobile capabilities.

Sources: [SDK product](https://antigravity.google/product/antigravity-sdk/),
[SDK hooks](https://github.com/google-antigravity/antigravity-sdk-python/blob/main/google/antigravity/hooks/README.md),
[OpenAI-compatible adapter](https://github.com/google-antigravity/antigravity-sdk-python/blob/main/google/antigravity/connections/local/local_openai_connection_config.py).

## Managed gateway and usage continuation

See the [current implementation and setup](../../docs/desktop/ONLINE_MODELS_AND_CODE_WORKSPACE.md#managed-gateways-and-durable-ide-usage--2026-10-30). The original qualification limits above describe earlier checkpoints. Gateway lifecycle now uses reviewed local development inputs; portable gateway redistribution remains pending. Usage is a ledger-backed page with persistent budget controls. Real local two-session, budget-reopen and gateway-auth evidence is recorded separately from native UI qualification.

Voice and vision: see [OpenLive setup and tuning](../../docs/VOICE_AND_VISION.md).

## Portable Intel Mac candidate — 2026-11-02

`prepare_workbench.py --portable` now accepts explicit `--portable-python`,
`--portable-java` and `--resources` directories. Python must contain the pinned
SDK; Java must retain its legal tree; resources must match the pinned model and
engine manifests. Optional `--portable-openssl /absolute/prefix` bundles only that
prefix's `libssl.3.dylib`, `libcrypto.3.dylib` and `LICENSE.txt`, relocating their
references inside Python. Arbitrary external native dependencies remain errors.
The packager normalizes native library identities, verifies dependencies, signs
the runtime, and refreshes engine integrity manifests after signing.

The candidate was physically moved to a directory with spaces. Its packaged
Python imported SSL and the SDK in a minimal environment; two real local chats,
usage and budget persistence, wrong-passphrase rejection, and all 12 native
HyperL CPU recipes passed there. Three Mach-O packaging tests include loading
relocated dependent libraries after removing the original build-input path.
See [verification evidence](../../docs/VERIFICATION_2026-11-02.md).
The candidate remains ad-hoc signed and not notarized. Desktop computer control
was denied, so this run does not qualify the new candidate's visual interface.
Optional gateway, OpenLive, crawler and other service runtimes remain separate.

Local SDK chat now streams real native text through the usage broker. The broker
admits each request before dispatch and withholds completion/usage chunks and DONE
until ledger settlement succeeds. Stream/line limits, timeout, Stop and failure
reporting remain enforced. Hosted responses retain their existing path. Run
`benchmark_latency.py --runtime /absolute/runtime.json --output /absolute/results.json`
with the packaged Python for isolated local first-text timing; output length varies
with default sampling, so this is not a fixed-token decode benchmark.

## Ollama and LM Studio hosts — 2026-11-02

Models includes named Ollama and LM Studio sources, reusing the existing catalog,
compatible completion route, SecretStorage and usage admission. Local defaults
are `http://127.0.0.1:11434/v1` and `http://127.0.0.1:1234/v1`; no runtime or model
is installed by selecting them. For remote use choose authenticated HTTPS or an
authenticated loopback tunnel. Ollama ignores ordinary local API keys; a client
bearer string alone is not server authentication. Current LM Studio hosting does
not support Intel Mac; Ollama on Intel Mac is CPU only. Android can connect to
these hosts with its model profiles. See the [platform plan and real API
checks](../../docs/LOCAL_INFERENCE_HOSTS_2026-11-02.md).
