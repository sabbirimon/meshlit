# Online models, power, peripherals and portable configuration

Updated 2026-10-06. This is the current implementation guide; validation is in
`../PROGRESS.md`. Source implementation is not proof of a paid API call, external
USB driver, physical phone inference or internet task federation.

## Human and agent model selection

Chat conversation options save the model source, instructions, output token
limit, temperature and history length. Offline uses the currently loaded SDK,
native CPU or verified layer coordinator engine. Online uses an explicitly
selected enabled profile. Local failure never triggers a paid fallback. Online
responses are buffered, local replies stream. Model-specific context, GGUF weight
precision and native KV key precision stay in Models; this does not convert weights.

Profiles use encrypted app storage, up to 20 profiles. Presets configure real API
base URLs for OpenAI, Claude, DeepSeek, Qwen US, Gemini and Hugging Face. Custom
OpenAI-compatible HTTPS endpoints are supported. Model IDs are entered by the
owner or fetched from the actual API; no invented inventory or account balance.
Qwen keys/endpoints must match the user's region/workspace. Chat subscriptions
and API billing are separate. Per-profile API-key authentication may be disabled
for an explicitly anonymous endpoint; servers can still return 401/403.

Requests reject credentials/query/fragment in profile base URLs, never forward
keys through redirects, disable automatic connection retries, have a 120-second
call limit and 4 MiB response limit, and propagate cancellation to the real call.
Supported formats: OpenAI Chat Completions with `max_completion_tokens`, compatible
Chat Completions with `max_tokens`, Claude Messages, Gemini generateContent.
Temperature is omitted by default; enable only for models supporting it.
No unsupported tool-call, image/audio or OAuth capability is advertised.

`MODELS_LIST` includes `cloud:<profile-id>` entries. `MODEL_GENERATE` accepts this
ID. Agents need saved MODELS delegation and per-profile `agentAllowed`; enabling
one does not enable the other. Existing scopes never enlarge themselves. OpenClaw
continues to have independent gateway and Android-control consent.

## Public discovery, free offers and costs

Hugging Face public `/v1/models` discovery requires no login. Live offers retain
model ID, provider, nullable input/output USD per million-token rates, nullable
`is_free`, source URL and fetch timestamp. Search covers model/provider; the free
filter includes only offers explicitly marked `is_free=true`. Missing free status
or prices remain unknown. Public discovery does not remove the token, quota,
billing or geographic conditions of hosted inference. Gated weights require the
owner to accept repository terms and provide appropriate credentials.

Applying an HF offer pins `namespace/model:provider`, preventing a rate from one
provider being silently used for another route. Refresh is a user action, not
background traffic. Other providers link to current official pricing pages and
support manually reviewed prices. No universal automatic price scraper is
implemented. Structured adapters for other officially published pricing catalogs
are future work; a changed schema must fail visibly, not retain a new 'verified'
timestamp on old rates.

Chat shows actual reported input/output token counts, or unknown. Cost is
`input_tokens * input_rate / 1e6 + output_tokens * output_rate / 1e6`, only when both
counts and rates exist. Rates are assumptions or timestamped web snapshots, not
invoices; cache tiers, reasoning billing differences, tools, taxes, credits and
regional price differences can affect charges. These estimates are not a hard
provider spending limit. Set provider-side spending limits separately.

Primary references checked 2026-10-06:
- https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create
- https://developers.openai.com/api/docs/pricing
- https://platform.claude.com/docs/en/api/messages/create
- https://platform.claude.com/docs/en/about-claude/pricing
- https://api-docs.deepseek.com/api/create-chat-completion/
- https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope
- https://ai.google.dev/gemini-api/docs/text-generation
- https://ai.google.dev/gemini-api/docs/pricing
- https://huggingface.co/docs/inference-providers/main/hub-api
- https://huggingface.co/docs/inference-providers/en/pricing

## Power and cost readings

Power & Costs samples Android battery percent/status, current µA, voltage mV,
charge counter µAh, energy counter nWh where supported, temperature, power saver,
thermal status, network metering and validation. Unsupported sentinel values
become null. Signed battery power is `current_µA * voltage_mV / 1e9` watts.
Negative current is discharge under Android's contract; OEM inconsistencies need
physical validation. This is device battery-side power, not app-only, wall-plug or
USB output measurement. Charging may hide load behind net current.

A visible-screen session integrates adjacent negative readings using trapezoids.
Charging/unknown status, missing samples, clock regressions and gaps over 15s are
excluded. Export contains up to 720 observed samples as JSONL, never synthetic
history. Observed recharge-cost estimate is `Wh / 1000 / efficiency * tariff`.
The tariff/currency are user supplied; 85% efficiency is a labeled assumption,
not a measured property. Long-term background and per-node calibrated energy
accounting remain future work.

Saved controls: battery threshold 5–80%, low-battery download pause, unmetered
validated-network downloads, charging required for new model loads. Downloads
check admission and pause within the 5-second monitor interval. Imports are not
cancelled by download policy. Resume is manual. Running inference is not forcibly
interrupted; firmware charge limits/bypass charging are not implemented.

## USB / OTG and acceleration

External Devices polls actual USB interfaces/classes, VID/PID, endpoint counts,
permission status, Android storage volumes and persisted SAF grants every 3s while
open. Android's permission prompt authorizes a selected attached device. A folder
picker grants mounted storage access; Files uses SAF and Models imports real GGUF
files. This does not format disks, implement raw mass-storage protocols or directly
mmap a model from a document-provider stream. USB attach/detach count changes go
to the app log while this screen is tracking. No fake connected devices appear.

Classification is a hint from USB classes, not a product database or proof of a
working driver. Audio/input rely on Android routing/drivers. Camera, serial,
Ethernet, GPU/NPU/FPGA adapters need actual compatible plugins. A GPU attached by
USB does not imply CUDA/Vulkan compute support or usable VRAM.

Acceleration displays actual Android-advertised OpenGL ES and Vulkan features,
the installed native CPU runtime and SDK availability. New URL/import models can
use the chosen supported default backend; existing models keep their own runtime
options. Native coordinator/local CPU thread caps 1–4 affect the next process and
are reduced by device policy. The SDK's acceleration remains SDK-managed.
CUDA/TensorRT, ROCm/HIP, Metal/CoreML, OpenCL/Vulkan inference, Intel OpenVINO/oneAPI,
Qualcomm QNN, Huawei CANN/HiAI/MindSpore Lite, Rockchip RKNN, MediaTek NeuroPilot and
Samsung ENN are explicit future adapters, not functioning toggles or bundled
proprietary libraries. See `declarative-federation-roadmap.md` for adapter gates.

Android primary references:
- https://developer.android.com/reference/android/os/BatteryManager
- https://developer.android.com/develop/connectivity/usb/host
- https://developer.android.com/reference/android/os/storage/StorageManager

## SSH and network rules

SSH uses the open-source maintained `com.github.mwiede:jsch:2.28.7`, Java 8 baseline.
Mandatory independently verified `SHA256:` host-key pin; no accept-new, agent
forwarding or public SSH listener. Password or unencrypted private-key credentials
are stored encrypted. Modern algorithms available depend on Android JCA support;
Ed25519/chacha/curve25519 on older platforms may need an explicitly reviewed crypto
provider. Unsupported negotiation fails rather than enabling obsolete algorithms.

Saved hosts support real exec requests, a 60-second deadline, cancellation that
disconnects the session, and stdout/stderr bounded to 64 KiB each with truncation
reported. Logs contain connection ID, completion and exit status; command text,
credentials and output are not logged. Cancellation cannot undo remote side
effects. Remote hosts need an existing SSH server and owner-authorized credentials.
No automatic SSH-server install, SFTP UI, interactive PTY, tunnel deployment or
worker enrollment is claimed. Agents require both SSH scope and per-host opt-in.

Reviewed https://github.com/termius: the public organization supplies components,
including an archived CLI and SSH test-host tooling, rather than a complete
embeddable Android client. No Termius code was copied. JSch reference and license:
https://github.com/mwiede/jsch (BSD-style; preserve dependency notices).

Network Rules persists and validates up to 64 port rules with default allow/deny,
priority and an inbound TCP editor. The same policy now gates new legacy inference,
HTTPS control 18792 and TLS RPC worker 50551 connections. Address rules separately
require IPv4 private LAN ranges; this editor cannot authorize arbitrary WAN/IPv6.
Default empty policy denies ports. Add explicit allow rules before peer enrollment
or worker use. Existing connections are not retroactively disconnected. This is
an app listener gate, not Android-wide VPN, iptables, SSH/cloud outbound filtering.

## Configuration profiles

Settings → Configuration Profiles exports/imports strict schema-v1 JSON bounded
to 256 KiB. Defaults stage a safe profile for review. Export includes appearance,
power policy, native thread/default options, registered model IDs/runtime options,
disabled online profile templates and listener rules. Secrets, files, prompts,
conversations, task logs, SSH keys, API keys, device pairing/trust, Android grants,
root/autonomy consent and agent scopes are excluded.

Import first validates and previews contents. Apply preflights hardware/context
constraints, pauses no work implicitly (active transfers must be paused first),
updates matching IDs, skips missing model IDs, and imports cloud templates disabled
with agent access off. Existing local credentials for matching provider IDs are
retained; the preview says so. Model changes require explicit reload. Settings
span multiple stores: partial failures report what applied, and reapply is possible.
This is not a distributed transaction, peer enrollment or executable IaC plugin.

## Agent and log management

Agent Management shows real registered MCP tools, saved delegation scopes and
local typed job state; cancelling active typed jobs invokes their real controller.
Registry mutations/list snapshots are synchronized. Added read tools: `power_status`,
`external_device_status`, `online_profiles_list`, `ssh_connections_list`; SSH exec
is a separate scoped tool. No hardware permission may be silently granted by an
agent. The historical fake Healthy agent-runtime bootstrap entry is removed;
real typed controllers continue operating through their actual journals/tools.

Logs support severity/source/search, redacted preview, filtered TXT/JSONL export,
and confirmed buffer clear. At most 2000 entries are retained in process memory;
restart/eviction is explicit. Durable rotated diagnostics, signed audit export and
per-cluster event correlation are future work. Existing files/logcat are not
removed by clearing this buffer. Redaction is best effort; source code must still
avoid emitting credentials or prompts.
