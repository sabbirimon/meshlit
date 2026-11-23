# Meshlit Native Studio

Extract the complete directory. Run Meshlit.exe on Windows, Meshlit on Linux, or Meshlit.app on macOS. Keep the editor, models, inference and toolchain folders beside the launcher. First launch installs the bundled Meshlit extension into a dedicated local editor profile. General Chat opens in an empty window. Choose **Open coding IDE** to open a project in a separate window. Open a terminal with **Terminal → New Terminal**. The bundled Zig toolchain supplies the C and C++ drivers. Try `zig version`, `zig cc hello.c -o hello` or `zig c++ hello.cpp -o hello`.

The Rust launcher and bundled VSCodium workbench run without Java. The native llama.cpp server starts on demand for the included Qwen model. Models & connections can select an Ollama, LM Studio or another compatible server. Tokens are stored with the editor's secret storage. The inference service binds to loopback with a per-process API key. Chats in this edition are held in memory.

STUDIO-CONTENTS.json lists installed model-free bytes and upstream hashes. Release qualification checks for JVM executables, libraries, modules and JARs. It runs the packaged extension, generates a local reply and compiles and executes C in the editor terminal. Earlier Compose installers and the Android Kotlin application are separate distributions. Advanced legacy management features have separate porting and qualification work.

## Processor qualification plan

| Resource | Measurement and acceptance |
| --- | --- |
| CPU | Select a conservative native build by architecture. Gate x86 AVX2 on FMA, F16C and operating-system register support. Compare ARM dot-product and baseline builds on real ARM devices before enabling automatic selection. Check the same model and output with a fixed seed. |
| Memory and topology | Account for resident weights, KV cache, staging buffers and the OS reserve. Compare local SSD load, warm RAM reuse and cross-NUMA transfers. Record peak resident memory and reject placements above the configured budget. |
| Accelerators | Probe the specific adapter and validate a real dispatch before routing work. Measure prefill and decode separately with matched context, quantization and batch size. Record p50/p95 time to first token, sustained tokens/second and power or thermal throttling where available. |
| Paired devices | Measure wired and wireless latency and throughput before admitting remote layers. Test interrupted links and lost leases on two physical hosts. Keep explicit limits for memory, threads and devices, and record the selected placement. |
