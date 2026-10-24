# Future build plan — Android and desktop peering, UI and rewards
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-12. Implementation is paused. Target is
`/path/to/workspace/meshlit-v2-dev`; Android Studio `mllm` is a reference
owned by another agent. See [the exact handoff](PEERING_UI_HANDOFF_2026-10-12.md).
These are acceptance gates for this workstream, not a global project phase or
completion percentage. Do not start any gate until the owner resumes work.

## Gate 0 — make the current continuation compile and preserve data

Read AGENTS.md, AGENT_BUILD.md and the handoff. Review the complete working diff,
including untracked files and the earlier ModelInventory changes. Keep current
Kotlin/AGP/native dependencies; do not downgrade to match the older reference.

Compile the portable security module, Android integration and desktop. Fix actual
API/type errors before expanding scope. Verify node/address/appearance persistence,
receipt owner/history checks, encrypted wallet files, failed writes, cancelled
operations and old address-book schema migration. Add meaningful tests for the
new desktop vault and host diagnostics. Leave secret values out of errors and logs.

Acceptance: no lost settings or unrelated source changes; changed contracts pass;
no fabricated model/token/device/reward results. Keep current load/admission locks.

## Gate 1 — finish Android signed pairing and functional settings

Complete modern Network and pairing navigation: saved devices, discovery,
verification/approval, relay host/join, NFC and identity backup/restore. Add proper
Back navigation and consistent compact/wide headers. Retain existing native
layer-worker TLS pairing as a distinct compute capability.

Use the pinned public key, challenge nonce and signed response metadata before
persisting compute trust. Keep sessions scoped, expiring and revocable. Test wrong
keys/IDs, expired proofs, replay, malformed/oversized QR, self-pairing, permission
loss/regrant, hosting bind failure, cancellation and endpoint edits. A discovered
address must never automatically gain agent/tool/cloud/recovery permissions.

Preserve first-use legal acceptance and all owner opt-ins. Authentication and
listener firewall checks must protect external mutation requests. Audit loopback
exemptions against their actual internal consumers; do not broaden them to LAN.

Acceptance: real local network enrollment and signed request/response tests; every
new setting persists and controls a real backend; both app flavors compile.

## Gate 2 — finish real desktop-to-phone and desktop host connections

Keep HTTPS/system certificate verification for remote hosts and literal-loopback
HTTP rules. Accept bare valid hosts by normalizing the correct `/v1` or `/api`
path. Keys remain session-only and bound to the chosen endpoint. Test has a shared
five-second headers/body deadline, cancellation and typed auth/TLS/network errors.
Saving/selecting an address does not authorize network requests.

Add a distinct signed Meshlit peer adapter. Share handshake DTOs and cryptography
with Android; parse public-key-pinned pairing links without copying Android UI
classes into desktop. Support the actual Android `/v1/identity`, `/v1/handshake`,
`/v1/model` and named `/v1/infer` SSE events. The buffered Android client gateway
and OpenAI-compatible APIs remain separate protocol choices.

Pair only after explicit human action and owner approval. Verify server identity,
nonce, signature, accepted scope and expiry before keeping an in-memory token.
Stop and close real calls on cancellation. Do not weaken TLS because the old QR
advertises HTTP; provide an actionable authenticated transport setup explanation.

Acceptance: real phone/desktop protocol compatibility, auth/revoke/expiry and
mid-stream disconnect tests; no credential forwarding on redirect or endpoint change.

## Gate 3 — finish the UI redesign without losing advanced features

Use a coherent Chat / Models / Devices / Monitor workspace on both platforms,
with secondary tools and preferences reachable through grouped navigation/search.
Desktop rail/header/composer source is started; Android pairing uses the retained
modern shell. Complete the shared design, error/status panels and reachable routes.

Preserve chat history, attachments, Markdown/code actions, loaded model state,
streaming Stop, native model options, online-provider selection, training/tools,
accessibility and licence gates. Never enable a pending backend merely to fill a UI.

Verify light/dark/system and saved appearance, 48dp touch targets, keyboard focus,
large text, narrow/wide windows, scrolling and Back. Translate new labels through
the existing language system. Use actual desktop renders and an installed Android
APK; compile alone does not prove that the UI fits or works.

Acceptance: real screenshots/render evidence and manual interaction results,
without silently deleting existing destinations or introducing stub controls.

## Gate 4 — relay, NFC and identity recovery on real devices

A designated node hosts an explicitly enabled relay with expiring six-digit codes,
public-key-pinned invites, bounded enrollment, replay/rate checks, heartbeat expiry
and revocation. Enrollment is distinct from compute approval. Preserve cancellation
and close rejected/unconsumed POST connections. Internet access needs a reachable
operator-owned authenticated HTTPS transport; enrollment does not solve NAT.

NFC read/share is explicit, bounded and lifecycle-managed; unsupported hardware
returns unavailable. BLE permission changes must produce typed badges. Recovery
requires an encrypted identity backup file AND its passphrase and must reproduce
both the node ID and signing key. Recovery does not grant the old device's OS or
agent permissions to a new installation.

Acceptance: the owner's two-device checklist, named devices/OS versions, malformed
QR test, NFC where supported, disconnect/revoke and uninstall/reinstall identity
restore. Keep unrun hardware checks explicitly pending.

## Gate 5 — local contribution ledger and wallet qualification

Keep credits local and nontransferable until settlement exists. Verify domain-
separated signatures, chain links, unique job IDs, bounds, atomic writes and same-
identity prefix-only imports. Wrong-owner/forked/altered backups must fail visibly.

Record only successful locally hosted work with actual backend token accounting.
Unknown counts, text chunks, estimates, failed/cancelled jobs and forwarded work
must not mint credits. Do not award uptime or relay rewards from a timer alone.
Desktop wallet currently handles identity/receipt source operations; a qualified
hosting producer is a separate gate. Personal chat is not a hosting reward.

Acceptance: durable identity/receipt recovery, corruption and concurrent-write
checks, production job-lifecycle tests and clear pending/finalized/local labeling.

## Gate 6 — connect to the original MCC design

Read the reference research worktree's R-225 contribution-coin design and
core-chain starter. Its placeholder/zero signatures and in-memory ledger are
not a settlement system. Use its concepts only after verifying current source.

1. Define a versioned compute completion receipt with requester job nonce,
   model and input/output commitments, measured accounting and both signatures.
2. Add independently verified witnesses and anti-replay/anti-collusion rules.
   A different IP subnet alone does not prove independence.
3. Bind peer identity and chain spending identity with signatures from both keys.
   A P-256 fingerprint is not automatically an Ed25519 MCC address or BIP39 seed.
4. Add a durable, chain-ID-pinned ledger, validator/membership rules, consensus,
   finality, deterministic integer issuance and reproducible recovery.
5. Add transfers only with account nonce, fee/balance validation and explicit
   user review. Keep spending keys away from relay/compute/OCR peers.

Acceptance: independently validated work, no duplicate settlement, partition/
validator-change tests, finalized-balance proofs and restoration of the same
wallet address. Do not display spendable MCC before these gates pass.

## Gate 7 — optional DeepSeek-OCR and measured data savings

Reference: https://github.com/deepseek-ai/DeepSeek-OCR

Plan an optional authenticated GPU-peer `ocr_document` tool. The reference uses
CUDA/PyTorch; do not claim the current Intel CPU desktop or text-only GGUF runtime
can consume its visual embeddings. Select/pin the model/runtime before downloading
weights. Bound page count, bytes, image dimensions, memory, output and time; expose
cancellation and typed unavailable-runtime errors. Send documents only to the
explicitly selected and authorized peer.

Cache extraction by document hash/model revision/settings. Preserve source/page
citations; deduplicate and retrieve relevant passages instead of always forwarding
all extracted Markdown. Measure the receiving model's actual tokenizer counts,
OCR accuracy (including Bangla/English and tables), latency, memory and wire bytes.
OCR alone does not guarantee fewer tokens for ordinary text-model input.

Negotiate lossless gzip for suitable JSON/text separately. OCR is not a generic
binary/tensor/network compressor. Any lossy context/activation scheme needs its
own accuracy baseline and explicit capability negotiation.

Acceptance: measured savings on named workloads, source retention/citations,
uncached/cache/cancellation/failure checks and no advertised fixed savings percentage.

## Build and delivery gates

Before APK builds, verify the pinned starter locally:

```sh
python3 scripts/prepare-bundled-model.py
./gradlew :core-trust:test :core-discovery:test :core-federation:test :desktopApp:test \
  --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :core-inference:testDebugUnitTest :core-mcp:testDebugUnitTest \
  :core-sandbox:testDebugUnitTest :core-net:testDebugUnitTest \
  :app:testMeshlitV1DebugUnitTest :app:testMeshlitV2DebugUnitTest \
  --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process
./gradlew :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
./gradlew :app:lintMeshlitV1Debug :app:lintMeshlitV2Debug
python3 -m unittest discover -s companions/crawler/tests -v
python3 scripts/check-hyperl-alpha6.py
python3 scripts/update-doc-tracking.py --write
python3 scripts/update-doc-tracking.py --check
python3 scripts/validate-feature-map.py
```

Use the repository's desktop stabilization build guide for actual JVM packaging,
render commands, verified engine/model inputs and recovered installer checks.
Keep strict lint errors blocking; do not set abortOnError=false to manufacture a pass.
Run only one Gradle validation at a time on this machine, and separate emulator
runs from heavy builds. Record exact logs, artifacts and named devices. Do not
infer complete project or physical-phone qualification from these scoped gates.

Keep a recoverable source snapshot and the last working release. No release/push
is part of this paused continuation. Owner review and any later publication request
remain separate from local implementation and validation.
