# Meshlit Studio for VS Code — initial workspace handoff
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Run **Meshlit: Open Workspace in Studio** from the Command Palette. Choose a trusted Meshlit executable/app when prompted. Local trusted folders only; repository settings cannot override the launcher, and no shell command is constructed from the folder path. Nothing is uploaded and no model call occurs.

This first extension opens the embedded Meshlit coding workspace. It does not yet provide a VS Code chat participant, language server, debugger, autonomous edits or shared usage-budget authority. Those need separate contracts and qualification. Existing beta.5 installers predate the workspace entry point; use a newly built app or the repository's `scripts/launch-studio-dev.sh` with JDK 21/Android SDK and the existing Gradle cache configured.

Package with `python3 scripts/package-vscode-extension.py /absolute/output.vsix`, then VS Code → Extensions → Install from VSIX. For development, start VS Code with `--extensionDevelopmentPath=/absolute/repository/companions/vscode`. `node --test companions/vscode/extension.test.cjs` checks argument boundaries. No runtime npm dependencies, telemetry, bundled VS Code code or proprietary assets.

Reference: [VS Code extension manifest](https://code.visualstudio.com/api/references/extension-manifest), [Workspace Trust](https://code.visualstudio.com/api/extension-guides/workspace-trust).
