# Device-aware local models, context and KV cache

## Product contract

Android remains the current build target. The model library measures Android API,
primary ABI, RAM, free storage, low-RAM classification, CPU count, SoC label,
thermal status where the OS exposes it, and installed native engine artifacts.
Availability of an artifact does not prove that a native model load succeeds.
No device category or chipset name implies a working GPU/NPU driver.

`DeviceRuntimePolicy` is a portable deterministic planner; `DeviceRuntimeProbe`
is the Android adapter. Remote participants must report the same capability shape
through a future authenticated adapter. A browser, router, NAS, switch or firewall
is not assumed to support compute because it can join a control group.

## Conservative policy in this build

| Observed device | Category | Suggested native context | Context cap | CPU threads |
| --- | --- | --- | --- | --- |
| Low-RAM, under 4 GiB RAM, or API below 29 | Light | 512 if memory constrained; otherwise up to 2048 | 2048 | 1 |
| Other supported device | Balanced | 2048, or 512 under pressure | 4096 | Half logical CPUs, bounded 1–4 |
| At least 8 GiB RAM and API 31+ | High capacity | 4096 when the estimated budget exceeds 2 GiB | 8192 | Half logical CPUs, bounded 1–4 |

These are application policy estimates, not performance benchmarks. Budget is
60% of currently available RAM minus 256 MiB. Native admission accounts for
weight overhead, estimated KV payload and a compute reserve. Unknown KV dimensions
use a conservative reserve; known metadata never produces a fabricated sensor
reading. Severe-or-higher thermal status blocks new heavy loads. Older OS versions
report thermal status as unavailable. This does not interrupt an already-running
request or silently switch its model; live throttling is a later lifecycle gate.

Current supported local inference ABIs are arm64-v8a and x86_64 on Android API 24+,
with an installed backend. A 32-bit phone needs a separately built and tested
native engine; a universal APK does not establish 32-bit inference compatibility.
Dynamic wallpaper color is OS-gated. The user can disable animation in Settings.
Theme animation is automatically reduced under Android low-RAM/low-memory, power-save or severe thermal states without changing the saved user preference. Real GPU/NPU capability probing remains future work; the planner currently governs recommendations, native thread count,
context caps and load admission.

## Runtime choices and context

RunAnywhere is the default engine and loads the real bundled SmolLM2 135M GGUF.
The pinned SDK 0.20.12 load request/JNI signatures do not expose native context or
KV precision. Requested registry metadata is not proof of an applied native setting.
Its loaded context is therefore reported as unknown (0 in the core DTO), and the
UI labels these controls as SDK-managed.

The optional native local engine uses the packaged llama-server executable without
remote workers. `-c` applies context size; `-t` follows the current device plan.
The UI persists per-model settings and explicitly requires reload. Context presets
are 512, 1024, 2048, 4096 and 8192, bounded by the GGUF training context where known,
the application cap and current device policy. RoPE scaling and arbitrary context
extension are not silently enabled. Agents can use `MODEL_OPTIONS_SET` and inspect
`DEVICE_RUNTIME_STATUS`; the mutation requires the Models delegation/device scope.

The native adapter verifies `/props` reports the expected model path and effective context before Ready. The same native process lifecycle supports approved RPC workers. Cluster settings
use the selected model's context/cache preferences, but cluster capacity estimates
are not actual native KV allocation measurements.

## Weight quantization and source selection

Weight quantization is a property of a real GGUF artifact. Curated variants have
separate URLs/IDs. The Hub panel fetches actual repository files, revision, sizes
and available LFS SHA-256. It pins the revision before queuing a transfer. File
filtering offers Q4/Q8/other and filename search. Installed quantization is read
from GGUF `general.file_type`; unknown formats remain unknown.

Changing quantization means downloading/importing and loading another artifact.
No on-phone requantization is implemented. Split GGUF files and safetensors are
rejected by the current single-file loader. Native runtime compatibility is
validated by the actual loader; a catalog label is not a compatibility guarantee.

## KV cache

The native engine applies `--cache-type-k` with f16, q8_0 or q4_0; values remain
f16. This avoids implying that every CPU/backend supports quantized values and
flash attention. Unsupported native configurations report their actual failure,
without secretly falling back to another precision.

The bounded GGUF reader extracts transformer dimensions, architecture and training
context without reading tensor data. KV payload estimate uses blocks, KV heads,
key/value dimensions and selected context/precision. It is explicitly labeled an
estimate and excludes padding, allocator overhead, compute buffers and sliding-window
optimizations. Hybrid/recurrent/unknown architectures do not get a guessed KV estimate.

KV belongs to the loaded runtime session. Unloading destroys the native model/cache.
Prompt-prefix reuse is disabled in native requests. Cache occupancy/hit-rate,
selective eviction, session export/import and crash recovery are not fabricated.

## Next implementation gates

1. On named low/mid/high physical phones, record OS, ABI, observed resources,
   actual startup/model load, latency, memory high-water mark and temperature.
2. Confirm effective context and native K/V types from server properties/runtime
   logs after each configuration; test unsupported configurations and storage pressure.
3. Add tested native SDK context/KV APIs or a supported SDK upgrade before enabling
   these controls for RunAnywhere itself.
4. Add scoped session identity, tokenizer-aware prompt budgeting and generation
   reservation before prefix reuse or automatic context truncation.
5. Add measured cache statistics and typed clear/export/import operations with
   native acknowledgment, storage quotas, encryption, TTL and cancellation.
6. For shared phone memory, replicate the compact task log first. Any KV checkpoint
   must bind model hash, tokenizer hash, native revision, context, RoPE settings,
   cache types and layer placement. Recovery requires a lease/epoch and replay
   evidence; copying an opaque cache file is insufficient.
7. Add real backend probes for Vulkan/OpenCL/CUDA/ROCm/Metal/CANN/MUSA/other vendors
   behind portable capability interfaces; build and validate each on real hardware.

## Evidence

See `PROGRESS.md`. Unit admission tests, successful compilation, real artifact
checks and live Android inference are separate evidence levels. No overall
completion percentage follows from these planner/UI controls.
