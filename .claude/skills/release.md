---
name: release
description: Bump the version number for a new release.
tools: Edit
---

# /release — Bump Meshlit's version number

Bumps `versionCode` + `versionName` in `app/build.gradle.kts`.
The Android system uses `versionCode` for upgrade ordering;
`versionName` is the human-readable label shown in Settings → About
and the Play Store listing.

## Usage

```
/release major|minor|patch 0.2.0
```

or just `/release` — the user wants a new number but doesn't care
which one.

## Convention

- `major` — bump the leading digit (0.x.y → 1.0.0).
- `minor` — bump the middle digit (0.2.x → 0.3.0); reserves a
  quarter for new features.
- `patch` — bump the trailing digit (0.2.1 → 0.2.2); for hotfixes.

## Implementation

1. Read `app/build.gradle.kts`.
2. Increment `versionCode` by 1 (or to the next "round" number if
   the user prefers — e.g. 5 → 10 for a release boundary).
3. Update `versionName` to match the new convention.
4. Run `./gradlew :app:assembleDebug` to confirm the build still
   works.
5. Summarise the new values in the response.

## Don't

- Don't bump the SDK or AGP versions in the same change. Open a
  separate PR for those.
- Don't edit `app/CHANGELOG.md` — that file is updated by the
  release engineer, not the agent.
- Don't commit — the user will commit when they're ready.