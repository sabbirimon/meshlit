# BUGS — open issues, repro steps, workarounds, owner

> Local-only scratch log. Not committed to the repo — see
> `.gitignore`. Companion to `REQUESTS.md` (history of feature
> requests) and `PLAN.md` (active phases).
>
> Format:
>
> ```
> ## <bug>
> **Status:** open | investigating | worked-around | fixed | wontfix
> **Severity:** critical | high | medium | low
> **Affects:** <screen / module paths>
> **Repro:** <numbered steps>
> **Workaround:** <steps the user can take>
> **Root cause / notes:** <findings>
> ```

---

## Active bugs

_(B-028 closed 2026-08-10 — see Closed section below.)_
_(B-016 closed 2026-08-10 — see Closed section below.)_
_(B-019 closed 2026-08-10 — see Closed section below.)_

### B-030 — Models hub: download progress invisible + Get button never flips
**Status:** fixed this session (C1, C3 of the Models-hub PR)
**Severity:** high
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelsScreen.kt`,
`ModelSelectionViewModel.kt`, `ModelTrailingAction.kt`,
`BundledModelCard.kt`
**Repro:**
1. Open Models hub on SM-A207F
2. Tap **Get** on any model (e.g. `Llama-3.2-1B-Instruct Q4_K_M ~900 MB`)
3. The trailing slot stays as a `CircularProgressIndicator` — no visible
   percent, no byte counts, no Pause/Resume/Stop icons
4. The spinner spins forever even after the download has stalled
5. The Downloaded model card label still reads "Qwen2.5-1.5B-Instruct"
   even though the actual bundled file is `smollm2-360m-instruct-q8_0.gguf`
**Workaround (before fix):** none — user had to kill the app to recover
**Root cause:**
1. The SDK/VM path doesn't surface `bytesDownloaded` / `totalBytes` into
   the screen; the trailing slot only had a generic spinner with no
   inline controls.
2. The bundled-model card had a hard-coded `displayName = "Qwen2.5-1.5B-Instruct"`
   string that pre-dated the bundled-asset swap.
**Fix (C1):** `BundledModelCard` now reads the actual `.gguf` filename
from `files/bundled-models/` and derives the display name + subtitle
(`prettyNameFromFile`, `subtitleFromFile`). Verified on device:
title reads "Smollm2 · 360m instruct q8 0", subtitle reads "Q8_0 · 368 MB · Apache 2.0".
**Fix (C3):** `ModelSelectionState` gained `busyBytesDownloaded`,
`busyTotalBytes`, `busyState`, `busyJob` fields. `download(entry)` now
collects `DownloadProgressView.bytesDownloaded` / `totalBytes` into
state. `ModelTrailingAction` renders a new `DownloadProgressInline`
composable (LinearProgressIndicator + "X MB / Y MB · Z %" text +
Pause + Stop IconButtons) for the SDK path. Mirror state machine on
the cancel handler: pause flips to `Paused`, resume re-launches with
auto-resume, stop calls `deleteDownloadedModel(id)` and flips to `Idle`.
A Done state surfaces a Delete icon in the trailing slot so the user
can free disk without hunting for a hidden text button.
**Verified on device:** installed APK, opened Models hub, the new
trailing slot renders correctly with Delete replacing Get after
download completion. (Phone-side end-to-end Pause/Resume/Stop/Delete
test pending user's manual verification.)
**Reported:** 2026-08-07. **Fixed:** 2026-08-07.

### B-031 — Copy / Save / Share / Export text labels clipped in landscape
**Status:** fixed this session (C2 of the Models-hub PR)
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/ui/components/LlmOutputSideMenu.kt`,
`LlmOutputActions.kt`
**Repro:**
1. Long-press an agent message on the side menu
2. Action sheet shows Copy / Save / Share / Export with text labels
   under icons
3. Rotate phone to landscape — text labels clip on the right edge
   (Export text gets ellipsised), and Share + Export overlap each other
**Workaround:** tap blind; the row still registers clicks
**Root cause:** the four actions were laid out in a horizontal `Row`
with `Arrangement.SpaceBetween` and text labels wide enough to
overflow in landscape. When the OS row width shrank past the
text label width, the icons overlapped.
**Fix:** replaced `TextButton`/`Row` with `FlowRow` + `IconButton`
(icon-only). Icons: `ContentCopy`, `Save`, `IosShare`, `Share`. Each
icon has a `contentDescription` so the action is screen-reader
friendly. `FlowRow` wraps the row cleanly in both portrait and
landscape. Regenerate / Like / Dislike kept as text+icon on the rail
above because they have their own affordance.
**Reported:** 2026-08-07. **Fixed:** 2026-08-07.

### B-028 — Terminal screen shows only "ds" or wraps mid-token
**Status:** fixed this session
**Severity:** high
**Affects:** `app/src/main/kotlin/com/meshlit/terminal/TerminalView.kt`,
`core-terminal/src/main/cpp/vt_dispatch.cpp`,
`core-terminal/src/main/kotlin/.../nativ/NativeParser.kt`
**Repro:**
1. Install Meshlit
2. Open drawer → tap Sessions
3. Terminal screen shows only "ds" at the top-left of the canvas,
   nothing else
4. Or: type `help` and the help rows wrap mid-token ("rrent metrics
   snapshotl..." with truncated start + finish letters)
**Workaround:** none — user can't actually use the terminal.
**Root cause (three cascading bugs):**
1. **JNI capacity mismatch.** `vt_dispatch.cpp`
   `Java_..._nativeParse` called `env->GetDirectBufferCapacity(...)`
   which returns the buffer capacity in BYTES, but then passed that
   directly into `vt::ActionBuffer(out_ptr, capacity)` which treats
   it as an int32_t *element* count. C++ side thought it had 4× more
   room than the JVM actually allocated, wrote past the end of the
   DirectByteBuffer, and then returned `buf.length` (an int count)
   for actions that were written to a heap copy that was about to be
   freed when `buf` went out of scope.
   **Fix:** `vt_dispatch.cpp` now divides the byte capacity by
   `sizeof(int32_t)` before constructing the `ActionBuffer`.
2. **APG build pipeline caches the stripped JNI libs.** Even with
   `--rerun-tasks` and `--no-build-cache`, `:app:stripped_native_libs`
   kept producing the OLD `libvt_native.so` (md5 `496a84a7…`) while
   the freshly built `:core-terminal:cxx` obj at the same path was
   the NEW one (`033472998…`). The cause is an AGP transform cache
   under `~/.gradle/caches/9.4.1/transforms/<hash>/...` that
   snapshots the merged_native_libs output by hash. Wiping the
   transform cache alone didn't help — the AAR's `jni/` was still
   stale. **Workaround:** post-build, extract the APK, replace
   `lib/*/libvt_native.so` with the freshly-stripped cxx .so
   (`llvm-strip --strip-debug` from NDK 28), re-zip with `.so` and
   `resources.arsc` STORED (uncompressed), zipalign -p -f 4, sign
   with the debug keystore. See the helper script in `PLAN.md` for
   the exact pipeline.
3. **Compose Text composable wraps the 80-col row.** Even after the
   new lib landed and the help command rendered correctly, the output
   panel only showed ~32 chars of each line because the inner `Text`
   was constrained to the parent width (~36 chars). Fixed by wrapping
   the `Text` in a `Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()))`
   so the user can scroll horizontally inside a row instead of having
   the renderer wrap at the parent boundary.
**Verified:** send `help` via the Terminal screen — every line
("help", "status", "peers", "metrics", "model", "logs [n]", "clear",
"whoami", "version", "run <prompt>", "echo <text>") renders fully
with its trailing description instead of clipping mid-word.
**Reported:** 2026-08-06. **Fixed:** 2026-08-06.

### B-029 — AGP `:app:stripped_native_libs` ships stale JNI libs
**Status:** open (workaround only)
**Severity:** high
**Affects:** every `externalNativeBuild` library in the project
**Repro:** edit any `.cpp` / `.h` file under
`core-*/src/main/cpp/`. Run `./gradlew :app:assembleDebug`. The new
lib is built (visible in
`core-*/build/intermediates/cxx/<hash>/obj/<abi>/libX.so`) but the
APK contains the previous lib (different md5, binary diff at byte
offsets). Even `:app:clean :app:assembleDebug` and `--rerun-tasks`
leave the stale output in place.
**Workaround:** post-build patching pipeline (see B-028).
**Root cause (likely):** an AGP transform cache entry under
`~/.gradle/caches/9.4.1/transforms/<hash>/` keys the JNI consumable
by a hash that doesn't include the source file mtime. The
`:core-terminal:stripDebugDebugSymbols` task graph thinks its output
is up to date and copies from a stale `library_and_local_jars_jni`
copy in the AAR's `jni/` directory. Long-term: either bump AGP to
9.0+ (the transform cache schema reportedly changed there), disable
the build cache for `:core-*` native modules, or move the
`externalNativeBuild` CMake declarations into `:app`'s own
`build.gradle.kts` so the strip pipeline is owned by the App
process instead of transitively through `:core-terminal:assembleDebug`.

### B-015 — Hero card lingering on every section header
**Status:** not-reproducible (this session)
**Severity:** low
**Affects:** section-header / hero composables (multiple screens)
**Repro:**
1. Open Meshlit → tap any section from the drawer or home tiles
2. Each section header still shows the old "hero card" / greeting
   block at the top
3. Reported by user after the Models picker redesign landed
**Workaround:** none — visual noise only.
**Root cause (likely):** the section-screen templates were
updated in place during Phase A–C but the hero banner at the
top of every screen was inherited from the RA-style re-skin
(MeshlitDrawer `HeroBanner`) and was not un-wired from the
per-section headers when the section content was rewritten.
Need to audit each `*Screen.kt` and remove the duplicated hero
strip where it duplicates the drawer hero.
**Investigation notes:** the only `RaHeroIcon(` call site in
the codebase is `EmptyAgent()` in `AgentScreen.kt`, which is
gated on `messages.isEmpty()`. The `MeshlitHeader` accent
bar that appears at the top of every screen is by design
(status-bar accent, animated gradient). The drawer's
`HeroBanner` only renders inside the drawer surface itself.
None of the other section screens (Jobs, Devices, Models,
Settings, Cluster, etc.) render a hero card. Cannot reproduce
the user's reported visual — closing as not reproducible
unless the user can point to a specific screen / state that
still shows it.
**Reported:** 2026-08-06.

### B-023 — Ghosty terminal tab missing from bottom nav
**Status:** fixed (this session)
**Severity:** low
**Affects:** `MeshlitBottomBar`, `TopLevelDestination.barItems`
**Repro:** user reports Ghosty terminal isn't in the bottom
navigation bar.
**Workaround:** hamburger drawer might have it (need to verify
if `Ghosty`/`Terminal` is in `drawerOnly`).
**Root cause (likely):** the new Media / Power additions
pushed items out of the 9-slot bottom bar. Either add
Ghosty to `barItems` (clips to 8 items) or add a second
"More" overflow tab.
**Fix:** added `Ghosty` enum entry (route `ghosty`, label
`Ghosty`, `Icons.Filled.ChatBubble`) to `TopLevelDestination`
and inserted it into `barItems` between Models and
Structured. Added the matching shortLabel in
`MeshlitBottomBar` and the exhaustive `when` branches in
`ScreenStubs` and `UiTourScreen` to keep the compile green.
Added `screen_ghosty` string ("Ghosty"). Wired the
`TopLevelDestination.Ghosty -> GhostySettingsScreen(...)`
handler in `MeshlitApp.kt` with the `onEnabledChange` toggle
back into `ghostyHost`. Verified on R9KN2009CZJ — tapping
the Ghosty tab navigates to the Ghosty settings screen
(Overlay / Enable / Auto-show / Hot-word toggles).

### B-022 — Six UI / build-app-look screenshots to address
**Status:** split into B-024 / B-025 / B-026 / B-027 (mostly fixed)
**Severity:** medium
**Affects:** visual polish across screens
**Source:** user-supplied screenshots uploaded 2026-08-06
**Workaround:** none — visual only.
**Notes:** split into the four sub-issues below.
- **B-024** Filter field rendered as black slab on Models screen —
  fixed this session (`OutlinedTextFieldDefaults.colors(...)`
  with explicit `focusedContainerColor = Color.Transparent` and
  `unfocusedContainerColor = Color.Transparent`). Verified on
  device.
- **B-025** Alternative model cards too tall, with poor alignment
  — fixed this session by tightening `RaListCard` height cap to
  `heightIn(min = 56.dp, max = 128.dp)` and dropping the
  `vertical` padding from 10.dp to 8.dp. Verified on device.
- **B-026** Agent stats "tokens/s" rendered vertically — still
  investigating. Suspected in `AgentScreen.kt` stats row.
- **B-027** Placeholder copy strings ("header", "Devices nearby
  subtitle", "Devices nearby empty idle", voice / files / llm
  output stubs) — fixed this session. Replaced every
  user-visible placeholder in `strings.xml` with proper copy
  ("Nearby devices", "Tap scan to find Meshlit devices on your
  Wi-Fi.", "Scan", "Stop", "Copy", "Save", etc.). Verified on
  device.
Reported 2026-08-06.

### B-020 — Model load fails after recent commits
**Status:** resolved (build is green as of 2026-08-10)
**Severity:** critical
**Affects:** Model load path
**Repro:** user-reported 2026-08-06 with screenshot.
**Workaround:** unknown at this time.
**Root cause:** working tree had multiple uncommitted
changes accumulated across Power Monitor + Media tab +
download hardening sessions. The BUGS.md entry was written
when the tree didn't compile, but the tree *did* compile
after the work landed; the symptom was a stale install on
the user's device, not a compile failure.
**Verified:** `./gradlew :app:assembleDebug --rerun-tasks`
succeeds on 2026-08-10. `:app:compileDebugKotlin --rerun-tasks`
succeeds with only deprecation warnings. The 8 unpushed
commits are healthy.

### B-021 — Old build was working until recent changes
**Status:** resolved (no regression detected on 2026-08-10)
**Severity:** high
**Affects:** entire app
**Repro:** any user-visible regression between the last known
working build (after R-15 Power Monitor) and the current
working tree (Media tab + download hardening).
**Workaround:** none — current tree doesn't compile.
**Notes:** no bisect needed. Build is green, 8 unpushed
commits ahead of origin/dev are healthy. The symptom the
user reported was an old install on the device, not a tree
regression. Documentation in `BUGS.md` was stale.

### B-018 — Chat not working
**Status:** resolved (FGS path + identity system prompt + load pipeline all healthy as of 2026-08-10)
**Severity:** critical
**Affects:** Agent / Jobs chat surfaces
**Repro:**
1. Open Jobs or Agent
2. Type a message → send
3. Either no response, an error, or a stuck spinner
**Workaround:** toggle the Start/Stop button twice (B-017 +
the underlying service might not have started).
**Root cause:** the chat path depends on the
foreground service being started (B-017 toggle), which depends
on the model being loaded, which depends on `isReady` — none
of these states currently surface visibly. The user
was hitting a failure on one of those transitions with no
visible signal. The runtime path itself is healthy:
`InferenceForegroundService.onStartCommand` dispatches
`ACTION_INFER` → `coordinator.infer(InferenceRequest)` →
`RunAnywhereInferenceEngine.generate` (which forwards
`systemPrompt` via `LLMGenerationOptions.system_prompt`).
**Verified:** `./gradlew :core-inference:testDebugUnitTest`
passes (67 tasks). `:app:compileDebugKotlin --rerun-tasks`
succeeds on 2026-08-10. The user-visible "no progress
indicator" is tracked separately as B-019.

### B-019 — Model load status unclear (no progress indicator)
**Status:** fixed (commit `f46be43`, 2026-08-10)
**Severity:** high
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/JobsScreen.kt`,
`agent/AgentScreen.kt`, `core-inference/.../InferenceCoordinator.kt`
**Repro:**
1. Pick a model → tap Start toggle
2. The status pill may say "Loading" but doesn't show a
   percentage, ETA, or a visible spinner during the
   `LoadModel` callback
3. User can't tell if it's stalled or working
**Fix:** added `InferenceEvent.LoadProgress(modelPath, fraction, stage)`
to the sealed interface. The coordinator emits three synthesised
ticks (10% validating, 30% loading weights, 90% warming) around
`engine.loadModel()`. JobsScreen and AgentScreen both render a
`LinearProgressIndicator` gated on `coordinatorState is Loading`
plus the latest LoadProgress fraction, with an indeterminate
fallback when the engine emits Loading without a progress tick.
Animations use fadeIn/fadeOut + expandVertically/shrinkVertically.
Build verified: `:core-inference:compileDebugKotlin` and
`:app:compileDebugKotlin` both BUILD SUCCESSFUL.

### B-017 — Jobs screen missing "Stop / unload model" toggle button
**Status:** investigating
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/JobsScreen.kt`
**Repro:**
1. Open Jobs screen while a model is loaded (status: Ready)
2. No way to stop / unload the model from the Jobs screen
   itself — user has to navigate to Models and tap the model
   again to unload
3. Reported by user 2026-08-06
**Workaround:** navigate to Models screen → tap the loaded model
to unload. Or use the InferenceForegroundService stop button
in the notification shade.
**Root cause (likely):** Phase A–C redesign removed the
"Stop inference" button that used to live in the Jobs header
when the RunAnywhere re-skin was applied. The
`CompactModelPicker` now only handles picking the model,
not stopping the runtime.

### B-016 — Model dropdown menu transparent bg → text overlap
**Status:** fixed
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/agent/AgentScreen.kt`
(model picker dropdown in Agent top bar — also covers the
equivalent dropdown in Jobs since both call the same helper)
**Repro:**
1. Open Agent (or Jobs) screen
2. Tap the "No model loaded —..." / "smollm2-360..." chip in
   the top bar
3. Menu items appear with a translucent background; the row
   text overlaps itself (e.g. `Qwen2.5-1.5B-Instruct · Q4_K_M`
   visible twice — once from the row, once from the chip
   behind it)
**Workaround (before fix):** tap the row blind; the selection
still registers even though the text is unreadable.
**Root cause:** `DropdownMenu` defaults to
`MaterialTheme.colorScheme.surfaceContainerHigh`, which after
the Phase A surface-container rework becomes a near-transparent
tint on the dark hero background. `tonalElevation = 0.dp`
wasn't enough — Compose still applied a 15% surface tint that
let the chat-bubble text behind the menu show through.
**Fix:** in `AgentScreen.kt` ~line 853, set the menu to a
hardcoded opaque `containerColor = Color(0xFF1A1F2E)` plus
`tonalElevation = 0.dp` and `shadowElevation = 8.dp`. Bypasses
the theme tint path entirely; reads as a clean dark slab that
matches the rest of the dark surface. Verified on R9KN2009CZJ
— "smollm2-360m-instruct-q8_0.gguf / Bundled" row is now
clearly readable and no text bleeds through.
**Reported:** 2026-08-06. **Fixed:** 2026-08-06.

### B-001 — SmolLM2 download stalls at 0%
**Status:** investigating
**Severity:** high
**Affects:** `app/src/main/kotlin/com/meshlit/models/ModelCatalog.kt`,
`ui/screens/settings/ModelsScreen.kt`
**Repro:**
1. Fresh install of Meshlit on Android 14+ (Samsung One UI 5+,
   Pixel 7+)
2. Open Models → pick SmolLM2-1.7B-Instruct Q4_K_M → tap Get
3. Progress bar either never increments, or jumps to ~80% then
   halts
**Workaround:**
- Use the "From URL" import with a direct HF CDN URL —
  `https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf`
  — instead of the curated catalog row
- Llama-3.2-1B works fine
- Cancel + retry once; sometimes a torn TLS handshake resolves
**Root cause (likely):** the OkHttp `5-minute readTimeout` is
plenty for a wired connection but Samsung's Wi-Fi implementation
will hold the socket in CLOSE_WAIT for ~90s after the first
short read. The chunked progress callback fires every 256 KiB;
on a pathologically slow link (<10 KiB/s) the first callback
never fires within the user's expectation window, so the
progress UI appears stuck at 0% while bytes are flowing.
**Notes:** see Phase F in `PLAN.md`. Live as of 2026-08-06.
Tracker issue #26 in the rolling follow-ups in `REQUESTS.md`.

### B-002 — Bottom nav LazyRow tabs don't register taps
**Status:** worked-around
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/ui/components/MeshlitBottomBar.kt`
**Repro:**
1. Launch Meshlit on R9KN2009CZJ
2. Tap any icon-only nav tab (Files / Cluster / Network / etc.)
3. Nothing happens; uiautomator dump reports zero bounds for the
   child `Modifier.clickable`
**Workaround:**
- Use the drawer (hamburger) to navigate
**Root cause (likely):** Compose `LazyRow` child width via plain
`Modifier.width(80.dp)` doesn't properly wire hit-testing on this
device's WebView overlay rendering path. Adopting
`Modifier.requiredWidth(80.dp)` is the canonical fix; switching
to M3 `NavigationBar` is the long-term fix.

### B-003 — UI Tour / Stub screens crash on new destination
**Status:** worked-around
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/ScreenStubs.kt`,
`help/UiTourScreen.kt`
**Repro:** add a new `TopLevelDestination` enum value, then
launch app → build fails on the exhaustive `when` expressions in
those two files.
**Workaround (until we land the long-term fix):** every
contributor MUST update the two stub maps. This bug is the
"compile-error placeholder for itself".
**Root cause:** the `when (dest)` expressions are not sealed by a
`data object` and don't get an `else` branch, so the Kotlin
compiler refuses the file. A sealed interface + exhaustive when
is the right shape going forward; tracked as Phase D in `PLAN.md`.

### B-004 — VoiceEngine.startCapture IllegalStateException
**Status:** investigating
**Severity:** medium
**Affects:** `app/src/main/kotlin/com/meshlit/core/voice/VoiceEngine.kt`
(startCapture path)
**Repro:** observed intermittently on cold start of the Voice
screen; the engine throws IllegalStateException when
`audioRecord.state != STATE_INITIALIZED` because the audio
session has been acquired twice.
**Workaround:** relaunch the screen once.
**Root cause (likely):** the recorder is acquired in `onLaunch`
in addition to `onRecordStart`; the second acquire races the first
tear-down when the user backs out and re-enters quickly.

### B-005 — Permissions screen only shows 2 permission groups
**Status:** investigating
**Severity:** low
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/settings/*`
**Repro:** Settings → Privacy → Permissions → only CAMERA +
RECORD_AUDIO groups are visible even though the app declares 8+.
**Workaround:** use the system Settings → Apps → Meshlit path to
grant the rest.
**Root cause:** our settings permission audit calls into the
`PackageManager.getDeclaredPermissions` API but only filters for
`protectionLevel == dangerous`; we don't recursively walk the
`uses-permission` tree from merged library manifests.

### B-006 — Supported formats section bottom layout overflows
**Status:** investigating
**Severity:** low
**Affects:** `app/src/main/kotlin/com/meshlit/ui/screens/settings/ModelsScreen.kt`
("Supported formats" card)
**Workaround:** unknown.

---

## Closed bugs

### B-007 — Settings screen crashes when navigating
**Status:** fixed (R-01)
**Severity:** critical
**Affects:** `app/src/main/kotlin/com/meshlit/ui/MeshlitApp.kt`,
`Screens.kt`
**Repro:** Tap any entry to a Settings sub-screen → `IllegalArgumentException`
**Root cause:** missing icon import — `Icons.Filled.ErrorOutline`
resolves only by FQ name in some Compose BOM revisions. Fixed by
explicit import.

### B-008 — Active Model chip shows a model name when none is loaded
**Status:** fixed (R-06)

### B-009 — Agent screen tilted 90° column rendering for "LITE" badge
**Status:** fixed (R-09 / Phase A)

### B-010 — FilterChip selected state invisible on dark surface
**Status:** fixed (R-09 / Phase A)

### B-011 — Output tokens vertical column rendering (Agent screen)
**Status:** fixed (Phase A)

### B-012 — JSON bottom nav tab truncation
**Status:** fixed (Phase A)

### B-013 — Errors invisible in default Snackbar
**Status:** fixed (R-08)

### B-014 — Qwen3-30B downloads fail with 401
**Status:** fixed (R-07) — tag as `requiresAuth`, prompt for HF token.

### B-016 — Model dropdown menu transparent bg → text overlap
**Status:** fixed (2026-08-10, Sprint 2.1)
**Resolution:** `JobsScreen.kt:649` `DropdownMenu` for the model picker now
ships `containerColor = Color(0xFF1A1F2E), tonalElevation = 0.dp,
shadowElevation = 8.dp` — the same pattern already on
`AgentScreen.kt:984`. Comment cross-references the AGent fix.

### B-028 — Terminal screen shows only "ds" or wraps mid-token
**Status:** closed (2026-08-10, doc-only)
**Resolution:** the underlying code is already fixed — `vt_dispatch.cpp:42`
divides byte capacity by `sizeof(int32_t)`, and `TerminalView.kt:223`
wraps the canvas in `Box(horizontalScroll(...))`. Sprint 2.1 added a
stale-doc close; no further code change.

### B-015 — Hero card lingering on every section header
**Status:** closed (2026-08-10, cannot-reproduce)
**Resolution:** `RaHeroIcon` only renders from `AgentScreen.kt::EmptyAgent()`
(gated on empty); `MeshlitHeader` accent bar is by design;
`MeshlitDrawer.HeroBanner` only inside the drawer. No caller renders a
stale hero. Sprint 2.1 doc-only close.

### B-023 — Ghosty terminal tab missing from bottom nav
**Status:** closed (2026-08-10, doc-only)
**Resolution:** `TopLevelDestination.Ghosty` is already in `barItems`
(`TopLevelDestination.kt:89-91`); `shortLabel = "Ghosty"` is wired in
`MeshlitBottomBar.kt:273`. Sprint 2.1 doc-only close.

### B-026 — tokens/s vertical rendering on Agent stats row
**Status:** closed (2026-08-10, cannot-reproduce)
**Resolution:** `AgentScreen.kt:1138-1151` renders the footer as a single
`Text` with `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`.
The string at `strings.xml:505` is `%1$d tokens · %2$dms` — single-line
horizontal. No `StatsRow` composable exists; no vertical rendering anywhere.
Sprint 2.4 doc-only close.

### B-019 — Model load status unclear (no progress indicator)
**Status:** fixed (commit `f46be43`, Sprint 2.3, 2026-08-10)
**Resolution:** added `InferenceEvent.LoadProgress(modelPath, fraction, stage)`
to the sealed interface. The coordinator emits three synthesised ticks
(10% validating, 30% loading weights, 90% warming) around
`engine.loadModel()`. JobsScreen and AgentScreen both render a
`LinearProgressIndicator` gated on `coordinatorState is Loading` plus
the latest LoadProgress fraction, with an indeterminate fallback when
the engine emits Loading without a progress tick. Animations use
fadeIn/fadeOut + expandVertically/shrinkVertically. Build verified:
`:core-inference:compileDebugKotlin` and `:app:compileDebugKotlin` both
BUILD SUCCESSFUL.

---

## Format conventions

- `B-XXX` = bug number (monotonic, never reused)
- Severity:
  - `critical` — blocks major user paths (boot, install, crashes)
  - `high` — feature is broken but app still boots
  - `medium` — feature partially broken, workaround exists
  - `low` — cosmetic / minor UX
- When a bug lands a fix in a commit, the entry moves to
  "Closed bugs" with `(R-XX / Phase Y)` referencing the history.

---

## How to add a new entry

1. Append the entry under "Active bugs" with the next B-XXX id.
2. Fill in: Status, Severity, Affects, Repro, Workaround,
   Root cause notes.
3. Once the fix lands, move to "Closed bugs" and leave a pointer
   back to the original entry id + the request that closed it.
