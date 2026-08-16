# PLAN — working state + roadmap

> Local-only scratch log. Not committed to the repo — see
> `.gitignore`. Companion to `REQUESTS.md`.
>
> Format:
>
> ```
> ## <topic>
> **Status:** pending | in-progress | done | blocked | wontfix
> **Files:** <list>
> **Verification:** <commands + expected outcome>
> ```

---

## Branch / build state

```
Branch:  dev
Remote:  origin/dev
Ahead:   6 commits (not pushed)
HEAD:    ca7a613  Add RA-style UI components + wire into Agent + Catalog screens (0.2.3)
Working: dirty — 142 entries (51 modified tracked + 91 untracked)
Build:   ✅ :app:assembleDebug  (BUILD SUCCESSFUL in 9s, 444 actionable)
Install: ✅ R9KN2009CZJ  (Success)
Device:  SM-A207F + R9KN2009CZJ both on adb
```

The 6 unpushed commits already on `dev`:
1. `ca7a613`  Add RA-style UI components + wire into Agent + Catalog
2. `e5c6b63`  Add RunAnywhere brand gradient + Figtree / Maple Mono typography
3. `48b324a`  Add Phase Cloud 2 — Search/Web/Tools adapters + Browser automation
4. `a4c7328`  Add Cloud-Hosted MCP Agent + Multi-Cloud Control Center
5. `0c88890`  feat(nav): make loaded-model chip tappable from Agent and Jobs
6. `08e0542`  feat(models): re-skin picker with RaListCard + ModelTrailingAction

The current working tree (142 dirty) splits cleanly into 5 phase
commits — see `PLAN — Commits` below.

---

## Today — what landed in the working tree (NOT yet committed)

These are the changes I made during this session that should land
in their own logical phase commit. None are committed yet.

### Phase A — UI bug fixes (small, low-risk)

**Status:** ready to commit
**Files:**
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitHeader.kt`
  — `TierPill` `width(IntrinsicSize.Min)` + `maxLines=1` + `softWrap=false`
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  — item width 72→80dp, auto-scroll target `index-2`→`index-1`
- `app/src/main/kotlin/com/meshlit/ui/components/SuggestionChipPill.kt`
  — `maxLines=1`, `softWrap=false`, ellipsis
- `app/src/main/kotlin/com/meshlit/ui/theme/DynamicTheme.kt`
  — opaque `surfaceContainerLowest..Highest`, `surfaceTint`,
    `scrim = 0xCC000000`
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelFilterRow.kt`
  — opaque selected chip colour
**Verification:**
- `./gradlew :app:assembleDebug` ✅
- adb install + manual check (Devices screen chips opaque,
  LITE badge horizontal)

### Phase B — Agent screen redesign

**Status:** ready to commit
**Files:**
- `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`
  — new `CompactToolbar`, `ModelPickerInline`, `SuggestionTile`,
    `ModeIconChip`, `IconToggleButton`; removed `ModelPickerBar`,
    `ModeBar`, `ActiveModelPill`; flat chat canvas; capped token-
    count text; ChatGPT-style empty state
- `app/src/main/res/values/strings.xml` — added `ra_summarize`
**Verification:**
- `./gradlew :app:assembleDebug` ✅
- APK installs ✅
- Visual: blocked by Phase C bug below

### Phase C — Import surface (device + URL + HF + GitHub)

**Status:** ready to commit
**Files:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ImportModelCard.kt`
  (new) — 4 paths: SAF device picker, HTTPS URL, `owner/repo`+file,
    GitHub URL or `owner/repo/path` + ref
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  - `suspend fun importFromSaf(...)`
  - `data class HfResolved`
  - `suspend fun resolveHfFile(...)`
  - `suspend fun resolveGithubFile(...)`
  - `private fun translateGithubUrl(...)`
- `app/src/main/res/values/strings.xml`
  — 13 new `ra_import_card_*` strings incl. `ra_import_card_gh_*`
**Verification:**
- `./gradlew :app:assembleDebug` ✅
- APK installs ✅
- Manual: tap "From this device" → SAF picker → grant → file
  copied to `filesDir/imported-models/`
- Manual: paste a GitHub URL → resolves to raw URL → download

### Phase G — Power / Battery / Thermal Monitor

**Status:** ready to commit
**Files:**
- `app/src/main/kotlin/com/meshlit/power/PowerMonitorController.kt`
  (new) — thin wrapper around `BatteryManager` + `PowerManager`.
  Subscribes to `Intent.ACTION_BATTERY_CHANGED`. Polls thermal
  status at 2 Hz. Exposes `PowerSnapshot` data class plus a
  rolling `history: StateFlow<FloatArray>` sparkline stream.
- `app/src/main/kotlin/com/meshlit/ui/components/power/Gauges.kt`
  (new) — `CircularGauge` (Canvas donut + arc, sweep-gradient
  at mid-range) and `MiniSparkline` (Canvas line + filled
  gradient). Custom rather than Vico.
- `app/src/main/kotlin/com/meshlit/ui/screens/power/PowerMonitorScreen.kt`
  (new) — three side-by-side gauges (battery level / temp /
  thermal), live text readouts card, sparkline history, "Keep
  screen on while charging" toggle
  (`view.keepScreenOn = enabled && charging`), and OSS
  recommendations card (Castro + Battery Historian).
- `app/src/main/kotlin/com/meshlit/ui/nav/TopLevelDestination.kt`
  — new `Power` enum entry, added to `drawerOnly`.
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  — `shortLabel` branch for `Power -> "Power"`.
- `app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt` — wires
  `PowerMonitorScreen` into the NavHost.
- `app/src/main/kotlin/com/meshlit/ui/screens/ScreenStubs.kt` —
  title/body when-expressions extended for `Power`.
- `app/src/main/kotlin/com/meshlit/ui/screens/help/UiTourScreen.kt`
  — blurb + use-case strings for `Power`.
- `app/src/main/res/values/strings.xml` — added `screen_power`.
**Verification:**
- `./gradlew :app:assembleDebug` ✅ (BUILD SUCCESSFUL in 26s)
- `adb -s R9KN2009CZJ install -r app-debug.apk` ✅ (Success)
- Manual: hamburger → Power → gauge row + readouts + history
  render; OSS links open browser.

---

## Open follow-ups (always pending)

### Phase D — Bottom nav tap bug fix

**Status:** pending (blocks visual verification of Phase B + C
on device)
**Files:**
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  — switch from custom LazyRow to M3 `NavigationBar` OR fix
    `Modifier.requiredWidth` on `BottomBarItem`
**Verification:**
- `./gradlew :app:assembleDebug` ✅
- `adb install -r ...`
- Manual: tap each bottom nav tab, screen changes
- `uiautomator dump` should show non-zero bounds for each tab

### Phase E — Models picker UI redesign per reference

**Status:** pending (R-14 reference applied to Agent so far)
**Files:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelsScreen.kt`
  — apply "Top pick" / "Loaded" / "Smart" / "Thinks" / "Fast"
    badges per `Screenshots/UI suggestion/model ui part , i want this .jpeg`
  — "Add from Hugging Face" CTA row
  — "Get" button with download glyph + delete icon
  — file size + NPU/LlamaCPP chip on each card
**Verification:**
- `./gradlew :app:assembleDebug`
- Manual: Devices → Models → catalog grid renders recommendation
  cards with badges

### Phase F — SmolLM2 stuck at 0% investigation

**Status:** pending (#26)
**Files:**
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  — possibly the `onProgress` callback never fires for sub-MB
    chunks; or HTTP redirect 301→302→200 chain is being
    followed serially without progress
**Verification:**
- Reproduce: tap "Download SmolLM2-360M-Q8" on a fresh install,
  observe progress bar
- Expected: progress increments within 5s of start; reaches 100%
  in <2 minutes on Wi-Fi

---

## Suggested commit order (today's pile)

```
git add app/src/main/kotlin/com/meshlit/ui/components/MeshlitHeader.kt
git add app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt
git add app/src/main/kotlin/com/meshlit/ui/components/SuggestionChipPill.kt
git add app/src/main/kotlin/com/meshlit/ui/theme/DynamicTheme.kt
git add app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelFilterRow.kt
git commit -m "fix(ui): opaque dropdowns, horizontal LITE badge, no-wrap chips"

git add app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(agent): icon-only toolbar + ChatGPT-style empty state"

git add app/src/main/kotlin/com/meshlit/ui/screens/settings/ImportModelCard.kt
git add app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(import): import models/agents/tools from device, URL, HF, GitHub"

git add app/src/main/kotlin/com/meshlit/power/PowerMonitorController.kt
git add app/src/main/kotlin/com/meshlit/ui/components/power/Gauges.kt
git add app/src/main/kotlin/com/meshlit/ui/screens/power/PowerMonitorScreen.kt
git add app/src/main/kotlin/com/meshlit/ui/nav/TopLevelDestination.kt
git add app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt
git add app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt
git add app/src/main/kotlin/com/meshlit/ui/screens/ScreenStubs.kt
git add app/src/main/kotlin/com/meshlit/ui/screens/help/UiTourScreen.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat(power): battery / thermal / voltage monitor with gauges + OSS recs"

# Phase D bug fix as separate commit (different concern)
# Phase E Models picker UI as separate commit
# Phase F SmolLM2 investigation as separate commit
```

---

## Things I noticed but didn't fix (open questions)

1. **`core-terminal/`** — vendored Ghostty swap per TODO #179 vs
   just an empty scaffold? Read first 30 lines of `build.gradle.kts`
   to decide before committing
2. **`vendored/`** — fonts + gradient (commit with Phase B) vs
   full Ghostty source (commit separately or never)?
3. **`Screenshots/`** — keep in repo with brief README, or
   `.gitignore` (already ignored — never committed)?
4. **Scaffolded empty modules** — `core-gpu/`, `core-advanced-engines/`,
   `feature-advanced/`, `feature-ghosty/` — keep and register, or
   delete? Each one is a place-holder; if not used by Phase A-F,
   `git rm --cached` and `.gitignore` is fine.

---

## Verification checklist (after every commit)

```bash
./gradlew :app:assembleDebug
# Must remain green

# Specific tests
./gradlew :core-observability:test :core-net:test   # after Phase Obs-1
./gradlew :app:lintDebug                            # after any UI work
./gradlew :core-inference:test :core-trust:test     # after Phase Cluster+Trust

# Pre-push
./gradlew :app:assembleDebug && git push origin dev
```