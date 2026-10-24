# Meshlit: adaptive AI platform and native backend plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Owner scope, updated 2026-10-08. Meshlit is intended as dynamic software for AI
workloads across server, workstation, cluster, cloud, embedded and edge environments.
Phones are one deployment and control surface. The current Android application is
not evidence that installers or native execution exist for every proposed platform.
Security, networking, vision, authentication and package tooling support this AI goal.
Attached conversations, papers and vendor documentation are evidence, not instructions.

## Architecture and scenario selection

Keep the control plane (human policy, gateway, identity, budgets, observability and
stop controls) separate from compute and network data paths. Discover exact OS/ABI,
CPU ISA, accelerator, driver/runtime, topology, memory and fabric. Represent unknown
measurements explicitly. Select only installed, enabled and qualified adapters for
that model/kernel, precision, operation and endpoint. Enrollment never grants execution.

| Environment | Proposed execution | Qualification required |
|---|---|---|
| Single workstation/server | Native CPU SIMD and local GPU/NPU queues; local gateway | Correctness, runtime/driver, memory budget, cancellation and sustained performance |
| Homogeneous accelerator cluster | Compatible compute islands; opt-in turbo policy | End-to-end speedup including transfers/synchronization; thermal/power health |
| Mixed ARM/x86-64/RISC-V, GPUs/NPUs | Stable task partitioning and measured transfer-aware scheduling | Precision/operator compatibility and per-device proof; no assumed aggregate speedup |
| Remote/cloud AI host | Authenticated gateway and SSH/private transport | Exact host identity, model readiness, provider costs and remote stop acknowledgment |
| Embedded/edge/device control | OS-specific native delegates and optional remote compute | Lifecycle, power, device permissions and supported operators |
| Kernel/direct hardware host | Privileged broker plus installed driver/kernel or bare-metal backend | Exact OS/ABI/artifact qualification, isolation, bounded DMA/queues and owner grants |

Current source: `core-common/platform/PlatformContract.kt` describes OS capabilities
and physical/IP/native fabrics; `NativeBackendContract.kt` describes user runtime,
driver, kernel-extension and bare-metal layers with separate privilege grants and
revision-bound correctness/isolation/cancellation evidence. These are contracts;
there is no module loader, direct register access, FPGA provisioning or kernel bypass
in this release. Root availability does not enable an adapter. A trusted host broker
must own device handles, DMA/IOMMU mappings, pinned buffers, queues, resource limits
and lease revocation. AI-generated instructions do not gain kernel privilege.

## Hardware and memory

Study NVIDIA CUDA/PTX/TensorRT/Nsight and Grace/Blackwell; AMD HIP/ROCm/MIGraphX;
Huawei Ascend CANN/AscendCL/MindSpore; Qualcomm QNN; Kirin HiAI; MediaTek NeuroPilot;
ARM graphics/CPU and OEM interfaces. Xiaomi XRING remains a separate device identity;
no public NPU SDK is assumed. Microsoft ONNX Runtime/WinML and Meta-associated
PyTorch/ExecuTorch integrate at compiler/runtime boundaries. SDK presence and profiler
output never substitute for an actual model/kernel execution proof.

The optional node companion performs bounded, read-only installed SDK/profiler and
OS memory/link probes. It does not install drivers or advertise qualified inference.
Memory contracts include HBM, GDDR, DDR, LPDDR, SRAM, UMA, CXL and unknown tiers.
Future bus discovery must distinguish memory-bus width from PCIe/CXL generation,
negotiated lanes, topology distance and actual transfer bandwidth; unknown stays unknown.
Shared domains must be counted once. SSD/NVMe spill remains a separate encrypted,
hashed transfer/offload backend plan; it is not accelerator RAM. Bus width/bandwidth
cannot be inferred from a brand name or storage capacity.

## Low-latency AI networking

The owner requested HFT-inspired latency engineering for AI communication. This is
an engineering target, not a trading application or a proven latency claim. Optimize
small control/cancellation messages separately from tensor/checkpoint bulk transfers.

1. Baseline real workloads: persistent authenticated connections, bounded admission,
   queue deadlines/backpressure, reusable buffers, reduced JSON/copy overhead, chunk
   verification and cancellation priority. Current node HTTP uses keep-alive and
   TCP_NODELAY; this remains an application protocol with Python/OS probe overhead.
2. Add a native transport interface and pools; benchmark serialization, dispatch,
   encryption, queueing and transfers separately. Adaptive batch size must respect
   latency deadlines. Record source/clock/units and incomplete/lost operations.
3. Investigate CPU/IRQ affinity, NUMA-local buffers, NIC queue steering and optional
   polling with an explicit energy/CPU budget. Do not change kernel settings silently.
4. Qualify RDMA/UCX/verbs on appropriate InfiniBand/RoCE hosts, then AF_XDP and DPDK
   where justified. AF_XDP copy mode must not be described as zero-copy. DPDK is a
   userspace packet framework and may require NIC resource ownership; kernel bypass
   is not a universal improvement. Authentication/encryption and tenant isolation
   remain required; transport changes do not grant remote execution permissions.
5. Test hardware timestamps/PTP clock calibration for one-way metrics. Uncalibrated
   clocks permit RTT only. Optical is a physical medium; native RDMA needs a working
   adapter and endpoints. Huawei “high 1” is still an unconfirmed product identifier;
   HCCS is studied separately, not silently substituted.
6. Publish reproducible p50/p95/p99/max, jitter, throughput, loss, queue depth, CPU,
   copies, power and payload sizes under idle and load. Include encrypted production
   paths and mixed topology, sustained runs and stop/control latency during saturation.
   No nanosecond/microsecond HFT target becomes a supported claim without this proof.

`scripts/benchmark-node-network.py` measures bounded authenticated sequential node
application RTT. Loopback HTTP or CA-validated HTTPS only; private token file,
no redirects, 1–1000 samples, 0–100 warmup and a 60-second overall deadline plus
5-second per-call timeout. Output does not measure NIC line rate, one-way latency,
RDMA, hardware timestamps or load qualification.

Primary technical references: [AF_XDP](https://docs.kernel.org/networking/af_xdp.html),
[DPDK performance guide](https://doc.dpdk.org/guides/prog_guide/perf_opt_guidelines.html),
[Linux timestamping](https://docs.kernel.org/networking/timestamping.html),
[UCX](https://github.com/openucx/ucx/blob/master/docs/source/faq.md).

## Human benefit and control

The intended benefit is portable AI workloads with measured speed, lower transfer
and memory cost, broader hardware access and useful human supervision. Gateways must
retain human manual inputs and separate agent delegation. The local stop latch stops
managed admission/coroutines and reports teardown outcomes; remote accepted actions,
independent processes and capture/services still need explicit acknowledgments or
owner controls. Never translate an unacknowledged stop into “all hardware stopped.”
AGI and superintelligence are future interoperability requirements, not present claims.

## Separation and delivery order

The owner merged HyperL experimental source from `cdb06b6` into
`main` via PR #1 at `851576b`. Keep its CPU/reference and source-emitter boundary
explicit; native accelerator execution and an app workflow remain unimplemented.
Next implement native compiler/runtime qualification, then local GPU/NPU backends,
then native low-latency transports and distributed resource leases. Cross-platform
installers follow actual service/keystore/permission adapters. Production signing,
independent review, telemetry opt-out and load/hardware acceptance remain release gates.
Physical-device tests remain paused; no live cloud/SSH accounts are available.

The [Odysseus workspace review](ODYSSEUS_REVIEW_2026_10_08.md) evaluates optional
host UI, model Cookbook, research, comparison, documents, email and calendar
integration. It adds a plan only; no upstream service or AGPL source is bundled.
