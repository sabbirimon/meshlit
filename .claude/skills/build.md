---
name: build
description: Build the Android app — assemble debug APK for the project modules.
tools: Bash
---

# /build — Build the Meshlit Android app

Builds the debug APK for the `:app` module and verifies the
`:core-*` library modules compile. Run before committing any
non-trivial change.

## Steps

1. Confirm the working directory is `/Users/code/AndroidStudioProjects/mllm`.
2. Run `./gradlew :app:assembleDebug :core-cloud-mcp:assembleDebug :core-trust:assembleDebug :core-common:assembleDebug`.
3. Surface any compile errors verbatim — do not paraphrase.
4. If the build succeeds, the APK is at
   `app/build/outputs/apk/debug/app-debug.apk`.

## Common error patterns

- `Unresolved reference 'put'` — usually a missing
  `kotlinx.serialization.json.put` import in a `buildJsonObject { }` block.
- `when expression must be exhaustive` on a `TopLevelDestination` —
  you added a new enum entry; every `when` needs a branch.
- `SharedFlow.tryEmit` unresolved — use the wrapper `coordinator.tryEmit(...)`
  helper instead; `SharedFlow` is read-only and does not expose `tryEmit`.
- `FloatArray.serializer()` unresolved — use `FloatArraySerializer()` from
  `kotlinx.serialization.builtins`.
