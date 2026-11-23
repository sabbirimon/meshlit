# Meshlit Native Studio

Studio uses a blue branching icon with a circular rim joined to the trunk at its base for its launcher, running app and Windows installer. Artwork credits are in [NOTICE](../../NOTICE).
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-23: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Extract the complete directory. Run Meshlit.exe on Windows, Meshlit on Linux, or Meshlit.app on macOS. Keep the editor, models, inference and toolchain folders beside the launcher. First launch installs the bundled Meshlit extension into a dedicated local editor profile. General Chat opens in an empty window. Choose **Open coding IDE** to open a project in a separate window. Open a terminal with **Terminal → New Terminal**. The bundled Zig toolchain supplies the C and C++ drivers. Try `zig version`, `zig cc hello.c -o hello` or `zig c++ hello.cpp -o hello`.

The Rust launcher and bundled VSCodium workbench run without Java. The native llama.cpp server starts on demand for the included Qwen model. Models & connections can select an Ollama, LM Studio or another compatible server. Tokens are stored with the editor's secret storage. The inference service binds to loopback with a per-process API key. Chats in this edition are held in memory.

STUDIO-CONTENTS.json lists installed model-free bytes and upstream hashes. Release qualification checks for JVM executables, libraries, modules and JARs. It runs the packaged extension, generates a local reply and compiles and executes C in the editor terminal. Earlier Compose installers and the Android Kotlin application are separate distributions. Advanced legacy management features have separate porting and qualification work.

## Model discovery and device pairing on the preview branch

**Find a model** shows search results by publisher on the left and details for the selected repository on the right. The detail view lists reported architecture, parameter count and task when the repository supplies them. The GGUF variant selector shows the download size. Sort by relevance, likes, downloads or update date. The RAM filter uses an estimate and can be cleared to inspect larger files.

On November 24 the two installed pairing modules exchanged fresh P-256 challenges between a physical Intel Mac and Windows HP. The HP reached the Mac's advertised Wi-Fi endpoint. A direct request to the HP's temporary listener timed out. Both directions verified when the Mac used a local SSH forward to that listener. The HP also verified a fresh challenge from the running Mac Studio window. This protocol test used temporary peer identities for the bidirectional check. Saving both peers in the ordinary desktop profiles requires completing **Devices → Pair using a link** on each host. Each shared listener expires after ten minutes.

## Voice and vision in the native workbench

Open **Management → Voice and vision** for the OpenLive companion settings and speech previews. Microphone and speech actions use Studio's permission checks. Managed companion startup requires a configured local runtime on macOS or Linux. Windows dictation remains available separately from General Chat.

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

Open **Models & connections** from General chat. **Import model file** registers an artifact in place. Use **Import model folder** for checkpoints with a tokenizer, shards or runtime configuration. GGUF files use the bundled engine. Other entries show the container, declared quantization, evidence for detection and available runtime. Choose **Select** on an eligible package and point Studio at the Python executable for a trusted, installed runtime. Studio probes the environment before switching. A format listed without Select can be inspected. To use a model already deployed on a server, connect in Provider connections and choose its advertised model ID. **Discover models** searches Hugging Face after you enter a query, shows available single-file quantizations and sizes, and asks before downloading. Studio pins the repository revision, checks the download size and SHA-256, and keeps successful downloads in Studio storage. Forgetting a registration leaves its model file on disk.

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

## General Chat on the development branch

Use **Back** to return from model management, permissions or tools. Open a project with **Open coding IDE** for editor menus and terminals. Chat shows elapsed time during local verification, loading and generation. A failed request restores the draft for retry. Timings show decode speed when the runtime reports it, end-to-end throughput when only a token count is available, or elapsed seconds otherwise.

**Attach file or image** adds one UTF-8 text file up to 32 KiB or PNG, JPEG or WebP up to 4 MiB. Preview the filename in the composer and click it to remove it. Sending an attachment to a provider asks for confirmation naming the destination. Images require a vision-capable provider. **Internet search** offers Wikipedia results with source links in the draft, or opens a DuckDuckGo query in your browser. **Voice** uses Windows speech recognition with microphone permission and requires a locally installed speech language. Other systems can use system dictation. Replies support Copy, Reply, Save and local read-aloud with Stop. Linux read-aloud uses an installed Speech Dispatcher client. Open **Permissions** to allow or block internet search, voice input, file reads, provider attachment sharing and read-aloud. Studio saves each choice across restarts. Device pairing, app control and the reply filter have their own controls.

Optional local workers are Transformers with SafeTensors, ONNX Runtime GenAI, OpenVINO GenAI and MLX-LM. Provide a trusted model folder and an environment with the corresponding libraries. The current worker loads the model per request. Raw arrays, pickle checkpoints and compiled binaries require the architecture, tokenizer and matching runtime shown in the catalog. [Model format milestones](../DESKTOP_STUDIO_PLAN.md#studio-model-format-support--2026-11-24)

The Windows build workflow produces a per-user Setup.exe alongside the portable ZIP and exercises installation, General Chat startup and uninstall. Check the asset list for the particular release you download.

On the development branch, first launch offers the bundled offline model, provider connection and model library. Open Settings → Welcome and setup to view it again. The library search filters entries by name or displayed format/runtime. **Selected model reply settings** saves the output token limit and, for GGUF or provider routes, temperature per model. Optional Python workers retain their own sampling behavior.

## Studio 2.0.49 device and model preview

Enable device networking in **Devices** on both computers. Choose a nearby computer or enter its device ID. The receiving user opens Devices and approves the requesting computer. Both peers retain the Studio identity key. Select the saved peer and choose **Share models** to authorize specific bundled or imported GGUF models for that computer. On the other device choose **Use a shared model**. Device networking is opt-in. Owners can stop sharing and forget a device. Previously saved link identities remain available.

The private [Studio relay](../../companions/studio-relay/README.md) supports outbound connections on separate networks. Studio first uses a fresh LAN advertisement when available. Relay traffic carries the same key-pinned mutual TLS session. A relay deployment and access token are required for internet use.

The Mac and Windows HP exchanged signed identities in both installed Studio profiles on November 24. A subsequent test of the new transport found the HP through LAN discovery, listed the explicitly shared bundled Qwen model, and streamed its answer to the Mac over direct Wi-Fi. This test used temporary identities and the packaged Windows Electron executable. The HP runtime reported 10 completion tokens across nine text updates. Remote inference runs on the host that owns the selected model.

New profiles open a Get started screen, show a model recommendation based on available RAM, and offer optional startup settings. Model management includes on-demand loading, idle unload, and model server activity. Startup at login is opt-in and uses the current user's launch or startup directory. The bundled inference engine runs on CPU. The discovery view links to the publisher's model card and shows declared capabilities when available.
