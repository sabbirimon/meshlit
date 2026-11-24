# Scenario routing, attachments and media

Updated 2026-10-06. This is source behavior and its acceptance plan. Read
PROGRESS.md for actual test/device evidence; an API adapter is not paid-provider
access or successful media generation proof.

## Model router

Settings → Model router manages up to 30 encrypted local recipes. Each contains
an ID/name, exact scenario, optional keywords, priority, enabled/agent opt-in,
mode and one to three installed local model IDs or `cloud:<profile-id>` targets.
There are no invented model quality scores or seeded provider responses.

Automatic selection matches the caller's scenario and any configured keyword,
then sorts by descending priority and stable ID. An explicit recipe bypasses
scenario matching but still must be enabled. No match fails visibly; there is no
paid, free or offline fallback. Coding/reasoning/summarization/translation are
example user-authored scenarios, not verified specializations of a selected model.

- SINGLE runs one selected model.
- CHAIN gives the next model the original task and preceding model's actual
  output, labeled untrusted reference data. Step instructions can request review,
  extraction or revision. The last actual reply is the result.
- COMPARE gives each model the same task and returns labeled answers. It does not
  fabricate a winner, consensus or a merged answer.

Execution is sequential. The singleton Android native context cannot safely keep
arbitrary local models running concurrently. All targets are preflighted before
the first call and checked again at step boundaries. Local artifact installation
and device resource policy are required; ModelLibrary enforces load policy and
InferenceRequest pins the expected model path under the coordinator lock. Cloud
profiles must be enabled and have their required key; agent calls also require
the route/profile opt-ins and saved MODELS delegation. Unknown usage/cost stays
unknown, including local usage affected by the documented stream-event bug.

Bounds: three steps, 1024 output tokens per step, 180-second step timeout,
24,000-character original task, bounded intermediate input/output. No automatic
retry after timeout/failure. Completed steps are returned with real outputs and
durations when a later step fails; success stays false. Cancellation propagates.
Failure during preflight runs no models. Active native/provider work is not rolled
back by stopping, and a provider may still charge for an accepted request.

Chat → conversation options offers enabled recipes or automatic rules plus a
scenario. Existing direct offline/online selection remains available. Settings
also has a text-task runner with step/status/output and Stop. MCP tools are
`model_routes_list` and `model_route_execute`; these direct calls are separate
from the 28 durable command operations and do not imply distributed durable jobs.
Only the latest route execution is retained in memory; recipes persist encrypted.

This is task/model routing, not transformer layer sharding. Phone pipeline workers
remain governed by the separate layer execution and recovery contracts.

## Attachments and vision

Chat's attachment button reads up to three user-selected UTF-8 text files through
Android's document picker. Each input is bounded to 128 KiB and 10,000 characters;
the combined reference is bounded and the final draft must fit 12,000 characters.
Binary/invalid UTF-8 data is rejected rather than pretending it was understood.
Actual text and file names enter the user draft and saved conversation after Send.
PDF/office/archive parsing, OCR and binary attachments in saved chat messages are
future adapters. An attached file does not execute code or install a model.

The attachment menu also opens Media Studio. Vision accepts an actual user-picked
image, bounds source bytes to 8 MiB, decodes/resizes on IO to roughly 1024 pixels,
encodes bounded JPEG and uploads only on the explicit Run action. The current
online adapter uses an OpenAI-format image-content request and the selected
profile's model. That model must actually support vision; a provider failure is
reported. Existing local image/camera and voice paths remain in Settings → Media,
where missing local native media models/backends report unavailable.

## Media Studio

Enabled OpenAI/OpenAI-compatible profiles supply endpoint and encrypted key.
Generation uses a separately entered media model ID. An enabled text model does
not establish image, speech or video capability. No paid API was exercised without
operator credentials, and text price rates are not reused as media prices.

| Operation | Actual adapter/source behavior | Current limit |
| --- | --- | --- |
| Image | POST `images/generations`, one inline base64 PNG, actual file/preview/export | 8-MiB image; URL-only responses/redirects are unavailable |
| Speech | POST `audio/speech`, entered voice/model, WAV response, playback/export | 4000 input characters, 16-MiB output; AI-generated speech label |
| Video | Multipart POST `videos`, 4-second 1280×720 job; explicit GET status and completed content | No fabricated progress; 64-MiB MP4; no background polling |

Video references persist encrypted with profile/endpoint and the actual remote
job ID/status/progress. Changing the profile endpoint refuses reuse of the old
reference. Check Status is read-only; Download requires completed status. Forget
removes only the local reference and does not cancel/delete remote work. Stop
cancels this app's request; it cannot promise remote cancellation/refunds. A
creation timeout may have an unknown remote outcome and must not silently retry.

Files install through bounded temporary files and atomic rename, with container
checks and storage admission. Local quota reserves each operation's maximum size
within 128 MiB. Export uses an explicit Android document destination. Delete only
affects files owned by the generated-media directory. Provider response/decoder,
retention and physical-device playback tests remain acceptance gates.

Generic music/sound synthesis, on-device diffusion/video models, image editing,
reference-image video generation and mixed multimodal recipe steps remain future
adapters. No synthetic output, dummy video or arbitrary file URL is substituted.
Do not automatically pass camera/audio content between models or providers.

## Next multimodal routing milestone

Introduce versioned typed inputs/outputs for text/image/audio/video/document and
verified backend operations (vision, STT/TTS, image generation, video generation,
DSP). Preflight every graph edge's modality, codec, dimensions, memory, quota,
network/privacy and delegation. Keep original user media references separate from
model-generated instructions. Admit capture/preprocessing/tool/storage nodes by
real capability evidence, not requested role names. Shard diffusion/audio/video
models only after the respective native runtime actually supports it; text-layer
RPC evidence cannot establish that capability.

Acceptance: real two-model local chain, selected online comparison with explicit
credentials, no-match/disabled/removed model, low-resource load rejection, profile
revocation between steps, cancellation and later-step partial failure. Media needs
real provider outputs, image/PDF capability failures, denied/oversized files,
quota/interrupt/restart tests, physical audio playback and remote video recovery.

Primary API references checked 2026-10-06:
- https://developers.openai.com/api/docs/guides/image-generation
- https://developers.openai.com/api/docs/guides/text-to-speech
- https://developers.openai.com/api/docs/guides/video-generation
