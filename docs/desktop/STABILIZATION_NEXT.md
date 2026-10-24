# Desktop-only stabilization — next implementation slices
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Updated 2026-10-10. Owner asked for three concrete fixes plus model visibility
**for the desktop workstation only**: (1) the "Add device" flow is broken for
normal users, (2) the desktop UI is plain and dated, (3) KittenTTS should be
added, (4) the model list should be honest about what is actually available
locally and on the running Ollama. This document is the scoped plan for those
slices and does **not** retread the broader S0–S6 stabilization, which remains
[planning only](STABILIZATION_PLAN.md).

> **Scope rule.** Nothing below touches `app/`, `core-*/android/`, or any
> `V2*` / `*Screen.kt` / `*ViewModel.kt` / `MeshlitApplication.kt` Android
> source. Every change is contained in `desktopApp/`, `scripts/`, and
> `docs/desktop/` / `docs/models/`. If a slice needs an Android-only signal it
> is rewritten through a desktop-local view-model seam.

## Observed baseline (this Intel Mac, 2026-10-10)

| Item | Observed | Source |
| --- | --- | --- |
| Running app | `MeshlitPreview.app` PID **58757**, version **2.0.40**, ad-hoc signed, Intel x86_64, 0 % CPU, no listening sockets | `ps -ef`; `codesign -dv`; `lsof -nP -p 58757` |
| Bundled desktop model | `Qwen2.5-1.5B-Instruct-Q4_K_M` · 1.12 GB · Apache-2.0 · revision `91cad511…`, SHA-256 `6a1a2eb6…` | [`desktopApp/distribution/bundled-model.json`](../../desktopApp/distribution/bundled-model.json) |
| Bundled Android model | `smollm2-135m-instruct-q4_k_m` · 105 MB · Apache-2.0 · revision `09816acd…` | `app/src/main/assets/models/bundled-model.json` (out of scope) |
| Model catalog classes | SmolLM2 1.7B, Llama 3.2 1B, Qwen 2.5 1.5B, DeepSeek-R1 1.5B, Phi-3.5-mini (Phase 2) | [`docs/models/catalog-classification.md`](../models/catalog-classification.md) |
| Local Ollama | `127.0.0.1:11434` · `gemma4:latest` (9.6 GB, Q4_K_M) and `gemma4:e4b` (same digest) · digest `c6eb396d…` · context 131 072 | `GET /api/tags` from this shell |
| Desktop TTS code | **None.** No `tts/` package, no Piper, no Whisper, no KittenTTS | `find desktopApp -name "*.kt" \| xargs grep -i "tts\|synthesi\|piper\|whisper\|speech"` returns only `DesktopDestinations.kt` |
| Desktop feature tracker rows | 41 desktop areas tracked · 23 `available=true` · 18 pending | [`docs/DESKTOP_FEATURE_TRACKER.md`](../DESKTOP_FEATURE_TRACKER.md) |
| Stabilization milestone | S0–S6 plan documented, **implementation not started** | [`docs/desktop/STABILIZATION_PLAN.md`](STABILIZATION_PLAN.md) |

The desktop is **not** missing the local LLM: the Qwen 1.5B starter is real,
verified, loadable and runtime-tested. The desktop **is** missing honest
device-add, modern UI chrome, any TTS adapter, and a truthful picture of what
models are actually present (bundled, downloaded, or sitting on a local
Ollama).

## Delivery order for this scoped milestone

| Gate | Deliverable | Depends on | Exit evidence |
| --- | --- | --- | --- |
| D0 | Honest model inventory + Ollama probe | Available host and Ollama | `desktop/evidence/models.json` listing every model with hash, source, and runtime; one `--models-check` run |
| D1 | Desktop "Add device" works for real users | D0 evidence | `--adddevice-check` JSON with saved node, real health probe, real auth, real reply |
| D2 | Desktop UI modernised without faking features | D1 working | `--ui-check` JSON + screenshot of regrouped dashboard and glow composer |
| D3 | KittenTTS offline TTS adapter behind a license gate | D1 working; Python 3.9+ on host | `--tts-check` JSON with real `kitten-tts-mini` synthesis, SHA-256 verified weights, license file present |
| D4 | Single recovery / rollback plan (this milestone only) | D1–D3 working | This document; updated `DESKTOP_BUILD_LOG.md` row; previous working app retained |

These IDs belong to this document only; they do not renumber the parent S0–S6
gates and they do not promote themselves to "production" once passed. D3
license work is the riskiest item and is sequenced last so that an unresolved
license question cannot block D0–D2.

## Group A — desktop "Add device" (D1)

The desktop "Add device" panel is `NodesPanel` in
[`Nodes.kt`](../../desktopApp/src/main/kotlin/com/meshlit/desktop/Nodes.kt).
Three concrete break points prevent normal use:

| Break point | Location | What a normal user sees |
| --- | --- | --- |
| `HostEndpoint.parse` requires the full base path to match the protocol | `HostClient.kt:30` `require(uri.rawPath == protocol.basePath \|\| …)` | Pasting `http://192.168.1.5:8080` (the address a phone actually advertises) is silently rejected with "Wrong API base path." |
| `HostClient` requires a token for any OPENAI host, even on loopback | `HostClient.kt:38,100` | Local Ollama at `http://127.0.0.1:11434` (no token) cannot be used. The "Test" or "Connect" path either throws or is unreachable. |
| `NodesPanel` has no Test/Connect path, no diagnostic and no last-result | `Nodes.kt:72-125` | There is no way to find out *why* a saved address fails, no way to retry, and no record of when a node last worked. |

### A1 — accept the bare host (no base path)

In `HostClient.kt:30`, replace the strict `rawPath == basePath` check with
"if `rawPath` is empty or matches `basePath` with optional trailing slash,
accept it; otherwise reject with the same message". Add an internal helper
`HostEndpoint.normalise(text, protocol)` that appends `protocol.basePath` when
the user pastes a bare host. Keep the no-userinfo, no-query, no-fragment, no
control-character and HTTPS-or-loopback guards from the existing `require`
calls.

### A2 — allow loopback OPENAI without a token

In `HostClient.kt:38`, change the `require(endpoint.loopback || token.isNotBlank())`
to `require(endpoint.loopback || token.isNotBlank())` is already the rule, so
loopback already passes. The real blocker is the `localProperties()` and
`generateDeterministic()` preconditions at lines 77 and 100 that demand
`token.isNotBlank()` even on loopback. Add a small `endpoint.allowsAnonymous`
flag (true only when `loopback == true` and `protocol == OPENAI`) and relax
those two preconditions when that flag is set. Bearer header is only emitted
when the token is non-blank, which is already the case at `HostClient.kt:52`.
Do not relax the HTTPS rule for non-loopback.

### A3 — `HostClient.health()` with 5-second timeout

Add a `fun health(): JsonObject` to `HostClient` that issues `GET <base>/models`
with a 5-second connect and 5-second request timeout, returns the parsed JSON
`data` array size, and never throws on transport errors — it returns a
`{ ok: false, reason: "..." }` object instead. Use the same bounded JSON
parser. This is the foundation of the Test button.

### A4 — Test button in `NodesPanel`

Between the endpoint field and the "Save device address" button, add a
secondary `TextButton("Test connection")` that:

1. Calls `HostEndpoint.parse(endpoint, protocol)`.
2. If parse fails, shows the same `failure` banner with the parse message.
3. Otherwise opens a `HostClient(ep, tokenOrEmpty)` for ≤ 6 seconds and
   calls `health()`. Reports the result inline as a small caption ("3 models
   reachable", "HTTP 401 — set token", "Connection refused at 127.0.0.1:11434",
   "Timeout after 5 s"). Closes the client in a `finally`.

The token field is *not* added to the saved-row payload — tokens stay
session-only as the existing `Nodes.kt:84` text already says.

### A5 — "Select & authenticate" wires through to the chat form

The existing button at `Nodes.kt:92` is wired to a single `onSelect(node)`
callback, but `Main.kt:345` only uses it to populate a label. Wire it
through to populate the actual `ModelManager`/host selection so the saved
endpoint becomes the active chat host. Add a `Main.kt:232` parameter
`activeHost: HostEndpoint?` that is read by the composer and the Stop/load
controls. The "Test" call from A4 is also written to `Main.kt`'s
host-status caption so a working test is visible from the main window.

### A6 — `SavedNode` schema v1 → v2 with last-result

The current `nodes.json` is `version: 1` with `{id, name, category, endpoint,
protocol}`. Bump to `version: 2` and add `lastConnectedAt` (ISO-8601 or `null`)
and `lastResult` (`"ok" | "auth" | "unreachable" | "timeout" | "http"`, or
`null`). Migration: read v1 files as if every entry had `null` for both new
fields. Cap file size at the existing 256 KiB and the existing 64-node cap.
Do **not** add bearer tokens, capability claims, or trust levels to the
persisted row. Keep the v2 migration additive so a v2 file is not
regressible to v1.

### D1 acceptance

| Check | Required result |
| --- | --- |
| `HostEndpoint.parse("http://192.168.1.5:8080", OPENAI)` | Throws "Wrong API base path." → fixed to normalise to `http://192.168.1.5:8080/v1` and accept |
| `HostEndpoint.parse("https://phone.example.com", OPENAI)` | Same normalisation, then accept with HTTPS guard |
| `HostEndpoint.parse("http://10.0.0.5:8080/v1", OPENAI)` (non-loopback, http) | Still rejected — "Remote hosts require HTTPS with a trusted certificate." |
| `HostClient.health()` against `http://127.0.0.1:11434` without a token | Returns `ok: true, models: <N>`; bearer header is omitted |
| `HostClient.health()` against an unreachable host | Returns `ok: false, reason: "Connection refused"` within 5 seconds, no exception |
| `NodesPanel` "Test" with reachable Ollama | Shows reachable count; "Save device address" enabled |
| `NodesPanel` "Test" with refused port | Shows "Connection refused" caption; saved row keeps last result |
| Saved `nodes.json` v1 | Read on launch as v2 with `lastConnectedAt = null` |
| Saved `nodes.json` v2 | Read normally; "Select & authenticate" populates the active host caption in `Main.kt` |

Evidence is written to `desktop/evidence/adddevice.json` from a real
`--adddevice-check` launcher flag, run against this Mac's running Ollama.

## Group B — desktop UI modernised (D2)

The desktop UI is honest but plain: one Compose window, Material 2 colour
scheme, hand-rolled `HeroMenu` cards with `Canvas`-drawn glyphs, a
`DesktopDestinations` list of 64 rows of which 18 are `available=false`, and
an `OutlinedTextField` composer. This is not a full redesign — that is
tracked separately in
[`UI_WORKSPACE_REDESIGN.md`](../UI_WORKSPACE_REDESIGN.md) and
[`STITCH_GLASS_UI.md`](../STITCH_GLASS_UI.md) which already name the
"Gemini-style" / "OpenBot" / "Grok" language. The plan here makes the
desktop **honest about the rows it cannot deliver** and adds the smallest
amount of motion and glass so it does not feel like a debug shell.

### B1 — `MeshlitGlow.kt` with a `BreathingGlow` modifier

Create `desktopApp/src/main/kotlin/com/meshlit/desktop/MeshlitGlow.kt` with:

- `@Composable fun BreathingGlow(tint: Color, intensity: Float = 0.35f, durationMs: Int = 2400, content: @Composable () -> Unit)`
- A `Modifier.glowBorder()` extension that paints a 1.5 dp outline whose
  alpha is driven by `rememberInfiniteTransition().animateFloat`.
- No third-party animation library; uses `animateFloat` + `drawBehind`.

This is the same colour and motion language the user asked for in the
earlier turn ("Gemini / OpenBot / Grok"). Keep it inside Compose
`drawBehind` so the accessibility-tree label is unaffected and the glow
disappears when the user enables "Reduce motion" in `Appearance`.

### B2 — regroup `DesktopDestinations` into 5 sections

Replace the 64-row flat list with 5 sections backed by the same
`DesktopDestination` data class. The data is **not** deleted; only the
display order changes. Each row keeps its existing `available` boolean.

| Section | Rows | Visible when |
| --- | --- | --- |
| **Chat** | engine, management, models, discover, behavior, guardrails, providers, colibri | Always |
| **Devices** | nodes, ssh, network, terminal, ghostty, remote commands, p2p, external | Always; greyed rows keep their "pending" caption |
| **Build** | monitor, health, memory, power, acceleration, tasks, ide, files, hyperl, crypto | Always |
| **Voice & media** | voice, dolphin, gibberlink, packets, media, automation | Voice row flips to `available=true` only after D3 passes; otherwise keeps its "Desktop port pending" caption |
| **Settings & help** | appearance, configuration, personalization, recovery, training, router, cloud, operations, gateway, commands, packages, securitylab, agents, ssh, firewall, device, tasks, permissions, termux, notifications, audit, logs, peers, runtime, crawler, openclaw, hooks, help, legal, about | Always |

The `search` function in `DesktopDestinations.kt:71` is kept as-is so the
existing global `Search` panel still works. No row is silently hidden; greyed
rows render with the existing 0.5 alpha.

### B3 — `DesktopPillInput.kt` composer

Replace the plain `OutlinedTextField` in `Main.kt` composer with
`DesktopPillInput` (new file
`desktopApp/src/main/kotlin/com/meshlit/desktop/DesktopPillInput.kt`):

- Single-line rounded pill with a `BreathingGlow` border.
- Inline Send button on the right, disabled when the text is empty.
- "Host" indicator on the left showing the currently selected `SavedNode` and
  its `lastResult` from A6. A red "unreachable" or amber "auth" tint overrides
  the glow.
- Keyboard: `Enter` sends, `Shift+Enter` inserts a newline, `Esc` clears focus
  and triggers the existing Stop path.

The plain `OutlinedTextField` is removed only from the main composer; the
settings dialog keeps the original control.

### B4 — first slice of S1: `SessionStore.kt` for in-memory session persistence

`Main.kt` keeps conversation state in `mutableStateOf` only. The S1 gate in
the parent plan needs that lifted into a lifecycle controller; for **this
milestone** we land the smallest piece that makes the UI honest:
`SessionStore` that writes the visible message list to
`~/.meshlit/desktop/sessions.json` on every commit, and rehydrates it on
launch. Format is `meshlit-session/1` with `[{role, content, ts}]`. No
encryption yet — the S1 macOS Keychain boundary from the parent plan is the
next step but is out of scope here. The existing "Temporary sessions
remain available" copy in `STABILIZATION_PLAN.md` is preserved by giving
the user a "New temporary session" button that does not write to disk.

### B5 — status footer

Add a 24-dp footer to the main `Window` that shows: bundled model SHA-256
prefix, last Ollama probe result, and current `lastResult` of the selected
node. Footer text is taken from real source — never invented. The existing
`HuggingFace.kt` "search" panel and `Preferences.kt` theme picker remain
unchanged.

### D2 acceptance

| Check | Required result |
| --- | --- |
| `MeshlitGlow` renders with motion on | Two-second visual capture shows a breathing alpha 0.1 → 0.35 → 0.1 envelope |
| `MeshlitGlow` with "Reduce motion" | Alpha is held at 0.2; no `animateFloat` call |
| `DesktopDestinations` regrouped | 5 sections visible; total row count unchanged (64) |
| Greyed row (e.g. `containers`) | Renders with "Desktop port pending" caption and 0.5 alpha, still searchable |
| `DesktopPillInput` send on Enter | Sends current text; field clears; disabled when empty |
| `DesktopPillInput` selected host unreachable | Pill border tints red, status footer matches |
| `SessionStore` write/rehydrate | Quit and relaunch the app; the previous chat is restored; "New temporary session" starts a clean one that is **not** written |
| Footer text | Matches real bundled model hash, real Ollama probe, real `lastResult` |

Evidence: `desktop/evidence/ui.json` written by `--ui-check`, plus an
offscreen PNG via the existing `--render-management-check` path.

## Group C — KittenTTS desktop TTS adapter (D3)

The voice architecture in [`docs/MEMORY_AND_VOICE.md`](../MEMORY_AND_VOICE.md)
already names three decoupled adapters — STT, Chat-LLM, TTS — and currently
the desktop implements **none of them**. The user has asked for KittenTTS to
be added. This group lands the smallest TTS adapter that satisfies the
existing "Voice" row in `DesktopDestinations.kt` and the "Real data and
bundled model requirement" rule in `AGENTS.md`.

### C1 — `TtsAdapter` interface

Create `desktopApp/src/main/kotlin/com/meshlit/desktop/tts/TtsAdapter.kt`:

```kotlin
package com.meshlit.desktop.tts
import java.nio.file.Path

interface TtsAdapter {
    val id: String
    val displayName: String
    val licenseNote: String
    fun isReady(): Boolean
    fun voices(): List<String>
    /** PCM16 mono 22 050 Hz, ≤ 4 000 chars input, bounded 64 KiB output cap. */
    fun synthesize(text: String, voice: String, out: Path): SynthesisResult
    fun close()
}
data class SynthesisResult(val durationMs: Long, val sampleRate: Int, val channels: Int, val pcmBytes: Long)
```

This is the analogue of `HostClient` for synthesis, deliberately narrow so
the Group B composer can later pipe the LLM reply into it. The three
decoupled adapters (STT, Chat-LLM, TTS) from the parent voice diagram are
preserved: this slice only adds TTS.

### C2 — `KittenTtsAdapter`

The desktop JVM cannot load the Python `kittenml` package directly. Two
viable integration paths exist; **C2 picks path (a)** because the
desktop is intentionally language-agnostic for the `companions/` style
helpers and already has `companions/crawler` as a Python subprocess
([`AGENTS.md`](../../AGENTS.md) line "companions/crawler: optional
host-side Crawl4AI service. Python/Chromium are not bundled in the APK.").

**Path (a):** `KittenTtsAdapter` shells out to a small Python helper at
`companions/kitten-tts/synthesize.py`. The JVM holds the adapter, the
helper holds the `kittenml` import. WAV output is read by the adapter
and converted to signed PCM16 mono 22 050 Hz in a `finally` block before
the temporary file is deleted. The Python helper is invoked via
`ProcessBuilder` with a 30-second deadline and stdin/stdout captured;
the adapter does not accept shell metacharacters in the `text` or
`voice` arguments and rejects inputs over 4 000 characters.

**Path (b) (rejected for D3):** call into ONNX runtime from JVM. This
would require either re-exporting KittenTTS to ONNX (not currently
maintained upstream as of the 2026-10-10 README) or implementing a
JNI wrapper around the Python model. Either path is a separate
qualification and is tracked in the deferred-scope list below.

The adapter surfaces a `lastError` field that the new TTS dialog reads
to show why a synthesis failed. No retry, no model upgrade, no silent
fallback to a different TTS provider.

### C3 — license gate for KittenTTS weights

This is the riskiest item. As of 2026-10-10, the KittenTTS repository
[README](https://github.com/KittenML/KittenTTS) ships **two** licence
artefacts:

| Asset | Licence | Commercial use |
| --- | --- | --- |
| Code | Apache-2.0 | OK |
| `kitten-tts-mini` (80 M), `kitten-tts-micro` (40 M), `kitten-tts-nano` (15 M) model weights | MIT (per repository `LICENSES` folder) | OK, with attribution |
| **KittenTTS 2** weights | "Stellon Labs Community License" with a $1 M revenue/funding commercial-use threshold | Restricted below that threshold; explicit acceptance required |
| Speaker / pyannote diarisation | MIT | OK |

**D3 ships only the legacy MIT-licensed model variants** (`nano`, `micro`,
`mini`) and **does not** download or bundle `KittenTTS 2` weights. The
adapter defaults to `kitten-tts-mini` (≈ 80 MB, English-only) so the
download fits the existing 256 MiB voice-pack size cap from
[`prepare-voice-packs.py`](../../scripts/prepare-voice-packs.py). A
separate `KittenTts2Adapter` is **not** added in this milestone; if the
user wants the 1.7 B / 506 MiB model it lands behind an explicit
"Accept Stellon Labs Community License" checkbox in the TTS dialog and
downloads the weights from the upstream URL with a recorded SHA-256
verify. That adapter is a separate, named gate, and the menu row
labelled "KittenTTS 2" is greyed out in the meantime.

This matches the existing voice-pack pattern
([`docs/MEMORY_AND_VOICE.md`](../MEMORY_AND_VOICE.md)) where the Lessac
voice archive "commercial rights not qualified" is preserved as the
owner's experimental research artefact, not the default.

### C4 — `scripts/prepare-kitten-tts.py`

Add `scripts/prepare-kitten-tts.py` analogous to
`scripts/prepare-voice-packs.py`:

- Takes `--archive <upstream tarball>` and `--output <zip>`.
- Hardcodes the upstream archive SHA-256 (the same value already known
  to the user from the `AGENTS.md` "Real data and bundled model
  requirement" rule).
- Verifies the archive SHA-256 before extraction.
- Extracts only the named `kitten_tts_mini_*.onnx` / `tokens.txt` /
  `config.json` files (no `..`, no absolute paths, no symlinks, no
  > 128 MiB single file, no > 256 MiB total).
- Emits a `meshlit-voice-pack/1` manifest with `kind: "KITTEN_TTS"`,
  `language: "en"`, and `license: "MIT (KittenTTS weights, see
  https://github.com/KittenML/KittenTTS/blob/main/LICENSES)"`.
- Writes a `.part` file, then atomic-renames.

This script is **not** a license waiver: it just packages what the
upstream release ships.

### C5 — TTS dialog and a `--tts-check` evidence harness

Add `desktopApp/src/main/kotlin/com/meshlit/desktop/DesktopTtsDialog.kt`
that:

1. Lists installed voice packs (read the same `~/.meshlit/desktop/voice/`
   directory the proposed `SessionStore` from B4 uses).
2. Lets the user pick a voice, paste up to 4 000 characters, and click
   "Preview". Preview writes a `.wav` to a user-chosen path and
   shows the resulting duration.
3. Surfaces the adapter's `lastError` if the preview fails.

The new `--tts-check` launcher flag (analogous to `--local-check` in
[`LocalCheck.kt`](../../desktopApp/src/main/kotlin/com/meshlit/desktop/LocalCheck.kt))
runs a real synthesis of the literal string "Meshlit voice check." and
writes a JSON to `desktop/evidence/tts.json` with:

- `adapter`: `kitten-tts` (only the MIT-licensed adapter is invoked)
- `model`: e.g. `kitten-tts-mini`
- `license`: the manifest's `license` field
- `sha256_zip`: the prepared voice-pack SHA-256
- `duration_ms`: real `sf.info`-style measurement of the produced WAV
- `pcm_bytes`: the actual byte count
- `sample_rate`: 22 050
- `channels`: 1

The check is **not** a quality claim; it is a transport/lifecycle claim
analogous to the existing `--local-check` and `--monitor-check`.

### C6 — wire KittenTTS into the existing "Voice" destination

`DesktopDestinations.kt:28` ("voice") flips to `available = true` **only
after** `--tts-check` passes against a verified voice pack on this
machine, and **only** if the user-visible license is MIT (the
Stellon-licensed KittenTTS 2 path stays greyed until a separate gate
passes). The TTS dialog is opened from the existing "Voice" row and
from a future chat-composer hook; the latter is out of scope here.

### D3 acceptance

| Check | Required result |
| --- | --- |
| `prepare-kitten-tts.py` rejects an archive with a wrong SHA-256 | `Upstream archive SHA-256 mismatch` and exit code ≠ 0 |
| `prepare-kitten-tts.py` produces a manifest | `meshlit-voice-pack/1`, `kind: "KITTEN_TTS"`, ≤ 256 MiB total |
| `KittenTtsAdapter.isReady()` without a voice pack | `false` with `lastError: "no voice pack installed"` |
| `KittenTtsAdapter.synthesize("hello", "expr-0", tmp.wav)` with pack present | WAV file exists; PCM16 mono 22 050 Hz; duration > 0 |
| Same call with 4 001-char input | Rejected with `text too long` before invoking Python |
| TTS dialog "Preview" against a real pack | Audio is played via the existing `EngineSettings` sound path, **only if** the user explicitly enables it; otherwise the WAV is written and the duration is shown |
| `--tts-check` JSON | Contains all fields above; `duration_ms > 0`; `pcm_bytes > 0` |
| KittenTTS 2 menu row | Greyed with "Accept Stellon Labs Community License" caption until a separate adapter lands |

Evidence: `desktop/evidence/tts.json` from a real run, plus the
prepared voice-pack zip + its manifest.

## Group D — honest model inventory (D0)

The model picker cannot claim a row that the desktop cannot actually run.
This group makes that honest, without writing fake data and without
spending a stabilisation slice on the S0 gate.

### D0.1 — read the bundled model

`desktopApp/src/main/kotlin/com/meshlit/desktop/ModelInventory.kt` reads
`desktopApp/distribution/bundled-model.json` and, for the file in the
default `~/.meshlit/desktop/models/` directory, computes SHA-256 with
`java.security.MessageDigest` and byte size. The result is wrapped in a
`BundleModelStatus(bundled: Boolean, present: Boolean, sizeOk: Boolean,
hashOk: Boolean, sha256: String)`. None of the existing UI text is
changed yet — this is a probe.

### D0.2 — read imported model references

The existing `ModelManager.kt` already keeps an imported list. Extend
`ModelInventory` to also read it and produce a list of
`ImportedModelStatus(name, path, present, sizeOk, hashOk)`. Path is
the user-visible file path, not the absolute filesystem path with the
user's home expanded in the UI.

### D0.3 — probe running Ollama

`ModelInventory` calls the new `HostClient.health()` (from A3) against
any saved `SavedNode` whose endpoint is loopback. It records
`OllamaStatus(reachable: Boolean, modelCount: Int, models: List<String>,
probeAt: Instant)`. The names returned by `/v1/models` are surfaced in
the picker — but **only** those that also pass a `GET /v1/models/{id}`
availability check on the next refresh, because Ollama's
`/v1/models` can list models that are not currently loaded. The
"currently loaded" check is bounded to a 3-second request and is
allowed to fail; the failure shows the model as "available, not loaded"
rather than fabricating a loaded state.

### D0.4 — `--models-check` evidence

The new `--models-check` launcher flag writes
`desktop/evidence/models.json` containing every section above plus a
`hostCapabilities` block that says what the desktop's CPUID probe
returned (from the existing
[`CpuBackend.kt`](../../desktopApp/src/main/kotlin/com/meshlit/desktop/CpuBackend.kt)).
No `enabled = true` flags are invented; only what was probed is written.

### D0.5 — model picker honesty

`ModelManager.kt` already lists bundled and imported rows. With the
inventory data, add three small captions next to each row:

- Bundled: hash prefix of the verified file, or red "missing/changed"
  if the file is gone or the hash does not match.
- Imported: file path (no `~`), size, and the same hash check.
- Ollama: model name, "available" or "available, not loaded" badge.

The picker does **not** show models from `desktop-features.json` that
the desktop does not actually have. If a `Phi-3.5 ONNX` row appears in
the catalog, it stays under the "Hard / Phase 2" gate from
[`docs/models/catalog-classification.md`](../models/catalog-classification.md)
and is not added to the desktop picker as if it shipped.

### D0 acceptance

| Check | Required result |
| --- | --- |
| `ModelInventory` with the bundled model present and verified | `BundleModelStatus(bundled=true, present=true, sizeOk=true, hashOk=true)` |
| Same, with the file moved | `present=false`; picker shows "missing"; no retry is silently attempted |
| Same, with the file size altered | `sizeOk=false`; picker shows "changed"; refuses to load |
| Imported row whose path no longer exists | `present=false`; "Forget" button still works |
| Ollama with the running `gemma4:latest` and `gemma4:e4b` | `OllamaStatus(reachable=true, modelCount=2, models=[...])` |
| Ollama unreachable | `reachable=false, modelCount=0`; picker shows "Ollama not reachable" with the last probe time |
| `--models-check` JSON | All sections present; `hostCapabilities` matches the CPUID output of this Mac |

Evidence: `desktop/evidence/models.json` from a real run.

## Build and acceptance procedure (D0–D4)

The D-gates use the same toolchain as the parent stabilisation guide
[`STABILIZATION_BUILD_GUIDE.md`](STABILIZATION_BUILD_GUIDE.md). The
shortest real sequence is:

```sh
# Source / JDK / model inputs (see parent guide §2–3).
cd "$MESHLIT_REPO"
meshlit_gradle :desktopApp:test

# Build a real local candidate; do not publish it.
python3 scripts/package-macos-preview.py \
  --app-image "$MESHLIT_REPO/desktopApp/build/compose/binaries/main/app/MeshlitPreview.app" \
  --jdk-home "$MESHLIT_JDK" \
  --output "$MESHLIT_WORK/d-next-delivery" --scratch "$MESHLIT_WORK/d-next-package" \
  --runtime-provenance "$MESHLIT_RUNTIME_RECORD" \
  --runtime-source "$MESHLIT_RUNTIME_SOURCE" --model "$MESHLIT_MODEL" \
  --server "$MESHLIT_WORK/llama-baseline/bin/llama-server" \
  --fast-server "$MESHLIT_WORK/llama-avx2/bin/llama-server" \
  --cpu-probe "$MESHLIT_WORK/cpu-features" \
  --hyperl-library "$MESHLIT_WORK/hyperl-native/libmeshlit_hyperl.dylib"

# Run the four new evidence flags against the staged app.
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_APP/Contents/MacOS/MeshlitPreview" \
  --models-check "$MESHLIT_WORK/evidence/models.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_APP/Contents/MacOS/MeshlitPreview" \
  --adddevice-check "$MESHLIT_WORK/evidence/adddevice.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_APP/Contents/MacOS/MeshlitPreview" \
  --ui-check "$MESHLIT_WORK/evidence/ui.json"
env -u JAVA_HOME -u JDK_HOME -u CLASSPATH PATH=/usr/bin:/bin:/usr/sbin:/sbin \
  "$MESHLIT_APP/Contents/MacOS/MeshlitPreview" \
  --tts-check "$MESHLIT_WORK/evidence/tts.json"
```

The parent guide's PKG/DMG recovery steps (`pkgutil --expand-full` and
`hdiutil verify`) apply unchanged. D3 is the only gate that depends
on a network-free Python helper plus a downloaded MIT-licensed voice
pack; the rest of the gates run with the network fully disabled.

## Stop conditions for this milestone

Promote nothing on data loss, secret exposure, an unauthorized side
effect, a corrupt model activation, a reproducible core crash, or a
faked TTS/audio outcome. Specifically:

- `--tts-check` must not pass if the WAV it produces is silent, is the
  wrong sample rate, or has the wrong byte count.
- `--models-check` must not pass if any bundled/imported file's hash
  check is skipped because of an `onFailure { /* swallow */ }`.
- `--adddevice-check` must not pass if a non-loopback `http://` host
  is accepted, or if the loopback Ollama path still requires a token.
- `--ui-check` must not pass if any of the 18 greyed `available=false`
  rows became silently enabled.

The previous working app is retained as the rollback target. The
parent S0–S6 stabilisation plan remains the source of truth for
distributed-execution and S4 authority gates; this D-milestone is
explicitly **not** a production admission and does not authorize a
merge or a new release.

## Deferred scope (this milestone)

- GPU/NPU dispatch (parent S0–S6 backlog).
- Full Voice dialog end-to-end with STT and listening UI; only TTS
  lands in D3.
- KittenTTS 2 / Stellon Labs Community License path; tracked
  separately, behind an explicit acceptance gate.
- Distributed execution and two-device S5 gate; the parent plan
  remains the source.
- A full Material 3 / "Gemini-style" redesign; this milestone only
  adds motion, regrouping, and an honest pill composer.
- S4 common authority / agent dispatcher.
