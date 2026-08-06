# Meshlit — Active TODO List

This is the live, user-facing task list. Puku should keep this in sync
with what the user has asked for in the current session. Tasks here
are also registered with the in-session `TaskList`, but `TODO.md`
is the persistent source of truth across context resets.

## In progress

- [ ] **#183** — **Personality / weight-tuning / templates** —
  multiple personalities and skills added to the same open
  weights. Per-model persona preset (system prompt), per-skill
  template (e.g. summarizer, code-reviewer), and a weight-tuning
  override that re-points the loaded model to a tuned LoRA / merge
  without forcing a re-download. Backed by a new
  `PersonalityRepository` in `core-inference` and a `PersonaPicker`
  Composable in the toolbar (alongside the dispatch picker on
  Jobs, alongside the mode picker on Agent).
- [ ] **#179** — Add a **ghostty-org/ghostty**-backed terminal
  screen — a power-user shell over the local sandbox + a VT100/
  ANSI compat layer. The existing `core-terminal` module is a
  hand-rolled VT parser; the user wants to swap it for the
  upstream Ghostty terminal renderer (MIT-licensed, Zig core with
  Android NDK bindings). Need to verify the licence compatibility
  with the project's Apache 2.0 + verify the NDK build matrix,
  then replace `core-terminal/vt/Parser.kt` etc. with the
  vendored SDK.

## Backlog (open user requests)

_(see Open low-priority items below)_

## Recently landed

- [x] **#200–#210 / Obs-1** — **Phase Observability 1** shipped.
  Adds a global tracer (Off / Local / Otel) wired through
  `core-observability/TracingController`, with per-source toggles
  for network / inference / agent spans. Inference runs through
  `InferenceTracingObserver`; agent calls through
  `AgentTracingObserver`; HTTP calls through
  `core-net/NetworkObserver` (an OkHttp `EventListener`). Meshlit
  Capture `VpnService` writes libpcap-format files under
  `filesDir/exports/captures/` (IPv4/IPv6 + TCP/UDP metadata,
  96-byte payload preview). New **Help** destination (drawer-only)
  hosts the **User Manual** (every feature documented with
  intent / use case / config steps / troubleshooting),
  **UI Tour** (first-visit overlay + per-screen recap) and
  **Feedback** (Bug / Feature, GitHub Issues URL builder, last
  200 log lines attached). Drawer **Sync** quick action now calls
  `catalogEngine.refresh(...)`; **Boost** toggles the inference
  thread priority via `SettingsRepository.inferenceBoostEnabledFlow`;
  **About** now opens the manual. **LogScreen** supports source
  filtering (App / Network / Inference / Agent / System) and
  exports `.txt` + `.jsonl`. **NetworkMonitorScreen** is the new
  four-tab surface (Meshlit HTTP / Device packets / External
  capture / Tools — PCAPdroid + Termux). New `:core-observability`
  + `:core-net` Gradle modules, OpenTelemetry 1.43.0 + OTLP/gRPC
  exporter + logging exporter, `BIND_VPN_SERVICE` and
  `FOREGROUND_SERVICE_SPECIAL_USE` permissions. **Files**:
  `core-observability/.../{TracingController, OtelBootstrap,
  SinkSpanProcessor, TracerHolder, LogSource}.kt`;
  `core-net/.../{NetworkObserver}.kt`;
  `core-net/.../capture/{MeshlitCaptureVpnService, PcapWriter,
  PcapParser, PacketParser}.kt`;
  `app/.../ui/screens/help/{HelpHubScreen, UserManualScreen,
  UiTourScreen, TourOverlay, FeedbackScreen}.kt`;
  `app/.../ui/screens/network/NetworkMonitorScreen.kt`;
  `app/.../observability/{LogBuffer, LogExporter}.kt`;
  `app/.../quickactions/{SyncViewModel, BoostViewModel}.kt`;
  `app/.../network/{pcapdroid/PcapdroidBridge, termux/TermuxBridge}.kt`.
- [x] **#181** — Redesign **Jobs UI** so it stops swallowing
  vertical real-estate. Dispatch picker moved into the top bar
  (Local / Remote / **Cluster** tabs), single-line toolbar with
  compact model picker + start/stop + status pill + identity
  badge, full-width chat surface in the middle, chat-style input
  row at the bottom. Cluster mode resolves the first trusted
  peer via `ClusterDispatch.firstPeer()` and routes through the
  same `RemoteEvent` SSE pipeline as Remote. **Files**:
  `JobsScreen.kt`, `InferenceDispatchMode.kt`,
  `ClusterDispatch.kt`, `MeshlitApplication.kt`,
  `MeshlitHeader.kt`.
- [x] **#180** — **Stop button beside model dropdown on Jobs** now
  works. Previous version had `enabled = !isRunning` which
  blocked the button while inference was live, so users couldn't
  cancel a generation. Fixed by removing the `enabled` guard
  (`onClick = { if (isLive) onStop() else onStart() }`). Located
  inside `CompactToolbar`'s `IconButton` block.
- [x] **#176** — **Copy / Save / Share / Export** affordances on
  every LLM output bubble. Long-press still supports copy via
  `SelectionContainer`; the new affordance is a small toolbar row
  that fires the full four actions and writes files to
  `filesDir/exports/meshlit-output-<ts>.txt`. Wired into Jobs
  (`ExchangeBubble`) and Agent (`AgentBubble`).
- [x] **#175** — Models management: **NPU / llama.cpp / Easy /
  Fast / Balanced / Heavy** chips, plus **download speed, file
  size, ETA** to the `DownloadStatusPanel`. The panel now shows
  `% complete`, `MB downloaded / MB total`, `2.0 MB/s`, and
  `ETA 1m 30s` side-by-side.
- [x] **#174** — Fix bundled model loader by mirroring the GGUF
  into the RunAnywhere SDK's canonical path
  (`{filesDir}/RunAnywhere/Models/LlamaCpp/{id}/{id}.gguf`).
  Verified: Jobs screen now shows "Ready / smollm2-360m-instruct-q8_0".
- [x] **#185** — Located existing nearby-devices / host-client
  discovery UI (Devices screen + `NearbyDiscoveryPanel`).
- [x] **#186** — Firewall feature: composite `MeshlitFirewall`
  (phase-3 CIDR/node/tier + port/protocol/direction layer),
  per-port `PortLayerPolicy`, multi-port exposure catalogue
  (`MeshlitExposedPort` 8080/8090/8100/8110/8120), port-layer
  gate wired into `InferenceHttpServer`, settings persistence
  via `SettingsRepository.firewallFlow`.
- [x] **#187** — Google Play Services Code Scanner for QR
  pairing. Replaced "delegate to any installed barcode scanner"
  with `GmsBarcodeScanning.getClient(activity).startScan()` —
  Play Services' bundled scanner UI handles its own camera
  surface (no `CAMERA` permission, no CameraX dep, no
  PreviewView). Includes typed `ScanResult` sealed class
  (Success / Cancelled / PlayServicesMissing / Failed) so the
  Devices sheet can surface a clear error instead of failing
  silently. Wired into `QrPairingSheet`'s scan button.
- [x] **#188** — Designed the on-device agent capability surface
  (camera, mic, GPS, network state, dialer, SMS, storage).
  Each capability owns a master toggle, a runtime permission,
  and a per-target allowlist. Risk class (LOW/MEDIUM/HIGH)
  drives the confirmation dialog copy.
- [x] **#189** — `AgentCapabilityRegistry` (pure-data state
  holder in `:core-cloud-mcp/agent/`) + `AgentCapabilityRegistryHolder`
  (wires it to `SettingsRepository` flows on a background
  scope). `MeshlitApplication.agentCapabilities` is the app-
  level entry point.
- [x] **#190** — `agent_call_dial` tool + dispatcher. Opens the
  system dialer with a pre-filled number; the user still has to
  hit the green button. No `CALL_PHONE` permission required.
- [x] **#191** — `agent_sms_send` tool + dispatcher. Uses
  `SmsManager` (multipart via `divideMessage` when body exceeds
  the single-part limit). Re-checks the per-recipient allowlist
  defensively at dispatch time.
- [x] **#192** — `agent_camera_capture` tool + dispatcher. Uses
  CameraX `ImageCapture` bound to a synthetic `LifecycleOwner`;
  captures a single JPEG and returns base64 bytes. Requires
  `CAMERA` runtime grant. Added `androidx.camera:*:1.4.1` deps.
- [x] **#193** — `agent_data_state` tool + dispatcher. Returns
  Wi-Fi / cellular / ethernet / VPN / metered state via
  `ConnectivityManager`; Wi-Fi path also returns SSID + link
  speed + RSSI. No permission needed.
- [x] **#194** — `agent_storage_list` / `agent_storage_read` /
  `agent_storage_write` tools + dispatcher. Uses Storage
  Access Framework (`DocumentsContract`) — user grants a tree
  via `ACTION_OPEN_DOCUMENT_TREE`, the persistable URI is
  stored in the allowlist. Path-traversal rejected. Intermediate
  directories auto-created.
- [x] **#195** — `agent_location_get` tool + dispatcher. Uses
  `FusedLocationProviderClient.lastLocation` (no fresh fix).
  Surfaces `no-fix` / `stale` / `ok` states plus lat/lon/
  accuracy/altitude/bearing/speed/fixTime/provider. Requires
  `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
- [x] **#196** — `agent_mic_listen` tool + dispatcher. Uses
  `MediaRecorder` in opus/ogg, default 5 s cap (500 ms–30 s).
  Returns base64 audio bytes. Requires `RECORD_AUDIO` runtime
  grant.
- [x] **#197** — Settings → Cloud → Agent capabilities screen.
  One card per [AgentCapability] with master toggle, permission
  grant button, and per-target allowlist editor (SMS recipients,
  storage tree URIs via `OpenDocumentTree`).
- [x] **#198** — Agent-loop integration: `AgentCapabilityRegistrar`
  subscribes to the registry and pushes `agent_*` tools into
  `cloudCoordinator.toolRegistry` whenever the user flips a
  capability on. `AgentCapabilityRouter.DispatcherFacade` lets
  `core-cloud-mcp` route calls without depending on app code.
- [x] **#199** — Redesigned **side menu / drawer** as a 3-column
  tile grid. Every destination (15 entries) now renders as a
  square tile with icon-on-top + label-below, selected state
  highlighted via `primaryContainer`. Replaces the v1 list-of-
  rows layout which didn't scale past 5 items. Hero banner +
  quick-action tiles + screens grid + capability-tier footer
  unchanged. **File**: `app/.../ui/components/MeshlitDrawer.kt`.

## In progress — UI redesign

_(no open redesign items — see Recently landed)_

## Open low-priority items from earlier sessions

_(all three open items below resolved in Obs-1 sweep — see
Recently landed → "Catalog/Devices low-priority sweep" for
details)_

- [x] **Devices screen Add Cluster feature** — deferred. The
  Cluster dispatch mode is an in-memory "first trusted peer"
  resolver (`ClusterDispatch.firstPeer()`), not a separately-
  registered endpoint. There's no cluster-registration API to
  call from the Add sheet — adding a fake tab would mislead
  users. Resolved as: documented in the manual
  (`ManualSections.Cluster`) so the user knows Cluster mode is
  driven by the Devices list, not by a manual Add.
- [x] **Catalog screen management** — done. New
  `CatalogDetailsSheet` shows every row's metadata (id, family,
  architecture, quant, size class, license, origin, language,
  strengths) plus the current download status. Loaded and
  in-progress rows open the sheet on tap; the Failed state adds
  a Retry button to the sheet.
- [x] **Devices screen custom endpoint textbox** — done. The
  URL field is now the *first* field in `AddEndpointSheet` with
  a `supportingText` hint: "First field — paste the server URL.
  Most reliable on dev devices." Stays discoverable on first
  launch without forcing a separate screen.

## How to keep the list healthy

1. When the user asks for a new feature, append a numbered item
   here **and** `TaskCreate` it.
2. When a task is done, mark it done in this file **and**
   `TaskUpdate` it.
3. When the user sends a course correction that supersedes a
   task, update the existing entry rather than creating a new one.