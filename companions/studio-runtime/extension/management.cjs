const {randomBytes}=require('node:crypto');
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const groups=Object.freeze([
 {id:'models',title:'Models & compute',description:'Choose where inference runs and tune the requests you send.',coverage:'Model controls available',items:[
  ['models','Models and sources','Bundled inference and your saved provider, device and coordinator endpoints.','meshlit.mainModels'],
  ['llm','LLM settings','Sampling, output limits and context history for chat.','meshlit.mainLlmSettings'],
  ['gateways','Provider and gateway dashboards','Open the configured service dashboard or add a model connection.','meshlit.modelPicker']
 ]},
 {id:'devices',title:'Devices & clusters',description:'Review approved sources and the resources their owners advertise.',coverage:'Partial device management',items:[
  ['devices','Device connection assistance','Review exact saved sources and their individual contact-check approval.','meshlit.deviceConnections'],
  ['sharing','Resource sharing','Inspect an authorized host’s resource catalog. Advertised models do not prove free capacity.','meshlit.resourceSharing'],
  ['teams','Agent teams','Review configured roles, bounded jobs and their status. Team routing is separate from model-layer sharding.','meshlit.agentTeams']
 ]},
 {id:'network',title:'Network & connections',description:'Manage explicit model APIs and tools without scanning the network.',coverage:'Connection setup available',items:[
  ['add-model','Add model or server','Connect a local runtime, remote device API, existing coordinator or online provider.','meshlit.addModelSource'],
  ['protocols','APIs and protocols','Supported endpoint contracts and phone-side setup instructions.','meshlit.protocols'],
  ['connectors','MCP connectors','Configure installed or remote tool servers. Agent selection and permissions remain separate.','meshlit.connectors'],
  ['credentials','Credentials','Inspect secret-store availability and manage endpoint-bound provider credentials. Secret values stay outside this overview.','meshlit.credentialsDashboard'],
  ['ssh','SSH & terminals','Review saved connection setup and explicitly connect a user terminal. Opening this dashboard does not start a remote session.','meshlit.sshDashboard']
 ]},
 {id:'health',title:'Health & resources',description:'Read observed host metrics and inspect optional runtimes.',coverage:'Local observations available',items:[
  ['monitor','System resources','Actual CPU, memory and process samples, guarded workload policy, and Stop local model.','meshlit.systemMonitor'],
  ['voice','Voice runtime','OpenLive setup, runtime state, installed speech engines and live-call settings.','meshlit.voice'],
  ['companions','Companion services','Configure optional installed services and use their own availability checks.','meshlit.companions']
 ]},
 {id:'usage',title:'Usage, storage & project memory',description:'Inspect recorded usage and manage project-scoped information.',coverage:'Project scope for memory and changes',items:[
  ['usage','Usage and budgets','Session-local usage or unlocked encrypted history and saved spending controls.','meshlit.usage'],
  ['memory','Project memory','Open the project’s protected notes and recall controls. Choose a workspace when using the main window.','meshlit.memory','project'],
  ['changes','Workspace changes','Review actual last-turn or Git working-tree changes in a project window.','meshlit.workspaceChanges','project'],
  ['workspaces','Workspaces','Open or return to a project in its separate IDE window.','meshlit.workspaces']
 ]}
]);
const destinations=Object.freeze(Object.fromEntries(groups.flatMap(group=>group.items.map(([id,title,description,command,scope])=>[id,{title,description,command,scope}]))));
function runtimeSummary(value={}){
 const state=value&&typeof value==='object'?value:{};
 const model=state.localModel&&typeof state.localModel==='object'?state.localModel:{};
 const labels={ready:'Loaded',loading:'Loading',idle:'Not loaded',stopped:'Stopped',error:'Load failed'};
 const label=typeof model.label==='string'?model.label.slice(0,180):'Bundled local model';
 const usage={session:'Session-only local usage',protected:'Protected history unlocked',locked:'Protected history locked'};
 return {
  host:state.hostReady===true?'Ready':state.hostReady===false?'Not ready':'Not reported',
  model:Object.hasOwn(labels,model.state)?labels[model.state]:'Not reported',
  modelLabel:label,
  usage:Object.hasOwn(usage,state.usageMode)?usage[state.usageMode]:'Not reported'
 };
}
function render(runtime={}){
 const nonce=randomBytes(24).toString('hex'),summary=runtimeSummary(runtime);
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.55 var(--vscode-font-family)}main{max-width:1180px;margin:auto;padding:40px 34px}header{display:flex;justify-content:space-between;gap:20px;align-items:flex-start}h1{font-size:28px;font-weight:600;letter-spacing:-.6px;margin:0 0 8px}h2{font-size:20px;font-weight:500;margin:0 0 6px}h3{font-size:15px;font-weight:500;margin:0 0 7px}p{color:var(--vscode-descriptionForeground);margin:0 0 12px;max-width:80ch}button,input{font:inherit}button{padding:8px 13px;color:var(--vscode-button-secondaryForeground);background:var(--vscode-button-secondaryBackground);border:1px solid var(--vscode-panel-border);border-radius:7px;cursor:pointer}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button:disabled{opacity:.5;cursor:wait}button:focus-visible,input:focus-visible,a:focus-visible,summary:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:3px}.runtime{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:28px 0 10px}.runtime article{padding:17px 18px;border:1px solid var(--vscode-panel-border);border-radius:11px;background:var(--vscode-editorWidget-background,var(--vscode-editor-background))}.runtime small{color:var(--vscode-descriptionForeground);display:block;font-size:11px;margin-bottom:7px}.runtime strong{font-size:15px;font-weight:500;overflow-wrap:anywhere}.runtime p{font-size:11px;margin:6px 0 0;overflow-wrap:anywhere}.observation{font-size:11px}.search-label{display:block;font-size:12px;color:var(--vscode-descriptionForeground);margin:25px 0 7px}input{width:100%;padding:11px 13px;color:var(--vscode-input-foreground);background:var(--vscode-input-background);border:1px solid var(--vscode-input-border,var(--vscode-panel-border));border-radius:8px}.layout{display:grid;grid-template-columns:170px minmax(0,1fr);gap:28px;margin-top:30px}nav{position:sticky;top:16px;align-self:start;display:flex;flex-direction:column;gap:4px}nav a{display:block;padding:9px 10px;text-decoration:none;border-radius:6px;color:var(--vscode-descriptionForeground);font-size:12px}nav a:hover{background:var(--vscode-list-hoverBackground);color:var(--vscode-foreground)}section{margin-bottom:34px;scroll-margin-top:20px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:15px}.coverage{font-size:10px;border:1px solid var(--vscode-panel-border);border-radius:20px;padding:4px 8px;color:var(--vscode-descriptionForeground);text-align:right;max-width:160px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.card{display:flex;flex-direction:column;align-items:flex-start;padding:20px;border:1px solid var(--vscode-panel-border);border-radius:11px}.card p{font-size:12px;flex:1}.card button{font-size:12px;margin-top:8px}.project{display:block;font-size:10px;letter-spacing:.04em;color:var(--vscode-descriptionForeground);margin:0 0 8px}.planned{border-top:1px solid var(--vscode-panel-border);padding-top:22px}.planned summary{cursor:pointer;font-size:13px}.planned ul{color:var(--vscode-descriptionForeground);font-size:12px;line-height:1.7;padding-left:20px}#result{min-height:23px;margin:12px 0;color:var(--vscode-descriptionForeground);font-size:12px}#result.error{color:var(--vscode-errorForeground)}#empty{padding:24px 0}[hidden]{display:none!important}@media(max-width:850px){.layout{display:block}nav{position:static;flex-direction:row;flex-wrap:wrap;margin-bottom:20px}nav a{padding:7px}.section-heading{flex-direction:column;gap:5px}.coverage{text-align:left;max-width:none;margin-bottom:12px}}@media(max-width:560px){main{padding:26px 18px}.runtime,.grid{grid-template-columns:1fr}header{flex-direction:column;gap:8px}}
 </style></head><body><main><header><div><h1>Management</h1><p>Models, devices, connections and resources in one place. Each dashboard shows the controls and observations it actually supports.</p></div><button id="refresh">Refresh status</button></header><div class="runtime" aria-label="Current Studio runtime"><article><small>MODEL HOST</small><strong id="host-state">${escape(summary.host)}</strong><p>This Studio window</p></article><article><small>LOCAL MODEL</small><strong id="model-state">${escape(summary.model)}</strong><p id="model-label">${escape(summary.modelLabel)}</p></article><article><small>USAGE MODE</small><strong id="usage-state">${escape(summary.usage)}</strong><p>Credentials and tool grants remain separate</p></article></div><p class="observation">Runtime state is read from this window. Refresh to update it. Remote availability is checked in each connection’s own dashboard.</p><label class="search-label" for="search">Find a management dashboard</label><input id="search" type="search" placeholder="Try models, devices, memory or CPU" autocomplete="off"><div id="result" role="status" aria-live="polite"></div><div class="layout"><nav aria-label="Management categories">${groups.map(group=>`<a href="#${group.id}">${escape(group.title)}</a>`).join('')}</nav><div>${groups.map(group=>`<section id="${group.id}"><div class="section-heading"><div><h2>${escape(group.title)}</h2><p>${escape(group.description)}</p></div><span class="coverage">${escape(group.coverage)}</span></div><div class="grid">${group.items.map(([id,title,description,_command,scope])=>`<article class="card" data-search="${escape(group.title+' '+title+' '+description)}">${scope==='project'?'<span class="project">PROJECT WINDOW</span>':''}<h3>${escape(title)}</h3><p>${escape(description)}</p><button data-destination="${id}" aria-label="Open ${escape(title)}">${scope==='project'?'Open in project':'Open dashboard'}</button></article>`).join('')}</div></section>`).join('')}<p id="empty" hidden>No matching dashboard. Try a model, connection or resource name.</p><details class="planned"><summary>Coverage still to build and qualify</summary><ul><li>Unified device enrollment, peer groups, worker placement and cluster recovery management.</li><li>Automatic scenario routing and load balancing across devices; a gateway dashboard is a separate configured service.</li><li>Cross-device health history and alerts. Current resource observations are local; thermal and power-saver signals are unavailable in the current CPU policy adapter.</li><li>A full storage allocation, cleanup and media-artifact manager. Usage history, project memory and file changes have separate controls today.</li></ul></details></div></div></main><script nonce="${nonce}">
 const api=acquireVsCodeApi(),result=document.getElementById('result'),search=document.getElementById('search');let pending=false;
 function busy(value){pending=value;document.querySelectorAll('button').forEach(button=>button.disabled=value);}
 function filter(){const words=search.value.toLowerCase().trim().split(/\\s+/).filter(Boolean);let count=0;document.querySelectorAll('.card').forEach(card=>{card.hidden=!words.every(word=>card.dataset.search.toLowerCase().includes(word));if(!card.hidden)count++;});document.querySelectorAll('section').forEach(section=>section.hidden=![...section.querySelectorAll('.card')].some(card=>!card.hidden));document.getElementById('empty').hidden=count!==0;api.setState({...api.getState(),managementQuery:search.value});}
 search.addEventListener('input',filter);search.value=api.getState()?.managementQuery||'';filter();
 document.querySelectorAll('[data-destination]').forEach(button=>button.onclick=()=>{if(pending)return;busy(true);result.className='';result.textContent='Opening dashboard…';api.postMessage({managementPage:true,op:'open',id:button.dataset.destination});});
 document.getElementById('refresh').onclick=()=>{if(pending)return;busy(true);api.postMessage({managementPage:true,op:'refresh'});};
 window.addEventListener('message',({data})=>{if(data.type!=='managementResult')return;busy(false);result.className=data.error?'error':'';result.textContent=data.error||data.message||'';if(data.runtime){document.getElementById('host-state').textContent=data.runtime.host;document.getElementById('model-state').textContent=data.runtime.model;document.getElementById('model-label').textContent=data.runtime.modelLabel;document.getElementById('usage-state').textContent=data.runtime.usage;}});
 api.postMessage({managementPage:true,op:'refresh'});
 </script></body></html>`;
}
function register(context,vscode,{getRuntime=()=>({})}={}){
 let panel;
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.management',async()=>{
  if(panel){const existing=panel;existing.reveal();try{const value=await getRuntime();if(panel===existing)await existing.webview.postMessage({type:'managementResult',runtime:runtimeSummary(value),message:'Runtime state refreshed.'});}catch{if(panel===existing)await existing.webview.postMessage({type:'managementResult',runtime:runtimeSummary(),error:'Runtime state could not be read. Open the specific dashboard to inspect its status.'});}return;}
  const current=require('./shell.cjs').createPanel(vscode,'meshlit.management','Management',vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  panel=current;context.subscriptions.push(current);let busy=false,closed=false;
  current.webview.html=render();current.onDidDispose(()=>{closed=true;panel=undefined;});
  const refresh=async()=>{try{const value=await getRuntime();if(!closed)await current.webview.postMessage({type:'managementResult',runtime:runtimeSummary(value),message:'Runtime state refreshed.'});}catch{if(!closed)await current.webview.postMessage({type:'managementResult',runtime:runtimeSummary(),error:'Runtime state could not be read. Open the specific dashboard to inspect its status.'});}};
  current.webview.onDidReceiveMessage(async message=>{
   if(closed||busy||message?.managementPage!==true)return;
   if(message.op!=='refresh'&&(message.op!=='open'||!Object.hasOwn(destinations,message.id)))return;
   busy=true;
   try{
    if(message.op==='refresh'){await refresh();return;}
    const destination=destinations[message.id];
    const hasProject=!!vscode.workspace?.workspaceFolders?.some(folder=>folder.uri?.scheme==='file');
    const handoff=destination.scope==='project'&&!hasProject;
    await vscode.commands.executeCommand(handoff?'meshlit.workspaces':destination.command);
    if(!closed)await current.webview.postMessage({type:'managementResult',message:handoff?'Choose a workspace, then open '+destination.title.toLowerCase()+' in its project window.':''});
   }catch{if(!closed)await current.webview.postMessage({type:'managementResult',error:'This dashboard could not open. Check its setup and permissions, then try again.'});}
   finally{busy=false;}
  });
  await refresh();
 }));
}
module.exports={register,render,runtimeSummary,groups,destinations};
