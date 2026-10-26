# Usage history and model budgets
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Implementation increment: 2026-10-26. This extends the Studio/mobile LTS plan with a shared, device-local accounting authority. It does not complete cross-device billing or all inference adapters.

## Coverage

- Android: `OnlineProviders.generate` records direct text chat, online model-route steps and delegated online text calls. Android local SDK, Colibri, media and calls outside this controller are **not covered**.
- Studio: interactive chat records local and remote generations. CLI, benchmarks, separately hosted requests and other processes do not share this budget. Local generation records zero API cost, without inventing token counts, power or hardware cost.
- Provider-reported input/output counts are retained when present. Unknown usage remains unknown. A successful HTTP/model response is not proof of zero cost or a paid entitlement.
- No prompt, reply, key, raw endpoint or provider error body is written to the ledger. Route identity is an endpoint/protocol/model hash; model IDs and request metadata remain sensitive and are encrypted.

History begins with requests made through this update. Earlier in-memory counters and provider account history are not imported or reconstructed.

## Everyday use

Open **Dashboards → Usage & costs**. Android opens its Keystore-backed history. In Studio, create/unlock a separate usage passphrase (12–256 characters) before chatting. Studio never saves this passphrase; losing it means the encrypted history cannot be unlocked. A second Studio process cannot open the same vault concurrently. Host keys remain session-only unless explicitly saved in the separate credential vault described below.

Choose a three-letter currency. For an unpriced Studio host or gateway, use **Use currency; prices unknown** to bind its billing currency without inventing token rates. Budgets are independent for each currency; there is no exchange-rate conversion. Enter a monthly allowance and a per-request allowance, then save. Blank monthly allowance explicitly disables that currency's local limit. Changing policy affects future admission, not requests already sent. Month boundaries use UTC, not device locale.

The per-request allowance is reserved in full before a paid request is sent. A conservative input-size estimate and requested output limit are used to reject requests whose estimated cost already exceeds that allowance. This is **an estimate admission guard, not a hard provider billing cap**: provider tokenization, hidden processing, tool charges, rate changes and account activity can exceed it. Set a billing cap at the provider for a billing guarantee. This feature does not manage ChatGPT/Codex account credits.

Review both input and output rates in Android's provider editor, or for the selected Studio remote model in Usage. Rates are currency units per million tokens, with up to nine decimal places. Reviewed prices expire for budget admission/free-only after 24 hours. Studio rates are session-only and bound to the exact selected endpoint/protocol/model; Android prices persist with the profile. Published/manual provenance is retained in every request's immutable price snapshot. Manual review is a user declaration, **not authenticated account verification**.

Free-only accepts local Studio inference or freshly reviewed direct models with both prices explicitly zero. Unknown, paid, stale and opaque gateway routes are blocked. It does not convert trial credit or missing prices into free access. There is no automatic fallback to paid models.

## History and uncertain outcomes

History is paged by currency and month and grouped by route/model. It shows request time, outcome, reported token counts, elapsed time, estimate or manually reconciled amount, and pricing provenance. Refresh reads the local journal only; it never polls a paid service.

Failed, stopped and interrupted calls can still incur charges. Reservations survive failure and restart. Missing usage is never refunded automatically. Unresolved previous charges block new budgeted calls in that currency, even across month boundaries. Requests admitted before a budget was enabled also require reconciliation if their costs are unknown.

Use **Reconcile with provider bill**, enter the charge for that individual attempt (zero only when confirmed), and record it. This appends a separate manual correction; it does not rewrite the original reported usage or pretend to fetch an invoice. A running request cannot be reconciled. A later confirmed correction can be entered as another event. Totals combine current estimates and manual reconciliations, with their provenance visible in the history.

## Optional gateways

Android's provider presets and Studio's remote-host controls offer **OmniRoute** and **FreeLLMAPI**. Supply your own deployment's base URL and scoped key, explicitly enable network access, discover/select a model, and send through the existing OpenAI-compatible API. Android requires trusted HTTPS; Studio permits HTTP only on literal loopback. Neither integration installs a server, imports upstream source, grants an account, or assumes free quota.

- [OmniRoute](https://github.com/cheaper-inference/OmniRoute) is an optional gateway. Its configured downstream routing and fallback belong to that gateway.
- [FreeLLMAPI](https://github.com/tashfeenahmed/freellmapi) is labelled experimental; its upstream describes personal experimentation. It is not an LTS production dependency.

Meshlit adds no gateway retries or fallback. Explicit gateway profiles retain any reported outer-request tokens but leave downstream cost unknown. Local free-only and currency-budget admission block opaque gateways. Deliberately use unrestricted mode only with a separately reviewed provider/gateway billing policy. Custom proxies must also be declared as gateways; a client cannot prove what an arbitrary server does behind its endpoint.

## Storage and remaining gates

`shared-workspace/UsageLedger.kt` uses synchronized admission and append-only attempt/completion/reconciliation events. Money is integer nanocurrency units with decimal parsing and rounded-up cost calculations. Write failure blocks admission. The complete journal is bounded to 12 MB and a finite event count, failing closed when full. Automatic deletion, archival/export, multi-device allocations, provider invoice ingestion, authenticated free-tier verification and gateway downstream accounting remain future work.

Android uses committed encrypted preference writes and existing backup exclusions. Studio uses PBKDF2/AES-GCM, a fresh nonce per atomic replacement, private file permissions where supported, and a process ownership lock. Damaged or wrong-passphrase storage is never silently reset. Neither platform synchronizes the ledger or sends telemetry.

Validation results for this increment are tracked in PROGRESS.md. Compile/unit checks do not establish live provider billing, gateway deployment or physical Android-device qualification.

## Persistence failure handling — 2026-10-26

Malformed journal values and costs inconsistent with saved prices/reported units are rejected. If storage reports failure, the ledger blocks further reads/admission until reopened; this covers an uncertain write that may already have reached disk. Studio can lock and unlock its usage vault; restart Android before reviewing/reconciling interrupted records. Do not clear history to recover budget allowance.

Android provider edits/removal now commit profile metadata and the endpoint-bound key together. A failed commit disables credential use in that provider instance until restart/review. Dispatch rechecks the current profile and local key after budget reservation. This check cannot retract a request already accepted by a remote provider, and does not claim global vault revocation across all adapters.


## Optional Studio credential vault

In the selected remote host settings, expand **Saved credentials (optional)**. Create or unlock a separate 12–256-character passphrase, enter the host token and choose **Save current token**. After restarting, unlock and choose **Use saved token**, then explicitly enable host access and connect. Changing the host/protocol clears the selected token; another host cannot use its saved lease. The passphrase is not retained on disk and has no recovery mechanism.

**Lock and clear token** stops the active client and removes session access. **Forget this host** also removes its encrypted saved entry; revoke the actual API token with its provider when needed. Security → Revoke session access locks the vault but keeps encrypted entries for a later explicit unlock. Already accepted remote work cannot be recalled.

The bounded `credentials.mcv` format uses PBKDF2-HMAC-SHA256 (210,000 iterations), a random salt, AES-256-GCM with authenticated version/salt header and a fresh nonce per write. Atomic replacement, forced writes, POSIX private permissions and exclusive ownership follow the existing usage-vault pattern. Failed/ambiguous persistence locks the instance. JVM strings cannot guarantee memory zeroization; this is encrypted-at-rest storage, not hardware-backed keychain storage. Windows ACL and installed-platform qualification remain open. No agent, CLI or automatic routing grants are added.

Validation: Final `:desktopApp:test` regression succeeded: 52 cases, 51 passed and one physical SSH fixture skipped, zero failures/errors. Existing native workspace/dashboard render checks passed; the new credential controls still need interactive installed-app qualification. Documentation tracking, feature-map validation and `git diff --check` passed. Android and release packaging were not rerun for this Studio-only increment. Credential tests exercise restart, wrong passwords, exact host/protocol binding, stale leases, deletion, concurrent ownership, failed writes and authenticated corruption. No live credentials or paid calls are used.
