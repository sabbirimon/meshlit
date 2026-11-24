# AI-assisted repair and evolution design

Requested by IMON. **Design only; no general self-repair or self-modifying app
is implemented.** Existing retry/resume/checkpoint features have narrower scope.
Read the update roadmap in PLAN.md and the current evidence in PROGRESS.md.

## Runtime repair loop

1. Detect a real fault from typed runtime/device health states and bounded audit
   metadata. Distinguish model corruption, missing artifact, memory pressure,
   unavailable backend, thermal throttling, permission denial and transport loss.
2. Construct a redacted diagnostic snapshot with version, hardware capabilities,
   numeric resource observations, closed error codes and actual failed operations.
   Exclude conversations, credentials, private paths, raw commands and user files.
3. Choose an already-available local model, a user-approved online provider, or
   deterministic diagnostic rules if inference is unavailable. A broken primary
   model cannot be assumed to diagnose itself; keep a lightweight offline rule path.
4. Research only approved documentation/release sources when web use is enabled.
   Retain URL, fetch time, content hash and relevance. Retrieved text is untrusted
   evidence; it cannot grant permissions or become executable instructions. Do not
   bypass login, robots restrictions or access controls to obtain a repair.
5. Produce a typed repair proposal with evidence, prerequisites, affected resources,
   expected outcome, risk, rollback plan and bounded verification. Invalid model
   JSON or unsupported actions must return unavailable, never a fabricated repair.
6. Execute only allowlisted reversible actions inside saved human/agent repair
   policy: resume a known transfer, revalidate an artifact, restart an opted-in
   adapter, or reload the same compatible model while idle. Use deadlines, retry
   budgets, cooldowns and battery/thermal/network constraints. Provide Stop.
7. Compare actual post-action health to the recorded baseline. Store an encrypted
   bounded outcome/history and rollback where supported. Repeated failures enter
   quarantine and request review rather than consuming resources in a crash loop.

Human and agent configuration must be independent. Repair must also satisfy the
existing operation scope, enrolled-device approval and OS grant. A repair toggle
must never grant root, install updates, open a firewall port, spend cloud money,
rotate credentials, erase user data or replay irreversible tools automatically.

## Source evolution and deployment

AI may propose a source patch or dependency update in an isolated workspace on an
owner-approved build host. Include a reproducible failure, license review, tests,
Android lint/build, artifact provenance and the exact source diff. Evaluate the
patch before merging. Use signed versioned artifacts and Android installation
approval, or the supported Play update flow for the production channel. Preserve
upload/signing keys outside the agent workspace and retain the last known-good
version/data migration plan. The running APK must not rewrite its own executable
or install code from a web page as a repair shortcut.

## Evidence gates

- Fault injection and actual restoration for corrupt/missing models, memory pressure,
  interrupted downloads, revoked permissions and transient offline peers.
- Negative tests for secret leakage, prompt injection, denied repair scopes,
  unsafe reset/deletion, retry exhaustion and cancellation.
- Native checkpoint compatibility and fenced replicated recovery before claiming
  cluster self-healing; physical-phone evidence for phone-cluster execution.
- Local AI failure fallback, verified source attribution, independent review,
  successful signed upgrade/rollback and Play-channel behavior on real devices.

The monitoring dashboard should show healthy/degraded/repairing/quarantined states,
proposals, actual actions and verified outcomes with source provenance. Status
indicators alone do not prove that an AI repaired or evolved the system.
