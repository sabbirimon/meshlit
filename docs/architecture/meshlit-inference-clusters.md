# Dedicated inference runtime and HyperL cluster plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

2026-10-29. The owner requested a Meshlit-owned runtime inspired by DeepInfer,
device-to-device and cluster support, and improvements to HyperL. The dedicated
private repository is [meshlit-inference](https://github.com/sabbirimon/meshlit-inference).
Its initial commit `f5356d3` contains plans and review provenance, not an engine.

Canonical planning documents:

- [Milestones and acceptance](https://github.com/sabbirimon/meshlit-inference/blob/main/PLAN.md)
- [Architecture](https://github.com/sabbirimon/meshlit-inference/blob/main/docs/ARCHITECTURE.md)
- [HyperL workstream](https://github.com/sabbirimon/meshlit-inference/blob/main/docs/HYPERL.md)
- [Pinned source review](https://github.com/sabbirimon/meshlit-inference/blob/main/docs/RESEARCH.md)

Two distinct paths are planned: independent jobs across enrolled devices and real
layer execution of one compatible model. Reuse the existing Meshlit native RPC,
pairing, capability, cancellation and usage contracts; do not replace working
components with new placeholders. Extract shared modules only after baseline tests
and source/license review. Physical multi-phone and oversized-model evidence remains
open as described in [the existing pipeline plan](../layer-pipeline-and-recovery.md).

HyperL remains an optional separately licensed backend. Proposed improvements are
reference/native benchmark coverage, memory/copy reduction, bounded execution and
distribution of independent jobs, followed by qualified elementwise partitioning.
Ordered f32 and precise reductions must retain their numerical contracts. No new
HyperL optimizations, GPU execution or cluster adapter are implemented by this plan.

DeepInfer was reviewed at `553741557a57df44a46fb3ab72b6aafeaf945337`. Its current
supported hardware and incomplete serving layer make it a future accelerator-host
adapter, not a Mac/Android dependency or ready OpenAI-compatible endpoint. No source
or weights were installed and no inference benchmark or paid call was performed.

Next: reproduce existing local/worker evidence, inventory exact capabilities,
define versioned admission/reservation contracts and qualify one paired worker.
Studio/Android dashboards and optional provider gateways remain in this app repo.
The new repository does not resolve existing gateway, phone or packaging gates.
