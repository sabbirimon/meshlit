# vendored/

This directory is intentionally **not tracked** (see `.gitignore`). It
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
