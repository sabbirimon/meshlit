# Meshlit Full Studio

Extract the entire archive to a writable folder. On macOS unpack with Archive Utility or tar to preserve the application bundles. Open Meshlit.exe in the Meshlit folder on Windows, Meshlit/bin/Meshlit on Linux, or Meshlit.app on macOS. Keep the editor and toolchain directories beside the application.

Open Coding IDE, choose a folder, then select **Full IDE**. The separate VSCodium window has the file explorer, editor, source control UI, task runner, debugger interface and integrated terminal. Use Terminal → New Terminal to start the host shell. The Studio launcher sets JAVA_HOME and puts the included Eclipse Temurin 21 JDK first on PATH. Try `javac -version`, `jshell` or `jdb`. Project-specific debuggers, Git command-line tools and third-party extensions may require separate installation. The general chat dashboard stays available in Meshlit. An offline starter model ships separately inside the full distribution under the application's resources.

STUDIO-CONTENTS.json lists upstream hashes and installed byte counts for the client, editor and JDK. Model files, symbolic links and the distribution archive are excluded from the 500,000,000-byte acceptance threshold. The compressed download can be smaller. The standalone native installers and the portable Full Studio archive are different editions.
