# vendored/
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Dependency pins, documentation and notices are tracked. Third-party source
trees are intentionally **not tracked** (see `.gitignore`). This directory
holds local copies of large third-party trees that Meshlit consumes
during development but that should not bloat the repository.

## What goes here

| Path                     | Source                                                                 | Size  | Used for                                                              |
|--------------------------|------------------------------------------------------------------------|-------|-----------------------------------------------------------------------|
| `vendored/upstream/`     | [ghostty-org/ghostty](https://github.com/ghostty-org/ghostty)          | ~210 MB | Reference for the `:core-terminal` VT parser swap (TODO #179)        |
| `vendored/runanywhere-kotlin/` | [RunAnywhere Kotlin SDK](https://github.com/RunanywhereAI/runanywhere-sdks) | ~230 MB | Local SDK build cache; the actual artifact is fetched via Maven in `gradle/libs.versions.toml` |

## How to populate

```bash
# Ghostty upstream (Swift/CMake source — used only as a reference while
# we port the VT parser to C++ in :core-terminal/src/main/cpp)
git clone --depth=1 https://github.com/ghostty-org/ghostty.git vendored/upstream

# RunAnywhere Kotlin SDK source (used to verify local changes against
# the upstream SDK; production builds use the published Maven artifact)
git clone --depth=1 https://github.com/RunAnywhereAI/runanywhere-sdks.git vendored/runanywhere-kotlin
```

After cloning, both directories are ignored by git. Builds do not
depend on their presence — they are reference material only.

## Why not submodules?

Submodules would force every contributor to clone ~440 MB before
building, and CI runners don't need them. Keeping them as local-only
makes the repository fast to clone and cheap to mirror.

## Optional RunAnywhere llama.cpp and desktop browser agent

Reviewed commit pins are in `sources.lock.json`. These are opt-in development
sources, ignored by git, and not required by the Android build:

```sh
python3 scripts/sync-optional-sources.py runanywhere-llama on-device-browser-agent
```

The supplied local checkout contains both trees. Their original LICENSE/NOTICE
files remain intact. The Chrome extension is a separate desktop companion;
Android Chrome cannot load its extension APIs. See
`docs/runanywhere-browser-and-llama.md` for the integration boundaries.

Default Android inference continues to consume `libs.runanywhere.sdk` and
`libs.runanywhere.llamacpp`. The pinned source snapshot is not asserted to be
ABI-compatible with the older SDK AARs. Do not replace the AAR's libllama.so with
an arbitrary source build. An optional standalone server can be built with:

```sh
python3 scripts/build-optional-llama.py --jobs 2
```

This requires host CMake/C++ tooling and can download CMake dependencies. The
result is a host executable, not an Android APK/JNI artifact.

OpenClaw integration reviewed at `a81b9cd71a5991597ab664f2cf8561b3e0afbe30`.
Its MIT notice and the Bouncy Castle provider notice are retained in `licenses/`.
The upstream checkout is a review reference, not a bundled Android dependency.
