# Studio first-run agreements — deferred
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Status: planned, not implemented. On 2026-11-06 the owner explicitly prioritized
building the standalone HyperL engine first and agreements later. This plan adds
no acceptance gate, consent receipt, runtime change or packaging change.

## Existing behavior and scope

Android already has `LegalAgreementGate` and `LegalAgreementStore`, with explicit
Terms and Privacy checkboxes, persisted version `2026-10-09.2`, and bootstrap
waiting for acceptance. Preserve Android's version and existing acceptance data.
Desktop Studio currently activates its extension features without that gate.
Android documents cannot simply be relabelled as desktop terms.

## Proposed implementation after engine work

1. Review desktop-specific `STUDIO_TERMS_OF_USE.md` and
   `STUDIO_PRIVACY_POLICY.md` against actual local/remote inference, credentials,
   project tools, storage, optional telemetry and third-party behavior. Proposed
   initial desktop version: `2026-11-06.1`; finalize when documents are approved.
2. Bundle the complete offline documents during workbench preparation. Validate
   their size, version and SHA-256; missing or invalid documents prevent acceptance.
3. Add a first-run agreement page before Studio registers or starts model, agent,
   network, voice or background operations. Provide readable full documents and
   links. Keep Terms and Privacy choices unchecked until explicitly selected.
4. Persist an append-only version/checksum/timestamp receipt in the editor's
   user-data/profile storage only after explicit acceptance. Preserve old receipts;
   reject corrupt storage or failed writes without starting tasks. No synthetic
   acceptance for native QA or existing users.
5. Declining or closing the first-run page leaves Studio tasks unavailable. Provide
   a command to reopen it. After acceptance, Settings can reopen the documents and
   display the accepted version/date without requiring repeated acceptance.
6. Keep API keys, Usage unlock, project trust, microphone/camera permissions,
   autonomy grants, online routing and optional telemetry separate. Agreement
   acceptance grants none of those permissions.

## Acceptance gates

- First launch does not spawn a model host or register executable task paths before
  acceptance; command-palette entry points cannot bypass the gate.
- Missing/changed documents, stale versions, malformed receipts, failed persistence,
  double clicks and closed-page callbacks fail safely.
- Reload with a valid matching receipt starts normally without another prompt;
  older receipts remain intact when a genuinely changed version is accepted.
- Tests cover exact document binding, unchecked defaults, safe decline, receipt
  persistence/reload, and preserved independent permission controls.
- Native QA uses explicit operator acceptance, then checks startup, Settings reopen
  and separate workspace windows. No implementation or qualification is claimed yet.

Likely source locations: `companions/studio-runtime/extension/studio-agreement.cjs`
(new), extension activation entry point, Settings route, command manifest, and
`prepare_workbench.py` asset packaging. Do not implement until agreement work resumes.
