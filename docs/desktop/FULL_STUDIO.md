# Meshlit Native Studio

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
