# Two separate Dolphin model integrations

Updated 2026-10-10. Owner explicitly requests both speech and chat models on desktop/server.

## Dolphin chat LLMs

Dolphin from Eric Hartford/Cognitive Computations is an instruction-tuned chat
family. It can be selected through a configured Ollama-compatible host or a
compatible trusted GGUF imported in Meshlit's Models section. This is a generic
runtime route; no Dolphin weight is bundled and no Dolphin generation has yet
been qualified in this desktop build.

- Model card: [Dolphin3.0 Llama3.2 3B](https://huggingface.co/dphn/Dolphin3.0-Llama3.2-3B).
- Ollama publisher page: [dolphin3](https://ollama.com/library/dolphin3).
- Discover: explicitly enable Hugging Face, search `Dolphin` GGUF repositories,
  review publisher/base licence, exact architecture and quantization, then select
  a revision-pinned file with full size/SHA metadata. The official card's
  Safetensors weights are not directly importable by the GGUF runtime.
- Ollama: operator installs/pulls the selected model separately; Meshlit connects
  to the operator-owned literal loopback `http://127.0.0.1:11434/v1` endpoint and
  refreshes the actual model list. Do not insert model names as if already installed.
- Use Response policy → My instructions or Model native to choose Meshlit's local
  system-message behaviour. These options do not alter trained refusals, model
  chat templates, hosted rules or tool authority. They apply to Meshlit's local
  runtime; remote hosts retain their own configuration.

The “uncensored” label describes publisher tuning, not a tested guarantee of
responses. The Llama-based card has a Llama licence; do not label all Dolphin
variants Apache/MIT or redistribute a large model without reviewing its terms.
Tool/function calling and coding quality require separate model evaluations.

## DataoceanAI Dolphin speech model

The requested [DataoceanAI/Dolphin](https://github.com/dataoceanai/dolphin) is ASR:
speech recognition, VAD, segmentation and language identification. It is separate
from the chat family and is not a text-to-speech voice engine. Upstream identifies
its code/weights as Apache-2.0; inspect the exact downloaded artifact's licence.

Desktop adapter status: **planned, no runtime/transcription proof**. Required work:

1. Pin upstream/runtime dependencies and a speech model with manifest size/hash;
   install in an isolated optional environment, not the bundled chat runtime.
2. Explicit trusted local model path; no automatic model download during a session.
3. User-selected audio and microphone grants, language/region, input size/duration
   limits, deadlines/cancellation and truthful transcript/timestamps.
4. Feed the transcript to the separately selected local/online chat model only
   when that route is approved. TTS is another adapter with its own recipient.
5. Verify real multilingual audio, silence/noise, failure, restart and cancellation
   on Intel Mac; then qualify Windows/Linux. Do not infer live voice from CLI compile.

Model/runtime storage remains opt-in; the compact desktop starter stays Qwen.
