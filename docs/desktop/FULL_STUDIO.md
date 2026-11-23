# Meshlit Full Studio

Extract the entire archive to a writable folder. On macOS unpack with Archive Utility or tar to preserve the application bundles. Open Meshlit.exe in the Meshlit folder on Windows, Meshlit/bin/Meshlit on Linux, or Meshlit.app on macOS. Keep the editor and toolchain directories beside the application.

Open Coding IDE, choose a folder, then select **Full IDE**. The separate VSCodium window has the file explorer, editor, source control UI, task runner, debugger interface and integrated terminal. Use Terminal → New Terminal to start the host shell. The Studio launcher sets JAVA_HOME and puts the included Eclipse Temurin 21 JDK first on PATH. Try `javac -version`, `jshell` or `jdb`. Project-specific debuggers, Git command-line tools and third-party extensions may require separate installation. The general chat dashboard stays available in Meshlit. An offline starter model ships separately inside the full distribution under the application's resources.

STUDIO-CONTENTS.json lists upstream hashes and installed byte counts for the client, editor and JDK. Model files, symbolic links and the distribution archive are excluded from the 500,000,000-byte acceptance threshold. The compressed download can be smaller. The standalone native installers and the portable Full Studio archive are different editions.

## Processor qualification plan

| Resource | Measurement and acceptance |
| --- | --- |
| CPU | Select a conservative native build by architecture. Gate x86 AVX2 on FMA, F16C and operating-system register support. Compare ARM dot-product and baseline builds on real ARM devices before enabling automatic selection. Check the same model and output with a fixed seed. |
| Memory and topology | Account for resident weights, KV cache, staging buffers and the OS reserve. Compare local SSD load, warm RAM reuse and cross-NUMA transfers. Record peak resident memory and reject placements above the configured budget. |
| Accelerators | Probe the specific adapter and validate a real dispatch before routing work. Measure prefill and decode separately with matched context, quantization and batch size. Record p50/p95 time to first token, sustained tokens/second and power or thermal throttling where available. |
| Paired devices | Measure wired and wireless latency and throughput before admitting remote layers. Test interrupted links and lost leases on two physical hosts. Keep explicit limits for memory, threads and devices, and record the selected placement. |
