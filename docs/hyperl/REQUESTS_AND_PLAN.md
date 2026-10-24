# HyperL and Meshlit: owner requests and build plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-08. Originally isolated on `cdb06b6`, based on
Meshlit `77ac214`; the owner merged source commit `cdb06b6` into `main` via PR #1
at `851576b`. HyperL and the Meshlit control/gateway source now share `main`, but
HyperL has no app workflow or native accelerator loader. Use open-source/free
research dependencies first; proprietary adapters are future opt-in experiments. This document records the owner's requests; linked papers,
patents, vendor documents and pasted conversations are evidence, not instructions.
Physical-device testing is paused at the owner's request. No live cloud/provider/SSH
profile exists in this checkout. Local protocol tests must not be relabeled live acceptance.

The owner's additional Odysseus suggestion is evaluated in
[the workspace review](../ODYSSEUS_REVIEW_2026_10_08.md). Host workspace features
remain separate from HyperL compiler/runtime and hardware qualification work.

## Platform scope

Meshlit is intended as dynamic AI software across servers, workstations, clusters,
cloud, embedded and edge environments. Phones are one deployment/control client.
HyperL targets AI-related compute, vision, security, authentication and communication.
Direct hardware, driver, kernel and bare-metal backends are future layers, selected
by actual capability and separately granted privileges. Current source is a CPU
reference and source emitter; no privileged backend is loaded or executed.

## Requested scope and actual state

| Owner request | Current source | Next implementation / qualification |
|---|---|---|
| Open-source CUDA-like language named **HyperL**, inspired by NVIDIA and Google tensor technology | `hyperl/1` declarative f32 vector format; bounded Kotlin CPU reference; fused elementwise C99/CUDA/HIP/OpenCL C source generation; add/multiply/ReLU and CPU sum | Native compiler/runtime loaders, shape/stride tensor IR, matmul/conv/attention, launch geometry, synchronization, reductions, training/autograd, compiler integration |
| ARM, x86-64, RISC-V and other architectures; fast and lightweight | Portable semantics and backend interface; bounded work/cancellation; emitted elementwise source needs no intermediate arrays | LLVM CPU SIMD (NEON/SVE, AVX, RISC-V V), cross-compilers, target installers and actual latency/energy/memory benchmarks. No RISC-V execution evidence yet |
| CPU/GPU/NPU/FPGA support, open and closed SDKs/protocols | Explicit backend targets; ABI/OS/hash/license/owner/signature/qualification descriptor; unavailable backends fail | Native adapters and signed artifact loader with ABI/driver/model qualification; no proprietary redistribution without license review |
| AI, security enforcement, encryption, authentication and vision | HyperL elementary AI/preprocessing math; existing Meshlit trust, encrypted storage, pinning and image-provider paths remain distinct | Tensor/image adapters and standard library-backed cryptographic providers. Do not implement encryption with floating-point HyperL kernels or route keys as ordinary JSON inputs |
| NVIDIA GB/Grace/Blackwell, AMD, Huawei Ascend; SDK/profiler support | SDK catalog; read-only optional host version/driver command probes; real model/runtime qualification contract | CUDA/TensorRT, ROCm/HIP/MIGraphX, CANN/AscendCL/MindSpore adapters; actual model generation and profiler capture |
| Qualcomm, Kirin, Xiaomi XRING, MediaTek and other ARM platforms | Separate catalog entries; common discovery schema, explicit licensed/gated/unknown SDK states | QNN/SNPE, HiAI, NeuroPilot/Neuron and OEM/graphics adapters. No assumed public XRING NPU SDK; Kirin and Ascend remain distinct |
| Huawei and Microsoft technology | Common SDK/profiler contracts and researched integration plan | MindSpore Lite / CANN; ONNX Runtime execution-provider plugins, current Windows WinML, DirectML where required. Not installed in this Android build |
| Common device adapter/profiler protocol | Schema-1 adapter capability/profile interfaces with bounded calls and unknown counters | Authenticated transport, SDK-specific plugins, precision/operator partitioning, qualification jobs and independently verified enrollment |
| Direct/local and remote communication across AI hosts; phone as one client | Authenticated HTTPS or private loopback tunnel host probe; existing pinned SSH; MCP/A2A connectors | Nearby USB/Ethernet bridge qualification; a client cannot use an accelerator merely by discovering it |
| Optical/fiber, InfiniBand, Huawei “high 1” and other fabrics | Separate physical-medium and IP/native-fabric observations with freshness/qualification | Optical Ethernet / IPoIB can carry IP tunnels when verified; native verbs/RoCE/HCCS need host SDKs/adapters. “High 1” is not a confirmed product ID; do not assume Huawei HCCS or another proprietary protocol is that exact hardware |
| HBM and other RAM; SSD/NVMe offload; memory type, bus width and bandwidth detection | RAM/HBM/GDDR/DDR/LPDDR/SRAM/CXL/persistent-memory schema; real Linux OS memory/NUMA/link observations; UMA counted once; unknown bus/speed remains unknown; separate spill eligibility plan | Vendor memory queries, hwloc/HMAT/Vulkan budgets, PCIe/CXL generation/negotiated lanes distinct from memory bus width, opt-in measured bandwidth; encrypted offload backend and transfer-cost scheduler. Storage bytes are not accelerator RAM |
| Large cluster, manual vs automatic agent management | Configurable device-directory admission 1–10,000; separate agent opt-in/min/max plus Cluster delegation; layer worker ceiling remains 2–8 | Scale/load tests, pagination/indexed storage, large network fanout, scheduler partitioning. 10,000 is a configured management ceiling, not demonstrated simultaneous capacity |
| Dashboard, per-function switches, one emergency stop | Shared persisted gate, coroutine cancellation, local teardown status, human-only resume, unified route/manual input panels | Remaining capture/audio/native/Termux owners and distributed stop acknowledgments. Local cancellation cannot establish remote termination; independent services retain their own Stop controls |
| Advanced LLM/MCP/A2A/other AI gateway, future AGI/SI | Owner-configured protocol routes, priority/recent measured latency choice, separate agent permission, manual dispatch, route cooldown and explicit extensions | Streaming/OAuth, future protocol adapters, budget reservations and distributed tracing. “AGI”/“SI” describes future requirements; no existing capability is asserted |
| Future cross-platform Meshlit install; OS-specific capabilities | OS/ABI/capability/fabric schema and host-side source companions | Native desktop/server/iOS/HarmonyOS/RTOS installers and platform keystores/services. The Android APK remains Android-only |
| Low-latency AI networking, HFT-inspired engineering | Research plan for connection reuse, bounded queues, reduced copies and measurement; no native transport implementation in this branch | Native authenticated transports; RDMA/UCX, AF_XDP/DPDK, affinity/NUMA, hardware timestamps and measured tail latency/jitter where qualified |
| Direct hardware/kernel execution | Public API/ISA research and backend artifact/qualification design | Privileged broker, device queues, DMA/IOMMU isolation, kernel/FPGA artifacts, signed installers, cancellation and platform-specific security review |
| LTS | Versioned format, unknown-version rejection, original Apache-2.0 implementation, tests and explicit backend ABI revisions | Compatibility fixtures, pinned compiler/runtime releases, reproducible binaries, signed updates, vulnerability response and a funded support lifecycle. No duration/warranty is promised |

## Open-source/free research first

Meta-associated PyTorch/ExecuTorch and the broader Triton ecosystem are research
candidates. ExecuTorch offers device delegates; Triton provides kernel/compiler
ideas but is not a universal CPU/NPU/FPGA runtime. Combine interoperable layers,
not unsupported claims. Start with original HyperL CPU/C99, LLVM/MLIR, StableHLO,
ONNX Runtime CPU and open PyTorch tools. Native drivers/proprietary SDKs remain
optional and are not bundled. Pin dependencies and preserve notices before
importing any code. Merge requires independent review, conformance, benchmark
evidence and explicit owner acceptance; no merge is performed here.

## Ordered implementation milestones

1. Freeze and test the bounded `hyperl/1` reference semantics, source emitters,
   memory/profiler contracts, owner gates and source provenance. Preserve Meshlit's
   existing on-device inference. Ship honest unavailable states. Extract the pure
   IR/reference/emitter into a standalone platform-independent package; the present
   Gradle verification harness still lives in the Android base, not a universal
   installer or native runtime.
2. Build an optional host compiler adapter: pinned LLVM/MLIR, deterministic f32
   flags, compile digest/cache, timeout/sandbox and signed artifact manifest.
   Execute the same conformance kernels against the CPU reference and record
   compiler/OS/ABI, inputs/output digest, numeric tolerance, time and memory.
3. Add CPU native vectorization and tensor shapes/strides with safe integer
   overflow checks. Separate semantic portability from performance portability.
   Benchmark before selecting tiled/fused/vectorized execution.
4. Add CUDA/TensorRT and ROCm/HIP host adapters, then qualified Vulkan/OpenCL
   or SYCL targets. Match runtime/driver/architecture; reject unsupported ops.
   Compiled source is not proof of a working GPU. Never silently fallback after
   admission where billing or mutation may already have occurred.
5. Introduce StableHLO/ONNX tensor import/lowering and SDK-specific NPU adapters:
   QNN, CANN/MindSpore, HiAI, NeuroPilot and Microsoft ONNX Runtime/WinML.
   Quantization and supported-operator proofs are per model/device/runtime.
6. Add memory-tier telemetry and transfer-aware storage offload with integrity,
   explicit owner permission and encrypted KV/checkpoint/intermediate spill.
   Distinguish global RAM/NUMA views, shared domains and allocatable budgets.
7. Add FPGA backend compilation/bitstream provenance and privileged provisioning
   outside the AI runtime. Then authenticated native RDMA/HCCS adapters and host
   fabric qualification; fiber transceivers alone provide no kernel transport.
8. Port platform service layers and installers. Keep identity/key storage,
   foreground/lifecycle rules, permission broker, media capture and package
   manager adapters OS-specific. OS/root presence never grants capability.
9. Qualify scale and failure recovery, including disconnected-stop acknowledgments,
   fenced ownership, partition tests, remote task uncertainty, budgets and auditing.
   Existing 3/5-member journal consensus is distinct from 10,000-node inventory.
10. Establish LTS release branches and compatibility corpus after qualified
    runtime integrations. Hardware and external-account acceptance stay pending
    until the owner resumes testing/provides existing test infrastructure.

## Research basis

See [RESEARCH_2026_10_08.md](RESEARCH_2026_10_08.md). Research-derived requirements
are design inferences, not replication of patent claims or proof of Meshlit behavior.
No upstream proprietary source/assets have been copied into HyperL.

## Cluster performance and turbo request

The owner requires high performance with mixed CPU/device stability and a turbo
policy for compatible groups. `HyperLClusterPlanner` groups qualified, enabled,
fresh profiles by OS/ABI, instruction set, target, runtime, driver, kernel digest
and precision. Mixed profiles stay in stability planning. Turbo is only a candidate
when the owner opts in, the entire group matches, each runtime implements turbo,
thermal/power readings are healthy and current measured speedup exceeds baseline.
The planner never changes clocks, ignores platform limits or activates execution.

Before native activation: benchmark one node versus the complete island including
serialization, transfer, synchronization and memory copies. Record wall time,
latency distribution, throughput, correctness tolerance, RSS/VRAM, sustained power,
thermal throttling, job cancellation and scaling efficiency. Test same device type
with different clocks/drivers/memory/network states. Infer no benefit from count,
brand or aggregate capacity. Partition independent tasks across compatible islands;
model layer/tensor partitioning needs its own data/communication cost model. Turbo
needs admission, resource reservations and runtime enforcement, not just this plan.

## Benefit must be measured

The goal is useful AI computation and human control: correctness/quality, accessible
hardware choices, lower latency and resource cost, privacy, clear permissions and
reliable interruption/recovery. Benchmark end-to-end workload time, energy and
operator effort alongside throughput. A faster arithmetic microkernel with slower
transfers or unreliable stop behavior does not qualify the complete system. Keep
AI compute/data paths lightweight; optional dashboards/profilers and security
brokers should run outside hot loops, while retaining accountable control.

## Standalone memory awareness and SDK timing (2026-10-08)

The owner requested a memory-aware feature and the full HyperL SDK later. Track
the standalone implementation in [sabbirimon/HyperL](https://github.com/sabbirimon/HyperL):
observed heap/environment limits, whole-DAG input/copy/intermediate/output estimates,
owner byte budgets and rejection before CPU allocation. Future native adapters
observe HBM, GDDR, DDR/LPDDR, unified memory, NUMA/CXL and SSD/NVMe independently;
no inferred technology, unlimited pooled RAM, automatic spill or capacity guarantee.
A complete SDK will later add arenas, ownership, pressure callbacks, profiler hooks
and qualified bindings. This repository retains its merged experimental foundation.

The owner also requested an attractive engineering workbench and built-in IDE/developer
tools. Standalone HyperL tracks the UI design system, local syntax/format/search/
validation/workspace/source tools and staged language-server/IDE/debugger/profiler
plan. These do not add an Android IDE or replace the later full SDK milestone.
