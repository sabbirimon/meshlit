# RunAnywhere sources and browser assistant

Meshlit already uses RunAnywhere Android SDK 0.20.12 for its on-device inference.
That path remains the default. The SDK, standalone llama.cpp fork and Chrome
extension are separate integrations; they are not interchangeable Android modules.

## Optional upstream sources

`sources.lock.json` records reviewed revisions and licenses. Download the two
optional sources into ignored `vendored/` directories with:

```sh
python3 scripts/sync-optional-sources.py
python3 scripts/build-optional-llama.py --jobs 2
```

The downloader fetches pinned Git revisions and refuses mismatched existing
checkouts rather than resetting local work. The build script verifies the pin
and builds a standalone host `llama-server` with CMake. CMake and a C++ compiler
are required. It does not replace the Maven SDK, ship an Android native library,
implement the direct JNI engine stub or automatically start a server. For a
local host server, consult its `--help`, keep its listener on loopback and select
a properly licensed model yourself. The app separately bundles its pinned starter model; this host build script does not download weights.

The RunAnywhere `on-device-browser-agent` directory is an upstream Chrome/Edge
extension companion. Its own AGENTS.md, README and dependency/build instructions
apply when modifying it. Its WASM/WebGPU/offscreen APIs are browser-extension
APIs and cannot simply be included in an Android WebView. Upstream licenses,
NOTICE and attribution are retained. No extension source was copied into app.

## Android browser agent

Settings → Browser opens Meshlit's visible Android WebView. Load a compatible
on-device model in Models first, open an HTTPS site and enter a task. You can use
Suggest → Approve/Reject for each action, or open Autonomy settings and enable
bounded sessions for that exact origin. Navigation and ordinary text filling have
independent opt-ins. Run autonomously performs real observe/infer/validate/act
steps, bounded to 1–20 actions and 120 seconds. Stop, backgrounding the activity,
permission revocation, a stale page or an invalid model action ends the session.

Agents use `BROWSER_STATUS`, `BROWSER_AUTONOMOUS_RUN` (prompt, browserMaxSteps)
and `BROWSER_STOP` through the durable command backend. Enable global BROWSER
delegation, per-origin Allow delegated agents and any enrolled-device BROWSER
scope separately. Commands cannot grant permissions or open a hidden session.
Credential/policy edits remain human-only. The coordinator enforces an on-device
engine allowlist under its dispatch lock, so backend replacement cannot route a
browser page to a cluster/network plugin. Browser inference also suppresses
prompt/result broadcasts, preventing legacy chat listeners from saving page
snapshots. Browser prompt admission is encrypted;
page snapshots are sent to local inference, not to the crawler/cloud provider.

Autonomous clicks are restricted to normal same-origin HTTPS links and navigate
to their observed URL without dispatching site click handlers. Action buttons,
form submission, downloads, payments and sensitive actions need manual review.
Opted-in ordinary text fields are filled without firing input/change events;
complex JavaScript forms may therefore require human interaction. Login/CAPTCHA/
OTP fields pause the loop. Label/DOM heuristics are not perfect classifiers of
site intent: keep browsing scope narrow. A model `done` is explicitly unverified.
The 135M starter is an inference smoke model, not a proven browser planner; an
invalid JSON response stops rather than substituting a fabricated action.

Saved login sharing is a separate human-confirmed flow using exact-origin bound
WEB_LOGIN environments. Passwords never become model input, and filling does not
submit login. Page scripts can read filled values. Frames, ambiguous forms and
MFA require a human.

Main-frame navigation uses the exact manually approved HTTPS origin, including
non-default ports. Ordinary website subresources still use the network; this is
not an outbound origin firewall. File/content access, mixed content, automatic
new windows and third-party cookies are disabled; no native JavaScript bridge.
Snapshots use the current viewport, at most 80 controls, 5,000 text characters
and 24,000 serialized characters. Oversized links are omitted rather than
navigated to a truncated URL. Scrolling changes the observed control set.
Frames, shadow DOM and complex widgets may be absent from this bounded snapshot.
This is Meshlit's embedded browser, not unrestricted Chrome/Samsung Internet
control. External browser operation uses the separately opted-in Android Accessibility
tools. In Settings → OpenClaw and autonomy, choose a detected installed browser,
review/save its package scope, enable delegation and grant Accessibility in
Android settings. Agents use `android_control` open/snapshot/click/type/back.
This generic UI tool path has not been validated on the Samsung; it has no
WebView DOM indices or browser-extension parity.

Unit policy checks, real WebView test-page checks and a successful local-model
real-site task are distinct evidence. Physical-device/browser-task acceptance is
pending until recorded in PROGRESS.md.
