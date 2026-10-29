const vscode=require('vscode');const crypto=require('node:crypto');const connections=require('./connections.cjs');const catalog=require('./model-catalog.cjs');
const SERVICES=['OmniRoute','FreeLLMAPI','Hugging Face'];
const defaults={OmniRoute:'http://127.0.0.1:20128/',FreeLLMAPI:'http://127.0.0.1:31415/','Hugging Face':'https://huggingface.co/settings/inference-providers'};
function dashboardAddress(value){const u=new URL(value);if(u.username||u.password||u.search||u.hash||!(u.protocol==='https:'||u.protocol==='http:'&&['127.0.0.1','[::1]'].includes(u.hostname)))throw Error('Use HTTPS or literal loopback HTTP without URL secrets');return u.toString();}
async function openDashboard(url){
 dashboardAddress(url);
 const config=vscode.workspace.getConfiguration('workbench.browser');
 if(config.get('dataStorage')!=='ephemeral'){
  const answer=await vscode.window.showInformationMessage('Vendor dashboards need separate temporary browser sessions. Closing a dashboard clears that tab’s login. Use isolated sessions for the integrated browser?','Use isolated sessions');if(!answer)return;
  await config.update('dataStorage','ephemeral',vscode.ConfigurationTarget.Global);
 }
 const commands=await vscode.commands.getCommands(true);if(!commands.includes('workbench.action.browser.open'))throw Error('This workbench does not include the integrated browser');
 await vscode.commands.executeCommand('workbench.action.browser.open',{url,openToSide:true});
}
function register(context){const panels=new Map();
 for(const service of SERVICES)context.subscriptions.push(vscode.commands.registerCommand('meshlit.gateway.'+service,()=>{
  if(panels.has(service)){panels.get(service).reveal();return;}
  const panel=vscode.window.createWebviewPanel('meshlit.gateway',service,vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});panels.set(service,panel);panel.onDidDispose(()=>panels.delete(service));
  const profiles=()=>context.globalState.get('meshlit.connections',[]).filter(p=>p.service===service);
  let current, busy=false;
  function state(){const list=profiles();if(!list.some(p=>p.id===current))current=list[0]?.id;const profile=list.find(p=>p.id===current);panel.webview.postMessage({type:'state',service,profiles:list.map(p=>({id:p.id,endpoint:p.endpoint,model:p.model})),current,models:profile?context.globalState.get('meshlit.catalog.'+profile.id,[]):[],health:profile?context.globalState.get('meshlit.connectionHealth',{})[profile.id]:null,dashboard:context.globalState.get('meshlit.dashboard.'+service,defaults[service])});}
  panel.webview.html=render(service);
  panel.webview.onDidReceiveMessage(async message=>{
   if(message.op==='ready'){state();return;}if(busy)return;busy=true;
   try{
    const profile=profiles().find(p=>p.id===current);
    if(message.op==='select'&&profiles().some(p=>p.id===message.id))current=message.id;
    else if(message.op==='add'){const p=await connections.addConnection(context,service);if(p)current=p.id;}
    else if(message.op==='dashboard')await openDashboard(context.globalState.get('meshlit.dashboard.'+service,defaults[service]));
    else if(message.op==='address'){
     const value=await vscode.window.showInputBox({title:service+' dashboard address',value:context.globalState.get('meshlit.dashboard.'+service,defaults[service]),prompt:'Desktop/server ports may differ. This is separate from the API endpoint.'});
     if(value)await context.globalState.update('meshlit.dashboard.'+service,dashboardAddress(value));
    }else if(message.op==='refresh'&&profile){
     connections.endpoint(profile.endpoint);const token=await context.secrets.get('meshlit.model.'+profile.id)||'';
     const models=await catalog.fetchCatalog(profile.endpoint,token);await context.globalState.update('meshlit.catalog.'+profile.id,models);
    }else if(message.op==='health'&&profile){
     connections.endpoint(profile.endpoint);const token=await context.secrets.get('meshlit.model.'+profile.id)||'';
     const result=await require('./connection-health.cjs').checkConnection(profile,token);const values=context.globalState.get('meshlit.connectionHealth',{});values[profile.id]=result;await context.globalState.update('meshlit.connectionHealth',values);
    }else if(message.op==='chat'&&profile){
     const models=context.globalState.get('meshlit.catalog.'+profile.id,[]);if(!models.some(m=>m.id===message.model))throw Error('Refresh the catalog before choosing this model');
     const all=context.globalState.get('meshlit.connections',[]);await context.globalState.update('meshlit.connections',all.map(p=>p.id===profile.id?{...p,model:message.model}:p));
     await vscode.commands.executeCommand('meshlit.chat',profile.id);
    }else if(message.op==='remove'&&profile){
     const choice=await vscode.window.showWarningMessage('Remove this saved connection and API key? Existing chats using it will be stopped.',{modal:true},'Remove');if(choice){
      await vscode.commands.executeCommand('meshlit.revokeConnection',profile.id);
      await context.secrets.delete('meshlit.model.'+profile.id);await context.globalState.update('meshlit.connections',context.globalState.get('meshlit.connections',[]).filter(p=>p.id!==profile.id));await context.globalState.update('meshlit.catalog.'+profile.id,undefined);
     }
    }
   }catch(error){panel.webview.postMessage({type:'error',message:typeof error.message==='string'?error.message.slice(0,180):'Action failed'});}
   finally{busy=false;state();}
  });
 }));}
function render(service){const n=crypto.randomBytes(20).toString('hex');return `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${n}'; style-src 'nonce-${n}';"><style nonce="${n}">
body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px var(--vscode-font-family)}main{max-width:1100px;margin:auto;padding:32px}header{display:flex;justify-content:space-between;gap:20px;align-items:center}h1{font-size:26px;font-weight:500}p,.muted{color:var(--vscode-descriptionForeground);line-height:1.5}button,input,select{font:inherit;color:inherit;border:1px solid var(--vscode-panel-border);background:var(--vscode-input-background);border-radius:6px;padding:9px 12px}button{cursor:pointer}button:hover{background:var(--vscode-list-hoverBackground)}button:focus-visible{outline:2px solid var(--vscode-focusBorder)}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}.bar{display:flex;gap:10px;flex-wrap:wrap;margin:16px 0}.section{border:1px solid var(--vscode-panel-border);border-radius:12px;padding:20px;margin-top:20px}#health,#error{white-space:pre-wrap}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:12px;border-bottom:1px solid var(--vscode-panel-border);overflow-wrap:anywhere}td:first-child{max-width:400px}#empty{padding:24px}small{font-size:12px}#address{overflow-wrap:anywhere}button:disabled{opacity:.4;cursor:default}@media(max-width:600px){main{padding:16px}header{display:block}}
</style></head><body><main><header><div><h1>${service}</h1><p>Your provider, models and account</p></div><button class="primary" data-op="dashboard">Open vendor dashboard</button></header><div class="muted" id="address"></div><div class="bar"><button data-op="address">Dashboard address</button><button data-op="add">Add API connection</button></div><p>Sign in directly on the vendor dashboard. Each dashboard tab has a temporary isolated session. Close it to clear that session. Provider keys and routing stay with ${service}.</p><section class="section"><div class="bar"><select id="connections" aria-label="API connection"></select><button data-op="health">Check API</button><button data-op="refresh">Refresh models</button><button data-op="remove">Remove connection</button></div><div id="health" class="muted">Not checked</div><small class="muted">Catalog reachability does not verify generation, credits, or upstream providers.</small></section><section class="section"><h2>Models</h2><div class="bar"><input id="search" placeholder="Search models" aria-label="Search models"><select id="cost" aria-label="Cost category"><option value="all">All costs</option><option value="free">Reported free</option><option value="paid">Reported paid</option><option value="trial">Trial</option><option value="subscription">Subscription</option><option value="unknown">Unknown cost</option></select><select id="provider" aria-label="Provider"><option value="">All providers</option></select></div><p><small>Labels use returned catalog metadata. Missing prices remain unknown. A free filter does not prevent a gateway from falling back to a paid route; strict budget rules still apply.</small></p><div id="empty"></div><table><thead><tr><th>Model</th><th>Reported cost</th><th></th></tr></thead><tbody id="models"></tbody></table><p id="freshness" class="muted"></p></section><p id="error" role="alert"></p></main><script nonce="${n}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id);let data={models:[]};const send=(op,extra={})=>api.postMessage({op,...extra});document.querySelectorAll('[data-op]').forEach(b=>b.onclick=()=>send(b.dataset.op));el('connections').onchange=()=>send('select',{id:el('connections').value});for(const id of ['search','cost','provider'])el(id).oninput=draw;
function draw(){const rows=data.models.filter(m=>(el('cost').value==='all'||m.cost===el('cost').value)&&(!el('provider').value||m.provider===el('provider').value)&&(m.id+' '+m.provider).toLowerCase().includes(el('search').value.toLowerCase()));el('models').replaceChildren();el('empty').textContent=!data.models.length?'Connect an API and refresh its model catalog.':rows.length?rows.length+' matching models · showing up to 200':'No models match these filters.';for(const m of rows.slice(0,200)){const tr=document.createElement('tr');for(const text of [m.id+(m.provider?' · '+m.provider:''),m.cost]){const td=document.createElement('td');td.textContent=text;tr.append(td);}const td=document.createElement('td'),b=document.createElement('button');b.textContent='Use in chat';b.onclick=()=>send('chat',{model:m.id});td.append(b);tr.append(td);el('models').append(tr);}el('freshness').textContent=data.models[0]?'Catalog fetched '+data.models[0].checkedAt:'';}
window.addEventListener('message',({data:d})=>{if(d.type==='error'){el('error').textContent=d.message;return;}if(d.type!=='state')return;data=d;el('address').textContent=d.dashboard;el('connections').replaceChildren();for(const p of d.profiles){const o=new Option(p.endpoint+' · '+p.model,p.id);o.selected=p.id===d.current;el('connections').add(o);}el('health').textContent=d.health?d.health.status+' · '+d.health.checkedAt+(d.health.modelCount!==undefined?' · '+d.health.modelCount+' models':''):'Not checked';const previous=el('provider').value;el('provider').replaceChildren(new Option('All providers',''));for(const p of [...new Set(d.models.map(m=>m.provider).filter(Boolean))])el('provider').add(new Option(p,p));el('provider').value=previous;document.querySelectorAll('[data-op="health"],[data-op="refresh"],[data-op="remove"]').forEach(b=>b.disabled=!d.current);draw();});send('ready');</script></body></html>`;}
module.exports={register,dashboardAddress,openDashboard};
