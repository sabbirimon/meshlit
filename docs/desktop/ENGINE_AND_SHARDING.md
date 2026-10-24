# Desktop engine and shared sharding admission
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. [Feature tracker](../DESKTOP_FEATURE_TRACKER.md).

The desktop engine owns one verified CPU llama-server process and one model. The
native backend is the pinned existing llama.cpp revision; neither Ollama nor
LM Studio's proprietary runtime is copied into Meshlit. Their compatible host
APIs remain independently selected and authorized.

## Actual local controls

Settings → Inference engine configures context (512–8,192), generation and prompt
CPU threads, batch/micro-batch sizes, key-cache precision, CPU backend selection
and idle unloading.
Values persist locally. Changing a value unloads the prior runtime and clears the
conversation; the next Load uses the new options. Default context is 4,096, one
slot, f16 KV keys/values, and five-minute idle unloading. Send on an unloaded
local model loads it in response to that human click, then submits that prompt.
Idle unloading never interrupts an active request. A dead owned process is
reported instead of remaining “ready.” No cloud fallback or new tool grants occur.

Before launch, the model/container and full managed hash are checked. The shared
bounded GGUF reader derives architecture, training context and transformer KV
estimates. Unknown KV/context metadata or insufficient observed available memory
blocks admission. Weight/KV/graph estimates retain headroom but are not OS memory
reservations or measured RSS. Unsupported architectures may still be used through
an independently configured host; no generic GGUF compatibility guarantee exists.
The running native `/props` response must identify the requested model and context.

Native environment overrides are removed; the app explicitly disables agent tools,
remote downloads, GPU offload and automatic context fitting for this CPU bundle.
Streaming text is buffered and UI snapshots limited to about 30 per second,
with a final lossless flush. Display updates are not native token counts. End-to-end
rates come only from reported completion usage and measured request duration.

## Intel vector dispatch

The unmodified pinned CPU backend builds twice: portable SSE4.2 and explicit
AVX2/FMA/F16C with system Accelerate. BMI2, AVX512 and AVX-VNNI are disabled.
Auto checks a hash-verified native probe, including OS XSAVE/XGETBV state, before
selecting the optimized binary. Both binary hashes are checked. Compatible CPU
can be selected; forced AVX2 requires verified support. No CPU speed is inferred
from a device label or a UI switch. Other architectures and GPU/NPU execution
need separate builds and qualification.

`--cpu-benchmark-check` runs a bounded developer-only matched baseline/AVX2
comparison: same model, context, threads/batches, prompt, output cap, temperature
zero and seed 42, alternating order with a short warm-up. The local benchmark
sampling fields are not applied to ordinary chat or remote providers. Native
completion usage determines the reported end-to-end rate; loading is measured
separately. Thermal/background conditions are not isolated.

## Shared whole-layer admission

`desktop-engine` compiles the same portable GGUF and negotiation sources used by
Android. With known block metadata, `ClusterNegotiation` now calls `LayerPlacement`:

- require fresh, approved, compatible CPU worker offers and a model-owning coordinator;
- subtract per-worker OS/graph reserve and one estimated spare layer;
- allocate positive integer layer counts whose sum equals the model block count;
- balance within individual memory limits; leave out weak optional members when
  sufficient capable workers remain;
- accept explicitly supplied manual counts only for admitted worker identities,
  with complete layer coverage and every worker within budget;
- reject stale, denied, hot, mismatched, insufficient or malformed offers.

Normal settings expose f16 and q8_0 key cache. The q4_0 developer-only smoke
produced an incorrect arithmetic answer despite valid native text/token/context
results, so q4_0 is held out of the normal UI. Functional acceptance is distinct
from model accuracy; even a correct short q8/f16 answer is not general quality proof.

These counts are `--tensor-split` proportions in the existing Android native RPC
path. They estimate uniform block costs. Native tensor placement, embedding/output
weights, backend allocation and measured throughput can differ. This planner does
not reserve memory, make a GPU/NPU CPU-compatible, join a model API as a worker,
provide consensus, replicate KV, or prove low-latency execution across the Internet.
Manual counts are exposed in the core planning API; a desktop cluster controller
and UI that safely negotiate live paired workers are still pending.

## Acceptance and performance

The targeted tests cover asymmetric worker memory, complete/manual placement,
excluded weak members, invalid manual identities, GGUF bounds, memory admission,
stream coalescing, provider streams and cancellation. Actual local starter generation,
context readback and owned-process unload are separate runtime checks. Physical
multi-device execution and a before/after controlled sharding benchmark remain
required before claiming a speed gain. More CPU threads are not automatically faster.

Current matched Intel case records AVX2 at 12.052–17.409 end-to-end tokens/sec
versus SSE4.2 at 2.499–7.100 across two observations per backend, each 128 native
tokens with equal settings. Variance is substantial; no stable multiplier is
claimed. See [raw evidence](evidence/cpu-benchmark-host-check.json) and the
[build ledger](../DESKTOP_BUILD_LOG.md). This does not qualify physical sharding.
