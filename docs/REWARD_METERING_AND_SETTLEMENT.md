# Reward metering, fraud controls and monetary settlement plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Status: design, 2026-10-23. No Meshlit credit is currently transferable, redeemable, purchasable or a monetary claim. The current `ContributionReceipt` is a self-signed, local record of a completed hosted job's reported output tokens. Its signature protects that device's history from accidental modification; it does not prove work to an independent buyer.

The Android wallet now has an explicit local receipt opt-in and a separate Contribution measurements view. It shows durable receipt totals and current-process inference counters with their different scopes. It does not upload proofs, expose a monetary balance, or claim that unknown energy, bandwidth and requester confirmation are measured. Current reward participation therefore remains local-only.

## One meter per service

| Service | Primary billable unit | Evidence required | Never infer from |
| --- | --- | --- | --- |
| Hosted text inference | Authoritative native generated tokens, plus separately quoted input/prefill tokens if the backend exposes them | Runtime request ID, tokenizer/model revision, completion state, requester acceptance, native usage | Text length, stream chunks, event count, estimated provider tokens |
| Layer worker | Accepted model-layer work per request, measured worker active time and bytes; first ship as a quoted share of the completed request | Coordinator and worker request IDs, model hash, layer placement, native completion, timeout/loss record | Nominal RAM, device count, connection duration |
| Custom compute/HyperL | Validated program ID/hash and completed CPU execution milliseconds, input/output sizes | Program and input commitments, bounded runtime result, requester acceptance | Claimed FLOPS or device model name |
| Storage rental | Verified stored bytes × elapsed time, with independent retrievability challenges | Content hash, lease, quota, challenge responses, deletion/expiry proof | Allocated but unused disk or a self-reported directory size |
| Relay/bandwidth | Authenticated bytes actually forwarded, with ingress/egress counters and job identity | Signed endpoint counters reconciled against relay logs and limits | Interface-wide network totals or uptime |
| Availability | Explicit reserved capacity windows with sampled reachability and successful challenge response | Reservation, independent probe, maintenance/outage record | A timer running on the rewarded device |

Record energy only when a calibrated meter or OS sensor supplies a value with provenance. Mark estimates separately. Risk score, thermal status and available memory determine admission or price quoting; none is a reward unit. Failed, cancelled, timed-out, forwarded-only and unknown-usage jobs settle at zero usage unless a separately agreed, independently verifiable partial-service rule exists.

## Contract and evidence

### Proof sharing and connection privacy

The future monetary program must require a separate, revocable enrollment consent before any proof leaves the device. Enrollment identifies the verifier and the exact redacted fields: job/nonce hashes, model or service revision, reported units, start/end times, result commitment, signer identities and signatures. Raw prompts, outputs, phone logs, contacts, coordinates, credentials and payment instruments are excluded. A participant may export the bounded proof journal for an appeal, inspect what was sent, pause future submissions and exit the program; already settled records follow a published retention and dispute policy. A local receipt switch alone must never turn on network sharing.

Approved same-owner local peers may exchange richer **operational** capacity and diagnostic summaries over an authenticated channel, subject to owner scopes. Internet and cross-owner peers receive only the minimum signed job proof needed for settlement, with stricter identity, replay, rate and transport checks. Neither local trust nor reward enrollment grants device control or raw log access. The verifier, not the rewarded device, must authenticate and reconcile the evidence. Self-reported IP geolocation cannot prove a worker's physical origin, independence or delivered work.

1. The buyer selects a service, model/program, device or auto placement policy, region/privacy constraints, maximum budget and a signed quote with unit rates, currency, expiry and cancellation/refund terms. A device cannot change a quote after acceptance.
2. A unique buyer nonce and globally unique job ID bind the job to the selected worker, model/program hash, input commitment, meter version and agreed limits. Do not place raw prompts, files, credentials or outputs in the public ledger.
3. The coordinator records the accepted start and final state. Worker and buyer sign a bounded completion receipt with measured units, timing, error/loss state and result commitment. Independent validators re-execute sampled small jobs or verify deterministic challenges when practical; probabilistic checks are stated as such, not called proof of arbitrary LLM quality.
4. The settlement service deduplicates job IDs and buyer nonces, verifies both signatures, matches the quote and usage, checks monotonic counters and budgets, and writes an append-only, transactionally durable journal. A double-entry balance records buyer debit, seller credit, platform fee and refund as separate balanced entries. A receipt is pending until reconciliation and payment finality.
5. Air-gapped peers may collect signed pending receipts offline. Synchronization orders and deduplicates them later; conflicting histories, expired quotes and overspend remain disputed. Offline devices cannot promise globally final or spendable rewards before reconciliation.

The first release should use a conventional durable settlement service and a licensed payment provider, with a clear point of finality, reversal and dispute rules. A blockchain or transferable coin is optional and would require separate consensus, custody/key recovery, legal and security qualification. Payment-system design treats processing, clearing and settlement as different steps; settlement finality must be explicit ([BIS payment-system overview](https://www.bis.org/publications/aer-2020/central-banks-payments-digital-era), [CPMI-IOSCO principles](https://www.bis.org/committees/cpmi/pfmi/overview)).

## Fraud and security gates

- Do not award a self-submitted receipt. Require a preauthorized buyer job and independently matched worker/buyer records. Keep private signing keys hardware-backed where available, rotate/revoke identities, and bind signed keys to the enrolled node. Device attestation is one signal, not proof of work.
- Reject replay, duplicate job IDs, cloned/restored wallets, forged counters, clock manipulation, backdated quotes, model substitution, result substitution and partial completion promoted to success. Use nonces, sequence numbers, hash-linked append-only events, certificate pinning and server-side monotonic records. Time is evidence only when anchored by a trusted party.
- Apply per-device, per-account and per-payment-instrument limits; detect self-dealing and collusive rings, repeated trivial jobs, correlated identities, sudden price/usage outliers, impossible throughput, shared network/payment metadata and challenge failures. Shared IP alone neither proves nor disproves independence.
- Quarantine suspicious rewards before payout. Preserve bounded signed evidence, appeal/dispute records, reversals and an audit trail with access controls and retention policy. Do not publish raw user content as fraud evidence.
- Stress partitions, delayed offline sync, worker loss, inconsistent usage, stolen keys, issuer compromise, double spend, chargebacks, refund races and account restoration before enabling monetary value. Red-team the meter with synthetic test jobs; production rewards require real jobs.

## Monetary boundary

Local same-owner credits can remain a nontransferable activity record. A marketplace with purchases, rentals, payouts, exchange or transferable tokens changes the product and may trigger payment, money-transfer, virtual-asset, consumer, tax and privacy obligations depending on jurisdiction. Use legal review and a licensed payments partner before accepting or moving value. FATF guidance treats business exchange and transfer of virtual assets as potentially regulated VASP activity; EU MiCA distinguishes certain issuer-only loyalty benefits from crypto-assets, which is a jurisdiction-specific distinction rather than a blanket exemption ([FATF VA/VASP guidance](https://www.fatf-gafi.org/en/publications/Fatfrecommendations/Guidance-rba-virtual-assets.html), [EU MiCA text](https://eur-lex.europa.eu/legal-content/en/TXT/?uri=CELEX%3A32023R1114)). Outsourcing card handling still leaves merchant responsibilities under PCI DSS ([PCI SSC guidance](https://www.pcisecuritystandards.org/faqs/does-pci-dss-apply-to-merchants-who-outsource-all-payment-processing-operations-and-never-store-process-or-transmit-cardholder-data/)).

## Delivery sequence and acceptance

1. **Meter only:** versioned usage schema for native tokens, layer work, CPU time, bytes and errors; no price or new credits. Compare counters against real Android and Studio jobs, including cancellation and worker loss.
2. **Reciprocal pending receipts:** buyer and worker signatures, nonce/replay rules, offline queue and reconciliation; adversarial tests for forgery and duplicated jobs.
3. **Fraud review:** independent challenge sampling, anomaly limits, disputes, key recovery/revocation and auditable correction entries. Demonstrate no award from self-generated or forwarded-only work.
4. **Marketplace simulation:** quotes, budget reservations, refunds and balanced ledger using test money only; published terms, privacy rules and transparent unit prices.
5. **Real payment pilot:** jurisdiction-specific legal review, approved processor, tax/invoice and payout flow, chargeback handling, customer support and finality tests. Keep self-hosted AI content out of payment records.
6. **Exchange or transferable asset:** separate product decision after the above gates. Do not convert historical local credits into money automatically.

The economic formula is `charge = sum(verified units × accepted unit prices) + disclosed fees − refunds`; use fixed-point integer minor currency units and an immutable quote version. Capacity and performance affect whether a seller can accept a job and the price they offer; they are not themselves proof that service was delivered.
