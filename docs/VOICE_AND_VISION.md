# Voice and vision companion
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

2026-10-30 — Studio source integration; audio/vision hardware qualification pending.

Studio → **Voice and vision** opens the working runtime/tuning dashboard. It
starts an independently installed OpenLive checkout and reads/writes its actual
settings API. This does not embed OpenLive in Android or export the bundled
Meshlit SDK model as an API. Local compatible servers (Ollama, llama.cpp, etc.)
can answer; hardware, model format and vision capabilities still determine support.
A text model can use a separately configured vision model.

## Install once

Review and install upstream outside the Meshlit repository. Reviewed source:
[OpenLive](https://github.com/katipally/openlive/tree/c9121ebe6ed8217f5b8e7b77c78cb36620c1a252),
Apache-2.0, copyright 2026 Yashwanth Reddy Katipally. No upstream code, binaries,
models or dependencies are bundled by this integration. Retain its LICENSE,
NOTICE and third-party notices when distributing an installed companion.

```sh
git clone https://github.com/katipally/openlive.git
cd openlive
git checkout c9121ebe6ed8217f5b8e7b77c78cb36620c1a252
ELECTRON_SKIP_BINARY_DOWNLOAD=1 pnpm --filter @openlive/web... --filter @openlive/agent... install --frozen-lockfile --fetch-timeout=180000
pnpm --filter @openlive/web exec node scripts/copy-voice-assets.mjs
```

Use Node >=22.13 and the package-manager version in upstream package.json.
Dependency installation runs upstream scripts; Meshlit never installs them or
models automatically. Different upstream revisions are unqualified.

## Use and tune

1. Choose the installed source folder in the dashboard. Start and select your
   installed Node executable. macOS/Linux managed launch is supported; Windows
   currently uses upstream's separate desktop application.
2. Open **call and speech tuning**. Configure providers in OpenLive's Settings →
   Models. For a local compatible server use its local address and a real model
   reported by that server. Add other providers through OpenLive. Credentials
   remain in OpenLive; Meshlit profiles and keys are not transferred.
3. Load saved settings in Meshlit's dashboard. Set exact answer and vision
   provider/model IDs, the local server URL, supported response effort and
   instructions. Save writes OpenLive's settings, then reads them back. Unsupported
   settings and request failures are visible. Provider IDs must already exist;
   entering an ID does not install a provider or prove model compatibility.
4. Tune actual speech engines, language, voice and speed in OpenLive Settings.
   Download models there explicitly. Start a new call for model-setting changes.
   Browser permissions authorize microphone, camera and screen capture.
5. End the call to release capture; **Stop runtime** terminates Meshlit's owned
   server process groups. Closing the dashboard leaves the servers running;
   unloading the extension stops them. An unexpected child exit stops its sibling.

Both listeners bind 127.0.0.1 (47833 agent, 47834 web). Occupied ports fail;
Meshlit does not run upstream's port-sweeping predev script or kill other services.
Startup is bounded to 90 seconds and Stop interrupts it. Telemetry environment
switches are disabled. OpenLive's existing user data remains in its own store.

Hosted models receive transcripts and enabled images and may charge. Their calls
are outside Meshlit's ledger/budget enforcement. Local speech does not imply a
local answering model. Do not configure remote providers if you require offline
operation. Flow automation and coding-agent permissions remain upstream controls;
this integration does not grant Meshlit app-access scopes.

## Android tuning

Android uses its existing native speech and chat runtime, not the Node companion.
Chat microphone → tune icon → Voice tuning now saves PCM microphone threshold,
pause duration (300–2000 ms), and Android playback speed/pitch. Capture uses the
saved threshold and silence cutoff, retains the 30-second audio cap and cancellation,
and defaults to the prior behavior. Android recognizer owns its endpoint detection;
these capture sliders are hidden for that input. Speed/pitch affect Android TTS
only and are bounded after applying the selected style. Offline/provider synthesis
keeps its existing supported controls. Sample playback and recognition-only testing
exercise real engines. Reset restores tuning defaults.

The existing selected chat model answers voice turns; speech and vision providers
remain independently selected. Existing photo/camera input is separate from live
video. This does not add OpenLive to Android, automatic acoustic barge-in, or local
vision support to text-only GGUF models.

## Voice review fixes

The 2026-10-30 review fixed concrete preview, cleanup, cancellation, PCM-format and Stop-ownership issues. Voice preview no longer depends
on recognition-provider readiness. Failed native cleanup remains visible even
when Stop canceled the session, and blocks further chat voice sessions until the
app process restarts. Cleanup runs in a non-cancellable context so a normal Stop
does not falsely trip that block. The older SDK streaming wrapper propagates
coroutine cancellation and releases the recorder even if stopping fails. Its
playback now validates the reported format/rate, converts float PCM to PCM16,
bounds total audio, uses cancellable writes and drains queued samples before stop.
Sample-rate changes and compressed/unknown formats fail instead of producing
incorrect playback. The direct offline adapter shares the same PCM conversion. Standalone playback
now owns a stop latch and flush callback; new playback waits for both track cleanup
and all SDK Stop calls so a late Stop cannot terminate a replacement. Invalid text
or playback rates fail before opening Android audio.

Regression evidence: 293 core-inference and 252 app tests per flavor passed
(797 total); Studio has 36 passing tests. The [Samsung evidence](desktop/evidence/voice-android-20261030.json) passes two
instrumented tests: real Whisper/Piper plus local LLM execution, and two microphone
capture/cancel/reopen cycles. It records 100 PCM callbacks and zero speech-detected
callbacks; live human recognition was not tested. Only a known-text synthetic
Piper sample was saved, not microphone audio. Physical tests distinguish model
transcription/synthesis/playback from microphone capture and release. Successful
playback callbacks do not independently prove speaker audibility or voice quality.
Automatic acoustic interruption and full-duplex Android conversation remain open.

## Evidence and remaining gates

Node integration tests launch small local HTTP fixture processes to verify real
process startup/termination, conflict refusal and persisted settings round trips.
They do not qualify OpenLive's engines, microphone, camera, GPU, latency or model
output. The pinned companion was installed on Intel macOS using Node 24.18.0. The
[real-runtime evidence](desktop/evidence/openlive-20261030.json) passes health,
settings/provider reads, settings save, persistence after process restart, web
page response and shutdown of both services. The recorded startup took 6.264 seconds
on this machine; this is a smoke-test observation, not a latency benchmark. Qualification used an isolated disposable profile and performed
no model generation, media capture or hosted requests. Re-run with:

```sh
node companions/studio-runtime/qualify-voice.cjs /absolute/openlive /absolute/node /tmp/openlive-evidence.json
```

Physical OpenLive audio/vision testing, Windows lifecycle, embedding OpenLive in Android,
direct Meshlit model/usage integration and packaged companion distribution remain open. Optional [Pipecat](https://github.com/pipecat-ai/pipecat)
and [Hugging Face speech-to-speech](https://github.com/huggingface/speech-to-speech)
were reviewed as alternatives; neither has an execution adapter yet.

Natural and free voice expansion (2026-10-31): see [Kokoro/Pocket and ElevenLabs setup](NATURAL_VOICES.md) for current implementation and evidence.
