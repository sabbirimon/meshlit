'use strict';
const path=require('node:path');
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const run=promisify(execFile);
const tasks={npm:{build:'npm run build',test:'npm test'},cargo:{build:'cargo build',test:'cargo test'},gradle:{build:'./gradlew assemble',test:'./gradlew test'}};
function buildCommand(platform,directory,kind,action){
    const task=Object.hasOwn(tasks,kind)&&tasks[kind][action];
    if(typeof task!=='string'||!['build','test'].includes(action))throw Error('Choose a supported build or test task.');
    if(platform==='Windows'){
        if(!/^[A-Za-z]:\\[A-Za-z0-9 _./\\-]+$/.test(directory))throw Error('Enter an absolute Windows project folder.');
        const command=task.replace('./gradlew','./gradlew.bat').replace(/^npm /,'npm.cmd ');
        return 'powershell -NoProfile -NonInteractive -EncodedCommand '+Buffer.from("$ErrorActionPreference='Stop'; Set-Location -LiteralPath '"+directory+"'; "+command+"; exit $LASTEXITCODE",'utf16le').toString('base64');
    }
    if(platform!=='Linux or macOS'||!/^\/[A-Za-z0-9 _./-]+$/.test(directory))throw Error('Enter an absolute project folder without shell punctuation.');
    return "cd '"+directory+"' && "+task;
}
function sshIntent(text){
    const s=text.trim().replace(/[.!?]$/,'');
    const direct=/^\/ssh (build|test)(?: ([\w.:-]+))?$/i.exec(s);
    if(direct)return {action:direct[1].toLowerCase(),host:direct[2]};
    const phrase=/^(?:please )?ssh to ([\w.:-]+) and (build|test) (?:my |the )?(?:app|project)$/i.exec(s);
    if(phrase)return {host:phrase[1],action:phrase[2].toLowerCase()};
}
async function remoteBuild(vscode,context,intent,result){
    const ssh=require('./legacy/ssh-dashboard.cjs');
    const profiles=context.globalState.get('meshlit.sshProfiles',[]).map(ssh.validate).filter(p=>!intent.host||[p.host,p.id,p.name].some(value=>value.toLowerCase()===intent.host.toLowerCase()));
    if(!profiles.length){await vscode.commands.executeCommand('meshlit.sshDashboard');throw Error('Save the SSH profile and verify its host key, then retry the build.');}
    const choice=await vscode.window.showQuickPick(profiles.map(p=>({label:p.name,description:p.user+'@'+p.host+':'+p.port,p})),{title:'Choose the SSH build target'});
    if(!choice)return;
    const platform=await vscode.window.showQuickPick(['Linux or macOS','Windows'],{title:'Operating system on '+choice.label});if(!platform)return;
    const directory=await vscode.window.showInputBox({title:'Existing project folder on '+choice.label,prompt:'Enter the absolute path to the project you want to build or test.'});if(!directory)return;
    const kind=await vscode.window.showQuickPick(Object.keys(tasks),{title:'Project build tool'});if(!kind)return;
    const remote=buildCommand(platform,directory,kind,intent.action);
    if(!await vscode.window.showWarningMessage('Run the project '+intent.action+' over SSH?',{modal:true,detail:choice.description+'\n'+directory+'\n'+tasks[kind][intent.action]+'\nThis runs the project’s build scripts with the SSH account’s permissions.'},'Run task'))return;
    ssh.checkIdentity(choice.p);
    const options=ssh.argumentsFor(choice.p).map(a=>a==='StrictHostKeyChecking=ask'?'StrictHostKeyChecking=yes':a);
    if(process.platform==='win32') options[options.indexOf('/dev/null')]='NUL';
    const binary=process.platform==='win32'?path.join(process.env.SystemRoot||'C:\\Windows','System32','OpenSSH','ssh.exe'):'/usr/bin/ssh';
    const output=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'SSH '+intent.action,cancellable:true},async(_,token)=>{
        const abort=new AbortController(),subscription=token.onCancellationRequested(()=>abort.abort());
        try{
            const response=await run(binary,['-T','-o','BatchMode=yes',...options,remote],{timeout:300000,maxBuffer:1024*1024,signal:abort.signal,windowsHide:true});
            return {code:0,...response};
        }catch(e){return {code:e.killed||e.signal||e.name==='AbortError'?'interrupted':e.code??'unknown',stdout:e.stdout||'',stderr:e.stderr||e.message};}
        finally{subscription.dispose();}
    });
    result('SSH '+intent.action,'Target: '+choice.p.name+'\nTask: '+tasks[kind][intent.action]+'\nExit: '+output.code+'\n'+(output.stdout+'\n'+output.stderr).slice(-7500));
}
function htmlSource(answer){
    const source=answer.trim().replace(/^```(?:html)?\s*\n/i,'').replace(/\n```\s*$/,'').trim();
    if(!/^(?:<!doctype\s+html[^>]*>\s*)?<html[\s>]/i.test(source)||!/<\/html>$/i.test(source))throw Error('The model returned an incomplete HTML file. Review its reply and retry with a smaller app.');
    return source;
}
function projectFiles(answer){
    const raw=answer.trim().replace(/^```(?:json)?\s*\n/i,'').replace(/\n```\s*$/,'');
    const {files}=JSON.parse(raw);
    if(!Array.isArray(files)||!files.length||files.length>32||Buffer.byteLength(raw)>512000)throw Error('Expected a project with 1–32 source files.');
    const names=new Set();
    for(const f of files){
        if(typeof f?.path!=='string'||f.path.length>180||typeof f.content!=='string'||f.content.includes('\0'))throw Error('Invalid project file.');
        const parts=f.path.split('/');
        if(parts.some(p=>! /^[a-zA-Z0-9_-][a-zA-Z0-9._-]{0,79}$/.test(p)||p.endsWith('.')||/^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(p)))throw Error('Unsafe project path.');
        const name=f.path.toLowerCase();
        if(names.has(name)||[...names].some(other=>name.startsWith(other+'/')||other.startsWith(name+'/')))throw Error('Conflicting project paths.');
        names.add(name);
    }
    return files;
}
async function saveProject(parent,files){
    const fs=require('node:fs/promises');
    const folder=await fs.mkdtemp(path.join(parent,'Meshlit-project-'));
    try{
        for(const file of files){
            const destination=path.join(folder,...file.path.split('/'));
            await fs.mkdir(path.dirname(destination),{recursive:true});
            await fs.writeFile(destination,file.content,{flag:'wx'});
        }
        return folder;
    }catch(e){await fs.rm(folder,{recursive:true,force:true});throw e;}
}
async function localTask(vscode,action,result){
    const [uri]=await vscode.window.showOpenDialog({canSelectFiles:false,canSelectFolders:true,canSelectMany:false,openLabel:'Use this project'} )||[];
    if(!uri)return;
    const kind=await vscode.window.showQuickPick(Object.keys(tasks),{title:'Choose the project tool'});if(!kind)return;
    const command=tasks[kind][action];
    if(!await vscode.window.showWarningMessage('Run this local project task?',{modal:true,detail:uri.fsPath+'\n'+command},'Run task'))return;
    const win=process.platform==='win32';
    const [bin,...args]=command.split(' ');
    const output=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:command,cancellable:true},async(_,token)=>{
        const cancel=new AbortController(),sub=token.onCancellationRequested(()=>cancel.abort());
        const opt={cwd:uri.fsPath,timeout:300000,maxBuffer:1024*1024,signal:cancel.signal,windowsHide:true};
        try{
            const reply=win?await run(path.join(process.env.SystemRoot||'C:\\Windows','System32','cmd.exe'),['/d','/c',command.replace('./gradlew','gradlew.bat')],opt):await run(bin,args,opt);
            return {code:0,...reply};
        }catch(e){return {code:e.signal||e.code||'unknown',stdout:e.stdout||'',stderr:e.stderr||e.message};}finally{sub.dispose();}
    });
    result('local '+action,'Task: '+command+'\nExit: '+output.code+'\n'+(output.stdout+'\n'+output.stderr).slice(-7500));
}
module.exports={buildCommand,sshIntent,remoteBuild,htmlSource,projectFiles,saveProject,localTask};
