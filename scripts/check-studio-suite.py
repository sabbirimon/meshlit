"""Run a compiled C program in the IDE terminal and generate an offline reply from the installed extension."""
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile


def main():
    root = Path(sys.argv[1]).resolve()
    client = root / {"Linux":"Meshlit","Darwin":"Meshlit.app/Contents/MacOS/Meshlit","Windows":"Meshlit.exe"}[platform.system()]
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        project = work / "workspace"
        project.mkdir()
        (project / "hello.c").write_text('#include <stdio.h>\nint main(void) { puts("studio-terminal-ok"); return 0; }\n')
        probe = work / "probe"
        probe.mkdir()
        (probe / "package.json").write_text(json.dumps({"name":"studio-acceptance","publisher":"meshlit-test","version":"1.0.0","engines":{"vscode":"^1.95.0"},"main":"./extension.js"}))
        (probe / "extension.js").write_text("exports.activate = () => {};")
        (probe / "test.js").write_text("""
const vscode = require('vscode'), fs = require('node:fs'), path = require('node:path');
exports.run = async () => {
    const folder = vscode.workspace.workspaceFolders[0].uri.fsPath;
    const mark = text => { fs.appendFileSync(path.join(folder,'progress.txt'), text+'\\n'); console.log('[studio-check]',text); };
    mark('test entered');
    const win = process.platform === 'win32';
    const term = vscode.window.createTerminal({name:'Native Studio acceptance',cwd:folder,
        shellPath:win?process.env.ComSpec:'/bin/sh',shellArgs:win?['/d','/q']:[]});
    let api;
    try {
        term.show();
        term.sendText(win ? 'zig cc -target x86_64-windows-gnu hello.c -o hello.exe && hello.exe > result.txt' : 'zig cc hello.c -o hello && ./hello > result.txt');
        mark('terminal submitted');
        const extension = vscode.extensions.getExtension('meshlit.native-studio');
        if (!extension) throw Error('Bundled extension was not installed');
        api = await extension.activate();
        mark('Meshlit activated');
        const prompt = 'Reply to this greeting in one short sentence: Hello!';
        const answer = await api.generate(prompt);
        if (!answer || !answer.trim()) throw Error('Missing offline reply');
        mark('local reply received');
        fs.writeFileSync(path.join(folder,'reply.txt'),answer);
        const until = Date.now()+120000;
        while(Date.now()<until) {
            const file=path.join(folder,'result.txt');
            if (fs.existsSync(file) && fs.readFileSync(file,'utf8').trim()==='studio-terminal-ok') return;
            await new Promise(r=>setTimeout(r,300));
        }
        throw Error('Bundled C compiler and IDE terminal did not produce expected result');
    } finally { term.dispose(); api?.unload(); }
};
""")
        env = {k:v for k,v in os.environ.items() if k not in {"JAVA_HOME","JDK_HOME","CLASSPATH","JDK_JAVA_OPTIONS","ELECTRON_RUN_AS_NODE"}}
        try:
            subprocess.run([client,"--self-test",work],env=env,check=True,timeout=480)
        except (subprocess.SubprocessError, OSError):
            for item in [project/"progress.txt", *sorted((work/"profile/settings/logs").rglob("exthost.log"))]:
                if item.is_file():
                    print(item.name, item.read_text(errors="replace")[-2500:], flush=True)
            raise
        assert (project / "result.txt").read_text().strip() == "studio-terminal-ok"
        answer = (project / "reply.txt").read_text().strip()
        assert answer
        info = json.loads((root / "STUDIO-CONTENTS.json").read_text())
        assert info["jvmFiles"] == 0 and info["installedModelFreeBytes"] > 500_000_000
        print(json.dumps({"platform":info["platform"],"installedModelFreeBytes":info["installedModelFreeBytes"],"terminal":"passed","offlineReply":answer}))


if __name__ == "__main__":
    main()
