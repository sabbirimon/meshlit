# Meshlit Native Studio
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-23: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Extract the complete directory. Run Meshlit.exe on Windows, Meshlit on Linux, or Meshlit.app on macOS. Keep the editor, models, inference and toolchain folders beside the launcher. First launch installs the bundled Meshlit extension into a dedicated local editor profile. General Chat opens in an empty window. Choose **Open coding IDE** to open a project in a separate window. Open a terminal with **Terminal → New Terminal**. The bundled Zig toolchain supplies the C and C++ drivers. Try `zig version`, `zig cc hello.c -o hello` or `zig c++ hello.cpp -o hello`.

The Rust launcher and bundled VSCodium workbench run without Java. The native llama.cpp server starts on demand for the included Qwen model. Models & connections can select an Ollama, LM Studio or another compatible server. Tokens are stored with the editor's secret storage. The inference service binds to loopback with a per-process API key. Chats in this edition are held in memory.

STUDIO-CONTENTS.json lists installed model-free bytes and upstream hashes. Release qualification checks for JVM executables, libraries, modules and JARs. It runs the packaged extension, generates a local reply and compiles and executes C in the editor terminal. Earlier Compose installers and the Android Kotlin application are separate distributions. Advanced legacy management features have separate porting and qualification work.

## Ubuntu installation

On Ubuntu systems that restrict unprivileged user namespaces, install the verified suite in the administrator-owned path used by its Chromium sandbox profile. From the parent of the extracted directory, for a fresh installation:

```sh
sudo test ! -e /opt/meshlit-studio && sudo cp -R Meshlit-Studio /opt/meshlit-studio
sudo chown -R root:root /opt/meshlit-studio
sudo install -m 0644 /opt/meshlit-studio/meshlit-studio.apparmor /etc/apparmor.d/meshlit-studio
sudo apparmor_parser -r /etc/apparmor.d/meshlit-studio
/opt/meshlit-studio/Meshlit
```

The included policy is scoped to the editor in that installation. [Ubuntu user namespace guidance](https://documentation.ubuntu.com/release-notes/24.04/)

## Processor qualification plan

| Resource | Measurement and acceptance |
| --- | --- |
| CPU | Select a conservative native build by architecture. Gate x86 AVX2 on FMA, F16C and operating-system register support. Compare ARM dot-product and baseline builds on real ARM devices before enabling automatic selection. Check the same model and output with a fixed seed. |
| Memory and topology | Account for resident weights, KV cache, staging buffers and the OS reserve. Compare local SSD load, warm RAM reuse and cross-NUMA transfers. Record peak resident memory and reject placements above the configured budget. |
| Accelerators | Probe the specific adapter and validate a real dispatch before routing work. Measure prefill and decode separately with matched context, quantization and batch size. Record p50/p95 time to first token, sustained tokens/second and power or thermal throttling where available. |
| Paired devices | Measure wired and wireless latency and throughput before admitting remote layers. Test interrupted links and lost leases on two physical hosts. Keep explicit limits for memory, threads and devices, and record the selected placement. |

## Devices, app control and model management

Open **Devices** to share a ten-minute identity link on the selected LAN or VPN interface, verify a peer's signed challenge, recheck its identity, or forget it. Paste a link with `/pair meshlit://pair?…`. Confirm the fingerprint with the device owner. Saving a peer grants no ability to control a remote desktop. A wired, Wi-Fi or private VPN connection must already route between the devices.

**Control an app** or `/control` opens a local accessibility review. Choose an app, choose an observed button or editable field, inspect the action, then allow it once. macOS uses the existing Meshlit Accessibility helper with an OS permission prompt. Windows supports accessible controls in Notepad, WordPad and Paint through UI Automation. Linux supports selected AT-SPI editors including gedit, GNOME Text Editor, Kate and Mousepad. Linux requires Python 3 with PyGObject and the Atspi 2.0 typelib. Some compositors and editors expose fewer controls. On Android, open Settings → Android automation in the full application and select an allowed app. Android Core candidates exclude the accessibility service. Desktop app observations stay local in this flow.

**Models & connections** registers existing single-file GGUFs in place, checks their SHA-256 on load, lists models from an authenticated provider, and lets you switch or remove saved registrations. Provider keys use the host secret store. Switching models clears the current conversation. **Output guardrails** switches recognition of email addresses and common API token patterns on or off for displayed replies. Other sensitive text may remain visible. Model capabilities and provider policies still apply.

## Managing models in native Studio

Open **Models & connections** from General chat. **Model library** lists bundled and imported GGUFs. **Import GGUF** verifies the file in place. **Discover models** searches Hugging Face after you enter a query, shows available single-file quantizations and sizes, and asks before downloading. Studio pins the repository revision, checks the download size and SHA-256, and keeps successful downloads in Studio storage. Forgetting a registration leaves its model file on disk.

Under **Provider connections**, choose Ollama, LM Studio or another OpenAI-compatible endpoint, then select a model returned by its inventory. Start the vendor server first. Select a saved route to start a fresh chat on that model. Keys use the editor's secret storage.

Select a local model and use **Load selected model** to start the bundled CPU server. **Copy endpoint** and **Copy API key** supply credentials for a loopback API client. **Unload** stops the server. Pair devices from this dashboard or type `/pair` in chat. A verified device identity and authorization for app control are separate settings. Open the coding IDE from General chat when you need a project window.

The workflow follows model import, selection, loading and local serving in [LM Studio's model guide](https://lmstudio.ai/docs/cli/local-models/import) and [developer server guide](https://lmstudio.ai/docs/developer/core/server). CUDA migration and accelerator qualification belong to [HyperL](https://github.com/sabbirimon/HyperL/tree/feature/native-mlir-engine/docs/CUDA_MIGRATION.md).

The offline acceptance test opens an Extension Development Host with a disposable profile and in-memory secret storage. On macOS the test uses Chromium’s mock Keychain. A normal Studio session uses the OS credential store for saved provider keys and pairing identity.

## The default Studio window

Launch **Meshlit** with no arguments to open **General chat**. The welcome screen has a message composer, a model selector and a navigation rail for models, devices, app control and settings. Recent chats are available within this running session and model route. Switching provider clears the previous conversations from that route.

Choose **Open coding IDE** and select a folder to open a separate project window with Explorer, editor tabs and a terminal. The Home window stays available. Home opens as an empty window. The launcher supplies its packaged toolchain environment directly at startup. Project trust applies when you choose a project. Home and the project IDE share the bundled workbench runtime and use separate window layouts. This restores the CS-01 window contract in the [older Studio design](../CHAT_STUDIO_REQUEST_RECONCILIATION_2026-11-06.md).

The package check launches Home first and requires a visible, initialized chat view with zero project folders. A second isolated test opens the IDE and compiles a C program. An **Extension Development Host** title and a workspace containing `hello.c` identify that test.

## Studio menu, Tools and Management

Use the **Studio** application menu or the **Management** and **Tools** entries in the chat rail. **Settings** is at the top right of General Chat. On macOS, **Meshlit → Meshlit Settings…** opens the same controls. The distributed workbench is a Meshlit-branded derivative of the pinned VSCodium release. On macOS, launch **Meshlit.app** from the extracted suite. Keep its adjacent editor, inference and toolchain directories together. The build renames the Electron helpers to match the Meshlit application identity, signs the helper bundles and the outer workbench, and checks the signatures. The macOS development package is ad-hoc signed. Its launch test opens General Chat with workspace trust enabled and checks the running Studio identity.

Management covers model registrations, local serving, CPU policy, verified devices, SSH profiles, MCP server configuration, reply filtering and host resources. Tools includes local app control and Stop, the bundled compiler check, component inventory, registered model tools and project memory. Project memory requires a trusted project window. Some legacy bot, voice, team and gateway pages depend on their separate runtime and remain outside this native package.
