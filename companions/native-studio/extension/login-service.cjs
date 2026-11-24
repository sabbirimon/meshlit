// SPDX-License-Identifier: Apache-2.0
'use strict';
const fs=require('node:fs/promises'),path=require('node:path'),os=require('node:os');
const {promisify}=require('node:util'),exec=promisify(require('node:child_process').execFile);
async function setLogin(root,enable){
    const home=os.homedir(),mac=process.platform==='darwin',win=process.platform==='win32';
    const file=win?path.join(process.env.APPDATA,'Microsoft/Windows/Start Menu/Programs/Startup/Meshlit Studio.lnk'):
        mac?path.join(home,'Library/LaunchAgents/bd.com.imon.meshlit.login.plist'):
        path.join(process.env.XDG_CONFIG_HOME||path.join(home,'.config'),'autostart/meshlit-studio.desktop');
    if(!enable){await fs.rm(file,{force:true});return;}
    const program=path.join(root,win?'Meshlit.exe':mac?'Meshlit.app/Contents/MacOS/Meshlit':'Meshlit');
    await fs.access(program);await fs.mkdir(path.dirname(file),{recursive:true});
    if(win){
        const script="$ErrorActionPreference='Stop'; $link=(New-Object -ComObject WScript.Shell).CreateShortcut($env:MESHLIT_LOGIN_FILE); $link.TargetPath=$env:MESHLIT_LOGIN_PROGRAM; $link.Save()";
        await exec('powershell.exe',['-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,env:{...process.env,MESHLIT_LOGIN_FILE:file,MESHLIT_LOGIN_PROGRAM:program}});
    }else{
        const escaped=program.replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'})[c]);
        const quoted=program.replace(/[%]/g,'%%').replace(/["`$\\]/g,c=>'\\'+c).replace(/\\/g,'\\\\');
        const body=mac?'<?xml version="1.0"?><plist version="1.0"><dict><key>Label</key><string>bd.com.imon.meshlit.login</string><key>ProgramArguments</key><array><string>'+escaped+'</string></array><key>RunAtLoad</key><true/></dict></plist>':
            '[Desktop Entry]\nType=Application\nName=Meshlit Studio\nExec="'+quoted+'"\n';
        await fs.writeFile(file,body,{mode:0o600});
    }
}
module.exports={setLogin};
