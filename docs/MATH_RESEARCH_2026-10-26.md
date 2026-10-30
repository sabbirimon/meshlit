# OpenAI mathematics: applications to Meshlit and HyperL
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-10-26. Source snapshot: `openai/math` commit
`1581d4cbdd69a0faf34b2089614fe2a814316a6a`. This is a targeted engineering assessment,
not a proof audit of every manuscript and not a new mathematical discovery.

## What is actually published

The [repository README](https://github.com/openai/math/blob/1581d4cbdd69a0faf34b2089614fe2a814316a6a/README.md)
reports 719 manuscripts in 372 families, with about 42% of top-line results
formalized. Related papers are not separate solved problems. The
[history](https://github.com/openai/math/blob/1581d4cbdd69a0faf34b2089614fe2a814316a6a/history.md)
records three withdrawals and other repairs on October 7. A catalog entry is
therefore evidence of a published claim, not our independent verification.
The [October announcement](https://openai.com/index/sharing-ai-progress-in-mathematics/)
explains the release and its computer-checkable proof artifacts. No Lean compiler
or proof corpus was downloaded or run in this increment.

All 372 family headings were scanned locally for engineering topics. Selected
catalog abstracts and the matrix/scheduling formalization scope notes were read;
719 individual proofs were not reviewed.

## Ranked candidates and adoption decisions

| Candidate | Published scope | Engineering assessment and next gate |
| --- | --- | --- |
| Matrix multiplication, family 107 | Arithmetic exponent at most 9/4 over complex numbers. The [formalization scope](https://github.com/openai/math/blob/1581d4cbdd69a0faf34b2089614fe2a814316a6a/lean/docs/107.md) explicitly excludes practical crossover sizes and bit complexity. | Highest compute-research relevance to HyperL. First obtain a concrete executable construction, bound temporary memory, compare against a qualified BLAS kernel, and verify rounding/error on realistic shapes. No phone/GPU speedup follows from the exponent alone. HyperL currently needs tensor/matmul foundations before this can be integrated. |
| Three-machine scheduling, family 124 | Optimal unit-duration precedence scheduling on exactly three identical machines; [scope](https://github.com/openai/math/blob/1581d4cbdd69a0faf34b2089614fe2a814316a6a/lean/docs/124.md). | Useful candidate for a restricted planner test oracle. Meshlit's phones and HyperL workers have unequal speeds, transfer costs, memory and thermal limits; the theorem does not cover that fleet. Verify schedules independently against dependency/capacity constraints before any planner adoption. |
| Matching, family 120; metric k-median, family 125 | The [catalog](https://github.com/openai/math/blob/1581d4cbdd69a0faf34b2089614fe2a814316a6a/CONTENTS.md) describes matching and metric facility-placement algorithms. | Potential task assignment and cache placement research. Establish a faithful graph/metric model first; RTT observations need not satisfy a stable metric. Compare total transfer plus execution time, not only solver time. |
| Lattice/CVP hardness | The [August results](https://openai.com/index/ten-advances-in-mathematics/) include a closest-vector approximation hardness result. | Hardness is not an encryption implementation or proof of this application's protocol. Preserve existing encryption. A separate versioned post-quantum negotiation design would need a reviewed provider, test vectors, authenticated downgrade protection and interoperability evidence. |
| Fluid singularities | OpenAI's [September announcement](https://openai.com/index/navier-stokes-solution/) presents a forced Navier–Stokes result and Lean artifact. | No demonstrated direct benefit to inference routing, ledger accounting or encryption. Keep out of the runtime. |

These adoption assessments are engineering inferences, not promises made by the
papers. NIST's [FIPS 203](https://csrc.nist.gov/pubs/fips/203/final) standardizes
ML-KEM based on Module-LWE and lists a pending errata note. Generic lattice
hardness, RSA factoring, symmetric encryption and signature security are distinct
questions. No home-grown cipher or crypto-suite change is justified here.

## Applied now

Meshlit G1 now checks replayed event domains, duplicate currency policies, legal
terminal states and costs recomputed from immutable prices and reported units.
An uncertain storage commit poisons the current ledger authority until reopen,
so stale cached allowances cannot admit another request. New tests use an
independent integer cost oracle, corrupted-event fixtures and a write-then-fail
storage fixture. This borrows the verification discipline, not a theorem or
algorithm from the repository.

Android profile and endpoint-bound key changes now share one checked encrypted
transaction. Storage failure blocks further credential use in that instance;
profiles and local keys are rechecked immediately before dispatch. This does not
claim cancellation of an already-dispatched provider request or global vault
revocation across every adapter.

Standalone HyperL gained exact-decimal oracle tests for its existing compensated
`precise-sum/1` primitive: 200 seeded cancellation fixtures plus subnormal and
extreme-magnitude cases. They improve regression coverage; they do not prove all
inputs, change `hyperl/1` ordered-f32 semantics, or establish faster execution.

## Remaining gates

G1 is still partial: Studio credentials remain session-only; cross-device budget
authority, all-adapter admission, schema evolution beyond current v1, live account
reconciliation and physical Android persistence/revocation remain open. Budgets
are local estimates, not provider billing caps. Automatic routing remains deferred.
Future algorithm work must carry a pinned source, exact assumptions, independent
correctness oracle, target benchmarks and a versioned opt-in before adoption.
