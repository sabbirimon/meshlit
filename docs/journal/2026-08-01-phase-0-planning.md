# Journal — 2026-08-01 — Phase 0 planning session

A snapshot of the project's state at the end of the planning session on
2026-08-01. Captured so a future agent doesn't have to reconstruct this
from git log.

## Snapshot of state

**Phase:** 0 — scaffolding not yet started. Identity work complete.

**Repository:**
- `git init` complete; first commit pending.
- Branch: `main` (none yet).
- Conventional Commits format adopted (`feat:`, `fix:`, `chore:`, `docs:`).
- Will move to `phase/0-scaffolding` branch when Phase 0 work begins.

**Files at this snapshot:**
- `.gitignore` — expanded for Gradle/IDE/Puku/native/gen
- `PROGRESS.md` — running journal
- `app/BUILD_GUIDE.md` — v1.1 (533 lines)
- `app/CLAUDE.md` — operating manual with new user-driven-choice rule
- `app/src/main/res/...` — Meshlit brand identity (colors, strings,
  themes, adaptive launcher icon, monochrome layer, all density buckets)
- `docs/architecture/` — empty, awaiting Phase 0+ diagrams
- `docs/decisions/0001-user-driven-choices.md` — ADR for §0 principles 9–10
- `docs/journal/` — this file
- All `core-*/` directories — created but empty pending Phase 0 Gradle
  restructuring

## What we did

1. Read `app/CLAUDE.md`, `app/BUILD_GUIDE.md`, and the four `SKILL-*.md`
   files. Verified the project's existing constraints and phase plan.
2. Designed and wrote Meshlit's brand identity:
   - App name + tagline.
   - Color palette (midnight + violet/cyan/emerald tri-role accent).
   - Theme (`Theme.Meshlit`).
   - Adaptive launcher icon (Brain + 3 satellites + light beams + hex
     cluster motif on a radial midnight background).
   - Monochrome layer for Android 13+ themed icons.
   - Generated raster fallbacks at all five density buckets using PIL.
3. Rewrote `app/BUILD_GUIDE.md` as v1.1:
   - Added principles 9 & 10 (user-driven choices).
   - Re-organized Phases 3–5 to include new feature areas.
   - Added §7 feature-area playbooks for all 10 expansion areas.
   - Added §8 honest limits of the expanded scope.
4. Updated `app/CLAUDE.md` with the new "user-driven choice" rule and
   a clarified no-public-SSH clause.
5. Set up version control: `git init`, expanded `.gitignore`, wrote
   `PROGRESS.md` (running journal), set up the `docs/` folder, added
   `app/BUILD_GUIDE.md` §2.5 with the v.c. convention.

## What we have NOT done yet

- Did NOT start Phase 0 multi-module Gradle restructuring — pending
  your go-ahead.
- Did NOT add a single line of Kotlin — UI shell is the next deliverable
  after the Gradle restructure.
- Did NOT enable any external dependencies beyond what was already in
  `libs.versions.toml`.
- Did NOT ship any new feature-area code. Everything in §7 is a plan.

## Decisions taken (full decision log in PROGRESS.md)

- Brand name chosen ("Meshlit") without the user being able to weigh in
  — flagged in PROGRESS.md; can be swapped.
- Icon designed and rasterized locally without downloading assets —
  keeps the project self-contained and license-clean.
- Build guide expanded in place (kept Phase 0–5 numbering, added §7)
  rather than rewritten — minimizes references to broken links.
- SSH rule kept as written; cluster-internal SSH added as a Phase 5
  opt-in feature.
- Tailscale + WireGuard presented as user-toggled cards in the Network
  screen, not as defaults.

## Next steps (in priority order)

1. Phase 0 — multi-module Gradle scaffolding (Task #1).
2. Phase 0 — Compose UI shell with bottom navigation (Task #11).
3. Phase 0 — physical-device verification (Task #6).
4. Phase 1 — NDK + llama.cpp + foreground service (Task #10).
5. Phase 1 — client UI + hardcoded-IP prompt dispatch (Task #7).

## Open questions for the user

- **Brand name:** keep "Meshlit" or change? (Flagged in PROGRESS.md.)
- **Palette:** violet/cyan/emerald + midnight; OK or change?
- **Proceed with Phase 0 scaffolding now?** Or refine the plan first?

## Tags to apply when this snapshot is committed

```
git add . 
git commit -m "docs: brand identity v1, build guide v1.1, scaffold repo"
git tag phase-0-planned
```

(That commit is the next step — see `PROGRESS.md` "Up next" section.)
