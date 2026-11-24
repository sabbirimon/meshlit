"""Run a compiled C program in the IDE terminal and generate an offline reply from the installed extension."""
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile


def check_home(client):
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp).resolve()
        probe = work / "probe"
        probe.mkdir()
        (probe / "package.json").write_text(json.dumps({"name":"home-acceptance","publisher":"meshlit-test","version":"1.0.0","engines":{"vscode":"^1.95.0"},"main":"./extension.js"}))
        (probe / "extension.js").write_text("exports.activate=()=>{};")
        (probe / "test.js").write_text("""
const vscode=require('vscode'),fs=require('node:fs'),path=require('node:path');
exports.run=async()=>{
    if(!vscode.env.appName.startsWith('Meshlit')) throw Error('Unexpected workbench identity '+vscode.env.appName);
    const api=await vscode.extensions.getExtension('meshlit.native-studio').activate();
    const deadline=Date.now()+45000;
    while(Date.now()<deadline){
        const s=api.homeStatus();
        if(s.home && s.ready && s.visible && s.folders===0){
            const result=await vscode.lm.invokeTool('meshlit_inspect_settings',{input:{},toolInvocationToken:undefined});
            const catalog=JSON.parse(result.content[0].value);
            if(catalog.schema!=='meshlit.settings.catalog.v1'||catalog.sections.length!==5)throw Error('Missing settings catalog');
            const controls=catalog.sections.flatMap(s=>s.controls);
            if(controls.some(c=>typeof c.available!=='boolean'||!c.label||(!c.available&&!c.reason)))throw Error('Invalid settings availability');
            if(!controls.some(c=>c.id==='memory'&&!c.available))throw Error('Project memory needs a trusted workspace');
            fs.writeFileSync(path.join(__dirname,'home.json'),JSON.stringify({...s,appName:vscode.env.appName,catalog}));
            for(const id of ['settings','tools','management','models'])await vscode.commands.executeCommand('meshlit.studio.'+id);
            await vscode.commands.executeCommand('meshlit.studio.chat');
            return;
        }
        await new Promise(r=>setTimeout(r,150));
    }
    throw Error('Normal startup did not open the visible chat composer in the project-free Studio window: '+JSON.stringify(api.homeStatus()));
};
""")
        subprocess.run([client,"--self-test-home",work],check=True,timeout=180)
        state=json.loads((probe/"home.json").read_text())
        print(json.dumps({"defaultWindow":"general-chat",**state}),flush=True)


def main():
    root = Path(sys.argv[1]).resolve()
    client = root / {"Linux":"Meshlit","Darwin":"Meshlit.app/Contents/MacOS/Meshlit","Windows":"Meshlit.exe"}[platform.system()]
    check_home(client)
    if "--home-only" in sys.argv[2:]:
        return
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        project = work / "workspace"
        project.mkdir()
        (project / "hello.c").write_text('#include <stdio.h>\nint main(void) { puts("studio-terminal-ok"); return 0; }\n')
        (project / "terminal-check.cmd").write_text(
            "@echo off\nzig cc -target x86_64-windows-gnu hello.c -o hello.exe > compiler.txt 2>&1\n"
            "if errorlevel 1 (echo failed > compiler.failed & exit /b 1)\nhello.exe > result.txt\n")
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
        shellPath:win?process.env.ComSpec:'/bin/sh',shellArgs:win?['/d','/c','terminal-check.cmd']:[]});
    let api;
    try {
        term.show();
        if(!win)term.sendText('zig cc hello.c -o hello && ./hello > result.txt');
        mark('terminal submitted');
        const extension = vscode.extensions.getExtension('meshlit.native-studio');
        if (!extension) throw Error('Bundled extension was not installed');
        api = await extension.activate();
        mark('Meshlit activated');
        await vscode.commands.executeCommand('meshlit.studio.navigation.focus');
        const logo=vscode.Uri.joinPath(extension.extensionUri,'legacy','media','meshlit.svg').fsPath;
        if(!fs.existsSync(logo))throw Error('IDE sidebar artwork missing from installed extension');
        if(api.homeStatus().home)throw Error('Project opened in the Home layout');
        const prompt = 'Reply to this greeting in one short sentence: Hello!';
        const answer = await api.generate(prompt);
        if (!answer || !answer.trim()) throw Error('Missing offline reply');
        const stats=api.lastReplyMetrics();
        if(!stats?.includes('s elapsed')||!stats.includes('output tokens')||!stats.includes('tokens/s'))throw Error('Offline reply is missing token measurements');
        mark('local reply received · '+stats);
        fs.writeFileSync(path.join(folder,'reply.txt'),answer);
        const until = Date.now()+300000;
        while(Date.now()<until) {
            if(fs.existsSync(path.join(folder,'compiler.failed')))throw Error(fs.readFileSync(path.join(folder,'compiler.txt'),'utf8').slice(-4000));
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
            subprocess.run([client,"--self-test",work],env=env,check=True,timeout=660)
        except (subprocess.SubprocessError, OSError):
            for item in [project/"progress.txt",project/"compiler.txt", *sorted((work/"profile/settings/logs").rglob("exthost.log"))]:
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
