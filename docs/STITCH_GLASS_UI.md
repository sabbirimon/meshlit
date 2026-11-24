# Stitch Glass UI — Design System Skill

This is the canonical design document for the iOS-glass aesthetic that
every Meshlit screen, component, and animation must follow. The values
below were copied **verbatim** from the Google Stitch source at
`screenshots/new stitch ui , copy same to same full desin please/meshlit---federated-edge-ai-cluster (2)/src/` and are the single
source of truth. **Do not deviate.** Any new screen must reuse the
existing primitives in `app/src/main/kotlin/com/meshlit/design/`:
`MeshlitDesignPalette`, `MeshlitGlassCard`, `MeshlitMeshGradientBackground`,
`MeshlitBreathingGlowButton`, `MeshlitShimmerProgressBar`, plus the
animation modifiers in `MeshlitStitchMotion.kt`.

## 1. Color tokens

### Canvas

| Mode | Hex | Tailwind source |
|------|-----|-----------------|
| `canvasDark` | `#090C17` | `App.tsx:130` → `bg-[#090c17]` |
| `canvasLight` | `#F4F7FC` | `App.tsx:131` → `bg-[#f4f7fc]` |

### Blur surfaces — NOT literal glass

Per the v2 design brief: "blur surfaces" means layered tonal
surfaces with `tonalElevation` 1–3 dp on a softened dark base.
**No `Modifier.blur`, no `haze`, no glass primitive.** The v1
`.glass-dark-card` / `.glass-light-card` CSS samples above
were never implemented in Compose (`MeshlitGlassCard.kt`
never landed) — and the v2 brief explicitly rejects literal
glass. Treat that section as historical reference only.

The v2 build uses these primitives instead:

| Surface | Hex | Token | Elevation |
|---|---|---|---|
| Base ink | `#06080F` | `MeshlitInk` | 0 dp |
| Surface | `#0E1322` | `MeshlitSurface` | 1 dp |
| Container | `#151B30` | `MeshlitSurfaceContainer` | 2 dp |
| High | `#1A2138` | `MeshlitSurfaceHigh` | 3 dp |
| Outline | `#2A3354` | `MeshlitOutlineV2` | — |

The pulse gradient (`#4DD9C0 → #7C6FF2 → #E8735F`) replaces
the iridescent ramp as the accent layer — see
`ui/theme/MeshlitPulseGradient.kt`.

### Iridescent accents

| Token | Hex | Tailwind |
|-------|-----|----------|
| `iridescentStart` | `#22D3EE` | cyan-400 |
| `iridescentMid` | `#A855F7` | purple-500 |
| `iridescentEnd` | `#34D399` | emerald-400 |
| `iridescentPink` | `#F472B6` | pink-400 |
| `iridescentIndigo` | `#818CF8` | indigo-400 |
| `streamingGlow` | `#38BDF8` | sky-400 |

### Text scale (Tailwind slate-100..900)

| Token | Hex | Purpose |
|-------|-----|---------|
| `textPrimary` (dark) | `#FFFFFF` | headlines |
| `textSecondary` (dark) | `#CBD5E1` | body |
| `textTertiary` (dark) | `#94A3B8` | captions |
| `textQuaternary` (dark) | `#64748B` | metadata |
| `textPrimary` (light) | `#0F172A` | headlines |
| `textSecondary` (light) | `#334155` | body |
| `textTertiary` (light) | `#64748B` | captions |

### Halo / glow shadows (CSS box-shadow analog)

| Token | Use |
|-------|-----|
| `haloCyanSoft` `rgba(56,189,248,0.25)` | cyan glowing borders (NodeManagement) |
| `haloCyanStrong` `rgba(56,189,248,0.5)` | floating CTA halo (Dashboard) |
| `haloPurpleSoft` `rgba(168,85,247,0.25)` | purple node glow |
| `haloPurpleIntense` `rgba(168,85,247,0.7)` | inner AI bubble glow |
| `haloEmeraldSoft` `rgba(16,185,129,0.25)` | emerald active node |
| `haloSkyIntense` `rgba(56,189,248,0.7)` | shimmer-bar inner |

## 2. Geometry

| Radius | px | Stitch source |
|--------|-----|---------------|
| pill | 9999 | `rounded-full` |
| small | 12 | `rounded-xl` |
| medium | 16 | `rounded-2xl` |
| large | 24 | `rounded-3xl` |
| hero | 42 | mobile viewport `rounded-[42px]` |

Standard padding: `p-5` = **20dp** for glass cards;
node-card padding = `p-3.5` = **14dp**;
small form fields = `p-3` = **12dp**.

## 3. Animations (CSS @keyframes verbatim)

| Keyframe | Where | Duration | Effect |
|----------|-------|----------|--------|
| `pulseGlow` | `.animate-pulse-glow` (PipelineFlowVisualizer jelly orbs) | 1.8 s | opacity 0.7→1 + drop-shadow cyan↔purple |
| `flowLine` | inline `animate-[flowLine_*s_linear_infinite]` on SVG paths | 4 s (3 s in Waveform) | `stroke-dashoffset: 200 → 0` |
| `rotateMesh` | `.animate-rotate-slow` | 35 s linear | rotate 360° |
| `shimmerWave` | `.shimmer-bar` background | 2.5 s | `background-position: -200% 0 → 200% 0` |
| `floatSlow` | `.animate-float-slow` | 4 s | translateY(0→-6px) + scale 1→1.02 |
| `animate-ping` | every status dot (Tailwind built-in) | 1 s | rgba ring expand |
| `animate-pulse` | streaming badges, audio bars | 2 s | opacity 1→0.5 |
| `animate-spin` | RefreshCw during streaming | 1 s linear | rotate 360° |

Hover/tap (Motion library equivalents → Compose `Modifier.scale()`):
- `whileHover scale: 1.01` (node cards, 300 ms)
- `whileHover scale: 1.05 y: -2 whileTap scale: 0.95` (CTA)
- `whileHover scale: 1.08-1.15 whileTap scale: 0.92-0.96` (action buttons)

Page transition: `initial { opacity 0, y 8 } → animate { opacity 1, y 0 }
exit { opacity 0, y -8 }` with `AnimatePresence mode="wait"` at **180 ms**.

## 4. Per-screen pacing

| Screen | Top-level layout |
|--------|-----------------|
| `DashboardScreen` | status bar + app bar + pill nav + Network/Active Inference cards + Available Nodes list + floating FAB |
| `NodeManagement` | h2 + "N cluster endpoints" + Cluster Capacity/Active Shards tile + filter pills + node-card list (per-status gradient border) |
| `AIConsole` | header strip + chat stream + input bar + suggested prompts + WaveformMesh card + Resource Usage card |
| `ModelDownloadManager` | header + SAF/HF pill + storage capacity banner + filter pills + 2-col model card grid |
| `NetworkMonitoring` | header + Download .pcap / Export GitHub Pills + 4-tab segmented control + monospace table + hex/ASCII inspectors |
| `SettingsView` | Trust Tiers card + Zero Telemetry card + Runtime card + Reset |
| `SpeechLab` | header + STT card with 12-bar waveform + TTS card |
| `VisionWorkbench` | header + sample toggle + 2-col (preview + query card) |
| `JobsScreen` | header + filter segmented control + vertical task card stack with shimmer bars |
| `AgentWorkbench` | gradient header tile + Run button + 3 preset goal cards + input card + scratchpad cards (thought/action/observation/final) |

## 5. Required primitives checklist (use these, don't reinvent)

- [x] Glass surface → `MeshlitGlassCard(palette, …)`
- [x] Mesh background → `MeshlitMeshGradientBackground(palette) { content }`
- [x] CTA button → `MeshlitBreathingGlowButton(variant = PILL_GRADIENT/GLASS/…)`
- [x] Shimmer bar → `MeshlitShimmerProgressBar(progress, palette)`
- [x] Animated flow line → `MeshlitWaveLine(start, end)`
- [x] Pulsing cluster node → `MeshlitPulsingClusterNode(color, palette)`
- [x] Circular gauge → `MeshlitCircularGauge(percent, color, palette)`
- [x] Polyhedral mesh wireframe → `MeshlitPolyhedralMesh(palette, size)` (WIP)
- [x] Glow halos → `Modifier.glow(color = MeshlitDesignPalette.Dark.haloCyanStrong, radius = 24.dp)`

## 6. Build checklist for any new screen

1. Wrap in `MeshlitDesignSystem(palette = palette) { … }`.
2. Use `MeshlitMeshGradientBackground(palette)` as the root.
3. Use `MeshlitGlassCard(palette, cornerRadius = 24.dp, contentPadding = 20.dp)` for every surface.
4. Apply `Modifier.glow(color)` on hover-capable / active-state surfaces.
5. Use `MeshlitShimmerProgressBar` for any progress UI.
6. Use `MeshlitBreathingGlowButton` for any CTA, never `Button`.

## 7. V1 vs V2 build packaging

The v2 redesign ships as a **separate Gradle product flavor**
so the v1 UI is preserved for emergency revert. The two
flavors coexist on the same device (different
`applicationIdSuffix`):

| Flavor | applicationId | USE_NEW_UI | versionName | UI surface |
|---|---|---|---|---|
| `meshlitV1` | `com.meshlit` | `false` | `<base>-v1` | Legacy v1 chrome (untouched) |
| `meshlitV2` | `com.meshlit.v2` | `true` | `2.0.0-v2build1` | New v2 chrome (this design) |

**Switch point:** `MainActivity.onCreate` reads
`BuildConfig.USE_NEW_UI` and routes to either `MeshlitApp()`
(v1) or `V2Root` (v2). Both branches share the same theme
wrapper (`MeshlitTheme`), the same `:core-*` modules, and
the same Koin singletons — only the Compose surface is
different.

**Revert path:** to roll back, set `USE_NEW_UI = false` in
the `meshlitV2` flavor (or delete the flavor entirely). The
v1 source tree (`app/src/main/kotlin/com/meshlit/ui/`) is
byte-identical to the pre-v2 state.

**CI matrix** (`.github/workflows/ci.yml`):
```yaml
strategy:
  fail-fast: false
  matrix:
    flavor: [meshlitV1, meshlitV2]
```
Builds and runs unit tests for both flavors on every PR.

**DataStore sharing:** both flavors read the same
`meshlit_config` DataStore (same package name), so
config / flags / node-id persist across both — intentional,
so rollback does not lose identity.

**Audit invariant** (per the plan's §5 wiring audit): the v2
tree uses sealed `UiState` + `collectAsStateWithLifecycle()`
exclusively; the audit script (`scripts/audit-ui-wiring.sh`)
fails the build if any v2 screen strays from that pattern.

## 8. Multi-source LLM catalog (Catalog screen)

The Models screen rows are not single URLs — every entry in
`RunAnywhereCatalog.all` (and every `Entry` synthesized from the
SDK) carries a `sources: List<DownloadSource>` list. Each
source has:

| Field | Meaning |
|---|---|
| `id` | Stable per-source id (`<entryId>@<org>:<quant>`) |
| `org` | Hugging Face org slug (`HuggingFaceTB`, `bartowski`, …) |
| `quant` | `Quant` enum — `Q8_0`, `Q4_K_M`, … |
| `approxSizeBytes` | Precise weight size for the size chip |
| `url` | Hugging Face `resolve/main/…gguf` URL |
| `tier` | `OFFICIAL` / `COMMUNITY_REQUANT` / `MIRROR` / `SDK_BUNDLED` |
| `priority` | Lower = preferred (the row's default download) |
| `sha256` | Optional digest — verify after fetch |

The Catalog screen renders three filter dimensions on top of
the catalog:

- **Type** — `CHAT` / `CODE` / `VISION` / `MULTIMODAL` /
  `EMBEDDING` (driven by `Entry.modelType`).
- **Tier** — `OFFICIAL` / `COMMUNITY_REQUANT` / `MIRROR` /
  `SDK_BUNDLED` (filtering by tier hides rows whose
  `availableTiers` don't include the chosen tier).
- **Size** — implicit via the per-row `SizeClass` chip
  (`SMALL` / `MEDIUM` / `LARGE` / `HUGE`).

The row chip group also exposes a clickable "Sources (N)" pill
that opens `CatalogSourcePickerSheet`. The sheet lists every
source for the entry, sorted by `priority`, with org / size /
quant / tier / URL. Tapping a row fires the SDK
`downloadModelById(id, url)` call against that source. The
default row download (the row's "Get" button) uses
`Entry.primarySource` (lowest `priority`).

The size chip uses `Entry.minSizeBytes` … `Entry.maxSizeBytes`
when the sources disagree on size (Q4_K_M from one org vs Q8_0
from another). For SDK-only rows the chip falls back to
`Entry.approxSizeMb`.

## 9. Build checklist for any new screen

1. Wrap in `MeshlitDesignSystem(palette = palette) { … }`.
2. Use `MeshlitMeshGradientBackground(palette)` as the root.
3. Use `MeshlitGlassCard(palette, cornerRadius = 24.dp, contentPadding = 20.dp)` for every surface.
4. Apply `Modifier.glow(color)` on hover-capable / active-state surfaces.
5. Use `MeshlitShimmerProgressBar` for any progress UI.
6. Use `MeshlitBreathingGlowButton` for any CTA, never `Button`.
7. Use existing color tokens — **never** hard-code hex.
8. Match durations: page enter 180 ms, modal 220 ms, card hover 300 ms.
