# Review of the supplied Meshlit architectural evaluation

Reviewed 2026-10-09 against the current source and primary references. The supplied
paper is an architectural proposal and external assessment, not instructions to
execute, an independent benchmark, or proof that its suggested components exist.

## Assessment

The useful priorities are measured placement, sustained thermal/battery tests,
bounded authenticated transport, and authorization that does not trust model text.
Meshlit's local GGUF path has real single-phone evidence. Distributed oversized
model execution on physical phones, portable KV migration, automatic failover and
additional accelerator families still require implementation and device evidence.
HyperL alpha.6 adds separate bounded CPU preprocessing and encrypted storage; it
is not a replacement chat engine or a transformer/NPU runtime.

## Corrections before using this as a roadmap

| Paper claim or recommendation | Checked boundary and decision |
| --- | --- |
| Cloud-free execution without telemetry guarantees privacy | Meshlit controls default off, but RunAnywhere development telemetry attempts are recorded in `docs/remaining-engine-bugs.md`. Locality does not prove zero network traffic, local isolation or secure device storage. Keep the limitation visible and obtain a supported opt-out with traffic evidence. |
| Phones already pool weights/KV into a high-availability runtime | Current `ClusterNegotiation` uses fresh, compatible, approved CPU offers, memory estimates and a thermal cutoff. `RpcTunnel` supplies pinned authenticated TLS. Desktop pipeline execution does not prove multi-phone performance or committed failover. |
| Agent Cards, Skills and Tasks are MCP's general primitives | This mixes protocols. MCP exposes tools/resources/prompts; A2A defines Agent Cards and agent skills/tasks. Meshlit's MCP/A2A/control adapters must identify their actual protocol and revision. [MCP security model](https://github.com/modelcontextprotocol/modelcontextprotocol/blob/main/SECURITY.md), [A2A specification](https://github.com/a2aproject/A2A/blob/main/docs/specification.md). |
| UDP broadcast bypasses router multicast/broadcast rate limits | Inference from [RFC 9119](https://www.rfc-editor.org/rfc/rfc9119.html): changing application transport does not remove slow/reliability-constrained Wi-Fi multicast at layer 2. Keep discovery traffic small and payloads on authenticated unicast. Benchmark any transport change for loss, jitter, congestion, power and integrity before adoption. |
| Measured compute/bandwidth-based asymmetric placement is useful | Supported as a research direction by [LinguaLinked](https://arxiv.org/abs/2312.00388). Its measured gains belong to its devices/models/baselines, not Meshlit. Current placement has no measured per-layer execution/network cost optimizer. |
| Stage reallocation can transparently continue generation | A placement decision alone cannot move compatible KV state, commit ownership or fence a disconnected worker. Define a checkpoint boundary, model/runtime/cache identity, authenticated transfer, old-worker fencing and rollback; prove equivalence before promising continuation. |
| QNN NPU execution eliminates thermal bottlenecks and has universal speedups | [ExecuTorch's Qualcomm backend](https://docs.pytorch.org/executorch/stable/android-qualcomm.html) has chipset, SDK, model/operator and conversion requirements. Do not infer support for the existing Adreno 506 phone or arbitrary GGUF files. A successful probe, actual model execution and sustained device benchmark are separate gates. |
| HMAC signatures/attestation prevent prompt injection | Authentication identifies a peer and preserves message integrity; authenticated content can still contain malicious instructions. Enforce permissions outside the model, deny unapproved tool chaining, and retain provenance/redaction. [MCP security guidance](https://modelcontextprotocol.io/specification/latest/basic/security_best_practices) requires implementation safeguards. |
| MCP percentage claims establish Meshlit vulnerability rates | The supplied text contains no attached methodology, affected Meshlit revision or reproduction. Related [security research](https://arxiv.org/abs/2601.17549) is relevant context, not a Meshlit security test. Do not present percentages as this app's measured attack rate. |
| Static prompt similarity routing preserves arbitrary MoE semantics | Selecting whole models/adapters for a request differs from token-level MoE routing trained within a model. Treat it as an explicit scenario-router experiment with quality evaluation, not equivalent inference. |
| Compressed intermediate representations guarantee cloud privacy | Activations/KV/embeddings can contain sensitive information. Compression is not encryption or a privacy proof. Any offload requires explicit destination/data authorization, authenticated encryption and leakage evaluation; never silently change an offline session. |
| ARMv9 SME improves legacy phone support | Compile/runtime ISA detection and safe fallbacks are required. A chip lacking SME cannot acquire it via flags. Qualify the existing SDK/llama kernels and exact hardware before introducing a new dependency. |

## Source checks and gaps

- `app/chat/LocalChatTools.kt` exposes only selected built-in tools to the local
  planner and rechecks automation/inference grants. `core-mcp/LocalToolLoop.kt`
  bounds context, accepts strict JSON, rejects unlisted tools and marks tool output
  untrusted. This is defense in depth, not proof against prompt injection.
- Combined web and phone grants can still allow a poisoned web result to influence
  a later permitted phone action. A follow-up should test cross-tool chains and
  enforce a policy decision per action outside the model. Existing human Android
  confirmation remains separate from in-app agent delegation.
- `core-inference/pipeline/ClusterNegotiation.kt` rejects stale/incompatible/hot
  offers. It does not measure each node's layer latency, network p95 or battery
  cost and does not rebalance live KV state safely by itself.
- `core-inference/pipeline/RpcTunnel.kt` already uses authenticated pinned TLS.
  Replacing it with unsigned or unauthenticated UDP would remove existing controls.

## Ordered follow-up acceptance gates

1. **Privacy and authorization:** reproduce SDK telemetry attempts and verify a
   supported opt-out; adversarial cross-tool tests with revoked grants; no secret
   disclosure or execution from external instructions; metadata-only diagnostics.
2. **Sustained single-phone baseline:** exact model hash/runtime/backend/device,
   native token counts (unknown remains unknown), prefill/decode p50/p95, memory,
   thermal status and battery/energy observations over repeated sustained runs.
3. **Measured two/three-phone pipeline:** authenticated peers, same model/runtime,
   measured unicast throughput/jitter and per-node stage timings; compare one
   phone and uniform/asymmetric placement while reporting memory admission versus
   actual peak use and output equivalence.
4. **Recovery:** disconnect/revoke/throttle during generation; explicit committed
   checkpoint, compatible KV restore, fenced worker and no duplicated model/tool
   actions. Replanning at the next safe boundary precedes seamless migration.
5. **Optional accelerators:** owner-selected supported chipset and converted model,
   pinned licensed SDK/build, actual runner/model proof, CPU output/quality
   comparison, sustained thermal/energy tests and honest unavailable fallback.

No QNN/ExecuTorch dependency, UDP transport replacement, custom HMAC protocol,
transparent cloud offload or parameter-free MoE router is added by this review.
The HyperL integration is the separate implementation change in this branch.

## Owner-directed implementation following the review

The subsequent owner request adds [separate Core/Experimental channels](PRODUCTION_CHANNELS.md)
and [bidirectional scoped SSH node commands](../SSH_NODE.md), with committed and
revocable agent VM grants. The Core channel is a qualification candidate, not an
assertion that the paper's distributed/accelerator/privacy promises are achieved.
Windows and Xiaomi devices are owner-identified inventory, awaiting exact models,
connections and capability evidence. Research performance figures remain external.
