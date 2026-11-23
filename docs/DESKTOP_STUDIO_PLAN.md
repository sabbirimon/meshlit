# Meshlit desktop Studio implementation plan
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-23: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Studio model format support — 2026-11-24

**First delivery.** General Chat opens by default. Models & providers is the entry point for registering a file or model directory, inspecting its format and choosing how to run it. The native desktop shell owns this workflow on Windows, Linux, Intel Mac and Apple Silicon. HyperL supplies optional execution capabilities. This priority supersedes the earlier Compose shell proposal below.

| Milestone | Deliverable | Acceptance |
| --- | --- | --- |
| 1. Format support layer | Record the artifact path, container or package, declared quantization, required sidecars and compatible adapters. Retain existing GGUF and provider registrations. | Cover every family below. Reject incomplete required parts, report ambiguous binary inputs, keep inactive entries out of chat routing and preserve source files. |
| 2. Runtime adapters | Keep bundled GGUF generation. Add explicit local conversion or generation where a supported runtime is installed. Use the provider's own inventory for models already served elsewhere. | For each adapter record the runtime version, architecture, device, model fixture and a real generation result. Cover missing dependencies, incompatible models and cancellation. |
| 3. Cross-platform delivery | Exercise the model picker, import, switching, restart persistence and chat in extracted Studio builds. | Track Windows x64, Linux x64, Intel macOS and Apple Silicon individually. Add vendor device checks for each accelerated path. Android has a separate acceptance queue. |

| Model family | Coverage requested | Execution work |
| --- | --- | --- |
| Raw weights | SafeTensors including indexed shards, PyTorch PT/PTH/BIN, Flax MessagePack, TensorFlow SavedModel/PB, Keras H5 and NumPy NPY/NPZ | Start with complete SafeTensors checkpoints. Raw research arrays need an architecture and tokenizer. Keep pickle-backed weights inert during inspection. |
| Quantization and kernels | AWQ, GPTQ, EXL2, EXL3, HQQ, AQLM, QuIP#, bitsandbytes NF4/INT8, FP8, NVFP4, Marlin | Read explicit configuration or tensor metadata. Track the container, numerical representation and kernel requirement separately. Prove each model/runtime/device combination. |
| CPU and hybrid | GGUF, legacy GGML, llamafile, RWKV and other runtime-specific binaries | Direct execution for supported GGUF. Require an identified runtime for legacy binaries. Keep self-executing packages inert on registration. |
| Accelerator packages | MLX, TensorRT-LLM, ONNX, OpenVINO IR, Core ML, LiteRT, ExecuTorch, MLC-LLM/TVM, RKLLM, QNN/SNPE | Check package markers and required companions. Prioritize installed runtimes with text-generation interfaces. For deployed engines, select a real model ID from a configured endpoint. |

### Windows home and Android feature parity

| Android reference | Studio development work | Remaining qualification |
| --- | --- | --- |
| Chat output actions and sharing | Right-aligned user messages, copy, quote and save replies. Attach selected text or images to a draft. | Test keyboard use, narrow windows and sending images to a real vision model on Windows. OS share sheets remain open. |
| Voice screen and speech adapters | On-demand Windows dictation with transcript review. | Test microphone approval, missing language packs and recognition on a physical Windows host. Continuous conversation and read-aloud are separate ports. |
| Web search and permission helper | Review a Wikipedia query before it leaves the device. Show the destination before sending attachments to a provider. Group tool access in Permissions. | Qualify general web search providers and additional permission controls. |
| Devices, jobs and recovery | Studio links model management, device pairing, SSH, local control and host resources. | Port job history, recovery views and device capability telemetry using actual backend data. |
| Desktop shell and distribution | Scope editor menus to the project window. Exercise install, launch and uninstall for the single-file Windows installer in CI. | Verify first launch on the HP and record model, startup stage, latency and any runtime error. |

**Research follow-up.** After the catalog can preserve a reproducible model identity, evaluate model-proposed weight edits on a separate, writable checkpoint. Retain the original, tokenizer, evaluation split, latency and quality baseline. Only promote a variant after held-out evaluation on the target runtime. Hosted chat models can participate in evaluation through their published interfaces. [Research paper](https://arxiv.org/abs/2611.21885)

## Current request reconciliation — 2026-11-06

The [Chat/Studio request inventory](CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md)
reconciles this plan with the current main shell, passwordless local chat, bounded
project autonomy, bots, personalities, voice/translation and media requests.
Earlier architecture choices and completion labels below retain their dated
scope. Studio settings parity, durable history/RAG, training, media adapters,
managed device execution and release acceptance remain separate gates.

Updated 2026-10-10. Scope: desktop/server expansion; preserve Android's existing starter and user data. Shared planning/desugaring/voice-error fixes have their own Android regression checks. This document distinguishes the current Intel
offline installer work from future milestones; it does not claim feature parity.

## Proposed next milestone

The owner selected detailed plan refinement before implementation. Follow [S0–S6 stabilization](desktop/STABILIZATION_PLAN.md) and its [build/acceptance guide](desktop/STABILIZATION_BUILD_GUIDE.md) for the next proposed delivery sequence. The broader milestones below remain scope and backlog; their existence does not imply completion or authorization to start implementation during planning.

## Repository review and reuse

LM Studio's desktop application is proprietary. Its published interfaces can be
used, but its app code/assets are not available for copying under the SDK licences.
Build an original Compose UI with comparable workflow, thin headers and minimal
padding. Preserve Meshlit branding, themes, accessibility and user controls.

| Project | Checked licence/state | Intended use |
| --- | --- | --- |
| [lmstudio-js](https://github.com/lmstudio-ai/lmstudio-js) | MIT | Optional integration for an owner-installed LM Studio host; Kotlin REST first, SDK bridge only if needed |
| [lms](https://github.com/lmstudio-ai/lms) | MIT, built inside JS monorepo | CLI lifecycle reference; not an independent inference engine |
| [lmstudio-python](https://github.com/lmstudio-ai/lmstudio-python) | MIT | Optional host automation, not required Python inside every desktop install |
| [mlx-engine](https://github.com/lmstudio-ai/mlx-engine) | MIT; Python dependencies separate | Later Apple Silicon backend; unavailable on Intel, no silent CPU substitution |
| LM Studio model-catalog/configs | Apache-2.0/MIT, archived | Schema/design references, not a live model availability source |
| [Ollama](https://github.com/ollama/ollama) | MIT | Explicit native API adapter for installed Ollama; later reviewed optional runtime packaging |
| [Jan](https://github.com/janhq/jan/blob/main/LICENSE) | Current root Apache-2.0 | Local/cloud workspace and assistant workflow reference; preserve licence/attribution for any copied component |
| [AnythingLLM](https://github.com/Mintplex-Labs/anything-llm) | MIT, dependencies separate | Persistent document/workspace/RAG patterns; selectively reuse reviewed components rather than embedding its whole app |
| [Open WebUI](https://github.com/open-webui/open-webui) | Current custom licence with branding conditions | Own authenticated API adapter; optional external branded service, no wholesale rebranding/copy |
| LM Studio office-document-processor-sources | MPL-2.0 modifications; mixed upstream/font licences | Separate optional document service only after component/source-obligation review |

The supplied comparison was outdated: Ollama now has a desktop chat app; Jan's
current root licence says Apache-2.0. Pin and review specific versions before actual
reuse. No LM Studio/Ollama/Jan/AnythingLLM code is imported merely by this plan.

## Architecture

Keep the Compose/JVM desktop shell. Use separate interfaces for `ModelCatalog`,
`ModelStore`, `DownloadManager`, `InferenceBackend`, `ConversationStore`,
`DocumentIndex`, `ToolPolicy` and `LocalApiService`. Each backend reports its own
supported model formats, operations and real metrics. Model configuration and
credentials bind to the selected backend; switching cannot leak history/keys.

Backend choices: bundled llama.cpp CPU, optional qualified GPU backend, existing
Ollama, existing LM Studio, Open WebUI, generic authenticated host, Meshlit phone/cluster and
later MLX on Apple Silicon. Model file sharing and task routing are separate from
genuine multi-node layer execution; remote hosts are not pooled RAM by default.

## Sequential milestones and acceptance

Current checkpoint: both recovered Intel DMG/PKG launchers pass real starter generation, authentication rejection, Unload and native HyperL CPU checks using their own runtime. Seven management categories and local scoped search render; actual OSHI samples and HF metadata pass. The shared planner has bounded automatic/manual layer proportions; a live desktop worker controller is pending. Full details, failed attempts and publication are in DESKTOP_BUILD_LOG.md. Milestones 2–6 remain partial/planned, not completed by the installer.


1. **Offline Intel installer foundation (payload-qualified; distribution tracked separately).** Bundle verified Qwen2.5
   1.5B Instruct Q4_K_M (~1.12 GB), portable Java and CPU engine. Load/unload,
   local GGUF selection, authenticated loopback and full close/Stop lifecycle.
   Qualify real generation and native token usage from both DMG and PKG payloads.
   No model binary in Git; publish checksummed preview assets and licence/source
   material. Android stays unchanged. This is not full model-library management.
2. **Model library and discovery.** Original sidebar: Chat, Discover, My Models,
   Workspaces, Developer, Nodes, Settings; Experimental Labs separate. Top model
   selector, actual loading/download indicators, compact settings inspector.
   Hugging Face search/direct repo links, quantization/size/licence/capability
   cards, revision-pinned downloads, pause/resume/cancel with ETag validation,
   SHA-256 before atomic activation, selected storage root, disk-space admission,
   file reuse/import/delete with references and explicit confirmation. Private or
   gated models require legitimate user access; no credential leaks or bypasses.
3. **Inference controls and Ollama/LM Studio adapters.** Temperature, top-p/k,
   repeat penalty, seed, system presets, output/context limits, threads, qualified
   GPU layers and KV-cache options only where actually supported. Ollama native
   tags/show/pull/create/chat/ps plus keep_alive unload; LM Studio documented
   discovery/load/unload/download APIs with version capability checks. Never claim
   importing an SDK supplies its proprietary engine. Meshlit auto/manual loading
   and configurable idle unload respect active requests, pinned models and RAM.
   Verify decode rate from native token counts/durations separately from total
   request latency. No silent switch to cloud or another model on failure.
4. **Durable chats and document workspaces.** Local encrypted-at-rest sessions,
   search/export/branch/retry, per-workspace system settings, attachments, streaming
   Markdown/code/tables and model comparisons. Local chunking/indexing, real
   embedding backend selection, bounded retrieval and exact document citations;
   provenance, access and index deletion propagate. PDF/Office processing is an
   optional isolated component, not fabricated plain-text extraction. Establish
   adversarial-document and missing-source tests before shipping RAG claims.
5. **Developer and agent workspace.** Meshlit-owned CLI plus OpenAI-compatible
   API, real metrics, request cancellation, client token creation/revocation and
   quotas. Loopback default; authenticated TLS/grants for LAN/Internet. Optional
   MCP, reviewed tool calls, bounded task plans, file/SSH/workspace scopes, audit
   and Stop. Experimental remote actions/sandbox/VM remain separate from Core;
   neither local inference nor an imported model grants execution permissions.
6. **Qualification and extended devices.** Native Intel/Apple Silicon Mac,
   Windows x64 and Linux x64 packages tested separately. MLX only on actual
   Apple Silicon hardware; CUDA/ROCm/Vulkan/NPU features only with qualified
   drivers/devices. Test low RAM/disk, crash recovery, stale callbacks, corrupt
   models, network loss and revoked grants. Signing/notarization, dependency audit,
   clean-target installation and reproducible builds precede production claims.

Keep optional adapters/dependencies lazy to limit disk space. Never clone whole
projects or duplicate model weights just to display UI controls. Each milestone
ships only working controls, with unsupported features explicitly unavailable.

## Primary references

- [LM Studio app terms](https://lmstudio.ai/app-terms)
- [LM Studio model workflows](https://lmstudio.ai/docs/app/basics)
- [LM Studio CLI](https://lmstudio.ai/docs/cli)
- [Ollama model import](https://docs.ollama.com/import)
- [Ollama model pulling](https://docs.ollama.com/api/pull)
- [Ollama keep_alive](https://docs.ollama.com/faq)
- [Ollama desktop chat app](https://ollama.com/blog/new-app)

Open WebUI extensions: multi-model selection/comparison, durable chats, document knowledge,
workspace presets, controlled tools and optional account/RBAC UI belong to milestones 2–5.
Current addition is its explicit authenticated models/chat API route, not a claim that
its whole Python/Svelte server, RAG index or multi-user system is bundled.
See [API reference](https://docs.openwebui.com/reference/api-endpoints/) and
[current licence](https://github.com/open-webui/open-webui/blob/main/LICENSE).
