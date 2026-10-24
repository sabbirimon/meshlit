# HyperL local validation — 2026-10-08

Isolated source commit `cdb06b6`, based on Meshlit `77ac214`. This is
original source research, not an application integration or universal runtime release.

Java 21, existing pinned offline Gradle dependencies, Android SDK 37, one Gradle
worker, 1 GiB Gradle heap, Kotlin compiler in-process. Command:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx1024m \
  -Pkotlin.compiler.execution.strategy=in-process :core-gpu:testDebugUnitTest
```

**BUILD SUCCESSFUL in 1m38s. 27 tests pass, zero failures/errors/skips.** Eight new
HyperL checks cover CPU vector arithmetic and sum, input immutability, unknown
version/backend rejection, invalid dependencies, shape/nonfinite/aggregate-memory
failure, source binding/unsupported reductions, real generated C99 execution,
license/ABI/owner/signature qualification predicates and stable/turbo planning.
Nineteen existing core-gpu checks also pass; these are not new HyperL hardware proof.

Installed `/usr/bin/clang` compiled generated C99 with `-std=c99 -O2 -ffp-contract=off`.
The actual host executable verified pointwise output `[0, 6, 12]` and exposed an
intermediate negative overflow instead of accepting a later ReLU's zero. Native
callers must reject an entire result containing any nonfinite marker; no native
runtime loader is implemented. The Kotlin CPU sum example returns `[18]`.

This host is macOS/x86-64. No NVIDIA, AMD, Qualcomm, Ascend, Kirin, MediaTek, XRING,
RISC-V, FPGA, RDMA/AF_XDP/DPDK, driver/kernel/bare-metal or Android-device execution
is qualified. Source generation does not compile or launch vendor kernels. There is
no measured speedup, tiny-RSS guarantee, automatic turbo, tensor LLM engine,
cryptographic provider or funded LTS support duration. Standalone packaging,
compiler/runtime backends, source provenance, conformance and actual platform
benchmarks are next milestones. This validation preceded the owner's merge of PR #1 into `main` at `851576b`.
Merged-source validation is recorded separately in `../CONTINUATION_2026_10_08.md`;
merging source does not qualify native accelerators or add an app workflow.
