# Real bundled starter model
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../../../../PLAN.md) · [Progress](../../../../../PROGRESS.md) · [Document status](../../../../../docs/DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

The APK includes **Qwen2.5 0.5B Instruct Q4_K_M**, 491,400,032 bytes (~469 MiB).
`bundled-model.json` pins its Hugging Face revision, URL, SHA-256 and Apache-2.0
license. The binary is ignored by Git; release builders must prepare it:

```sh
python3 scripts/prepare-bundled-model.py
# Or use an existing copy, which must match the same pinned hash:
python3 scripts/prepare-bundled-model.py --source /absolute/path/model.gguf
./gradlew :app:assembleMeshlitV1Debug :app:assembleMeshlitV2Debug
```

Gradle rejects missing or corrupt assets. First boot extracts and verifies the
actual file on an IO coroutine, registers it in the Models library, and attempts
to load it through the real RunAnywhere llama.cpp engine. Startup loading is
optional. Missing storage, native backend failures and memory limits are visible
errors. No generated text or model readiness is simulated.

This model replaces the former 135M starter after repeated fabricated replies on
the Xiaomi. It needs a larger APK, extraction and runtime copy. It remains a
small local model and does not establish reliable autonomous or consequential
answers. Larger 1.5B and other models remain optional downloads subject to RAM.
Models offers RunAnywhere's SDK downloader, resumable verified HTTPS with scoped
Hugging Face tokens, and multiple Android document imports. The SDK path reports
its real progress and requires an actual registry artifact; private repositories
use the HTTPS/token option. Downloads share a two-transfer concurrency limit.

A bundled model cannot be deleted from the APK through the UI. Unload it, disable
startup loading, or select another startup model. APK storage plus extraction plus
RunAnywhere's content-addressed runtime copy require more space than model size.

Source: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF
Base model: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct
License: Apache-2.0, reproduced in THIRD_PARTY_NOTICES.txt and LICENSE-Apache-2.0.txt.
