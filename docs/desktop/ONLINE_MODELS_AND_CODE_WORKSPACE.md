# Studio online models and coding workspace
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Desktop first, as requested on 2026-10-26. Mobile retains the verified Meshlit name/M icon update; a simpler chat selector is deferred until the desktop flow is qualified.

## Online models

Open **Online models** from the sidebar, chat toolbar or Dashboards card. Choose OpenRouter, Hugging Face, OmniRoute, FreeLLMAPI, OpenAI or a custom compatible service. Review the URL, enter a scoped API key (or unlock/use a saved key), explicitly allow the service and select **Connect / fetch models**. Filter the real returned catalog and select a model to return to chat. Chat's **Model** menu also switches between returned models or back to local inference. Changing services clears the conversation and authentication; selecting a model on the same host does not send a request until Send.

**Save connection address** persists only validated URL/protocol/gateway metadata for that service. Keys require the separate credential vault. Connection consent is session-only. Optional gateways require the owner's deployment; Meshlit does not install either gateway, assume free access, or add automatic routing. Free/paid availability and quotas belong to the provider account. Strict free-only/currency-budget policies block opaque gateway requests; these remain device-local estimates, not invoice caps.

Basic controls cover service, URL, key, consent, connect, model and Usage. Protocol and gateway flags are collapsed under Advanced. Existing local-host/Colibri technical settings remain in their dedicated pages. OpenRouter has an explicit, domain-bound `/api/v1` base path; other hosts retain the existing fixed-path validation. Model catalogs are bounded to 4 MiB, 2,048 entries and 32 JSON levels, with filtered presentation. Other response bounds are unchanged.

Official endpoint references checked for this implementation: [OpenRouter model catalog](https://openrouter.ai/docs/api/api-reference/models/list-all-models-and-their-properties), [Hugging Face compatible chat API](https://huggingface.co/docs/inference-providers/tasks/chat-completion). No authenticated catalog or paid generation test has been performed.

## Embedded coding workspace

Open **Code workspace** from the sidebar/chat toolbar. Choose a local project folder, browse a bounded file list and edit a UTF-8 text file up to 1 MiB. **Save** uses atomic replacement and refuses stale edits when the file changed outside Studio. Symlink traversal and files outside the selected root are rejected. Hidden files are omitted from the explorer; the explicit file picker still requires a file inside the workspace. Unsaved edits prompt before changing files/folders, closing the workspace or quitting.

**Focus**, **Code**, **Review** and **Custom** are Meshlit views, not copies of proprietary applications. Code shows the file explorer, Focus reduces it, Review shows the original and editable text, and Custom lets the user toggle files/terminal. Layout/panel preferences persist. The terminal is the existing human-only bounded command runner, not an interactive PTY or autonomous model tool. **Add to chat draft** copies at most 10,000 characters only after a click; the user reviews the draft and presses Send. Opening a folder never sends its contents to a model.

This is an initial native editor beside the existing chat, not the full VS Code workbench/Monaco SDK. Language servers, debugger, rich syntax highlighting, multi-file patch review and autonomous coding require further implementation. No performance or feature equivalence to Codex/Claude Code/Antigravity is claimed.

## VS Code extension

The dependency-free extension in `companions/vscode` contributes **Meshlit: Open Workspace in Studio**. It requires a trusted local workspace and uses a user-level absolute launcher, ignoring repository launcher overrides. It passes the folder as a literal argument without a shell. The initial extension has no network client, credentials or independent budget authority. VS Code chat/model-provider integration remains a later contract, rather than bypassing Studio's usage controls.

The local VSIX was accepted by the installed VS Code CLI and the Meshlit launcher configured in user settings with a local backup. Command-palette interaction remains to be qualified. Published beta.5 installers predate this workspace entry point; the configured development launcher runs the current source. See the [extension guide](../../companions/vscode/README.md).

## Verification

Android label/icon assembly and lint passed for both variants: zero errors, 396 existing warnings and 19 hints per flavor. All six APK outputs were inspected for the Meshlit label and exact legacy icon bytes. No phone is attached, so physical launcher appearance remains open.

Studio regression passed: 56 passed, 1 physical SSH fixture skipped, zero failures/errors. Native online/coding panel renders were inspected, and document tracking, feature map and whitespace checks passed. Tests cover OpenRouter path binding, bounded large catalogs, connection metadata validation, credential leases, workspace containment, external-edit conflicts and native rendering. One initial editor test exposed macOS `/var` versus `/private/var` aliases; selected-root normalization was corrected. Extension argument tests passed (2); its VSIX installed successfully. No provider credential or paid API request was used.
