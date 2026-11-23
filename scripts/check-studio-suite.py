"""Exercise the extracted Studio client and its integrated terminal on the native host."""
import argparse
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile

SYSTEM = platform.system()
PATHS = {
    "Darwin": ("Meshlit.app/Contents/MacOS/Meshlit", "editor/VSCodium.app/Contents/Resources/app/bin/codium"),
    "Windows": ("Meshlit/Meshlit.exe", "editor/bin/codium.cmd"),
    "Linux": ("Meshlit/bin/Meshlit", "editor/bin/codium"),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("suite", type=Path)
    args = parser.parse_args()
    root = args.suite.resolve()
    client, cli = [root / name for name in PATHS[SYSTEM]]
    if SYSTEM == "Linux" and not client.is_file():
        client = root / "Meshlit/bin/meshlit"
    with tempfile.TemporaryDirectory() as temp:
        temp = Path(temp)
        project = temp / "test workspace"
        project.mkdir()
        (project / "Hello.java").write_text('class Hello { public static void main(String[] args) { System.out.println("studio-terminal-ok"); } }')
        ext = temp / "probe"
        ext.mkdir()
        (ext / "package.json").write_text(json.dumps({"name": "studio-smoke", "publisher": "meshlit-test",
            "version": "0.0.1", "engines": {"vscode": "^1.90.0"}, "main": "./extension.js"}))
        (ext / "extension.js").write_text("exports.activate = () => {};")
        (ext / "test.js").write_text("""
const vscode = require('vscode'), fs = require('node:fs'), path = require('node:path');
exports.run = async () => {
    const folder = vscode.workspace.workspaceFolders[0].uri.fsPath;
    const win = process.platform === 'win32';
    const terminal = vscode.window.createTerminal({name: 'Studio acceptance', cwd: folder,
        shellPath: win ? process.env.ComSpec : '/bin/sh', shellArgs: win ? ['/d','/q'] : []});
    try {
        terminal.show();
        terminal.sendText('javac Hello.java && java Hello > result.txt');
        const deadline = Date.now() + 90000;
        while (Date.now() < deadline) {
            const file = path.join(folder, 'result.txt');
            if (fs.existsSync(file) && fs.readFileSync(file, 'utf8').trim() === 'studio-terminal-ok') return;
            await new Promise(resolve => setTimeout(resolve, 300));
        }
        throw Error('Bundled editor terminal did not compile and run Hello.java');
    } finally { terminal.dispose(); }
};
""")
        jdk = root / "toolchain"
        # Read the launch environment from the same packaged client method used by Full IDE.
        receipt = temp / "tools.txt"
        subprocess.run([client, "--studio-tools-check", receipt], check=True, timeout=60)
        home = Path(receipt.read_text(encoding="utf-8").strip())
        assert home.samefile(jdk)
        env = dict(os.environ, JAVA_HOME=str(home), PATH=str(home / "bin") + os.pathsep + os.environ["PATH"])
        settings = temp / "profile/User"
        settings.mkdir(parents=True)
        (settings / "settings.json").write_text(json.dumps({"terminal.integrated.inheritEnv": True,
            "terminal.integrated.enablePersistentSessions": False, "update.mode": "none", "extensions.autoCheckUpdates": False}))
        subprocess.run([str(cli), "--new-window", "--disable-gpu", "--disable-workspace-trust",
            "--user-data-dir", str(settings.parent), "--extensions-dir", str(temp / "extensions"),
            "--extensionDevelopmentPath=" + str(ext), "--extensionTestsPath=" + str(ext / "test.js"), str(project)],
            env=env, check=True, timeout=180)
        assert (project / "result.txt").read_text().strip() == "studio-terminal-ok"
        info = json.loads((root / "STUDIO-CONTENTS.json").read_text())
        print(f"Verified integrated terminal, offline compiler and Java runtime on {SYSTEM}. Model-free bytes: {info['installedModelFreeBytes']}")


if __name__ == "__main__":
    main()
