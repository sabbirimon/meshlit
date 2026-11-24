'use strict';
const viewHelp=require('./view-help.cjs');
const os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const fs=require('node:fs/promises'),{execFile}=require('node:child_process'),{promisify}=require('node:util');
const run=promisify(execFile);
const shirudo=require('./shirudo.cjs');
const groups={
 cloud:[['Provider accounts',['aws','gcp','azure']],['Model platforms',['hubExplore']]],
 permissions:[['Chat tool permissions',['internet','microphone','files','share','speech']],['Model and internet access',['models','search','attach','guard']],['Device access',['voice','control','stopControl','pair']]],
 management:[['Chat and permissions',['permissions','voice','liveVoice','search','attach']],['Models and compute',['models','server','cpu']],['Connections and access',['pair','ssh','mcp','guard']],['Host resources',['resources','inventory']]],
 tools:[['Network security',['shieldReport','shieldLive','shieldExport','shieldApply']],['Internet and media',['search','attach','voice']],['Desktop tools',['control','stopControl','ssh']],['Developer tools',['ide','compiler','memory']],['Community',['moltbook','hubExplore']],['Connectors and diagnostics',['mcp','toolList','resources','inventory']]],
 settings:[['Appearance',['theme']],['Chat and inference',['welcome','models','cpu','guard']],['Devices and permissions',['pair','stopControl']],['Developer',['server','inventory']]]
};
const titles={cloud:'Cloud providers',permissions:'Permissions',management:'Management',tools:'Tools',settings:'Meshlit Settings'};
function operations(vscode,context,root,hooks){
 let panel,section,token=0,detail=false;
 let lastShieldReport,lastDetails;
 async function showShield(live){
  const report=await shirudo.inspect(vscode,live);
  if(!report)return;
  lastShieldReport={...report,source:live?'Local Linux runtime':'Imported file',readAt:new Date().toISOString()};
  return showData('Shirudo · '+report.verdict,[
   ['Source',lastShieldReport.source+' · host-reported status'],['Host',report.host],['Target',report.target?'Yes':'No'],
   ['Scan summary',report.scan.alerts+' alerts · '+report.scan.warnings+' warnings · '+report.scan.ok+' passed'],
   ['Missing checks',report.scan.missing.join(', ')||'None'],['Loaded shields',String(report.loaded)],...report.conditions,['TPM attestation','Unverified']
  ]);
 }
 function items(){
  const cpu=context.globalState.get('threads',0);
  const data={
   shieldReport:['Inspect Shirudo report','Open a Linux node’s status JSON',()=>showShield(false)],
   shieldExport:['Export Shirudo report','Save the original JSON from the last import or live scan',()=>shirudo.exportReport(vscode,lastShieldReport)],
   shieldLive:['Scan local Linux target','Inspect this Linux target with operator authorization',()=>showShield(true)],
   shieldApply:['Apply signed Shirudo shield','Review a local .upatch and authorize the verified load',()=>shirudo.applySigned(vscode)],
   theme:['Desktop color theme','Choose a light, dark or high-contrast theme',()=>vscode.commands.executeCommand('workbench.action.selectTheme')],
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
   moltbook:['Moltbook','Read ten recent public posts from www.moltbook.com',async()=>{
       const posts=await require('./community.cjs').feed(AbortSignal.timeout(15000));
       return showData('Moltbook',posts.length?posts.map(p=>[p.title,p.text]):[['Recent posts','No posts returned']]);}],
   hubExplore:['Hugging Face Hub','Explore models, datasets and Spaces',async()=>{
       const kind=await vscode.window.showQuickPick(['models','datasets','spaces'],{title:'Explore Hugging Face'});
       if(!kind)return;
       const query=await vscode.window.showInputBox({title:'Search '+kind,prompt:'Search by name or leave empty to browse'});
       if(query===undefined)return;
       const url=new URL('https://huggingface.co/api/'+kind);
       url.search=new URLSearchParams({search:query.slice(0,200),limit:'20'});
       const response=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(15000)});
       if(!response.ok)throw Error('Hub returned HTTP '+response.status);
       const entries=await response.json();
       const picked=await vscode.window.showQuickPick(entries.filter(e=>typeof e.id==='string').map(e=>e.id),{title:'Open a Hugging Face '+kind+' page'});
       if(picked && /^[\w.-]+\/[\w.-]+$/.test(picked))await vscode.env.openExternal(vscode.Uri.parse('https://huggingface.co/'+(kind==='models'?'':kind+'/')+picked));}],
   toolList:['Registered model tools','Inspect the tool inventory exposed by installed extensions',async()=>{
       const list=(vscode.lm?.tools||[]).slice(0,128).map(t=>[String(t.name),String(t.description||'').slice(0,300)]);
       return showData('Registered model tools',list.length?list:[['Installed tools','No model tools registered in this host']]);}],
   compiler:['Check bundled compiler','Run Zig version on this device',async()=>{const r=await run(path.join(root,'toolchain',process.platform==='win32'?'zig.exe':'zig'),['version'],{timeout:10000,maxBuffer:4096,windowsHide:true});vscode.window.showInformationMessage('Bundled Zig '+r.stdout.trim());}]
  };
  const cloud=require('./cloud-providers.cjs');
  for(const [id,item] of Object.entries(cloud.providers))data[id]=[item.name,'Read account details or open the console',()=>cloud.show(id,vscode,showData)];
  for(const [id,label] of Object.entries(hooks.permissionLabels))data[id]=[label,hooks.allowed(id)?'Allowed':'Blocked',()=>hooks.editPermission(id)];
  return data;
 }
 async function showData(title,pairs){lastDetails={title,pairs};detail=true;await panel?.webview.postMessage({type:'details',title,pairs});}
 const dependencies={
  theme:['workbench.action.selectTheme','Use a workbench with the color theme picker enabled.'],
  liveVoice:['meshlit.voice','Enable the Meshlit voice companion and reopen this page.'],
  ssh:['meshlit.sshDashboard','Enable the Meshlit SSH integration and reopen this page.'],
  memory:['meshlit.memory','Enable the Meshlit project memory extension and reopen this page.'],
  mcp:['workbench.mcp.listServer','Use a workbench with MCP server support and reopen this page.']
 };
 function unavailable(id,registered){
  if(['shieldLive','shieldApply'].includes(id) && (process.platform!=='linux'||![shirudo.runtime,'/usr/bin/pkexec'].every(file=>require('node:fs').existsSync(file))))return 'Configure Shirudo on a supported Linux target. Use Inspect Shirudo report to view an exported report here.';
  if(id==='shieldExport'&&!lastShieldReport)return 'Inspect a report or scan the local Linux target first.';
  if(id==='memory'&&(!vscode.workspace.workspaceFolders?.length||!vscode.workspace.isTrusted))return 'Choose Open coding IDE, open your project folder and review its workspace trust to use Project memory.';
  if(id==='voice'&&process.platform!=='win32')return 'Built-in speech recognition is available on Windows. Use system dictation in the chat message box on this device.';
  if(dependencies[id]&&!registered.has(dependencies[id][0]))return dependencies[id][1];
  if(id==='toolList'&&!vscode.lm?.tools)return 'Open Studio with a workbench that supports the language model tool registry.';
  const permission={shieldReport:'files',shieldExport:'files',moltbook:'internet',hubExplore:'internet',search:'internet',attach:'files',voice:'microphone'}[id];
  return permission&&!hooks.allowed(permission)?'Open Permissions and allow '+hooks.permissionLabels[permission]+'.':'';
 }
 async function describe(target){
  const registered=new Set(await vscode.commands.getCommands(true)), data=items();
  return groups[target].map(([title,ids])=>({title,items:ids.map(id=>({
   id,label:data[id][0],detail:data[id][1],reason:unavailable(id,registered)
  }))}));
 }
 async function render(){
  const version=token,target=section;
  const sections=await describe(target);
  if(version===token&&target===section&&!detail)return panel?.webview.postMessage({type:'render',title:titles[target],groups:sections});
 }
 if(vscode.lm?.registerTool){
  context.subscriptions.push(vscode.lm.registerTool('meshlit_moltbook_read',{
   async invoke(_,cancel){
    if(!hooks.allowed('internet'))throw Error('Allow Internet search in Permissions to read Moltbook.');
    const stop=new AbortController();
    const subscription=cancel?.onCancellationRequested(()=>stop.abort());
    try{
     if(cancel?.isCancellationRequested)throw Error('Request cancelled');
     const posts=await require('./community.cjs').feed(AbortSignal.any([stop.signal,AbortSignal.timeout(15000)]));
     return new vscode.LanguageModelToolResult([new vscode.LanguageModelTextPart(JSON.stringify({source:'Moltbook',untrusted:true,posts}))]);
    }finally{subscription?.dispose();}
   }
  }));
  context.subscriptions.push(vscode.lm.registerTool('meshlit_shirudo_status',{
   prepareInvocation(){return {confirmationMessages:{title:'Share Shirudo status with the model?',message:'Share the verdict, check states, shield count and time for the last viewed report.'}};},
   async invoke(){
    if(lastShieldReport?.source==='Imported file'&&!hooks.allowed('files'))throw Error('File access is disabled.');
    if(!lastShieldReport)throw Error('Inspect a report in Tools → Network security first.');
    const {verdict,conditions,loaded,source,readAt,scan}=lastShieldReport;
    return new vscode.LanguageModelToolResult([new vscode.LanguageModelTextPart(JSON.stringify({verdict,conditions,loaded,source,readAt,scan,attested:false}))]);
   }
  }));
  context.subscriptions.push(vscode.lm.registerTool('meshlit_inspect_settings',{
   async invoke(options){
    const target=options.input?.section;
    if(target!==undefined&&!Object.hasOwn(groups,target))throw Error('Unknown settings section');
    const sections=[];
    for(const name of target?[target]:Object.keys(groups)){
     const entries=(await describe(name)).flatMap(group=>group.items.map(({id,label,reason})=>({id,label,available:!reason,reason})));
     sections.push({section:name,controls:entries});
    }
    const result={schema:'meshlit.settings.catalog.v1',sections};
    return new vscode.LanguageModelToolResult([new vscode.LanguageModelTextPart(JSON.stringify(result))]);
   }
  }));
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
   if(m?.op==='discuss'&&detail&&lastDetails){
    const data=lastDetails;return hooks.result(data.title,data.pairs.filter(([k])=>data.title.startsWith('Shirudo')?k!=='Host':true).map(([k,v])=>k+' · '+v).join('\n'));
   }
   if(m?.op==='chat')return hooks.go('chat');
   if(m?.op==='back')return detail?(detail=false,render()):hooks.back();
   if(m?.op==='section'&&Object.hasOwn(groups,m.target))return hooks.go(m.target);
   if(running||m?.op!=='action'||!groups[section].some(([,ids])=>ids.includes(m.id)))return;
   const version=token;running=true;
   try{
    const registered=new Set(await vscode.commands.getCommands(true));
    if(version!==token||unavailable(m.id,registered))return;
    await items()[m.id][2]();
   }catch(e){vscode.window.showErrorMessage(e.message);}finally{running=false;if(version===token&&!detail)render();}
  });
  const n=crypto.randomBytes(18).toString('hex');
  panel.iconPath=vscode.Uri.file(hooks.brand.file);
  panel.webview.html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${n}';script-src 'nonce-${n}'"><style nonce="${n}">body{max-width:940px;margin:24px auto;padding:0 24px;font:14px/1.5 var(--vscode-font-family);color:var(--vscode-foreground)}nav{display:flex;gap:9px;flex-wrap:wrap}button{font:inherit;color:inherit;background:var(--vscode-button-secondaryBackground);border:1px solid var(--vscode-panel-border);border-radius:7px;padding:6px 12px;cursor:pointer}button:disabled{opacity:.6}button:focus-visible{outline:2px solid var(--vscode-focusBorder)}h1{margin:32px 0 16px;text-align:center;color:var(--vscode-symbolIcon-classForeground,var(--vscode-textLink-foreground));font-family:var(--vscode-font-family),system-ui,sans-serif}h2{font-size:16px;color:var(--vscode-textLink-foreground)}section{border-top:1px solid var(--vscode-panel-border);padding:8px 0 20px}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:8px}.card{text-align:left;display:grid;gap:4px;padding:12px}.card span,dt{color:var(--vscode-descriptionForeground);font-size:12px}dd{margin:0 0 8px}nav svg{width:26px;height:26px;vertical-align:middle}${viewHelp.styles}</style></head><body><nav>${hooks.brand.markup}<button id="back">← Back</button><button id="chat">General chat</button><button data-page="management">Management</button><button data-page="tools">Tools</button><button data-page="cloud">Cloud</button><button data-page="settings">Settings</button></nav><h1></h1><main></main><script nonce="${n}">${viewHelp.script}const a=acquireVsCodeApi(),main=document.querySelector('main'),title=document.querySelector('h1'),tag=(name,text)=>{const e=document.createElement(name);e.textContent=text;return e};document.querySelector('#back').onclick=()=>a.postMessage({op:'back'});document.querySelector('#chat').onclick=()=>a.postMessage({op:'chat'});for(const b of document.querySelectorAll('[data-page]'))b.onclick=()=>a.postMessage({op:'section',target:b.dataset.page});window.addEventListener('message',({data:m})=>{title.textContent=m.title;main.replaceChildren();if(m.type==='details'){const dl=tag('dl','');for(const [k,v]of m.pairs)dl.append(tag('dt',k),tag('dd',v));const discuss=tag('button','Explain in chat');discuss.onclick=()=>a.postMessage({op:'discuss'});main.append(discuss,dl);return;}for(const g of m.groups){const section=tag('section',''),cards=tag('div','');cards.className='cards';section.append(tag('h2',g.title),cards);for(const i of g.items){const b=tag('button','');b.className='card';setAvailability(b,i.reason);b.append(tag('strong',i.label),tag('span',i.detail));b.onclick=()=>a.postMessage({op:'action',id:i.id});cards.append(b);}main.append(section);}});a.postMessage({op:'ready'});</script></body></html>`;
 }
 context.subscriptions.push({dispose:()=>panel?.dispose()});
 async function shieldChat(intent){
  if(intent==='status'||intent==='explain'){
   if(lastShieldReport?.source==='Imported file'&&!hooks.allowed('files'))throw Error('File access is disabled.');
   if(!lastShieldReport)return 'Open a report with /shirudo import or scan a configured target with /shirudo scan.';
  }else if(intent==='export'){
   if(!hooks.allowed('files'))throw Error('Allow file access in Permissions first.');
   await shirudo.exportReport(vscode,lastShieldReport);return 'Export dialog closed.';
  }else{
   const live=intent==='scan' && process.platform==='linux' && require('node:fs').existsSync(shirudo.runtime);
   if(!live&&!hooks.allowed('files'))throw Error('Allow file access in Permissions first.');
   const report=await shirudo.inspect(vscode,live);if(!report)return 'Scan cancelled.';
   lastShieldReport={...report,source:live?'Local Linux runtime':'Imported file',readAt:new Date().toISOString()};
  }
  const r=lastShieldReport;
  if(['explain','scan','import'].includes(intent)){hooks.result('Shirudo',r.verdict+' · '+r.loaded+' loaded shields\n'+r.conditions.map(c=>c.join(' · ')).join('\n')+'\nMissing checks: '+r.scan.missing.join(', ')+'\nTPM attestation unverified.');return 'Report added to the chat draft.';}
  return [r.source,r.host,r.verdict,r.scan.alerts+' alerts',r.scan.warnings+' warnings',r.scan.missing.length+' missing checks','TPM attestation unverified'].join(' · ');
 }
 async function composerDestinations(){
  const list=new Map();
  for(const target of Object.keys(groups))for(const group of await describe(target))for(const item of group.items)
    if(!list.has(item.id))list.set(item.id,{label:item.label,target:'studio:'+item.id,description:group.title});
  return [...list.values()];
 }
 async function composerAction(id){
  const known=Object.keys(groups).find(k=>groups[k].some(([,ids])=>ids.includes(id)));
  if(!known)throw Error('Unknown Studio tool.');
  const reason=unavailable(id,new Set(await vscode.commands.getCommands(true)));
  if(reason)throw Error(reason);
  hooks.go(known);
  return items()[id][2]();
 }
 return {open,shieldChat,composerDestinations,composerAction};
}
module.exports={operations,groups};
