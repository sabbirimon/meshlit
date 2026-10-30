# Studio online models and coding workspace
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

## Managed gateways and durable IDE usage — 2026-10-30

The Code OSS development workbench now has separate **Start local gateway**,
**Stop**, and **Copy API address** actions for OmniRoute and FreeLLMAPI. Start
opens the actual upstream dashboard inside Meshlit's integrated browser. The
owner completes that gateway's own setup/login and adds provider credentials;
Meshlit does not manufacture free API keys or reuse another application's keys.
Catalog free/paid/provider filters and connection checks remain separate from
account entitlements and generation. Copy API address copies a URL, not a key.

The reviewed development inputs are OmniRoute 3.8.52 and FreeLLMAPI server 0.2.1.
`prepare_gateways.py` produces an explicit local manifest; pass it through
`prepare_workbench.py --gateways`. Launch verifies version and entry checksum,
uses a private persistent data directory and loopback listener, clears inherited
provider/CLI credentials, disables background discovery/probes, and manages only
its own child process. An occupied port is an error, not permission to adopt or
stop another service. These checks do not hash every dependency or sandbox the
upstream application. The local cache paths are development dependencies;
portable installers still need complete runtime packaging, notices and platform
qualification. A build without the manifest reports the runtime unavailable.

**Usage → Usage & budgets** now reads the existing encrypted ledger, shows
reported token totals and unknowns, the newest 200 records, model/status filters,
and per-currency estimated spent/reserved amounts. Policy edits persist through
the Kotlin ledger: free-only, positive monthly/per-request limits, and removal
of one currency without losing others. Unknown pricing stays unknown. Opaque
gateways cannot be protected by these estimates; existing admission rules apply.
These are device-local admission estimates, not provider invoice caps.

Reproducible opt-in qualification scripts use disposable state:
`qualify_local_chat.py` exercises real bundled inference, SDK sessions, encrypted
restart and budgets; `qualify-gateways.cjs` exercises real upstream startup,
unauthenticated API-key rejection and owned-process shutdown. Neither needs
owner credentials or paid calls. See
[evidence](evidence/studio-afk-20261030.json) for actual results and open gates.

## Earlier native Studio implementation (historical)

Desktop first, as requested on 2026-10-26. Mobile retains the verified Meshlit name/M icon update; a simpler chat selector is deferred until the desktop flow is qualified.

## Online models

Open **Online models** from the sidebar, chat composer or Dashboards card. Choose OpenRouter, Hugging Face, OmniRoute, FreeLLMAPI, OpenAI or a custom compatible service. Review the URL, enter a scoped API key (or unlock/use a saved key), explicitly allow the service and select **Connect / fetch models**. Filter the real returned catalog and select a model to return to chat. Chat's model selector also switches between returned models or back to local inference. Changing services clears the conversation and authentication; selecting a model on the same host does not send a request until Send.

**Save connection address** persists only validated URL/protocol/gateway metadata for that service. Keys require the separate credential vault. Connection consent is session-only. Optional gateways require the owner's deployment; Meshlit does not install either gateway, assume free access, or add automatic routing. Free/paid availability and quotas belong to the provider account. Strict free-only/currency-budget policies block opaque gateway requests; these remain device-local estimates, not invoice caps.

Basic controls cover service, URL, key, consent, connect, model and Usage. Protocol and gateway flags are collapsed under Advanced. Existing local-host/Colibri technical settings remain in their dedicated pages. OpenRouter has an explicit, domain-bound `/api/v1` base path; other hosts retain the existing fixed-path validation. Model catalogs are bounded to 4 MiB, 2,048 entries and 32 JSON levels, with filtered presentation. Other response bounds are unchanged.

Official endpoint references checked for this implementation: [OpenRouter model catalog](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties), [Hugging Face compatible chat API](https://huggingface.co/docs/inference-providers/tasks/chat-completion). No authenticated catalog or paid generation test has been performed.

## Embedded coding workspace

Open **Workspace** from the sidebar or **Explore code** from the chat welcome. Choose a local project folder, browse a bounded file list and edit a UTF-8 text file up to 1 MiB. **Save** uses atomic replacement and refuses stale edits when the file changed outside Studio. Symlink traversal and files outside the selected root are rejected. Hidden files are omitted from the explorer; the explicit file picker still requires a file inside the workspace. Unsaved edits prompt before changing files/folders, closing the workspace or quitting.

**Focus**, **Code**, **Review** and **Custom** are Meshlit views, not copies of proprietary applications. Code shows the file explorer, Focus reduces it, Review shows the original and editable text, and Custom lets the user toggle files/terminal. Layout/panel preferences persist. The terminal is the existing human-only bounded command runner, not an interactive PTY or autonomous model tool. **Add file to chat draft** copies at most 10,000 characters only after a click; the user reviews the draft and presses Send. Opening a folder never sends its contents to a model.

This is an initial native editor beside the existing chat, not the full VS Code workbench/Monaco SDK. Language servers, debugger, rich syntax highlighting and multi-file patch review require further implementation. The bounded assistant file runner added below is separate from a full IDE agent engine. No performance or feature equivalence to Codex/Claude Code/Antigravity is claimed.

## VS Code extension

The dependency-free extension in `companions/vscode` contributes **Meshlit: Open Workspace in Studio**. It requires a trusted local workspace and uses a user-level absolute launcher, ignoring repository launcher overrides. It passes the folder as a literal argument without a shell. The initial extension has no network client, credentials or independent budget authority. VS Code chat/model-provider integration remains a later contract, rather than bypassing Studio's usage controls.

The local VSIX was accepted by the installed VS Code CLI and the Meshlit launcher configured in user settings with a local backup. Command-palette interaction remains to be qualified. Published beta.5 installers predate this workspace entry point; the configured development launcher runs the current source. See the [extension guide](../../companions/vscode/README.md).

## Verification

Android label/icon assembly and lint passed for both variants: zero errors, 396 existing warnings and 19 hints per flavor. All six APK outputs were inspected for the Meshlit label and exact legacy icon bytes. No phone is attached, so physical launcher appearance remains open.

Studio regression passed: 56 passed, 1 physical SSH fixture skipped, zero failures/errors. Native online/coding panel renders were inspected, and document tracking, feature map and whitespace checks passed. Tests cover OpenRouter path binding, bounded large catalogs, connection metadata validation, credential leases, workspace containment, external-edit conflicts and native rendering. One initial editor test exposed macOS `/var` versus `/private/var` aliases; selected-root normalization was corrected. Extension argument tests passed (2); its VSIX installed successfully. No provider credential or paid API request was used.

## Visual design continuation (2026-10-26)

The owner's three Gemini references apply to chat. The separate coding window uses an IDE-style file explorer, editor, optional terminal and real assistant chat. The earlier automatic split layout is replaced: Studio opens chat first, and Workspace opens the coding view deliberately. A supplied project path is still used when the coding view opens.

Design tokens: canvas #131314; surfaces #1E1F20; text #E3E3E3; primary #A8C7FA; secondary #C4B5FD. Figtree is packaged with its OFL licence. Buttons use sentence case and no tracked letter spacing. Chat content is bounded at 780 px; the 208 px navigation rail yields at compact widths. Welcome suggestions use four columns or two at narrower widths and set drafts without sending. The model selector lives in the composer. Settings, service tiles and dashboard panels inherit the common desktop theme.

The design was revised after the supplied references: the initial left-aligned greeting and plain action links were replaced by a centered Meshlit wordmark and suggestion cards. The coding layout is separate from this chat design. It does not claim Google Antigravity's agent execution, a complete VS Code engine, or new backend capabilities.

Validation: `:desktopApp:test` passed on 2026-10-26: 57 cases, 56 passed, one physical SSH test skipped, zero failures/errors. Actual Compose chat renders at 640, 1120 and 1440 px were generated, with compact/wide chat and coding/online panel renders inspected. [Chat render](evidence/studio-chat-redesign.png). No paid model call was made. These tests do not establish installed-package or live-provider qualification.

## Vendor-specific pages and native menus (2026-10-26)

Each service opens an Overview before requesting credentials. Overview links to the real vendor dashboard, public catalog and setup documentation; Models displays the connected API's returned catalog; Connection holds the endpoint, key/vault and explicit consent; Usage separates Meshlit estimates from provider account billing. Clicking the currently selected service no longer disconnects it. Browser navigation never receives the API key. Dashboard addresses may use HTTPS or literal-loopback HTTP, and cannot contain URL credentials, query parameters or fragments.

OmniRoute's documented server defaults are dashboard `http://127.0.0.1:20128` and API `/v1`; FreeLLMAPI server defaults are dashboard `http://127.0.0.1:3001` and API `/v1`. Both must already be installed/running. Their dashboard address is independently editable and persisted; desktop/development deployments may use different ports. No gateway was installed or launched by this change. Sources: [OmniRoute](https://github.com/cheaper-inference/OmniRoute), [FreeLLMAPI](https://github.com/tashfeenahmed/freellmapi). A vendor's public product list is not evidence of account access, quota or price.

Main, coding and settings windows now supply File, Edit, View, Terminal, Window and Help menus. File includes new chat, open folder, save file, VS Code handoff and quit. Edit actions are explicitly chat-draft copy/paste/clear; normal editor shortcuts remain handled by the text component. View navigates the implemented dashboards/settings; coding additionally exposes explorer/focus actions. Terminal opens the existing command runner. No fake Run/debug actions are added. Quitting still checks dirty code.

**VS Code ↗** in the workspace opens the current folder in installed VS Code through its registered, percent-encoded file URI. File → Open project in VS Code chooses a folder when outside the editor. VS Code must be installed; it controls workspace trust, extensions, debugging and terminal execution. The existing Meshlit extension uses the real VS Code Extension API but currently provides only the opposite workspace handoff. [VS Code API](https://code.visualstudio.com/api), [URI handoff](https://code.visualstudio.com/docs/configure/command-line), [Monaco](https://github.com/microsoft/monaco-editor).

Full embedded VS Code/Code OSS remains unfinished. The native Compose editor is not that SDK or a full workbench. A workbench integration still needs its own runtime/extension host, filesystem and terminal boundaries, licensing/distribution decisions, and preservation of Meshlit's credential and budget gates.

Validation: 60 desktop cases, 59 passed, one physical SSH fixture skipped, zero errors/failures. Tests verify safe dashboard URLs, persisted non-secret dashboard metadata and literal encoded VS Code folder paths. Native service and chat renders passed; vendor Overview render inspected. Physical macOS menu interaction, VS Code protocol opening and authenticated vendor accounts are not yet qualified by these headless tests. No paid calls were made.

## Real dashboards inside Studio (2026-10-26)

Choose **Online models → a service → Overview → Open dashboard in Meshlit**.
The page opens in an embedded WebView in a Meshlit window, without an external browser handoff. Catalog, documentation and account links use the same view. This loads the vendor's actual deployed interface; it does not recreate their charts or infer account data. For local gateways, first run the upstream server and supply its dashboard URL. The standard addresses remain `http://127.0.0.1:20128` (OmniRoute) and `http://127.0.0.1:3001` (FreeLLMAPI). Neither default was responding on this Mac during qualification.

**Read page** collects up to 12,000 characters of visible main-document text. Password/input/textarea/contenteditable fields, code, scripts and hidden elements are excluded. Common API-key, bearer-token and secret-label patterns are redacted on the host. The editable preview is still essential: arbitrary secrets cannot be reliably recognized. Source URL with query/fragment removed, capture time and truncation metadata accompany the text. Charts drawn on canvas and embedded frames are not read.

**Add to chat draft** appends reviewed text to the existing draft, labels it untrusted vendor content and returns to chat. Nothing is sent until the user presses Send. The selected local/online model then handles it through the existing chat controller and usage admission. Dashboard buttons themselves are controlled by the vendor and use its billing/limits, independently of Meshlit's local estimates. No automatic routing, account scraping, autonomous dashboard actions or quota reconciliation is added.

The browser has no Meshlit API-key/vault or host-tool bridge. Sign-in occurs on the vendor page. Saved initial addresses permit HTTPS or literal loopback HTTP and reject URL credentials/query/fragment. Top-level origin changes pause for review; this is a navigation UI check, not a network firewall: subresources and initial redirect requests use the browser's own web security. Certificate errors are not bypassed. Downloads, unmanaged popups and embedded-browser-incompatible OAuth/SSO are not supported in this pass. Loading failures remain visible. Login, CAPTCHA and MFA are human operations; authenticated compatibility needs vendor-by-vendor qualification.

Local storage uses a temporary directory with best-effort cleanup when closing. Browser cookies can be shared by origin within the Studio process; this is not a separate persistent profile per vendor and closing a view does not guarantee logout. Cookies are separate from Meshlit's credential vault. No browser history or captured page text is automatically written to Meshlit's usage journal.

Runtime: OpenJFX 21.0.13 platform dependencies, with source/notices from [OpenJFX 21.0.13+1](https://github.com/openjdk/jfx21u/tree/21.0.13%2B1). Native dependencies are resolved during build, not downloaded at first dashboard launch. OpenJFX carries GPLv2 with Classpath Exception plus third-party notices, retained in `desktopApp/distribution/notices` and the application jar. [WebEngine](https://openjfx.io/javadoc/21/javafx.web/javafx/scene/web/WebEngine.html) and [Swing embedding](https://openjfx.io/javadoc/21/javafx.swing/javafx/embed/swing/JFXPanel.html) document the runtime integration. Mac packaging module lists include its Java runtime dependencies; installer signing, distribution source obligations and Windows/Linux live qualification remain release gates.

Validation: `:desktopApp:test` passed (62 cases, 61 passed, one physical SSH fixture skipped). The headful `--dashboard-browser-check /absolute/evidence.txt` entry point ran a loopback HTTP fixture inside an actual Compose SwingPanel/WebView: JavaScript executed; password/textarea values were excluded; a rendered test key was redacted; a cross-origin redirect requested review; an unavailable gateway produced an error; the view and engine closed cleanly with process exit 0. A standalone Swing check also passed. These are renderer/bridge checks, not proof that a real authenticated vendor dashboard or full Code OSS workbench works. The rejected JCEF/Chromium candidate crashed in macOS native window teardown and was removed before this change. No paid provider request was made.

## Shared assistant modes and approval controls (2026-10-27)

Use the composer mode selector, **View → Assistant mode & approvals**, or **Advanced → Assistant modes**. These controls share one session across chat and the coding window. Choose an assistant workspace explicitly, or open a project in the coding window. Changing scope resets approval to Ask. Closing the coding window keeps the selected scope available from chat; the controls show its path. All mode, scope and approval selections reset when the app exits.

- **Chat:** ordinary streaming conversation; no tool interpreter.
- **Plan:** up to four model decisions to inspect files/status, navigate views and produce a plan; the executor rejects file writes even under broader permission.
- **Edit:** up to four model decisions, stopping immediately after one successful file creation/replacement.
- **Autopilot:** up to eight model decisions, a ten-minute coroutine deadline and Stop. Each network request retains its own existing 120-second timeout. A provider still processing an already sent request may charge for it; cancellation is not a refund.

Approval choices are **Ask for approval**, **Approve safe reads**, and **Allow workspace edits**. The latter requires an explicit session confirmation. It means the registered workspace file tools, not unrestricted computer access. The selected model receives approved file/status data. Each generation calls the existing durable budget ledger before dispatch and records real provider token observations afterwards, including interrupted/unknown usage. The displayed token statistic is the last model request; the Usage dashboard contains the individual attempts. Budget estimates are not provider-enforced billing caps.

Tools accept strict structured JSON and exact argument fields. Non-JSON, malformed or unknown actions stop rather than being repaired/retried automatically. Files are bounded to 32,768 characters for assistant context. Replacement requires a preceding read, a unique exact match and the editor's digest-based conflict check. New files require an existing parent and exclusive creation. No delete, shell/terminal, root, arbitrary browser navigation/clicking, credentials or payment tool is available. Private/hidden paths, traversal and symlinks are refused. These are application checks, not an OS sandbox against another local process concurrently modifying directories.

The approval dialog shows proposed changes before execution. Activity shows completed operations. Stop preserves already committed edits; use version control for rollback. The native editor's **Reload** refreshes assistant changes, and stale manual saves are refused. Unsaved editor changes block assistant writes. Reviewed vendor-page text follows the existing Read page → review → Add to chat draft flow and remains untrusted input. Provider account dashboards are human-operated.

## Native launch identity and build tracking (2026-10-27)

The Mac developer launcher uses a named `Meshlit.app` and `com.meshlit.desktop` bundle ID. It probes the shared instance lock, requests activation of an existing GUI, and serializes its own builds so it does not overwrite an in-use app. A raw Gradle `run` is still a development JVM; prefer `scripts/launch-studio-dev.sh --workspace /absolute/project` on Mac. `MESHLIT_PACKAGING_JDK` optionally selects a relocatable JDK; `MESHLIT_DEV_RESOURCES` optionally points to existing verified local model/engine resources without copying another gigabyte. Release packaging retains its self-contained resources and does not use that developer override.

Title/About metadata reports package version/channel (default `2.0.45-dev`), source revision, dirty-tree status and build time. This does not relabel the published `v2.0.0-beta.5` release. A second GUI launch activates the existing main window; it does not silently change its workspace. Closing/crashing releases the OS lock without stale-PID assumptions. Historical `java`/`jcef Helper` notification entries may remain in macOS settings; notification databases and permissions are not reset. The launcher ad-hoc signs and verifies the local bundle. Developer bundles are not Developer ID notarized releases.

Latest validation: 72 desktop tests, 71 passed and one physical SSH fixture skipped, zero errors/failures. New cases cover cross-JVM duplicate exclusion, lock recovery, Plan denial under broad permission, one-edit stopping, denial/cancellation, stale/dirty editors, containment/private paths/symlinks, safe-policy approvals, eight-step limits and strict action parsing. Script syntax checks pass. Native bundle/render/launch outcome is recorded in `evidence/studio-modes-validation.json`. No paid model or authenticated provider calls, model download, Android rebuild or published installer replacement was performed. Live-model agent reliability, full IDE embedding, other-OS installed behavior and mobile controls remain unqualified.


## Separate gateway integrations: source review and plan (2026-10-29)

Status: planned. The owner's requirement is **two separate service dashboards**,
not a combined OmniRoute/FreeLLMAPI account or routing page. Repository inspection
found only model-name search in Compose and ID-only model catalog parsing in the
new workbench. Free/paid/category filters are not implemented. Compose has an
embedded vendor WebView; that view is not yet migrated into Code OSS.

Read-only local checks found no matching applications in `/Applications` or
`~/Applications`, and no listeners at loopback ports 20128, 3001 or 31415. This
is not proof that no source installation or custom-port service exists. No
installer, login, provider call or account change was performed.

### OmniRoute plan

Reviewed source revision `689b1eb573e21ea1bac4467c2ccfd9b8aaa7c518`
(default branch `release/v3.8.53`). Its Electron wrapper owns a local server or
can attach to an existing server; default port 20128 is configurable.

1. Give OmniRoute its own Studio entry with Dashboard, Models, Routes, Usage and
   Connection. Load its actual dashboard inside Studio; retain an independent
   session per deployment/origin. Reuse the running upstream service rather than
   duplicating its database, provider keys or router. Show stopped, connecting,
   login-required and ready states from actual observations.
2. Offer existing desktop app/local server/remote server setup. Keep dashboard
   address separate from `/v1` API address and verify identity/version before
   attaching. Starting an installed app is explicit; downloads and updates are
   optional, pinned and verified. Inspect upstream OmniCopilot/Open VSX integration
   for reuse, but qualify Code OSS API/auth compatibility before depending on it.
3. Use upstream onboarding/password login or configured OIDC in the vendor UI.
   The inspected code permits deployments to disable password login when OIDC is
   active. Do not invent a shared Meshlit/vendor login or copy cookies/API keys
   across origins. Review auth redirects and use the provider's supported OAuth
   flow; an embedded-browser restriction must produce an explicit explanation.
4. Add per-OmniRoute model filters: All, Free tier, Paid, Subscription/included,
   Trial credits, Unknown; additional provider, capability, availability and
   configured-account filters. Treat catalog classification separately from actual
   route eligibility. Validate `/api/free-tier/summary` and pricing read adapters
   against the pinned deployment and required auth. Retain source and freshness;
   public free-tier totals do not prove the user's account balance or availability.
5. Keep upstream routing controls and account billing in its dashboard. Chat
   selects an explicit OmniRoute connection/model/route. A free catalog filter
   does not prohibit paid fallback: strict free-only must verify every permitted
   fallback or block the opaque route. Preserve Meshlit's existing conservative
   budget admission until that contract is qualified.

Source: [desktop wrapper](https://github.com/cheaper-inference/OmniRoute/blob/689b1eb573e21ea1bac4467c2ccfd9b8aaa7c518/electron/README.md),
[login](https://github.com/cheaper-inference/OmniRoute/blob/689b1eb573e21ea1bac4467c2ccfd9b8aaa7c518/src/app/api/auth/login/route.ts),
[free-tier summary](https://github.com/cheaper-inference/OmniRoute/blob/689b1eb573e21ea1bac4467c2ccfd9b8aaa7c518/src/app/api/free-tier/summary/route.ts),
[pricing](https://github.com/cheaper-inference/OmniRoute/blob/689b1eb573e21ea1bac4467c2ccfd9b8aaa7c518/src/app/api/pricing/route.ts).

### FreeLLMAPI plan

Reviewed source revision `36826e7cfe9ba281d5815e865045f1333b598f13`
(default branch `main`). Its desktop menu-bar app runs a local router/dashboard:
port 31415 by default, with port scan-up on conflict. Server deployments use a
separate configuration; Meshlit's current 3001 preset is not a desktop detector.

1. Give FreeLLMAPI its own Studio entry with its real Models, Playground, Keys and
   Analytics dashboard pages. Keep its connection, cookies/session, account and
   usage separate from OmniRoute. Attach to the existing service; do not launch
   a second router simply to display its dashboard.
2. Distinguish Desktop app and Server installation in setup. Confirm the actual
   desktop port (including a changed/conflicting port) and show the destination
   before connecting. An open TCP port alone is insufficient service identity.
3. Preserve upstream desktop-local automatic sign-in. The inspected auth routes
   gate this on desktop mode and request locality. Server installs use their
   setup/email/password flow; remote devices must not be treated as localhost.
   Do not retrieve hidden desktop credentials, proxy remote callers into local
   auto-login, or copy desktop auth tokens to a paired phone.
4. Add a distinct FreeLLMAPI filter panel: All, Free tier, Custom endpoint,
   Paid (only when explicitly evidenced), Unknown, plus provider/capability/
   availability filters. FreeLLMAPI's name and a custom OpenAI-compatible endpoint
   are not evidence that generation is free. Catalog subscription freshness is
   separate from model inference price. Read real model/quota metadata through
   versioned adapters, retain unknowns and avoid importing promotional totals as
   account credits.
5. Chat selects a FreeLLMAPI connection and model/route; the gateway retains its
   own provider keys and routing. Show Meshlit request observations separately
   from gateway totals, since other applications may use that gateway. Apply the
   same conservative paid-fallback and unknown-cost checks, independently of
   OmniRoute. Upstream describes personal experimentation usage; review its
   documentation and redistribution terms before optional bundling.

Source: [desktop app](https://github.com/tashfeenahmed/freellmapi/blob/36826e7cfe9ba281d5815e865045f1333b598f13/desktop/README.md),
[authentication](https://github.com/tashfeenahmed/freellmapi/blob/36826e7cfe9ba281d5815e865045f1333b598f13/server/src/routes/auth.ts),
[model routes](https://github.com/tashfeenahmed/freellmapi/blob/36826e7cfe9ba281d5815e865045f1333b598f13/server/src/routes/models.ts).

### Delivery and acceptance

Implement separate deployment profiles and dashboard/session lifecycle first;
then upstream login/logout and model metadata adapters; then filters and chat
selection. Qualify each gateway independently with an actual owner-controlled
installation. Test wrong-service ports, changed port, logout/restart persistence,
origin/session isolation, expired auth, missing/stale pricing, mixed-cost fallback,
and strict-free blocking with zero paid calls. Do not remove CSP/frame protections
to embed a dashboard; qualify a dedicated isolated top-level embedded view when
framing is unsupported. Keep vendor pages outside the agent's host-command bridge.
Read page remains reviewed, redacted, untrusted input; never read login fields.
Android later presents separate simplified service entries via authenticated paired
Studio access; a phone's loopback address is not the desktop's gateway.


### Connection health implementation (2026-10-29)

The Code OSS extension now includes **Models → Check API connection** and
**Meshlit: Check API Connection**. The user selects one saved deployment; its
scoped SecretStorage key is sent only to that endpoint's `GET /models`. No
completion request, automatic retry or background polling is performed. Results
are stored per connection with a check timestamp, never credentials or response
bodies. Request timeout is eight seconds, redirects fail closed, catalog bodies
are bounded to 2 MiB, and insecure non-loopback URLs are rejected.

Statuses distinguish reachable catalog (including an empty catalog or missing
selected model), authentication failed, access denied, rate limited, service
unavailable, unsupported catalog, invalid response, timeout and connection failure.
A redirect/TLS/network error is not diagnosed as a dead provider. Catalog success
is explicitly not generation proof, credit availability or gateway upstream health.
Three Node test groups passed covering these cases with fixture responses; no
live account or paid generation test was made. This is source implementation in
the development IDE, not a rebuilt installer or an Android feature.

Next, separately for OmniRoute and FreeLLMAPI: use documented authenticated
read-only provider/route health and quota adapters. Show **Unknown** for providers
without evidence. A model invocation test must be a distinct, explicitly approved
billable action using durable usage/budget admission, with bounded output and no
hidden failover. Re-check before dispatch rather than treating an old green badge
as a spending or availability guarantee. User-enabled refresh may later poll
bounded read endpoints with backoff, Retry-After handling and manual stop.

## Chat controls and gateway setup (2026-10-30)

Open **Chat** from the Meshlit rail. The composer has a mode chip and a model chip.
Use **+ → Connect a model or gateway**, choose the source/service, enter the API
base and service API key, and select a model returned by `/models` (or enter an
exact model ID when catalog retrieval is unavailable). A dashboard password is not
a gateway API key. After setup, use the model chip to select the saved connection
and its cached models. The header shows the destination. Use **+ → Models and
provider dashboards** for separate OmniRoute/FreeLLMAPI/Hugging Face pages, catalog
refresh, cost/provider filters and the catalog-only health check.

For an already running local OmniRoute the preset is
`http://127.0.0.1:20128/v1`; FreeLLMAPI desktop uses `31415`, server uses `3001`.
These addresses do not start a service. A phone's loopback addresses the phone,
not the Mac: remote connections require the owner's verified HTTPS host/tunnel.
OmniRoute main upstream is https://github.com/cheaper-inference/OmniRoute; its
local dashboard setup and provider credentials remain independent from Meshlit.
FreeLLMAPI main upstream is https://github.com/tashfeenahmed/freellmapi.
No bundled gateway runtime is delivered by this composer patch.

**Chat** has no workspace tools; **Plan** inspects approved files without editing;
**Edit / Build** proposes one reviewed edit per turn; **Autopilot** allows bounded
multiple steps with approvals and diff review retained. Open a local folder for
non-Chat modes. Changes remain unsaved until the owner saves them. This Build label
does not mean shell/build execution. Mode or route changes after the first message
open a new independent session rather than forwarding old conversation history.

**Enter** sends, **Shift+Enter** inserts a newline. Composing an IME character does
not submit. Send becomes Stop while running; New chat stays available. The usage
ledger must be unlocked before generation; this is separate from vendor login.
Reasoning effort, microphone input and unrestricted permission modes are not yet
implemented in the Meshlit composer. Harness has its own model/mode/settings UI.

### Default local model and locked chat

New Studio chats default to the shipped Qwen2.5 1.5B model. Opening a local chat
loads and validates the packaged engine/model; the header reports Loading, Ready
or Failed. This operation does not send a prompt or contact a model provider.
Send requires both a ready local model and the unlocked Meshlit usage ledger.
Use **Unlock chat** and enter the existing Meshlit usage passphrase in the app;
this is separate from a Mac login password or provider account. Drafts remain
editable while locked. There is no automatic reset of an existing vault.
