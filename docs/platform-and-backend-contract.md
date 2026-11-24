# Portable host and accelerator contract

Android is the implemented UI target. Other OS UIs are future ports, not currently
shipped applications. The native coordinator/worker is a portable subprocess host
contract; the reference companion speaks the same pinned TLS protocol without
installing Android. Keep scheduling, ledger/checkpoint formats, capability schemas,
model/artifact identity and tool contracts independent of Android UI/lifecycle.

## Platform adapters

| Target | Host adapter and constraints | Current validation |
|---|---|---|
| Android ARM64/x86_64 | Install-time PIE nativeLibraryDir, foreground service, Keystore, Compose | ARM64 build and desktop proof; installed-APK checks in progress |
| Linux | Native CPU/GPU worker, service manager, filesystem, TLS certificate storage | Companion supplied; Linux-host execution not yet validated |
| Windows | `.exe` worker, service/job objects, Credential Manager, Win32 memory/CPU probes | Future adapter; do not claim os.uname/statvfs-based code portable |
| macOS | Native worker/Metal, Keychain, launchd, desktop UI adapter | CPU native build/proof validated; Metal not validated |
| HarmonyOS Android-compatible | Android SDK path only if the device actually supports it | No device proof |
| HarmonyOS NEXT | ArkUI/native host, OHOS lifecycle/permissions/keystore; platform bridge to portable protocols | Future native port; Android APK compatibility must not be assumed |
| NAS/router/switch/appliance | CPU ABI-specific host only when installation is supported; otherwise scoped storage/tools | No universal binary or vendor privilege assumption |

The current Kotlin modules import Android in several places. Do not rename them
"multiplatform" and call the port done. Extract pure DTOs/policies first, retain
unit tests, define host interfaces, then implement one platform adapter at a time.
Use stable semantic versions, additive optional capability fields, explicit major
version rejection, bounded decoding and migrations. Preserve unknown optional
fields where round-trip matters. Device clocks cannot establish ownership leases
without a defined clock/consensus model; timestamp offers at receipt for planning.

## Optional accelerator backends

`scripts/build-pipeline-native.py --backend` configures only an explicitly selected
backend from the pinned upstream source. Builds fail when the required compiler,
SDK or driver is absent. CPU is the tested default. Backend flags do not prove
runtime availability; enumerate devices and execute a known model before enabling.

| Vendor/ecosystem | Adapter/backend candidates | Deployment constraints |
|---|---|---|
| NVIDIA | CUDA, optional NCCL | Proprietary toolkit/driver compatibility, redistribution and VRAM checks |
| AMD | HIP/ROCm, Vulkan | GPU/OS support matrix and compiler/driver requirements |
| Intel | SYCL/oneAPI/Level Zero, CPU SIMD/AMX | Toolchain and driver availability; measured quant/operator coverage |
| Apple | Metal, CPU Accelerate | Apple host, entitlement/lifecycle and memory budget validation |
| Qualcomm/Adreno | Android CPU, optional OpenCL/Vulkan; Hexagon bridge separately | Driver/native libraries and device permission; NPU presence is insufficient |
| MediaTek/Mali, Exynos/Xclipse, Tensor | CPU, optional supported Vulkan/OpenCL runtime | Chip family/driver/version and quant coverage probes |
| Huawei Ascend | CANN backend in the pinned source | Ascend hardware, CANN SDK/operator libraries, Linux/driver validation |
| Huawei mobile/HarmonyOS | MindSpore Lite/HiAI/NN runtime plugin where licensed/available | Separate OHOS bridge, model conversion, native ABI and permission tests |
| Moore Threads | MUSA backend, optional OpenCL | MUSA toolkit, device/driver/compiler checks; do not equate with CUDA |
| Hygon | Compatible HIP/DCU adapter where supported | Vendor toolchain/runtime matrix; no unverified CUDA translation claim |
| Cambricon/Biren/Kunlun/other Chinese vendors | Versioned plugin interface | Only advertise after a real backend, model conversion/operator support and hardware tests |

Examples (on a host with the matching SDK):

```sh
python3 scripts/build-pipeline-native.py --backend cuda
python3 scripts/build-pipeline-native.py --backend hip
python3 scripts/build-pipeline-native.py --backend sycl
python3 scripts/build-pipeline-native.py --backend metal
python3 scripts/build-pipeline-native.py --backend musa
python3 scripts/build-pipeline-native.py --backend cann
```

These are build configuration paths; only CPU builds are validated in this work.
Keep proprietary dependencies optional and document licenses. Do not bundle vendor
SDKs, private firmware or model weights without redistribution rights. Plugin
manifests need OS/ABI, runtime/driver version, quant types, model architectures,
operator coverage, feature IDs, memory residency limits, thermal/power constraints,
health probes and a fallback policy. GPU workers must advertise actual usable
VRAM/unified memory, not substitute host RAM for VRAM capacity.

## Future-proof execution surface

Use task IDs, deadlines, cancellation, progress/event sequences, idempotency keys,
scoped credentials, resource budgets and audit across both agents and human UI.
Expose capabilities and implementation availability separately from user settings.
Keep signed provenance for artifact/runtime versions, upgrade rollback, schema
migration tests, journal compaction, retention/quota enforcement and health-based
admission. Feature flags should gate real implementations, not conceal fake success.

Do not add Kubernetes, Docker, public gateways or automatic vendor downloads as
mandatory dependencies of phone clustering. They can be optional deployment
adapters for capable hosts. The core invariant remains: a phone-first hive, small
state replicas on all enrolled phones, selected heavy artifact holders, actual
native layer execution, and explicit tested recovery boundaries.
