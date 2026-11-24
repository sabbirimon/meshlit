'use strict';
const fs=require('node:fs'),path=require('node:path'),net=require('node:net'),crypto=require('node:crypto');
const {execFile}=require('node:child_process');
const os=require('node:os');
const SYSTEM_TERMINAL='/System/Applications/Utilities/Terminal.app';
const KEY='meshlit.sshProfiles',SSH='/usr/bin/ssh';
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function validate(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(key=>!['id','name','host','user','port','identity'].includes(key)))throw Error('Use only the named SSH profile fields. Passwords and key contents cannot be saved.');
 const {id,name,user,port}=value;let {host,identity=''}=value;
 if(typeof id!=='string'||!/^[a-zA-Z0-9-]{1,80}$/.test(id))throw Error('Invalid profile identity.');
 if(typeof name!=='string'||!name.trim()||name.length>80||/[\x00-\x1f\x7f]/.test(name))throw Error('Choose a profile name of 1–80 characters.');
 if(typeof host!=='string'||!host||host.length>253||/[\x00-\x20\x7f]/.test(host))throw Error('Enter a hostname or IP address without spaces or a URL.');
 if(host.startsWith('[')&&host.endsWith(']'))host=host.slice(1,-1);
 if(!net.isIP(host)&&!host.split('.').every(label=>/^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/.test(label)))throw Error('Enter a valid hostname, IPv4 or IPv6 address.');
 if(typeof user!=='string'||!/^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,63}$/.test(user))throw Error('Enter a username using letters, numbers, underscores, dots or hyphens.');
 if(!Number.isInteger(port)||port<1||port>65535)throw Error('SSH port must be a whole number from 1 to 65535.');
 if(typeof identity!=='string'||identity.length>4096||/[\x00-\x1f\x7f]/.test(identity)||identity&&!path.isAbsolute(identity))throw Error('Choose an absolute private-key file path or leave it empty.');
 return {id,name:name.trim(),host,user,port,identity};
}
function checkIdentity(profile,fileSystem=fs){
 if(!profile.identity)return;
 let stat;try{stat=fileSystem.lstatSync(profile.identity);}catch{throw Error('The selected key file is unavailable. Select it again.');}
 if(!stat.isFile()||stat.isSymbolicLink?.()||stat.size<1||stat.size>1048576)throw Error('Choose a regular key file of at most 1 MiB, not a folder or symbolic link.');
 fileSystem.accessSync(profile.identity,fs.constants.R_OK);
}
function availability({fileSystem=fs,platform=process.platform}={}){
 if(!['darwin','linux'].includes(platform))return {available:false,reason:'This dashboard currently supports installed /usr/bin/ssh on macOS or Linux.'};
 try{if(!fileSystem.statSync(SSH).isFile())throw Error();fileSystem.accessSync(SSH,fs.constants.X_OK);return {available:true,reason:'Installed OpenSSH is available. No connection has been checked.'};}
 catch{return {available:false,reason:'OpenSSH was not found at /usr/bin/ssh. Install it before connecting.'};}
}
function argumentsFor(value){
 const profile=validate(value);
 const options=['StrictHostKeyChecking=ask','ForwardAgent=no','ClearAllForwardings=yes','ForwardX11=no','PermitLocalCommand=no','ProxyCommand=none','ProxyJump=none','IdentityAgent=none','AddKeysToAgent=no','UpdateHostKeys=no','ControlMaster=no','ControlPath=none','ConnectTimeout=15','ServerAliveInterval=15','ServerAliveCountMax=3'];
 return ['-F','/dev/null',...options.flatMap(option=>['-o',option]),...(profile.identity?['-o','IdentitiesOnly=yes','-i',profile.identity]:[]),'-p',String(profile.port),'-l',profile.user,profile.host];
}
function systemTerminalAvailable({fileSystem=fs,platform=process.platform}={}){
 if(platform!=='darwin')return false;
 try{return fileSystem.statSync(SYSTEM_TERMINAL).isDirectory()&&fileSystem.statSync('/usr/bin/open').isFile()&&(fileSystem.accessSync('/usr/bin/open',fs.constants.X_OK),true);}catch{return false;}
}
const shellQuote=value=>"'"+String(value).replace(/'/g,"'\"'\"'")+"'";
async function openSystemTerminal(value,{run=execFile,fileSystem=fs,platform=process.platform,tempRoot=os.tmpdir(),environment=process.env,schedule=setTimeout}={}){
 const profile=validate(value);
 if(!systemTerminalAvailable({fileSystem,platform}))throw Error('System Terminal is unavailable on this Mac.');
 if(!availability({fileSystem,platform}).available)throw Error('OpenSSH is unavailable.');
 checkIdentity(profile,fileSystem);
 let directory,file;
 const cleanup=()=>{if(file)try{fileSystem.unlinkSync(file);}catch{}if(directory)try{fileSystem.rmdirSync(directory);}catch{}};
 try{
  directory=fileSystem.mkdtempSync(path.join(tempRoot,'meshlit-ssh-'));fileSystem.chmodSync(directory,0o700);file=path.join(directory,'connect.command');
  const envArgs=['PATH=/usr/bin:/bin','SSH_ASKPASS_REQUIRE=never'];
  for(const key of ['HOME','USER','LOGNAME'])if(typeof environment[key]==='string'&&!/[\x00-\x1f\x7f]/.test(environment[key]))envArgs.push(key+'='+environment[key]);
  const script=['#!/bin/sh','umask 077','unset ENV BASH_ENV CDPATH SSH_ASKPASS SSH_AUTH_SOCK SSH_SK_PROVIDER','/bin/rm -f -- '+shellQuote(file),'/bin/rmdir -- '+shellQuote(directory),'exec /usr/bin/env -i '+envArgs.map(shellQuote).join(' ')+' "TERM=${TERM:-xterm-256color}" '+[SSH,...argumentsFor(profile)].map(shellQuote).join(' '),''].join('\n');
  fileSystem.writeFileSync(file,script,{mode:0o700,flag:'wx'});
  await new Promise((resolve,reject)=>run('/usr/bin/open',['-a',SYSTEM_TERMINAL,file],{timeout:5000,maxBuffer:32768,encoding:'utf8',shell:false,env:{PATH:'/usr/bin:/bin',...(typeof environment.HOME==='string'?{HOME:environment.HOME}:{})}},error=>error?reject(Error('System Terminal could not be opened. No connection was verified.')):resolve()));
  // The script deletes itself before SSH. Also remove an unconsumed handoff if
  // Terminal never executes it; do not close or signal the user's terminal.
  const timer=schedule(cleanup,120000);timer?.unref?.();return 'System Terminal handoff opened. Verify host-key and login results there; authentication has not been verified.';
 }catch(error){cleanup();throw error;}
}
async function checkConfiguration(profile,{run=execFile,fileSystem=fs,platform=process.platform}={}){
 const runtime=availability({fileSystem,platform});if(!runtime.available)throw Error(runtime.reason);
 checkIdentity(profile,fileSystem);const args=['-G',...argumentsFor(profile)];
 await new Promise((resolve,reject)=>run(SSH,args,{timeout:5000,maxBuffer:131072,encoding:'utf8',windowsHide:true,shell:false,env:{PATH:'/usr/bin:/bin',...Object.fromEntries(['HOME','LANG','LC_ALL','TMPDIR'].filter(key=>typeof process.env[key]==='string').map(key=>[key,process.env[key]]))}},error=>error?reject(Error('OpenSSH configuration check failed. Review this profile and the selected key file.')):resolve()));
 return 'Configuration valid. No network contact, host-key handshake or authentication was attempted. Connect to check those in the terminal.';
}
function render(profiles=[],runtime={available:false,reason:'Runtime not checked.'}){
 const nonce=crypto.randomBytes(24).toString('hex');
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">*{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:940px;margin:auto;padding:40px 32px}h1{font-size:28px;margin:0 0 8px}h2{font-size:17px;margin:0 0 6px}p{color:var(--vscode-descriptionForeground);margin:8px 0 18px}article{padding:20px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin:14px 0}button{font:inherit;padding:7px 12px;border:1px solid var(--vscode-panel-border);border-radius:7px;background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);cursor:pointer}button:disabled{opacity:.5;cursor:default}button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}.actions{display:flex;gap:8px;flex-wrap:wrap}.address{overflow-wrap:anywhere}#status{min-height:24px}#status.error{color:var(--vscode-errorForeground)}small{color:var(--vscode-descriptionForeground)}@media(max-width:600px){main{padding:28px 18px}}</style></head><body><main><h1>SSH & terminals</h1><p>Save host details, then explicitly open an interactive SSH session in the built-in terminal or macOS System Terminal when available. No connection starts when you open this page or save a profile.</p><p>${escape(runtime.reason)}</p><div class="actions"><button data-op="add">Add SSH profile</button><button data-op="refresh">Refresh</button></div><p id="status" role="status" aria-live="polite"></p>${profiles.length?profiles.map(profile=>`<article><h2>${escape(profile.name)}</h2><p class="address">${escape(profile.user)} @ ${escape(profile.host)} · port ${profile.port}<br>${profile.identity?'Key file: '+escape(profile.identity):'Authentication: OpenSSH default key files or interactive password prompt'}</p><div class="actions"><button data-op="connect" data-id="${escape(profile.id)}" ${runtime.available?'':'disabled'}>Built-in terminal…</button>${runtime.systemTerminal?'<button data-op="connectSystem" data-id="'+escape(profile.id)+'">System Terminal…</button>':''}<button data-op="check" data-id="${escape(profile.id)}" ${runtime.available?'':'disabled'}>Check configuration</button><button data-op="edit" data-id="${escape(profile.id)}">Edit</button><button data-op="remove" data-id="${escape(profile.id)}">Remove</button></div></article>`).join(''):'<article>No saved SSH profiles.</article>'}<h2>Trust and session control</h2><p>OpenSSH checks your existing known_hosts files and asks in the terminal before accepting a new host key. Verify the displayed fingerprint with the host owner. Changed host keys are not accepted automatically. Profile checks are local syntax/configuration checks, not proof of network access or login.</p><p>Passwords and private-key contents are never stored in these profiles. Authentication prompts stay in the terminal. Your SSH config file, proxy commands, agent forwarding, X11 forwarding and port forwards are disabled for these sessions. Any commands you type run under the selected SSH account.</p><p>Type exit or close the terminal to end its session. Meshlit chat Stop and workspace-agent permissions do not control this human terminal. Removing a profile does not close an already-open terminal.</p><small>Built-in terminal is available with installed OpenSSH. macOS System Terminal is offered only when installed; its private temporary command file contains connection details, never passwords or key contents, and is removed before SSH starts. Ghostty and Termius handoffs are not implemented. This does not start an SSH server or enroll model workers.</small></main><script nonce="${nonce}">const api=acquireVsCodeApi();let pending=false;const status=document.getElementById('status');document.querySelectorAll('[data-op]').forEach(button=>button.onclick=()=>{if(pending||button.disabled)return;pending=true;api.postMessage({sshPage:true,op:button.dataset.op,id:button.dataset.id});});window.addEventListener('message',({data})=>{if(data.type!=='sshResult')return;pending=false;if(data.idleOnly)return;status.className=data.error?'error':'';status.textContent=data.message||'';});</script></body></html>`;
}
function register(context,vscode,dependencies={}){
 const fileSystem=dependencies.fileSystem||fs;let panel,busy=false,disposed=false;
 const runtime=()=>({...availability(dependencies),systemTerminal:availability(dependencies).available&&systemTerminalAvailable(dependencies)});
 function profiles(){const saved=context.globalState.get(KEY,[]);if(!Array.isArray(saved)||saved.length>64)throw Error('Saved SSH profiles are invalid.');return saved.map(validate);}
 async function persist(next){await context.globalState.update(KEY,next);}
 async function edit(original,currentPanel){
  const inactive=()=>disposed||panel!==currentPanel;
  const draft=original?{...original}:{id:crypto.randomUUID(),name:'',host:'',user:'',port:22,identity:''};
  for(const [key,label,prompt]of [['name','Profile name','A local label; no passwords or secrets.'],['host','SSH host','Exact hostname or IP address, without a URL or user@ prefix.'],['user','SSH username','Account to sign in as on this host.'],['port','SSH port','A number from 1 to 65535.']]){
   const value=await vscode.window.showInputBox({title:label,prompt,value:String(draft[key]),ignoreFocusOut:true,validateInput:input=>{try{validate({...draft,name:key==='name'?input:draft.name||'SSH',host:key==='host'?input:draft.host||'example.test',user:key==='user'?input:draft.user||'user',port:key==='port'?Number(input):draft.port});return undefined;}catch(error){return error.message;}}});
   if(value===undefined||inactive())return false;draft[key]=key==='port'?Number(value):value;
  }
  const keyChoice=await vscode.window.showQuickPick([{id:'keep',label:original?.identity?'Keep selected key file':'Use default keys or password prompt',description:original?.identity||'OpenSSH prompts only in the terminal; no password is saved.'},{id:'choose',label:'Choose private-key file…'},...(original?.identity?[{id:'clear',label:'Use default keys or password prompt'}]:[])],{title:'SSH authentication',ignoreFocusOut:true});
  if(!keyChoice||inactive())return false;
  if(keyChoice.id==='choose'){
   const selected=await vscode.window.showOpenDialog({title:'Choose an existing SSH private-key file',canSelectFiles:true,canSelectFolders:false,canSelectMany:false,openLabel:'Use key file'});
   if(!selected?.[0]||selected[0].scheme!=='file'||inactive())return false;draft.identity=selected[0].fsPath;
  }else if(keyChoice.id==='clear')draft.identity='';else if(keyChoice.id!=='keep')return false;
  const profile=validate(draft);checkIdentity(profile,fileSystem);
  const current=profiles();if(original&&!current.some(item=>item.id===original.id))throw Error('This profile was removed. Refresh the page.');if(!original&&current.length>=64)throw Error('At most 64 profiles can be saved.');
  await persist([profile,...current.filter(item=>item.id!==profile.id)]);return true;
 }
 const post=(message,error=false)=>{if(panel&&!disposed)void panel.webview.postMessage({type:'sshResult',message,error});};
 function refresh(){if(panel&&!disposed)panel.webview.html=render(profiles(),runtime());}
 context.subscriptions.push({dispose(){disposed=true;panel=undefined;}});
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.sshDashboard',()=>{
  if(panel){refresh();panel.reveal();return;}
  const current=require('./shell.cjs').createPanel(vscode,'meshlit.ssh','SSH & terminals',vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});panel=current;context.subscriptions.push(current);refresh();
  current.onDidDispose(()=>{if(panel===current)panel=undefined;});
  current.webview.onDidReceiveMessage(async message=>{
   if(!message||message.sshPage!==true||busy||disposed||panel!==current||!['add','edit','remove','check','connect','connectSystem','refresh'].includes(message.op))return;
   busy=true;
   try{
    if(message.op==='refresh'){refresh();return;}
    if(message.op==='add'){const saved=await edit(undefined,current);if(saved){refresh();post('Profile saved. No connection started.');}else post('Profile setup cancelled.');return;}
    const profile=profiles().find(item=>item.id===message.id);if(!profile)throw Error('That profile is no longer available. Refresh the page.');
    if(message.op==='edit'){if(await edit(profile,current)){refresh();post('Profile saved. No connection started.');}else post('Profile edit cancelled.');return;}
    if(message.op==='remove'){
     const answer=await vscode.window.showWarningMessage('Remove SSH profile “'+profile.name+'”? Existing terminal sessions will stay open.',{modal:true},'Remove profile');
     if(answer!=='Remove profile'){post('Removal cancelled.');return;}if(disposed||panel!==current)return;
     await persist(profiles().filter(item=>item.id!==profile.id));refresh();post('Profile removed. Existing terminal sessions were not changed.');return;
    }
    if(message.op==='check'){post(await checkConfiguration(profile,dependencies));return;}
    const available=runtime();if(!available.available)throw Error(available.reason);if(!vscode.workspace.isTrusted)throw Error('Trust this Studio window before opening a terminal.');
    checkIdentity(profile,fileSystem);
    const useSystem=message.op==='connectSystem';if(useSystem&&!systemTerminalAvailable(dependencies))throw Error('System Terminal is unavailable on this Mac.');
    const answer=await vscode.window.showInformationMessage(`Connect to ${profile.user}@${profile.host}:${profile.port} in ${useSystem?'macOS System Terminal':'the built-in SSH terminal'}?`,{modal:true,detail:'Verify new host fingerprints in the terminal. This opens a human-controlled remote shell. No agent, port forwarding or worker-enrollment grant is created.'},'Open SSH terminal');
    if(answer!=='Open SSH terminal'){post('Connection cancelled.');return;}
    if(disposed||panel!==current||!vscode.workspace.isTrusted)return;
    const latest=profiles().find(item=>item.id===profile.id);if(JSON.stringify(latest)!==JSON.stringify(profile))throw Error('SSH profile changed. Review it again before connecting.');
    checkIdentity(latest,fileSystem);if(!runtime().available)throw Error('OpenSSH is no longer available.');
    if(useSystem){post(await openSystemTerminal(latest,dependencies));return;}
    const terminal=vscode.window.createTerminal({name:'SSH · '+latest.name,shellPath:SSH,shellArgs:argumentsFor(latest),env:{SSH_ASKPASS_REQUIRE:'never',SSH_ASKPASS:null,SSH_AUTH_SOCK:null,SSH_SK_PROVIDER:null},isTransient:true});
    terminal.show();post('SSH terminal opened. Read the terminal for host-key, authentication and connection results; login has not been verified.');
   }catch(error){post(error.message||'SSH action could not complete.',true);}
   finally{busy=false;if(panel===current&&!disposed)void current.webview.postMessage({type:'sshResult',message:'',idleOnly:true});}
  });
 }));
}
module.exports={validate,checkIdentity,availability,argumentsFor,checkConfiguration,systemTerminalAvailable,openSystemTerminal,render,register};
