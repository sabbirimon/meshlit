# Ollama and LM Studio model hosts
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-03: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Reviewed 2026-11-02 against official documentation and this checkout.

## Platform decision

| Use | Ollama | LM Studio | Meshlit decision |
|---|---|---|---|
| Studio desktop/server inference | Native macOS, Windows and Linux host; macOS Intel is CPU only | Apple Silicon Mac, Windows and Linux; current Intel Mac unsupported | Optional named OpenAI-compatible connections |
| Android chatting with a computer | HTTP API client to an owner-configured host | HTTP API client to an owner-configured host | Supported through existing model profiles; no runtime installed on phone |
| Android offline inference | No official embedded Android engine qualified here | No official embedded Android engine qualified here | Retain RunAnywhere and the existing separate native pipeline runtime |
| CPU/GPU and memory across devices | A server endpoint alone does not demonstrate distributed execution | A server endpoint alone does not demonstrate distributed execution | Keep the distinct HyperL/native layer execution qualification |

The linked `lmstudio-ai` URL is an organization containing SDKs, a CLI and engine
components. It is not a single embeddable Android runtime. Public SDK/engine
repository licenses do not make the desktop application source available for
copying. Meshlit uses published HTTP interfaces; the applications stay separately
installed with their respective terms and model licenses.

For an offline Android alternative already in Meshlit, Settings → Acceleration
offers **Native llama.cpp · CPU** (`LocalModelBackend.NATIVE_LOCAL`) alongside
RunAnywhere when its native artifacts are installed for the ABI. Per-model
runtime options still govern load/context; the host presets below do not replace
that phone-native path.

This Intel Mac can use Ollama CPU inference. Its Radeon GPU availability in
Meshlit's separate Metal runtime does not imply Ollama can use that GPU. LM Studio
hosting needs another supported computer; it is not an installation target for
this Mac under the current official requirements.

## Implemented connection path

- Android: Settings → Model connections → **Connect Ollama host** or
  **Connect LM Studio host**. Enter the actual API base, refresh its model catalog,
  select an exact returned ID and explicitly enable the profile. The phone preset
  starts with an empty address and key authentication enabled to avoid treating
  the phone's loopback as the desktop. Agent access remains separately controlled.
- Studio: Models → the **Ollama** or **LM Studio** source. Defaults are
  `http://127.0.0.1:11434/v1` and `http://127.0.0.1:1234/v1`. Existing catalog,
  SecretStorage, request admission, cancellation and completion routes are reused.
- Both use `GET /v1/models` and `POST /v1/chat/completions`. Model IDs come from
  the host, with manual entry available when a catalog is unsupported. No preset
  invents an installed model or model readiness.
- Existing approved team sources can reuse compatible connections. Saving a
  profile does not grant team or tool authority, start a listener or pool memory.
- RunAnywhere remains the phone's local default. No local failure selects a host
  or a cloud provider automatically. No installer, daemon or model download runs
  when choosing these presets.

Remote addresses require HTTPS or an authenticated tunnel ending on literal
loopback. The receiving host must enforce authentication. Ollama's normal local
API ignores placeholder keys; adding a bearer string in Meshlit does not secure
that server. LM Studio can enable API token authentication. Meshlit does not
enable public binding or relax certificate verification for either host.

An Ollama server can also offer cloud models. Selecting a local-looking endpoint
is not proof of offline execution; choose an actually local model and configure
the host accordingly. The isolated qualification explicitly disabled cloud.

Context length, resident model lifetime, CPU threads and GPU placement belong to
the selected host runtime. Meshlit's phone overdrive controls do not silently
change an external server. Model catalogs, token limits and tool capabilities are
distinct; advertised tool support does not prove reliable model planning.

## Verification and remaining work

The installed Ollama 0.40.13 answered a real read-only model-catalog request. Its
existing two Gemma tags refer to the same 9,608,350,718-byte digest; neither was
loaded or modified for this work.

An isolated Ollama process, with cloud disabled and temporary model storage,
imported Meshlit's existing 491,400,032-byte Qwen2.5 GGUF. A real buffered request
answered `4`; streaming returned visible text, terminal completion and actual
usage (16 completion tokens). The isolated process and copied temporary artifact
were removed afterward. This is API execution evidence, not a comparison against
RunAnywhere or whole-app latency. The measured cold buffered request was 2.61 s
and stream first content 1.73 s in one sample; neither is a performance claim.

Current evidence is in `docs/desktop/evidence/ollama-host-20261102.json`. Both Android builds and their unit/lint gates passed for the initial integration.
The Samsung actual-client check also passed: it read the isolated Ollama catalog
and generated the correct arithmetic reply with positive reported input/output
usage, using an explicit USB reverse tunnel. The production chat-follow helper
passed long-stream, viewport-resize and manual-history checks in the same physical
run. [APK-bound evidence](desktop/evidence/samsung-chat-follow-ollama-20261102.json).
Later agent-conversation fixes are tracked separately from this host proof. LM Studio execution is
unverified: it is absent here and current Intel Mac hosting is unsupported.
Phone-to-host API inference is qualified over USB. Real host tool planning and
live WAN remain separate acceptance checks; API or catalog success cannot satisfy
them. Earlier actual agent handoff attempts failed and their transcripts remain
in the evidence directory; structural decoding capability alone is not a handoff.

Next acceptance steps:

1. Run both Android unit/build/lint gates and Studio connection tests.
2. Completed: exercise Android's actual compatible client against the isolated
   Ollama host over explicit loopback forwarding, verify reply/token usage, stop
   the isolated host and remove the temporary model store and reverse mapping.
3. On a supported owner-configured LM Studio host, test catalog, buffered and
   streamed completion, cancellation and authenticated rejection.
4. Benchmark the same model, prompt, context and threads per host before changing
   routing policy. Keep first content, total latency and tokens separate.
5. Consider native host management/telemetry only as explicit, separately reviewed
   controls. Do not expose pull/delete/server binding as default agent actions.

## Primary sources

- [Ollama source and MIT license](https://github.com/ollama/ollama)
- [Ollama API](https://docs.ollama.com/api/introduction)
- [Ollama OpenAI compatibility](https://docs.ollama.com/api/openai-compatibility)
- [Ollama macOS requirements](https://docs.ollama.com/macos)
- [LM Studio organization](https://github.com/lmstudio-ai)
- [LM Studio system requirements](https://lmstudio.ai/docs/app/system-requirements)
- [LM Studio server](https://lmstudio.ai/docs/developer/core/server)
- [LM Studio OpenAI compatibility](https://lmstudio.ai/docs/developer/openai-compat)
- [LM Studio authentication](https://lmstudio.ai/docs/developer/core/authentication)
- [LM Studio application terms](https://lmstudio.ai/app-terms)
