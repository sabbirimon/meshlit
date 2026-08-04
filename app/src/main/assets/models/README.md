# Bundled GGUF starter model

The `:core-inference` module ships a single bundled GGUF that
`MeshlitApplication.onCreate` extracts on first launch via
`BundledModelInstaller.ensureInstalled(...)`. The extraction is
SHA-256-verified against the source asset.

This file is **not** checked into the repo because it's 940 MB —
GitHub caps tracked files at 100 MB without LFS, and even with
LFS the per-push bandwidth for a public repo gets expensive fast.

## File

| Field        | Value                                                    |
|--------------|----------------------------------------------------------|
| Name         | `qwen2.5-1.5b-instruct-q4_k_m.gguf`                       |
| Size         | ~940 MB                                                  |
| Source       | `Qwen/Qwen2.5-1.5B-Instruct-GGUF` on Hugging Face        |
| Quantization | `q4_k_m`                                                 |
| SHA-256      | `1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370` |

## How to restore the asset locally

Run from the repo root. The Hugging Face mirror is what the SDK
catalog also points at; if you have a different mirror you trust,
swap the URL — the SHA-256 is the source of truth.

```bash
mkdir -p app/src/main/assets/models
cd app/src/main/assets/models

curl -L \
  -o qwen2.5-1.5b-instruct-q4_k_m.gguf \
  https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf

# Verify the bytes match what the installer expects. A mismatch
# causes the installer to throw IOException on first launch.
sha256sum qwen2.5-1.5b-instruct-q4_k_m.gguf
# expected: 1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370
```

After the file is in place, run `./gradlew :app:assembleDebug`
and the installer will extract it into `filesDir/bundled-models/`
on first launch.

## Why a single bundled model?

- One GGUF keeps `BundledModelInstaller` simple — single-asset
  branch, no picker, no race between multiple extractions.
- 1.5 B Q4_K_M fits a 4 GB-RAM phone with ~600 MB headroom for
  the runtime; Q8_0 of the same model would still fit but Q4_K_M
  is the conventional "small + decent" quant and matches what
  the RunAnywhere catalog recommends for that family.
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