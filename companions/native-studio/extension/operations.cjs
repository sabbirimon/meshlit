'use strict';
const os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const fs=require('node:fs/promises'),{execFile}=require('node:child_process'),{promisify}=require('node:util');
const run=promisify(execFile);
const groups={
 permissions:[['Chat tool permissions',['internet','microphone','files','share','speech']],['Model and internet access',['models','search','attach','guard']],['Device access',['voice','control','stopControl','pair']]],
 management:[['Chat and permissions',['permissions','voice','liveVoice','search','attach']],['Models and compute',['models','server','cpu']],['Connections and access',['pair','ssh','mcp','guard']],['Host resources',['resources','inventory']]],
 tools:[['Internet and media',['search','attach','voice']],['Desktop tools',['control','stopControl','ssh']],['Developer tools',['ide','compiler','memory']],['Connectors and diagnostics',['mcp','toolList','resources','inventory']]],
 settings:[['Chat and inference',['welcome','models','cpu','guard']],['Devices and permissions',['pair','stopControl']],['Developer',['server','inventory']]]
};
const titles={permissions:'Permissions',management:'Management',tools:'Tools',settings:'Meshlit Settings'};
function operations(vscode,context,root,hooks){
 let panel,section,token=0,detail=false;
 function items(){
  const cpu=context.globalState.get('threads',0);
  const data={
   welcome:['Welcome and setup','Choose how to start chatting',hooks.welcome],
   permissions:['Permissions','Review access by tool',()=>hooks.go('permissions')],
   liveVoice:['Voice and vision','OpenLive settings, voice previews and companion controls',()=>vscode.commands.executeCommand('meshlit.voice')],
   voice:['Voice input','Dictate a message and review the draft',hooks.voice],
   attach:['Attach a file or image','Choose a file for the next message',hooks.attach],
   search:['Internet search','Wikipedia sources in chat or web search in your browser',hooks.webSearch],
   models:['Models & providers',(1+context.globalState.get('model-catalog',[]).length)+' model registrations',hooks.models],
   server:['Local API server',hooks.server(),hooks.models],
   cpu:['CPU thread limit',cpu?cpu+' threads':'Automatic',hooks.cpu],
   guard:['Reply filtering',context.globalState.get('filter-output',true)?'On':'Off',hooks.guard],
   pair:['Paired devices',context.globalState.get('verified-peers',[]).length+' verified identities',hooks.pair],
   control:['Control a local app','Select a supported app and review an action',hooks.control],
   stopControl:['Stop app control','End the current local control session',()=>vscode.commands.executeCommand('meshlit.studio.stopControl')],
   ssh:['SSH profiles & terminals','Manage connections and open a terminal',()=>vscode.commands.executeCommand('meshlit.sshDashboard')],
   ide:['Open coding IDE','Choose a project folder for a separate window',hooks.ide],
   memory:['Project memory',vscode.workspace.workspaceFolders?.length?'Notes, action history and context preview':'Available in a trusted project window',()=>vscode.commands.executeCommand('meshlit.memory')],
   resources:['Processor & memory',os.availableParallelism()+' logical CPUs · '+(os.freemem()/1024**3).toFixed(1)+' GiB free',async()=>showData('Host resources',[
    ['Operating system',os.type()+' '+os.release()+' · '+os.arch()],['Processor',os.cpus()[0]?.model||os.arch()],['Logical CPUs',os.availableParallelism()],['Total RAM',(os.totalmem()/1024**3).toFixed(2)+' GiB'],['Free RAM',(os.freemem()/1024**3).toFixed(2)+' GiB']])],
   inventory:['Installed components','Workbench, compiler, runtime and model files',async()=>{
    const m=JSON.parse(await fs.readFile(path.join(root,'STUDIO-CONTENTS.json'),'utf8'));
    return showData('Installed components',Object.entries(m.components).map(([key,v])=>[key,v.files+' files · '+((v.applicationBytes+v.modelBytes)/1024**2).toFixed(1)+' MiB']));}],
   mcp:['MCP servers','Review installed server configurations',()=>vscode.commands.executeCommand('workbench.mcp.listServer')],
   toolList:['Registered model tools','Inspect the tool inventory exposed by installed extensions',async()=>{
       const list=(vscode.lm?.tools||[]).slice(0,128).map(t=>[String(t.name),String(t.description||'').slice(0,300)]);
       return showData('Registered model tools',list.length?list:[['Installed tools','No model tools registered in this host']]);}],
   compiler:['Check bundled compiler','Run Zig version on this device',async()=>{const r=await run(path.join(root,'toolchain',process.platform==='win32'?'zig.exe':'zig'),['version'],{timeout:10000,maxBuffer:4096,windowsHide:true});vscode.window.showInformationMessage('Bundled Zig '+r.stdout.trim());}]
  };
  for(const [id,label] of Object.entries(hooks.permissionLabels))data[id]=[label,hooks.allowed(id)?'Allowed':'Blocked',()=>hooks.editPermission(id)];
  return data;
 }
 async function showData(title,pairs){detail=true;await panel?.webview.postMessage({type:'details',title,pairs});}
 function render(){
  const data=items();
  return panel?.webview.postMessage({type:'render',title:titles[section],groups:groups[section].map(([title,ids])=>({title,items:ids.map(id=>({id,label:data[id][0],detail:data[id][1],disabled:id==='memory'&&(!vscode.workspace.workspaceFolders?.length||!vscode.workspace.isTrusted)}))}))});
 }
 function open(target){
  if(!Object.hasOwn(groups,target))throw Error('Unknown Studio dashboard');
  section=target;token++;detail=false;
  if(panel){panel.title=titles[target];panel.reveal();render();return;}
  panel=vscode.window.createWebviewPanel('meshlit.operations',titles[target],vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[],retainContextWhenHidden:true});
  panel.onDidDispose(()=>{panel=undefined;token++;});
  let running=false;
  panel.webview.onDidReceiveMessage(async m=>{
   if(m?.op==='ready')return render();
   if(m?.op==='chat')return hooks.go('chat');
   if(m?.op==='back')return detail?(detail=false,render()):hooks.back();
   if(m?.op==='section'&&Object.hasOwn(groups,m.target))return hooks.go(m.target);
   if(running||m?.op!=='action'||!groups[section].some(([,ids])=>ids.includes(m.id)))return;
   if(m.id==='memory'&&(!vscode.workspace.workspaceFolders?.length||!vscode.workspace.isTrusted))return;
   const version=token;running=true;
   try{await items()[m.id][2]();}catch(e){vscode.window.showErrorMessage(e.message);}finally{running=false;if(version===token&&!detail)render();}
  });
  const n=crypto.randomBytes(18).toString('hex');
  panel.webview.html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${n}';script-src 'nonce-${n}'"><style nonce="${n}">body{max-width:940px;margin:24px auto;padding:0 24px;font:14px/1.5 var(--vscode-font-family);color:var(--vscode-foreground)}nav{display:flex;gap:9px;flex-wrap:wrap}button{font:inherit;color:inherit;background:var(--vscode-button-secondaryBackground);border:1px solid var(--vscode-panel-border);border-radius:7px;padding:6px 12px;cursor:pointer}button:disabled{opacity:.6}button:focus-visible{outline:2px solid var(--vscode-focusBorder)}h1{margin:32px 0 16px}h2{font-size:16px}section{border-top:1px solid var(--vscode-panel-border);padding:8px 0 20px}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:8px}.card{text-align:left;display:grid;gap:4px;padding:12px}.card span,dt{color:var(--vscode-descriptionForeground);font-size:12px}dd{margin:0 0 8px}</style></head><body><nav><button id="back">← Back</button><button id="chat">General chat</button><button data-page="management">Management</button><button data-page="tools">Tools</button><button data-page="settings">Settings</button></nav><h1></h1><main></main><script nonce="${n}">const a=acquireVsCodeApi(),main=document.querySelector('main'),title=document.querySelector('h1'),tag=(name,text)=>{const e=document.createElement(name);e.textContent=text;return e};document.querySelector('#back').onclick=()=>a.postMessage({op:'back'});document.querySelector('#chat').onclick=()=>a.postMessage({op:'chat'});for(const b of document.querySelectorAll('[data-page]'))b.onclick=()=>a.postMessage({op:'section',target:b.dataset.page});window.addEventListener('message',({data:m})=>{title.textContent=m.title;main.replaceChildren();if(m.type==='details'){const dl=tag('dl','');for(const [k,v]of m.pairs)dl.append(tag('dt',k),tag('dd',v));main.append(dl);return;}for(const g of m.groups){const section=tag('section',''),cards=tag('div','');cards.className='cards';section.append(tag('h2',g.title),cards);for(const i of g.items){const b=tag('button','');b.className='card';b.disabled=i.disabled;b.append(tag('strong',i.label),tag('span',i.detail));b.onclick=()=>a.postMessage({op:'action',id:i.id});cards.append(b);}main.append(section);}});a.postMessage({op:'ready'});</script></body></html>`;
 }
 context.subscriptions.push({dispose:()=>panel?.dispose()});
 return {open};
}
module.exports={operations,groups};
