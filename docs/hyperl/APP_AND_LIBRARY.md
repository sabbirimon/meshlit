# HyperL libraries in Meshlit
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

The production-hardening port adds a shared local workflow to both Android app
flavors. Open **Settings → HyperL libraries**. The separate standalone project
is [HyperL](https://github.com/sabbirimon/HyperL); its desktop installers are not
Meshlit APKs. This increment does not make either project production-qualified.

## Use the screen

1. Choose one of twelve recipes: add, multiply, ReLU, weighted/residual ReLU,
   affine, affine-ReLU, residual-affine-ReLU, dot, sum, positive sum, squared norm.
2. Inspect the generated program and example inputs. Weighted ReLU starts with
   x=`[-1,2,3]`, w=`[2,3,4]`; the actual CPU result is `[0,6,12]`.
3. Choose an array budget (1–1024 MiB, default 16), then Validate. The complete
   shape graph and estimated retained arrays must fit the budget and observed JVM
   headroom before CPU input copies are allocated. Admission is not an RSS reservation.
4. Run CPU. The output names CPU_REFERENCE and measures total controller time;
   at most 256 values are previewed. Stop cancels work. Invalid shapes, nonfinite
   values, ordered-sum overflow and exceeded budgets are errors, not successful output.
5. For elementwise programs, choose Metal or Vulkan source and Generate source.
   Copy output places the displayed source/result on the OS clipboard explicitly.
   Source generation does not compile or run a phone accelerator. Reductions are
   CPU-only on the current paths.

Each editor accepts at most 65,536 characters. The language permits 1–8 inputs,
1–64 instructions and 1–262,144 values per input, with at most 1,048,576 retained
f32 values across the graph. No broadcasting, matmul, model engine, autograd,
file access, shell, network or root capability exists in these programs.
`affine` means per-element scale and offset, not a dense neural-network layer.

## Controls and privacy

The human-only controller admits one active operation, rejects busy requests
without queueing and applies a ten-second cooperative deadline. Execution and
source generation use the existing **HYPERL** per-function switch and latched
global emergency stop. Revocation cancels registered work; the gate rechecks
before returning a result. Resumption remains a human action in Operations.
Validation is local analysis and does not execute a backend.

Inputs stay in the transient editor and owned execution buffers. They are not
uploaded, delegated to agents or saved automatically. Clipboard use is explicit
and exposes the copied content to the OS clipboard. No root, VM, account or
internet is required, so the workflow is suitable for a single nonroot phone.
The separate Security Lab still requires its own VM/sandbox authorization.

Adding the HYPERL policy enum preserves existing settings defaults. An older
Meshlit build that cannot decode a saved newer policy fails closed into emergency
stop through OperationsControl; inspect and restore permissions deliberately
after downgrade. This screen has no agent invocation endpoint.

## Developer integration and provenance

`core-gpu` contains the Apache-2.0 portable contracts, strict codec, admission,
CPU reference, twelve-recipe library and source emitters. The app controller
owns admission and stop-gate wiring; Compose is outside compute loops. Both
flavors use the same Settings destination and controller.

The six portable files are copied from the owner's standalone HyperL alpha.5
work with only the package declaration changed. `PORT_PROVENANCE.json` records
normalized SHA-256 values. Run `python3 scripts/check-hyperl-port.py`; supply
`--upstream /path/to/HyperL` to compare the actual standalone checkout too.
Any subsequent source change needs an explicit new provenance record and
conformance checks, rather than silently diverging copies. A published shared
SDK can replace this transitional source port later.

Real CPU expected-value tests run all twelve recipes in both repositories.
Controller tests cover actual outputs, generated-source boundaries, malformed
inputs, budgets, disabled-feature/global-stop rejection and human resumption.
Runtime coordination fixtures verify capacity/deadlines/cancellation; they do
not qualify hardware. Android APK builds and lint are separate evidence.

## Useful scenarios and remaining gates

Use the library for aligned feature weighting, activation masks, residual
preprocessing, a small weighted score, positive aggregation or squared energy.
Application-owned sensor/audio/image code must first provide finite f32 vectors;
the screen does not capture sensors or parse images. Keep existing GGUF model
inference in core-inference; these recipes do not replace llama.cpp.

The standalone [use-case guide](https://github.com/sabbirimon/HyperL/blob/67b30e09741f4bba1672a891644fc73abb1f9855/docs/USE_CASES.md)
describes CLI/Python/C workflows, NumPy/CPU Torch copies, explicit macOS Metal
selection and encrypted dataset streaming. Those desktop integrations are not
automatically ported into the Android app. Native mobile GPU/NPU backends,
model/tensor libraries, distributed HyperL jobs and automatic storage spill need
separate designs and hardware tests. The owner resumed single-phone GPU testing:
the standalone native Vulkan runner passes eleven CPU-verified kernel outputs and
two expected overflow rejections on Samsung Galaxy A20s SM-A207F / Adreno 506,
Android 11. [Build and hardware scope](https://github.com/sabbirimon/HyperL/blob/67b30e09741f4bba1672a891644fc73abb1f9855/docs/ANDROID_VULKAN.md).
This ADB-shell experiment does not run through this screen or prove an app-UID
JNI adapter, model inference or mobile production readiness. Two-phone cluster
acceptance remains paused. Signing, lifecycle/thermal tests and security review
remain production gates. The six-file source port is unchanged.

## Android alpha.6 integration

The shared workbench now uses the separately licensed `core-hyperl` module for
Kotlin/native C99 execution, precise sums and encrypted dataset operations. The
old Apache `core-gpu` port is preserved. Read the [alpha.6 integration guide](ANDROID_ALPHA6.md) for licence opt-in, Android/API limits, safety boundaries and validation.
