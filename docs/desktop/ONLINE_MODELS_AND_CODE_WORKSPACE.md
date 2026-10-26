# Studio online models and coding workspace
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

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
