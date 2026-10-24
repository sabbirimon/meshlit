# HyperL experimental foundation
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](PLAN.md) · [Progress](PROGRESS.md) · [Document status](docs/DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

HyperL began in source commit `cdb06b6`, and the owner merged it into
`main` through [PR #1](https://github.com/sabbirimon/meshlit-v2/pull/1), merge
`851576bdd0c9daaea71f32f5daac8720671c6453`, on 2026-10-08. It remains an
experimental Apache-2.0 CPU library/source emitter within `core-gpu`, with a bounded app recipe workflow added on the production-library review branch.
Native mobile accelerator runtime integration remains unavailable. Start with [owner requests and build plan](docs/hyperl/REQUESTS_AND_PLAN.md),
[language v1](docs/hyperl/LANGUAGE_V1.md) and [research](docs/hyperl/RESEARCH_2026_10_08.md).

Implementation: `core-gpu` HyperL files and tests; `examples/hyperl`.
Only CPU reference execution and elementwise source generation exist. Real
accelerator compilation/runtime execution, crypto providers, tensors/vision,
RISC-V deployment and LTS release maintenance remain future milestones.
Use open-source/free research dependencies first. No vendor source is copied.

The owner subsequently requested standalone CLI/GUI distribution at
[sabbirimon/HyperL](https://github.com/sabbirimon/HyperL), with a full developer SDK
as a later milestone. The [standalone 0.1.0-alpha.1 CLI/GUI release](https://github.com/sabbirimon/HyperL/releases/tag/v0.1.0-alpha.1)
has memory-aware CPU admission and encrypted streaming dataset tools. Local
standalone validation passes 24 JVM, one C CTest and two installer checks, with
one actual GPU check skipped because this host has no available OpenCL GPU.
Standalone CI [37697211217](https://github.com/sabbirimon/HyperL/actions/runs/37697211217)
passes Ubuntu, Windows and macOS JVM/C/installer jobs. Those runners do not
qualify physical phones, accelerator kernels or native GUI sessions.
New standalone tooling/backends evolve there; the foundation
retained here is not automatically replaced or remerged. Its installation and
programming guide is maintained in that repository's `docs/USER_GUIDE.md`.
The owner also requested memory-aware execution: standalone CPU heap/environment
observations, full-DAG byte estimates and preflight admission evolve there, with
HBM/unified GPU/NUMA/CXL/SSD/NVMe adapters and the full SDK kept as later milestones.
The alpha.5 source port adds these CPU controls, the common JSON codec and twelve
recipes to this Android foundation. See [app workflow and provenance](docs/hyperl/APP_AND_LIBRARY.md).

Meshlit targets dynamic AI deployments across hosts and clusters; phones are one
client. Direct hardware/driver/kernel and low-latency native networking are future
qualified backends. See the research and owner plan for evidence and boundaries.

## Run the foundation checks

Use Java 21, the repository's Android SDK and pinned Gradle dependencies:

```sh
./gradlew :core-gpu:testDebugUnitTest --no-daemon --max-workers=1
```

`HyperLCpuBackend` is a Kotlin library API. The separate HyperL repository supplies
desktop installers; this Android app uses Settings → HyperL libraries.
Deserialize `examples/hyperl/elementwise.json` as `HyperLProgram`, pass finite f32
`x`/`w` arrays to `HyperLRuntime.execute(CPU_REFERENCE, program, inputs)`, or call
`HyperLSourceEmitter.emit(program, LLVM_CPU)` to obtain C99 text. Input bindings
in generated code sort names (`w`, then `x` in this example). Validate shapes and
aliasing before any native execution. The source emitter does not compile or load
untrusted code. The unit host check uses installed Clang; missing Clang is an
explicit skipped host check, not proof of another backend.

## Android alpha.6 integration

The shared workbench now uses the separately licensed `core-hyperl` module for
Kotlin/native C99 execution, precise sums and encrypted dataset operations. The
old Apache `core-gpu` port is preserved. Read the [alpha.6 integration guide](docs/hyperl/ANDROID_ALPHA6.md) for licence opt-in, Android/API limits, safety boundaries and validation.
