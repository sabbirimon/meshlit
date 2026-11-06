# Studio client of the HyperL model engine
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-06: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Owner direction, 2026-11-06: the custom model execution/sharding engine belongs
primarily to standalone HyperL. Meshlit Studio consumes that engine through an
explicit, versioned adapter; it does not absorb newly restricted HyperL source
into the Apache application. Mobile model defaults remain unchanged.

Canonical engine plan:
[HyperL engine plan](https://github.com/sabbirimon/HyperL/blob/3de097c9ce2cdd9b118c23047dae4bfb317cf768/docs/HYPERL_STUDIO_ENGINE_PLAN_2026-11-06.md).
That plan includes benefits to HyperL's own CLI/SDK and non-Meshlit clients.

HyperL's explicit responsibilities are **memory placement, model sharding,
splitting across multiple locations, expert caching, device scheduling, execution
and recovery**. The scope includes layer/pipeline, tensor, expert and verified
storage partitioning, within one device and across approved local/remote devices.
Studio should show actual partition assignments and execution locations. Existing
layer/tensor tests do not establish general expert paging or cross-location
recovery; those remain separate qualification gates in the canonical plan.

## Current delivery and next gates

1. Finish the current Studio increment: compact SVG navigation, native Studio
   menu, verified Qwen3-4B reasoning bundle alongside the default Qwen2.5-1.5B,
   truthful model identity, Thinking On/Off and bounded memory/time/cancellation.
   Source tests pass separately from native model acceptance.
2. Extend HyperL's existing residency planner with conservative expert/shared/
   router/cache accounting. This is planning, not GPU execution or expert paging.
3. Connect Studio to the existing standalone HyperL model/profile/job service:
   discover actual capabilities, pin profiles, show placement, submit/cancel jobs,
   preserve actual model identity and usage. Keep working direct local inference.
4. Qualify same-Mac CPU/RAM/4 GB AMD partial offload before promotion. The current
   GPU experiment stays separate from the shipped CPU backend. Disk capacity,
   available RAM and VRAM cannot be counted as one uniform memory pool.
5. Add one compatible MoE model and bounded expert cache only after reference
   equivalence, missing/corrupt-expert, low-memory and latency tests. Predictions
   prefetch; the model's learned router still determines the actual experts.
6. Reuse approved device/cluster routes and deterministic specialist task routing
   for bots, chats and IDE jobs. Physical Mac/Samsung, LAN and WAN acceptance are
   separate. No silent cloud fallback or inherited tool permissions.

## Management UI

Management → Models & Engine should expose profiles, architecture/digest, model
source, local/HyperL/device/cloud route, readiness, thinking support, context,
measured memory and jobs. Expert/cache details appear only for capable adapters.
An explicit Experimental section shows hardware qualification and failures.
Keep chat's selected model and execution destination visible. Bots reuse the same
profiles; their separate detail windows retain task and permission controls.

## Request reconciliation

- Standard reasoning model is the second desktop bundle; reduced-refusal weights
  remain optional user choices. Qwen3-4B is dense, not MoE.
- Soup already exists as an optional training companion. Reference its streaming
  design without claiming that it is an AMD inference engine.
- DeepSeek expert/kernel projects are evaluated for compatible backends; the
  existing DeepSeek coding harness is a different integration.
- Open weights or licensed downloadable proprietary weights require compatible
  runtimes. API-only models can be task targets, not local tensor shards.
- General Chat, Studio settings, voice/media, bot persistence, biometrics and
  other prior requests remain in the main request inventory. This engine plan
  does not mark those unfinished features complete.
- The user requested a plan before more work to conserve credits, and cleanup of
  obsolete generated builds. Preserve active/rollback artifacts and all user data.

## Evidence boundary

At planning time, Studio Node integration tests: 225 passed; packaged Python:
46 passed; focused desktop backend: 17 passed. Native Studio menu: 19 enabled
entries and two successful menu actions; 12 packaging tests passed. Qwen3 artifact
SHA/size/GGUF verified; native reasoning off/on produced correct simple answers and Stop cancelled a
further active request. These probes do not establish general reasoning quality.
Standalone HyperL's older 135M CPU-worker proof does not qualify this 4B bundle,
AMD GPU decoding, disk-paged experts or the new Studio adapter.

[Native Qwen3 acceptance record](desktop/evidence/studio-qwen3-native-20261106.json)
includes artifact identity, observed resource use, memory-admission rejection,
Thinking on/off replies, Stop and CPU-settle checks. Preserve the memory guard;
a correct short answer is not a general quality benchmark.

## Broader model formats — 2026-11-06

The owner requests more than the current GGUF bundle. Standalone HyperL is adding
a versioned format inspector and narrow SafeTensors conversion into its existing
JAX decoder. ONNX, LiteRT, Core ML and further adapters have separate execution
gates. Studio import/model-picker integration remains pending; do not label a file
runnable solely because its extension is recognized. Canonical plan:
[HyperL format support](https://github.com/sabbirimon/HyperL/blob/3de097c9ce2cdd9b118c23047dae4bfb317cf768/docs/MODEL_FORMATS_2026-11-06.md).

## HyperL format implementation checkpoint — 2026-11-06

Standalone HyperL now provides read-only model inspection and a bounded standard-Llama SafeTensors → f32 JAX checkpoint importer. 106 Python checks passed with JAX enabled, plus 5 native checks; Gradle distribution build succeeded. Numerical proof uses synthetic fixtures, not pretrained quality or GPU evidence. Studio GUI import and ONNX/LiteRT/Core ML execution remain pending. See the canonical `HyperL-plan/docs/MODEL_FORMATS_2026-11-06.md` for commands, supported architecture and future adapters.
