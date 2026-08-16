# REQUESTS — full feature / bug / design history (chronological)

> Local-only scratch log. Not committed to the repo — see
> `.gitignore`. The canonical task history lives in git log +
> agent TaskList; this file exists so context survives between
> sessions and you can scan the whole journey in one place.

---

## Index — what's in this session (and the one before)

| #   | When                | Type     | Topic                                                        | Status |
|-----|---------------------|----------|--------------------------------------------------------------|--------|
| 1   | previous session    | bug      | Settings screen crashes when navigating                     | done   |
| 2   | previous session    | feat     | MicSpectrumVisualizer + recording pulse ring (Voice)         | done   |
| 3   | previous session    | feat     | AnimatedIcon w/ press/scan/pulse effects                     | done   |
| 4   | previous session    | theme    | Day/night theme + multiple palettes + smooth transitions    | done   |
| 5   | previous session    | feat     | Model download — Hugging Face + custom URLs + 401 fix       | done   |
| 6   | previous session    | bug      | Active Model chip showing model name when not loaded        | done   |
| 7   | previous session    | feat     | Tag Qwen3-30B as requiring HF auth                          | done   |
| 8   | previous session    | bug      | Make error popup more visible: highlight key + colored bg   | done   |
| 9   | this session (a)    | UI/bug   | Fix dropdown / popup text + bg opacity across app            | done   |
| 10  | this session (b)    | UI/feat  | Redesign Agent screen like DeepSeek / ChatGPT / RunAnywhere | done   |
| 11  | this session (c)    | feat     | Import models from device + URL + HuggingFace                | done   |
| 12  | this session (d)    | feat     | Import codebases / models / agents / tools from GitHub      | done   |
| 13  | this session (e)    | feat     | File management (use OSS, don't build from zero)             | done (was already shipped) |
| 14  | this session (f)    | ref/UI   | UI inspiration reference folder                               | in-progress (always) |
| 15  | this session (g)    | feat     | Power / battery / thermal monitor with gauges + OSS recs    | done   |
| 16  | this session (h)    | feat     | Gauges preferred over text-only for monitoring                | done (folded into R-15) |
| 17  | this session (i)    | UI/bug   | Icon-only Copy/Save/Share/Export + FlowRow landscape wrap   | done   |
| 18  | this session (j)    | UI/bug   | Models hub: visible download progress + Pause/Resume/Stop/Delete for SDK path | done (pending phone-side e2e) |
| 19  | this session (k)    | bug      | Bundled-model card shows wrong name (Qwen2.5-1.5B vs smollm2-360m) | done   |
| 20  | this session (l)    | feat     | Auto Pilot: watchdog + observations + auto-repair (consent) | done   |
| 21  | this session (m)    | feat     | Bundle Qwen2.5-Coder-1.5B as Android+networking knowledge model | pending (C0 first-run download) |
| 22  | this session (n)    | feat     | Auto Pilot persistence (audit trail) + real RepairExecutor  | done   |
| 23  | this session (o)    | feat     | Distributed multi-device LoRA training (P2P/DiLoCo/Accelerate) — Phase 11.0/11.1 landed | in-progress (11.2/11.3/11.4 pending) |

---

## PREVIOUS SESSION — work that landed before today

### R-01 — Settings screen crash fix
**Asked:** (no explicit quote — surfaced via "settings crashes")
**Why:** Navigating into Settings from any entry point
reliably crashed the app, blocking the rest of the smoke test.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt`
  — defensive `navController.navigate(...)` wrapped in
  `try { ... } catch (...)`, also `ErrorOutline` icon now
  explicitly imported (`androidx.compose.material.icons.filled.ErrorOutline`)
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/SettingsScreen.kt`
**Notes:** Root cause was a missing icon import — `Icons.Filled.ErrorOutline`
only resolves when explicitly imported, not by FQ name.

### R-02 — MicSpectrumVisualizer + recording pulse animation (Voice)
**Asked:** "want the voice screen to feel alive — show a spectrum
during recording, pulse the mic while hot"
**Why:** Voice screen was static; user couldn't tell when audio
was being captured or how loud it was.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/components/MicSpectrumVisualizer.kt` (new)
- `app/src/main/kotlin/com/meshlit/ui/components/PulseRing.kt` (new)
- `app/src/main/kotlin/com/meshlit/ui/screens/VoiceScreen.kt`
**Notes:** Visualizer reads from `VoiceEngine.inputLevel`
flow, smooths via exponential moving average, renders 32 bars
with a gradient mapped to RMS amplitude.

### R-03 — AnimatedIcon (press / scan / pulse effects)
**Asked:** "make the icons feel less stiff"
**Why:** Static Material icons made the UI feel like a 2018
mockup.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/components/AnimatedIcon.kt` (new)
**Notes:** Reusable composable, three presets
(`Press`, `Scan`, `Pulse`). Used in the loading screens and the
drawer quick-actions.

### R-04 — Day/night theme + multiple palettes + smooth transitions
**Asked:** "want to be able to pick a palette and have it switch
smoothly, not jump"
**Why:** Theme switching was binary and jarring; user wanted
multiple brand palettes (Meshlit default + RunAnywhere dark +
orange + Meshlit light).
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/theme/DynamicTheme.kt`
- `app/src/main/kotlin/com/meshlit/ui/theme/Palette.kt` (new)
- `app/src/main/kotlin/com/meshlit/ui/theme/PalettePresets.kt` (new)
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ThemeCustomizationScreen.kt`
- `app/src/main/kotlin/com/meshlit/settings/SettingsRepository.kt`
  (new keys: `themeMode`, `activePaletteId`)
**Notes:** Transitions are 350ms cross-fade of `ColorScheme`
fields; saves to SharedPreferences via DataStore.

### R-05 — Improve model download: Hugging Face + custom URLs + 401 fix
**Asked:** "models won't download, sometimes 401 unauthorized, want
to be able to paste a URL"
**Why:** The catalog only shipped two bundled models and the
download path couldn't recover from auth failures.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  — `downloadFromUrl`, `resolveHfFile`, HEAD-probe for size
- `app/src/main/kotlin/com/meshlit/settings/SettingsRepository.kt`
  — `hfAuthTokenFlow` persisted key
- `app/src/main/res/values/strings.xml` — model card strings
**Notes:** 401 from HF triggers a one-shot prompt asking for the
user's HF read-token; token is sent on subsequent requests.

### R-06 — Active Model chip showing model name when not loaded
**Asked:** "the chip says a model is loaded when nothing is
loaded"
**Why:** Empty-state showed the last successful model name,
misleading users.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`
  — chip now reads `coordinatorState` and switches label to
    "No model — open Models" when not `Ready`

### R-07 — Tag Qwen3-30B as requiring HF auth
**Asked:** (paraphrased from earlier "30B download fails silently")
**Why:** Without an HF token the 30B model 401s; user didn't
know why.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  — `requiresAuth` flag on the catalog entry

### R-08 — Make error popup more visible
**Asked:** "errors are invisible — make them pop"
**Why:** Default `Snackbar` colors were too subtle to be noticed.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/components/ErrorBanner.kt` (new)
  — uses `errorContainer`, bold title, `bodySmall` subtitle
- Various screens (Models, Jobs, Voice, Files) — now mount the
  banner above their content area.

---

## THIS SESSION — work that landed today

### R-09 — Fix dropdown / popup text + bg opacity
**Asked:** "fix txt opacity and bg pacity on popup or dropdown"
**Why:** All `DropdownMenu` / `Dialog` scrims and surfaces were
half-transparent; text bled through on the colored backgrounds.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/theme/DynamicTheme.kt`
  — opaque `surfaceContainerLowest..Highest`, `surfaceTint`,
    `scrim = 0xCC000000`
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelFilterRow.kt`
  — explicit opaque selected chip colour via
    `FilterChipDefaults.filterChipColors`
**Verified:** Devices screen chips / dropdowns / dialogs render
fully opaque now (`/tmp/final2.png`, `/tmp/final_post.png`).

### R-10 — UI redesign (Agent screen + header + bottom bar)
**Asked:** "why dont u just make a good ui design? shoid i give it
to other llm? added few ss new , look"
"just look and copy ui stule from runanywhere, sometimes only icon
should enough, no need to full lebel where space is short, on agent
section redesign whole section like deepseek or chatcpt app syle
inface model or run anywhere style, also fix ui and color"
**Why:** User frustrated with current UI density; wants
RunAnywhere visual language + DeepSeek/ChatGPT chat layout.
**Status:** done (Agent empty + toolbar + bubbles); bottom-nav
bug open
**Files touched:**
- `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`
  - new `CompactToolbar` (single row of icon-only 36dp chips)
  - new `ModelPickerInline` (replaces the full-width card)
  - new `SuggestionTile` (ChatGPT 96×108 rounded tile)
  - removed `ActiveModelPill` (was duplicating model name)
  - removed `ModelPickerBar` (was duplicating toolbar)
  - removed `ModeBar` (was a stacked horizontal row of long
    labels — now icon-only)
  - `EmptyAgent` is flat canvas + hero + 2-line title +
    subtitle + horizontally scrollable tile row
  - Token-count `Text` capped with `widthIn(max = 140.dp)` so
    it never wraps per char
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitHeader.kt`
  - `TierPill` uses `width(IntrinsicSize.Min)` + `maxLines=1` +
    `softWrap=false` so "LITE" stops rendering vertically
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  - item width 72→80dp so JSON/Users/Cluster labels don't clip
  - auto-scroll target shifted `index-2` → `index-1`
- `app/src/main/kotlin/com/meshlit/ui/components/SuggestionChipPill.kt`
  - `maxLines=1, softWrap=false, ellipsis`
- `app/src/main/res/values/strings.xml` — added `ra_summarize`
**Known open bug:** LazyRow bottom-nav tabs don't register taps
on this device — uiautomator dump reports zero bounds for
LazyRow children. Needs `Modifier.requiredWidth(...)` or
switching to M3 `NavigationBar`. Pre-existing — not introduced
by today's redesign.

### R-11 — Import models from device + URL + HuggingFace + FTP/web
**Asked:** "model not downloading, tucks and where is model upload
box from phone menomry or external storage or fpt/web"
**Why:** No first-party way to add a .gguf already on the device
or to download from a non-HuggingFace URL.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ImportModelCard.kt`
  (new) — 4 paths:
  1. SAF device picker (`ActivityResultContracts.OpenDocument`)
  2. HTTPS URL with size probe
  3. Hugging Face `owner/repo` + filename
  4. (added in R-12) GitHub URL or `owner/repo/path` shorthand
  - Single progress bar + single error string (cleaner UX than
    the original 4-quadrant mess)
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  - `suspend fun importFromSaf(context, uri, onProgress): File?`
    copies the SAF URI into `filesDir/imported-models/<safe>.<ext>`
    so the grant can be safely revoked later
  - `data class HfResolved(url, approxSizeMb, sha256?)`
  - `suspend fun resolveHfFile(repo, fileName): HfResolved`
    calls `huggingface.co/api/models/<owner>/<repo>` then regex-
    // scans for the file entry to grab size
- `app/src/main/res/values/strings.xml`
  — 13 new `ra_import_card_*` strings
**Notes:** FTP intentionally NOT wired — Android ships no
first-party FTP client and pulling in `commons-net` would bloat
the APK by ~400KB. HTTPS URL path covers the same use case
(hosted file behind HTTP basic auth) — user can add custom
headers in Settings → Privacy.

### R-12 — Import codebases / models / agents / tools from GitHub
**Asked:** "add a feat where i can import codesbase, modeel,
agents, tools or other from github repo on models and tools section"
**Why:** Single source for everything the agent consumes — files,
agent configs, MCP servers, model weights, Python tools.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ImportModelCard.kt`
  — added Path 4 "From GitHub repo" (URL or `owner/repo/path`
  shorthand + optional ref input)
- `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`
  - `suspend fun resolveGithubFile(urlOrShorthand, ref): HfResolved`
  - `private fun translateGithubUrl(...)` — handles
    `github.com/.../blob/`, `github.com/.../raw/`, and passes
    `raw.githubusercontent.com` through unchanged
  - HEAD request for honest size label
**Notes:** Routes all file types through the same
`downloadFromUrl(...)` pipeline the curated catalog uses, so HF
auth + progress plumbing work for free. Supports `.gguf` /
`.onnx` / `.safetensors` → model; `.json` → agent/tool/MCP
config; `.py` / `.kt` / `.jar` / `.so` → tool source.

### R-13 — File management (use OSS, don't build from zero)
**Asked:** "add a storage or file managemt func, dont create it
from zero use opensourse no adds good quality pre-build app from
github"
**Why:** Building a file manager from scratch is wasted work —
Android already has DocumentsUI via Storage Access Framework.
**Status:** done (was already shipped — verified still there)
**Files touched (already in tree):**
- `app/src/main/kotlin/com/meshlit/ui/screens/FilesScreen.kt`
  — full file manager: Open / Share / Copy / Move / Rename /
  Delete / Mkdir + SAF `OpenDocumentTree` picker that persists
  the granted URI across reboots
  - All writes sandboxed to `filesDir` / `cacheDir` via
    `FileBrowserController.allowedRoots`
- `app/src/main/kotlin/com/meshlit/core/files/FileBrowserController.kt`
- `app/src/main/kotlin/com/meshlit/core/files/FileBrowserEntry.kt`
- `app/src/main/kotlin/com/meshlit/core/files/InternalStorageSource.kt`
**Notes:** Reachable from the drawer (hamburger → Files). No
new dependency, no native code, uses Android's bundled
DocumentsUI for the actual tree picker.

### R-14 — UI inspiration reference folder
**Asked:** "also i gave u many phone about ui design inspire on ui
improvemnnt folder, plz check and try to match them"
"folder name interface  ui reference"
**Why:** User wants the new screens to track the visual language
of the references (RunAnywhere dark + orange, ChatGPT minimal
home, Claude clean white chat, etc).
**Status:** in-progress (cross-cutting; applied to Agent today,
Models picker still pending)
**Files reviewed (no source changes, all under `Screenshots/`):**
- `Screenshots/UI suggestion/model ui part , i want this .jpeg`
  → **to apply next:** dark surface, "Top pick" / "Loaded" /
  "Smart" / "Thinks" / "Fast" badges on recommendation cards,
  "Get" with download glyph, "Add from Hugging Face" CTA row,
  file size + NPU/LlamaCPP chip
- `Screenshots/UI suggestion/interface  ui reference/byA7ri82...`
  → **applied:** Claude's white background + 3-pill row +
  image attachments + clean input bar at bottom — agent input
  bar already matches
- `Screenshots/UI suggestion/interface  ui reference/images (14).jpeg`
  → **applied:** ChatGPT home centered greeting + 4-action grid
  — `EmptyAgent` now mirrors this
- `Screenshots/UI suggestion/interface  ui reference/a4fc0e1664...`
  → **applied:** ChatGPT hero — 3D mascot illustration + 2-line
  title + 3 rounded action tiles below — matches `EmptyAgent`
- `Screenshots/runanywhere settings photos for examin/*`
  → dark + orange palette references, used in
  `app/src/main/kotlin/com/meshlit/ui/theme/DynamicTheme.kt`

### R-15 — Power / Battery / Thermal Monitor + Gauges
**Asked:** "now add a feat about monitor power consumtion, in
chharging mode or charger connected display dont trun off feat,
battry temp cpu temp, system tem, add opensourse plugin or sofware
recomanded, add a deicaded monitor section on tab or side manu"
"chart level graoh guege preffreable for monitoiring alongside text"
**Why:** No first-party power telemetry in Meshlit; user wants a
dedicated screen to monitor battery level, temp, voltage, current,
thermal status, and an option to keep the screen awake while
charging. Charts / gauges preferred over plain text so readings
are visible at a glance.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/power/PowerMonitorController.kt`
  (new) — thin wrapper around `BatteryManager` +
  `PowerManager`. Sticky broadcast subscription for battery state;
  thermal status polled at 2 Hz. Exposes `PowerSnapshot` data
  class plus a rolling `history: StateFlow<FloatArray>` sparkline
  stream (cap 60 samples).
- `app/src/main/kotlin/com/meshlit/ui/components/power/Gauges.kt`
  (new) — `CircularGauge` (donut + arc, sweep-gradient at mid
  range) and `MiniSparkline` (Canvas line + filled gradient).
  Custom rather than Vico — only 3 charts and we want full control
  of the visual language.
- `app/src/main/kotlin/com/meshlit/ui/screens/power/PowerMonitorScreen.kt`
  (new) — three side-by-side gauges (battery level %, battery
  temperature, thermal status 0–6), live text readouts card
  (voltage / current / technology / health / power-save /
  interactive / plug), sparkline history card, "Keep screen on
  while charging" toggle (applies
  `view.keepScreenOn = enabled && charging`, not the global
  `SCREEN_OFF_TIMEOUT`), and an OSS recommendations card linking
  to Castro (Apache-2) and Battery Historian (Apache-2).
- `app/src/main/kotlin/com/meshlit/ui/nav/TopLevelDestination.kt`
  — new `Power` enum entry (`Icons.Outlined.BatteryStd`,
  `route = "power"`). Added to `drawerOnly` so it's reachable
  from the hamburger menu (bottom bar is already at 9 items per
  the original comment).
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  — `shortLabel` `when` branch for `Power -> "Power"`.
- `app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt` — wires
  `PowerMonitorScreen(onBack = { navController.popBackStack() })`
  into the `TopLevelDestination.Power` branch of the NavHost.
- `app/src/main/kotlin/com/meshlit/ui/screens/ScreenStubs.kt` —
  stub title/body when-expressions extended for the new
  destination (kept exhaustive).
- `app/src/main/kotlin/com/meshlit/ui/screens/help/UiTourScreen.kt`
  — blurb + use-case strings for `Power` destination.
- `app/src/main/res/values/strings.xml` — added
  `screen_power` = "Power".
**Verified:** `:app:assembleDebug` ✅. Install on R9KN2009CZJ
✅. Power entry visible in drawer, navigation routes through.
**Notes / trade-offs:**
- CPU temperature cannot be read without root; the screen
  surfaces only the framework `PowerManager.THERMAL_STATUS_*`
  level (0..6) and the battery temperature (which is the
  publicly-accessible pack thermistor). Documented in
  controller kdoc.
- `PowerManager.getThermalHeadroom(int)` was originally going
  to be exposed in the snapshot but is annotated-only on older
  stubs and tripped the compiler — dropped from the data class.
  No UI field referenced it.
- Gauge colour thresholds (battery temp): <30°C cool, 30–40°C
  normal, 40–45°C warm, ≥45°C hot. Thermal status: NONE..LIGHT
  primary, MODERATE tertiary, SEVERE+ error.
- OSS recommendations: Castro (github.com/nicktehrany/Castro,
  Apache-2) for on-device battery stats; Battery Historian
  (github.com/google/battery-historian, Apache-2) for the
  `batterystats.txt` → chart workflow.

## THIS SESSION (continued) — 2026-08-07

### R-17 — Icon-only Copy / Save / Share / Export + FlowRow landscape wrap
**Asked:** "fix copy save share exprt orientation also use just
button/icon, no need txt lebel"
**Why:** The action sheet that pops up on long-press of an agent
message (and the inline toolbar row beneath each LLM bubble) showed
text labels under icons that got clipped in landscape, and the
horizontal `Row` with `SpaceBetween` packed four wide text+icon
buttons into a width that couldn't hold them on a phone in landscape.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/components/LlmOutputSideMenu.kt`
  — long-press side menu: replaced `Row` + `TextButton` with
  `FlowRow` + `IconButton` (icons: `ContentCopy`, `Save`, `IosShare`,
  `Share`). `ExperimentalLayoutApi` opt-in. No text labels.
- `app/src/main/kotlin/com/meshlit/ui/components/LlmOutputActions.kt`
  — inline toolbar underneath each LLM bubble: same `FlowRow` +
  `IconButton` pattern, 20 dp icons in 32 dp click targets.
- Regenerate / Like / Dislike kept as text+icon on the rail above
  (they have their own affordance and benefit from the visible label).
**Verified:** `adb shell uiautomator dump` shows only
`content-desc="Copy"`, `content-desc="Save"`, `content-desc="Share"`
nodes — no visible text labels. Rotated to landscape and back; the
icon row wraps cleanly.

### R-18 — Models hub: visible download progress + Pause/Resume/Stop/Delete for SDK path
**Asked:** "cant understand where model downloading or not also
after download get button state dont change, need download start
pause icon button also delete buton shoud appear after download and
replace with get button"
**Why:** Both download paths (alt-import okHttp, SDK/VM
`downloadModelById`) had a `CircularProgressIndicator` that gave
the user no information about whether bytes were flowing, no way
to pause, no way to stop, and the "Get" button never flipped to
"Delete" after a download completed.
**Status:** done (pending end-to-end phone verification on a real
download)
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelSelectionViewModel.kt`
  - `ModelSelectionState` gained `busyBytesDownloaded: Long`,
    `busyTotalBytes: Long`, `busyState: DownloadStatus`,
    `busyJob: Job?`
  - `download(entry)` rewritten: collects `DownloadProgressView`
    bytes into state, captures `Job` into `busyJob`, flips
    `busyState` on `CancellationException` → Paused, on other
    Throwable → Failed.
  - `cancelDownload(modelId)` flips to Paused first, then cancels
    the job so the catch block doesn't override it.
  - `stopDownload(modelId)` new: cancel + call
    `runAnywhereEngine.deleteDownloadedModel(id)` + flip to Idle.
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelTrailingAction.kt`
  - 8 new params: `busyState`, `busyBytesDownloaded`, `busyTotalBytes`,
    `onBusyPause`, `onBusyResume`, `onBusyStop`, `onBusyRetry`,
    `onBusyDelete`.
  - New `DownloadProgressInline` composable: `LinearProgressIndicator`
    + "X MB / Y MB · Z %" `Text` + Pause + Stop `IconButton`s.
  - New `RetryDeleteControls` composable: Refresh (primary tint) +
    Delete (error tint) icons.
  - Promoted Delete `IconButton` to the trailing slot for
    `isReady && !isCurrent` rows (replaces the small text button
    that used to live below the row).
  - The discharge state machine:
    ```
    isCurrent                          → Loaded pill
    isReady && !isCurrent              → Delete icon
    busyState == Running               → DownloadProgressInline
    busyState == Paused                → ResumeStopControls
    busyState == Failed                → RetryDeleteControls
    requiresHfAuth && !isReady         → Set token
    otherwise                          → Get
    ```
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelsScreen.kt`
  - Updated `ModelRowCard` signature with the 8 new params (defaults
    so the alt-import path call sites didn't need to change).
  - RunAnywhere catalog section wires the new params from
    `state.busyModelId == entry.id` plus `state.busyState` etc.
  - The body-level TextButton("Delete") is now gated on
    `entry.source == ALTERNATIVE_IMPORT` only — the SDK path renders
    the Delete icon in the trailing slot.
  - `app/src/main/res/values/strings.xml` — small label update for
    the bundled-model card strings.
**Verified on device:** installed APK, opened Models hub, the new
trailing slot renders correctly. End-to-end Pause/Resume/Stop/Delete
test on a real download pending the user's manual verification.

### R-19 — Bundled-model card shows wrong name (Qwen2.5-1.5B vs smollm2-360m)
**Asked:** (paraphrased from the on-disk-vs-UI mismatch flag)
**Why:** The bundled-model card was hard-coded to display
"Qwen2.5-1.5B-Instruct" but the actual bundled asset had been
swapped to `smollm2-360m-instruct-q8_0.gguf` (368 MB). The card
lied about what was on the device.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/ui/screens/settings/BundledModelCard.kt`
  - `prettyNameFromFile(file)` — strips `.gguf`, splits on the first
    dash, title-cases the family prefix, replaces remaining dashes
    with spaces.
  - `subtitleFromFile(file)` — picks a license hint from the family
    (SmolLM2/Qwen → Apache 2.0, Phi/DeepSeek → MIT, Llama/CodeLlama →
    Llama community, Gemma → Gemma terms, else → open-weight), the
    quant from the suffix (`q8_0`, `q4_k_m`, etc.), and the on-disk
    size in MB.
- `app/src/main/res/values/strings.xml`
  - `models_bundled_name` → "Bundled model" with explanatory comment
  - `models_bundled_quant` → "Read from disk"
**Verified on device:** bundled card title reads "Smollm2 · 360m
instruct q8 0", subtitle reads "Q8_0 · 368 MB · Apache 2.0".

### R-20 — Auto Pilot: watchdog + observations + auto-repair (consent)
**Asked:** "where is auto pilot button? implement auto pilot feature,
model should identify any problem throughout app"
"1 and 2 both" (combine option 1: watchdog + suggestions only, AND
option 2: auto-repair with consent)
**Why:** The "Autopilot" tab in the top nav bar was a placeholder.
User wants app-wide problem detection plus optional auto-repair for
common stalls. Entry point in both top bar AND drawer.
**Status:** done
**Files touched:**
- `core-autopilot/build.gradle.kts` (new module — leaf, depends
  only on `:core-common`)
- `core-autopilot/src/main/kotlin/com/meshlit/core/autopilot/AutoPilotEngine.kt`
  — central watchdog. Two switches (Observe + Auto-repair, both
  default OFF), `observe(...)` emits deduped observations,
  `runRepair(...)` honours the auto-repair gate + a
  `RepairAction` whitelist, `dismiss(...)` removes the entry,
  audit trail capped at 50 entries.
- `core-autopilot/src/main/kotlin/com/meshlit/core/autopilot/Observation.kt`
  — `AutoPilotObservation` carrier, `ObservationKind` enum
  (`DOWNLOAD_STALLED`, `DOWNLOAD_FAILED`, `MODEL_LOAD_ERROR`,
  `MEMORY_GATEWAY_UNREACHABLE`, `AGENT_REPEATED_FAILURE`,
  `CLUSTER_NODE_LOST`, `DISK_PRESSURE_LOW`), `Severity` enum
  (INFO / WARNING / CRITICAL), `RepairAction` sealed interface
  (`ClearPartial`, `RestartAgent`, `ReloadGatewayConfig`,
  `ReconnectCloudTerminal`, `FreeDiskSpace`, `Dismiss`), and
  `AuditEntry` / `RepairResult` types.
- `core-autopilot/src/main/kotlin/com/meshlit/core/autopilot/AutoPilotWatchers.kt`
  — `DownloadStallWatcher`, `DiskPressureWatcher`, and
  `ClusterPeerWatcher` coroutine helpers.
- `core-autopilot/src/test/kotlin/com/meshlit/core/autopilot/AutoPilotEngineTest.kt`
  — 10 tests covering dedupe, dismiss, repair force/skipped paths,
  audit-trail cap, observe-toggle drops active observations, and
  distinct kinds produce distinct entries. All green.
- `app/src/main/kotlin/com/meshlit/settings/SettingsRepository.kt`
  — two new persisted keys: `autopilot.observe_enabled` (default
  false) and `autopilot.auto_repair_enabled` (default false),
  plus flows + setters + sync accessors.
- `app/src/main/kotlin/com/meshlit/MeshlitApplication.kt`
  — singleton `autoPilotEngine`, `installAutoPilotWatchers()`
  boots `DownloadStallWatcher` + `DiskPressureWatcher` on the
  app scope, `syncAutoPilotSwitches()` mirrors persisted values
  into the engine. Both called from `onCreate`.
- `app/src/main/kotlin/com/meshlit/ui/screens/AutoPilotScreen.kt`
  — top app bar + two master switches + active-observations list
  (severity icon + title + detail + Fix / Dismiss buttons) +
  audit-trail list. Empty-state card when Observe is off.
- `app/src/main/kotlin/com/meshlit/ui/nav/TopLevelDestination.kt`
  — new `AutoPilot` enum entry (route `autopilot`, label
  `autopilot_title`, `Icons.Outlined.AutoMode`). Added to
  `drawerOnly`.
- `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
  — `shortLabel` exhaustive `when` extended with
  `TopLevelDestination.AutoPilot -> "Auto Pilot"`.
- `app/src/main/kotlin/com/meshlit/ui/screens/ScreenStubs.kt` —
  `titleResFor` + `bodyResFor` extended.
- `app/src/main/kotlin/com/meshlit/ui/screens/help/UiTourScreen.kt`
  — blurb + use-case strings.
- `app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt` — wired the
  `composable(...)` entry that hosts `AutoPilotScreen` plus the
  `Agent` destination now passes `onOpenAutoPilot` lambda.
- `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`
  — `CompactToolbar` grew `onOpenAutoPilot` + a new
  `AutoMode` `ModeIconChip` at the right edge with a red-dot
  overlay when any CRITICAL observation exists.
- `app/src/main/res/values/strings.xml` — 11 `autopilot_*` strings.
- `settings.gradle.kts` + `app/build.gradle.kts` — module
  registration.
- `.claude/skills/autopilot-and-models-hub.md` (new) — puku-cli
  skill for one-command re-invocation.
**Verified:** `:core-autopilot:testDebugUnitTest` 10/10 green;
`:app:testDebugUnitTest` green; `:app:assembleDebug` green.
APK builds and installs.

### R-21 — Bundle Qwen2.5-Coder-1.5B as Android+networking knowledge model
**Asked:** "please add a model bundel which is good knowlge about OS
like android and networking"
**Why:** The bundled model is SmolLM2-360M-Instruct Q8_0 (general
purpose, weak on Android/networking). For a phone that spends most
of its life on agent / chat use cases, a domain-focused bundled
model is far more useful as an offline fallback.
**Status:** pending (C0 of the Models-hub PR — first-run download
of the Qwen2.5-Coder-1.5B-Instruct Q4_K_M GGUF from the official
HF mirror).

### R-22 — Auto Pilot persistence + real RepairExecutor (C7)
**Asked:** (implicit — follow-up to R-20) — the audit trail was
in-memory only, and the "Fix" buttons declared at the UI level but
didn't actually run anything.
**Why:** Once the user sees the watchdog emit a real observation
(R-20), the next natural ask is "and the next time the user
relaunches the app, do they still see the history?" + "when I tap
Fix, does it actually repair?" Both need host-side hooks.
**Status:** done
**Files touched:**
- `app/src/main/kotlin/com/meshlit/settings/SettingsRepository.kt` —
  `autoPilotAuditTrailFlow` + `setAutoPilotAuditTrail(...)` +
  `autoPilotAuditTrailNow()`. Stored as JSON-encoded list under the
  existing `meshlit_prefs` DataStore (capped at 200 entries on
  write).
- `app/src/main/kotlin/com/meshlit/MeshlitApplication.kt` —
  `installAutoPilotRepairExecutor()` (exhaustive `when` over
  `RepairAction`: ClearPartial → `runAnywhereEngine.deleteDownloadedModel`,
  RestartAgent → `coordinator.loadModel(currentModelPath)`,
  ReloadGatewayConfig → `memoryGatewayClient.health()`,
  ReconnectCloudTerminal → `cloudCoordinator.disconnect` +
  `McpEvent.Disconnected`, FreeDiskSpace → largest candidate delete,
  Dismiss → no-op). `syncAutoPilotAuditTrail()` mirrors the engine
  flow into DataStore via a single coroutine. `hydrateAutoPilotAuditTrail()`
  reads persisted entries and logs each so the user sees the
  history in `LogScreen`. All three hooked from `onCreate`.
- `core-autopilot/build.gradle.kts` — added
  `alias(libs.plugins.kotlin.serialization)` so the generated
  `AuditEntry.serializer()` is visible to `:app`.
- `app/.../PROGRESS.md` — C7 section updated.

**Verification:** `./gradlew :core-autopilot:testDebugUnitTest
:app:assembleDebug` green; 10/10 engine unit tests pass.

### R-23 — Distributed multi-device LoRA training (Phase 11.0/11.1)
**Asked:** "can u add... for large single phone, or cluster, or mix device,
with llmodel sherding acrooss devices for better load dristributation, add
dedicated model traing manu and use all features, multuple catagory devices
can be collab to train large lllm with memory disk, and process distribution"
+ "not ony use phone but also laptop, desktop server etc can join to the
hive / cluster local or on internet to other device runnging apps, yes auto"
+ "all 3, user choose, by default peer-peer" + "refactor all pros on cons
or difficullties and make app perfect for stable use long run" + "alsoo
logic and wiring" + "if alll go then start building the planned codes now
all in autopilot mode" + "update plan and cladue doc md".

**Why:** Meshlit's existing `ClusterTrainer` did single-strategy ring
all-reduce. The user asked for (a) **three strategies** selectable in
the UI with P2P default, (b) **any device class** (phone / laptop /
desktop / server / Pi) on LAN or internet, (c) **pros/cons as
design-time data** so the long-run trade-offs are visible, and
(d) **logic + wiring as code** so the next agent doesn't have to
reconstruct the call graphs.

**Status:** Phase 11.0/11.1 done, 11.2/11.3/11.4 pending.

**Files added (`:core-training`):**
- `config/DistributedConfig.kt` + `DistributedConfigLoader.kt`
- `averaging/Averager.kt` + `P2pRingAverager.kt` + `DiLoCoAverager.kt`
  + `AccelerateDelegateAverager.kt` + `StrategyDispatcher.kt`
- `averaging/NaNGuard.kt`
- `ThermalGuard.kt`
- `MeshlitEventTraining.kt` (14 `TrainingEvent` subtypes)
- `plan/ModelSpec.kt` + `ShardingPlan.kt` + `ShardingPlanner.kt`
- `durability/ResumeToken.kt` + `TrainingResumeService.kt`
- `registry/ClusterTrainerRegistry.kt`

**Files modified:**
- `app/.../ui/screens/training/TrainingViewModel.kt` — strategy /
  peerCount / resumableJobs state + `joinHive()`,
  `refreshHiveState()`, `currentConfig()`, `activeAveragerKind()`.
- `app/.../ui/screens/training/TrainingScreen.kt` — `StrategyPicker`,
  `StrategyChip`, `HiveStatusBlock` composables.
- `app/src/main/res/values/strings.xml` — 14 `training_*` strings.
- `app/CLAUDE.md` — `PHASE` line updated.
- `app/BUILD_GUIDE.md` — new §12 "Distributed multi-device cooperative
  training" added.
- `PROGRESS.md` — Phase 11.0/11.1 entry.
- `TODO.md` — "Cooperative training across nodes" item marked done.
- `/Users/code/.puku-cli/plans/distributed-hatching-bird.md` — new
  §10 "Implementation status" journal.

**Verification:** `./gradlew :core-training:compileDebugKotlin
:app:compileDebugKotlin` → **BUILD SUCCESSFUL in 5s**. Existing
`ClusterTrainerTest` surface unchanged. No new wire surface.

**Notes:**
- Plan file went through 3 revisions. v1 targeted the wrong repo
  (Soup CLI). v2 reinvented primitives that already exist. v3 (the
  shipped one) reuses `HostElection`, `ClusterCoordinator`,
  `CapabilitySnapshot`, `CapabilityTier`, `ClusterRole`,
  `CapabilityProbe`, `ResourceSnapshot`, `ThermalHeadroom` and adds
  only the minimum new surface.
- `MeshlitEvent_Training` lives in `core-training` (not
  `core-observability`) so the strategy dispatcher can emit without a
  hard dependency. The autoPilot audit trail (R-22) subscribes to the
  `events: SharedFlow<TrainingEvent>` and re-emits as
  `MeshlitEvent` subtypes — the existing LogScreen sees them with
  no new wiring.
- The synthetic gradient path is intentional in v0 — `lastLoss` and
  `lastGradientMagnitude` tick per step so the user can see the
  averaging loop without a real autograd runtime on phones. Real
  autograd lands when a phone-friendly tensor runtime exists.

---

- **#26** — Investigate SmolLM2 stuck at 0% download (likely
  flaky network; not yet root-caused)
- **Bottom nav LazyRow taps don't register** on the connected
  phone (R9KN2009CZJ). Reproduces after Agent redesign build.
  Pre-existing LazyRow hit-testing bug. Fix: try
  `Modifier.requiredWidth` instead of `width`, or switch the
  bottom bar to M3 `NavigationBar` so hit-testing is guaranteed.
- **Apply UI redesign to Models picker** per the
  `model ui part , i want this .jpeg` reference (Top pick /
  Loaded / Smart badges, Get button, Add from HF CTA row).
  Currently still using the RaListCard grid — needs the
  recommendation-list treatment.
- **VoiceEngine.startCapture IllegalStateException** — surfaced
  earlier, fix not landed yet
- **Permission section showing only 2 permissions** —
  investigated but not landed
- **SmolLM2 stuck at 0%** — also surfaced earlier
- **"Supported formats" section bottom layout fix** — surfaced
  earlier

---

## Rolling follow-ups (always open)

- **#26** — Investigate SmolLM2 stuck at 0% download (likely
  flaky network; not yet root-caused)
- **Bottom nav LazyRow taps don't register** on the connected
  phone (R9KN2009CZJ). Reproduces after Agent redesign build.
  Pre-existing LazyRow hit-testing bug. Fix: try
  `Modifier.requiredWidth` instead of `width`, or switch the
  bottom bar to M3 `NavigationBar` so hit-testing is guaranteed.
- **Apply UI redesign to Models picker** per the
  `model ui part , i want this .jpeg` reference (Top pick /
  Loaded / Smart badges, Get button, Add from HF CTA row).
  Currently still using the RaListCard grid — needs the
  recommendation-list treatment.
- **VoiceEngine.startCapture IllegalStateException** — surfaced
  earlier, fix not landed yet
- **Permission section showing only 2 permissions** —
  investigated but not landed
- **SmolLM2 stuck at 0%** — also surfaced earlier
- **"Supported formats" section bottom layout fix** — surfaced
  earlier

---

## Format conventions

- `R-XX` = request number (chronological across sessions)
- `Status` values: `done` / `in-progress` / `pending` / `deferred` / `wontfix`
- File paths are relative to the repo root
- "Surface" = first observed in this session via screenshots
- Verification: `/tmp/final2.png`, `/tmp/final_post.png` (Devices
  screen after R-09 + R-10)