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

### Glass cards — `.glass-dark-card` & `.glass-light-card`

```css
.glass-dark-card {
  background: rgba(18, 22, 38, 0.72);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 12px 36px 0 rgba(0, 0, 0, 0.45);
}

.glass-light-card {
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 36px 0 rgba(99, 102, 241, 0.08);
}
```

Already wired in `MeshlitGlassCard.kt`. Always use
`MeshlitGlassCard(palette, …)` — never hand-roll a `Box(.background(.card))`.

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
7. Use existing color tokens — **never** hard-code hex.
8. Match durations: page enter 180 ms, modal 220 ms, card hover 300 ms.
