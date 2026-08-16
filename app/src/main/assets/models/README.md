# Bundled GGUF starter model

The `:core-inference` module ships a single bundled GGUF that
`MeshlitApplication.onCreate` extracts on first launch via
`BundledModelInstaller.ensureInstalled(...)`. The extraction is
SHA-256-verified against the source asset.

This file is **not** checked into the repo because it is several
hundred MB — GitHub caps tracked files at 100 MB without LFS, and
even with LFS the per-push bandwidth for a public repo gets
expensive fast.

## File

| Field        | Value                                                       |
|--------------|-------------------------------------------------------------|
| Name         | `smollm2-360m-instruct-q8_0.gguf`                            |
| Size         | ~368 MB                                                     |
| Source       | `HuggingFaceTB/SmolLM2-360M-Instruct-GGUF` on Hugging Face  |
| Quantization | `q8_0`                                                      |
| SHA-256      | `48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201` |

## How to restore the asset locally

Run from the repo root. The Hugging Face mirror is what the SDK
catalog also points at; if you have a different mirror you trust,
swap the URL — the SHA-256 is the source of truth.

```bash
mkdir -p app/src/main/assets/models
cd app/src/main/assets/models

curl -L \
  -o smollm2-360m-instruct-q8_0.gguf \
  https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf

# Verify the bytes match what the installer expects. A mismatch
# causes the installer to throw IOException on first launch.
sha256sum smollm2-360m-instruct-q8_0.gguf
# expected: 48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201
```

After the file is in place, run `./gradlew :app:assembleDebug`
and the installer will extract it into `filesDir/bundled-models/`
on first launch.

## Why SmolLM2-360M-Instruct Q8_0?

- Smallest viable conversational model in the curated catalog —
  the FGS auto-loads it within seconds of first bind so the user
  sees real tokens almost immediately.
- The asset basename (`smollm2-360m-instruct-q8_0`) matches the
  SDK's `DEFAULT_MODEL_ID`, so the auto-load path on
  `InferenceForegroundService` accepts the bundled file
  out of the box without any rename step.
- Q8_0 keeps quantization artefacts low enough for demos while
  staying under the ~400 MB download ceiling that Qwen 2.5
  1.5B Q4_K_M (~940 MB) exceeded on cellular.

## Why a single bundled model?

- One GGUF keeps `BundledModelInstaller` simple — single-asset
  branch, no picker, no race between multiple extractions.
- 360 M Q8_0 fits any Android 14+ device with comfortable RAM
  headroom; users who want a larger model can upgrade via the
  Models screen.
- Phase 3 / Phase 4 will let the user pick a custom model path
  via the Models screen — once that lands, the bundled asset is
  optional convenience rather than a hard dependency. The
  installer already returns `null` on missing assets so the
  build doesn't break without it.

## See also

- `core-inference/.../BundledModelInstaller.kt` — the install
  flow, sentinel-based idempotency, SHA-256 verification.
- `app/src/main/kotlin/com/meshlit/MeshlitApplication.kt` —
  `extractBundledModel()` runs on `appScope` so first launch
  doesn't freeze the launcher.
- `app/src/main/kotlin/com/meshlit/inference/InferenceForegroundService.kt`
  — auto-loads the extracted path on FGS startup.
