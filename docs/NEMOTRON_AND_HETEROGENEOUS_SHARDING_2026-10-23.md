# Nemotron and mixed-hardware model placement — 2026-10-23
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

This is a research and admission note, not a claim that the models below have
passed a Meshlit load or multi-device test. Keep the current pinned starter
model and its verified offline path until replacements pass those gates.

## What to try

| Model | Proposed Meshlit role | Current admission |
|---|---|---|
| NVIDIA Nemotron 3 Nano 4B, official Q4_K_M GGUF (2.84 GB) | Optional English chat/tool agent on a roomy phone; first Nemotron candidate for Studio CPU/GPU and a server worker | Research candidate. Model architecture is hybrid Mamba/Transformer; the Android RunAnywhere 0.20.12 backend and Meshlit layer worker have not been verified with it. Download and load on each target before offering it. Do not bundle it in the default APK. |
| Nemotron 3 Nano 30B A3B, NVFP4 (19.4 GB weights) | Studio or server agent behind an authenticated model API | NVIDIA GPU path is documented. AMD/other accelerators need a backend-specific compatibility run. Never use 3B *active* parameters as the memory budget; all 30B weights matter. |
| Nemotron 3.5 Lightning 30B A3B | Fast long-running Studio/server agent with compatible GPU | NVIDIA documents single DGX Spark/H100 deployment. The model needs full weight and KV admission; no phone claim. |
| Nemotron 3 Super 120B A12B | Multi-GPU server or hosted reasoning/router tier | NVIDIA lists 8 H100 80 GB for BF16 and a separate quantized path. Not a phone-cluster default. |
| Nemotron 3 Ultra 550B A55B | Optional datacenter or hosted high-accuracy tier | NVIDIA lists at least 4 B200/GB200-class or 8 H100 GPUs for its NVFP4 release. Not a phone/desktop download target. |

The official 4B GGUF is text in/text out, English-oriented, and licensed under
NVIDIA's separate Nemotron Open Model License. Its card describes local voice
assistants, IoT and conversational personas as use cases; it is not itself a
speech, vision or browser-control model. It supports a thinking-off chat-template
mode. Benchmark our actual short chat, tool JSON, refusal and repeated-reply cases
before routing autonomous actions to it. The 262K advertised context is an upper
model limit, not a promise that a phone can allocate that KV cache.

## Sharding across vendors

The model file and architecture determine what can be loaded; the runtime and
accelerator backend determine where tensors can execute. A NVIDIA-branded model
does not intrinsically require an NVIDIA GPU. Quantization format matters:
NVFP4 recipes are optimized for NVIDIA hardware, while GGUF Q4_K_M is the first
portable inference format to evaluate in Meshlit's existing llama.cpp paths.

| Worker | Practical path | Gate |
|---|---|---|
| NVIDIA CUDA GPU | llama.cpp CUDA/RPC for GGUF; vLLM/TensorRT-LLM for supported Nemotron checkpoints | Matching backend/runtime revision, VRAM and real prefill/decode measurements |
| AMD ROCm GPU | llama.cpp HIP/RPC or ROCm vLLM for supported architectures | Validate each model and quantization on the exact GPU/ROCm version; NVIDIA NVFP4 is not an assumed ROCm fast path |
| Apple Silicon | llama.cpp Metal/RPC for compatible GGUF | Real load and token benchmark; memory and native revision match |
| Android ARM64 | Existing RunAnywhere local GGUF or separately built CPU RPC worker | Native loader, model architecture, memory/thermal and battery qualification; no claim for Nemotron until measured |
| CPU server | llama.cpp GGUF CPU/RPC or an API-served model | RAM, threads, latency, power and authenticated private transport |

For **Meshlit's current phone layer pipeline**, start with GGUF architectures in
the metadata allowlist (`llama`, `qwen2`, `qwen3`, `phi3`, `gemma`, `gemma2`,
`mistral`). A verified dense Qwen/Llama/Gemma GGUF is the easier first
cross-vendor acceptance model. The hybrid Nemotron architecture is absent from
that admission list, so local GGUF support elsewhere does not establish that
Meshlit can shard it. Test a Nemotron-specific metadata/admission path, native
operator support on every worker, matching RPC version and reproducible output
before enabling it. Do not route incompatible peers into the same tensor graph.

Use **job routing or replicated model servers** when each worker can load a
whole model; that is usually better for throughput and unlike layer splitting
does not send activations between devices every token. Use true layer/tensor
splitting only when weights exceed one node's memory and the interconnect makes
the extra transfer worthwhile. vLLM's tensor/pipeline/data parallel modes target
server-class interconnects. The upstream llama.cpp RPC backend supports mixed
CUDA, Metal and CPU devices, but its own documentation calls it proof-of-concept
and insecure on an open network. Meshlit's worker transport must stay pinned,
authenticated and private; any new backend needs the same admission boundary.

## HyperL across platforms

The bounded `hyperl/1` graph contract and C99 CPU implementation are portable
design assets. Android has Kotlin reference and NDK CPU execution; Studio has a
native CPU panel and checks. A server/container port can build the same C99 core,
but needs a packaged binary, matching revision, licence display, cancellation,
memory admission and platform tests. CUDA, ROCm, Metal, Vulkan and NPU execution
are separate backends. Current HyperL recipes/graphs do not implement LLM tensor
operators, KV transfer, Nemotron Mamba state, or cross-device graph scheduling;
therefore HyperL is not a substitute for the current llama.cpp sharding engine.

The proposed mixed-architecture scheduler should use a versioned capability
record per CPU/GPU/NPU/TPU backend: exact operator set, tensor shapes, supported
dtypes/quantization, memory budget, transfer path, revision and measured latency,
throughput and energy. Admission should reject an unsupported graph segment,
not silently run a different computation. Partition the dependency graph at
explicit tensor boundaries, execute independent segments in parallel, account
for copy/serialization cost, and bound in-flight buffers. Prefer a local device
when network transfer outweighs compute savings. Compare each backend against
the CPU reference within declared numeric tolerances, including cancellation,
worker loss and repeated runs. Permissioned enrollment and revocation still
govern remote workers. A TPU means a supported host runtime and driver, not just
a device label; phone NPUs likewise need vendor-specific adapters. This is a
roadmap architecture, not current multi-architecture execution.

## Acceptance order

1. On Studio, verify the official 4B GGUF file hash, load with the pinned engine,
   run chat/tool cases and measure resident memory, first-token time and tokens/s.
2. On Samsung/Xiaomi, attempt the same only after a memory/thermal estimate and
   explicit optional download; report an unsupported architecture instead of
   silently selecting a different model.
3. On a trusted NVIDIA or AMD host, expose a model API first; compare local 4B,
   30B and current starter replies through Meshlit's router.
4. Qualify heterogeneous layer splitting on a supported dense GGUF before
   adding Nemotron hybrid graph support. Record output equivalence, transport
   bytes, per-token latency, failures, stop/revoke and energy cost.
5. Port HyperL CPU to server/container with the same graph conformance fixtures;
   add GPU backends only after numerical and cancellation tests.

## Primary sources

- [NVIDIA official Nano 4B GGUF and file size](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF/tree/main)
- [NVIDIA Nano 4B model card and execution notes](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-BF16)
- [NVIDIA Nano 30B NVFP4 files](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-30B-A3B-NVFP4/tree/main)
- [NVIDIA Lightning 30B model card](https://huggingface.co/nvidia/NVIDIA-Nemotron-3.5-Lightning-30B-A3B-NVFP4)
- [NVIDIA Super 120B model card](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Super-120B-A12B-BF16)
- [NVIDIA Ultra 550B model card](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Ultra-550B-A55B-NVFP4)
- [AMD ROCm vLLM parallelism guidance](https://rocm.docs.amd.com/projects/ai-ecosystem/en/latest/optimization/vllm-v1-optimization.html)
- [llama.cpp RPC status and mixed-device layout](https://github.com/ggml-org/llama.cpp/blob/master/tools/rpc/README.md)
