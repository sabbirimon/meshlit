# Code workspace and permission setup

## Offline source editor
Settings or drawer → Code workspace embeds bundled open-source CodeMirror 6.
It supports source files, syntax highlighting for Kotlin/Python/JavaScript/JSON,
line numbers, search, undo/redo and explicit save. Editor scripts and third-party
notices are bundled offline. No arbitrary web pages receive the native save bridge.
Files live under app-managed `code-workspace`; filenames cannot escape that directory.
Each file is capped at 1 MiB, the workspace at 64 files/20 MiB. Saves use SHA preconditions
and atomic replacement to avoid silent concurrent overwrites.

This is a source editor, not a complete Android APK compiler or bundled VS Code runtime.
Optional code-server requires a separately provisioned Linux host/runtime with its own
permissions and toolchain. Do not install Node, Gradle, SDK images or downloaded scripts
silently. Source execution needs a separate explicit sandbox/toolchain command path.

Editor regeneration:

```sh
cd companions/editor
npm ci --ignore-scripts
./node_modules/.bin/esbuild editor.js --bundle --minify --legal-comments=external --outfile=../../app/src/main/assets/ide/editor.js
```

Pinned dependencies and lockfile are included. Keep `THIRD_PARTY_NOTICES.txt` updated when
regenerating. Unsaved buffers are not yet process-durable; save before leaving or selecting
another file. Physical keyboard/IME and WebView editor accessibility need device checks.

## Permissions
First launch offers optional setup or Later. Settings → App permissions reports actual
runtime grants and requests notifications, nearby devices, microphone, camera and location
for their named features. Android app settings handles permanent denial; accessibility
requires the OS's separate settings grant. Text inference and source editing require none
of those sensor permissions. File import uses Android's document picker, not broad file access.

Download entry can request notifications contextually and still permits the owner to
continue if notifications are denied. Foreground-service permission and notification
delivery differ; do not claim notification permission is required for every runtime task.
Tools must inspect actual grants when invoked; a first-run checkbox is not proof of access.

## Tutorial later
See PLAN.md for the requested replayable guide. Future demonstrations must mark simulated
results and must not change trust, system grants or agent delegation without the user.
