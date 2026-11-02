'use strict';
const vscode=require('vscode'),fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const client=require('./connected-services-client.cjs'),{watch}=require('./automation-watch.cjs');
const terminal=new Set(['COMPLETED','FAILED','STOPPED']);
function register(context){
 let panel,selected,disposed=false,queue=Promise.resolve();const requests=new Set(),watchers=new Map();
 async function scope(root){
  if(disposed||!vscode.workspace.isTrusted||typeof root!=='string'||!path.isAbsolute(root))throw Error('Open and trust a local workspace first');
  const canonical=await fs.realpath(root);for(const folder of vscode.workspace.workspaceFolders||[]){if(folder.uri.scheme==='file'&&await fs.realpath(folder.uri.fsPath).catch(()=>null)===canonical)return canonical;}
  throw Error('This workspace is no longer open');
 }
 const project=root=>crypto.createHash('sha256').update(root).digest('hex');
 const key=(root,kind)=>'meshlit.connected.v1.'+project(root)+'.'+kind;
 async function stored(root,kind){if(!['openviking','artemis','jobs'].includes(kind))throw Error('Unknown capability');const raw=await context.secrets.get(key(root,kind));if(!raw)return kind==='jobs'?[]:undefined;const value=JSON.parse(raw);if(kind==='jobs'){if(!Array.isArray(value)||value.length>16)throw Error('Invalid automation history');return value;}client.endpoint(value.address,value.token);if(kind==='openviking')client.namespace(value.namespace);return value;}
 async function persist(root,kind,value){const raw=JSON.stringify(value);await context.secrets.store(key(root,kind),raw);if(await context.secrets.get(key(root,kind))!==raw)throw Error('Protected storage did not commit');}
 function serial(work){const next=queue.then(work,work);queue=next.catch(()=>{});return next;}
 async function jobs(root){return stored(root,'jobs');}
 async function updateJob(root,id,change){return serial(async()=>{const old=await jobs(root);await persist(root,'jobs',old.map(row=>row.id===id&&!(terminal.has(row.phase)&&change.phase&&!terminal.has(change.phase))?{...row,...change,updatedAtMs:Date.now()}:row));});}
 async function invoke(config,route,body,params){const control=new AbortController();requests.add(control);try{return await client.request(config,route,body,params,control.signal);}finally{requests.delete(control);}}
 async function stop(root,id){
  const row=(await jobs(root)).find(row=>row.id===id);if(!row)throw Error('No admitted task with this ID');
  let confirmed=false;try{confirmed=(await invoke(row.host,'/api/stop',{session_id:id})).status==='stopped';}catch{}
  await updateJob(root,id,{phase:confirmed?'STOPPED':'STOP_UNCONFIRMED',observedStatus:confirmed?'Host acknowledged Stop':'Remote stop is unconfirmed. Inspect the receiving host.'});return confirmed;
 }
 async function observe(root,row){
  const result=await invoke(row.host,'/api/sessions/'+row.id),status=String(result.status||'').toLowerCase(),phase=['completed','succeeded','success'].includes(status)?'COMPLETED':['failed','error'].includes(status)?'FAILED':['stopped','cancelled','canceled'].includes(status)?'STOPPED':'OBSERVED';
  await updateJob(root,row.id,{phase,observedStatus:JSON.stringify(result).slice(0,12000)});return (await jobs(root)).find(saved=>saved.id===row.id).phase;
 }
 function supervise(root,row){
  const id=root+'\0'+row.id;if(watchers.has(id))return;
  const control=watch({valid:async()=>{await scope(root);return JSON.stringify(await stored(root,'artemis'))===JSON.stringify(row.host);},observe:async()=>{const phase=await observe(root,row);await snapshot(root);return phase;},stop:async()=>{await stop(root,row.id);await snapshot(root).catch(()=>{});}});
  watchers.set(id,control);control.done.finally(()=>watchers.delete(id));
 }
 async function snapshot(root,message=''){
  const configs={};for(const kind of ['openviking','artemis']){const value=await stored(root,kind);if(value){const {token,...publicConfig}=value;configs[kind]={...publicConfig,hasCredential:!!token};}}
  const history=(await jobs(root)).map(({host,...job})=>job);await scope(root);
  if(panel&&selected===root)panel.webview.postMessage({type:'services',root,configs,jobs:history,message});
 }
 async function open(){
  const folder=(vscode.workspace.workspaceFolders||[]).find(f=>f.uri.scheme==='file');if(!folder){vscode.window.showErrorMessage('Open a project folder first');return;}
  try{const root=await scope(folder.uri.fsPath);selected=root;if(panel){panel.reveal();await snapshot(root);return;}
   panel=vscode.window.createWebviewPanel('meshlit.connectedCapabilities','Connected capabilities',vscode.ViewColumn.Active,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});context.subscriptions.push(panel);panel.webview.html=render();panel.onDidDispose(()=>panel=undefined);
   panel.webview.onDidReceiveMessage(async message=>{
    const owned=panel;const root=selected;
    try{
     await scope(root);if(!message||typeof message!=='object')throw Error('Invalid request');
     if(message.op==='ready'||message.op==='refresh'){await snapshot(root);return;}
     if(message.op==='open'){
      const allowed={roles:'meshlit.agentTeams',memory:'meshlit.memory',companions:'meshlit.companions',models:'meshlit.models',access:'meshlit.appAccess'};if(allowed[message.id])await vscode.commands.executeCommand(allowed[message.id]);return;
     }
     const kind=message.kind;if(!['openviking','artemis'].includes(kind))throw Error('Unknown service');
     if(message.op==='save'){
      const input=message.config,old=await stored(root,kind);if(!input||typeof input.token!=='string')throw Error('Invalid service settings');
      const token=input.token||((old?.address===input.address)?old.token:'');const address=client.endpoint(input.address,token);
      for(const key of ['enabled','localRecall','remoteRecall','agents'])if(typeof input[key]!=='boolean')throw Error('Invalid recall grants');
      const config={kind,address,token,namespace:kind==='openviking'?client.namespace(input.namespace):'',enabled:input.enabled,localRecall:input.localRecall,remoteRecall:input.remoteRecall,agents:input.agents};
      await scope(root);await persist(root,kind,config);await snapshot(root,'Configuration saved. Reachability is not yet verified.');return;
     }
     if(message.op==='remove'){await context.secrets.delete(key(root,kind));await snapshot(root,'Connection removed. Admitted external tasks retain host-bound Stop information.');return;}
     if(message.op==='stop'){await stop(root,message.id);await snapshot(root);return;}
     if(message.op==='task'){
      const row=(await jobs(root)).find(row=>row.id===message.id);if(!row)throw Error('Task admission not found');
      await observe(root,row);await snapshot(root);return;
     }
     const config=await stored(root,kind);if(!config?.enabled)throw Error('Save and enable this connection first');const binding=JSON.stringify(config);
     if(message.op==='find'){
      const hits=await client.find(config,message.query);await scope(root);if(JSON.stringify(await stored(root,kind))!==binding)throw Error('Connection or grants changed');
      if(panel===owned)owned.webview.postMessage({type:'context-results',hits});
     }else if(message.op==='read'){
      const text=await client.read(config,message.uri,message.layer);await scope(root);if(JSON.stringify(await stored(root,kind))!==binding)throw Error('Connection or grants changed');
      if(panel===owned)owned.webview.postMessage({type:'context-read',text,uri:message.uri,layer:message.layer});
     }else if(message.op==='status'||message.op==='devices'){
      const result=await invoke(config,message.op==='devices'?'/api/devices':'/api/status');await scope(root);if(panel===owned)owned.webview.postMessage({type:'service-observation',text:JSON.stringify(result).slice(0,12000)});
     }else if(message.op==='run'){
      const payload=client.taskPayload(message.task);
      const answer=await vscode.window.showWarningMessage('Run this phone task on '+config.address+'?\nDevice: '+payload.device_serial+'\nApp constraint: '+payload.locked_app_package+'\nTask: '+payload.goal+'\nThe Artemis model may receive app content and screenshots. Its app lock is an upstream constraint; Meshlit has not qualified its per-action enforcement.',{modal:true},'Run reviewed task');
      if(answer!=='Run reviewed task')return;
      await scope(root);if(JSON.stringify(await stored(root,kind))!==binding)throw Error('Automation host changed');
      const row={id:payload.session_id,goal:payload.goal,serial:payload.device_serial,appPackage:payload.locked_app_package,phase:'REQUESTED',observedStatus:'',host:config,updatedAtMs:Date.now()};
      await serial(async()=>{const old=await jobs(root);if(old.length>=16||old.filter(r=>!terminal.has(r.phase)).length>=2||old.some(r=>r.id===row.id))throw Error('Review existing admissions before starting another task');await persist(root,'jobs',[...old,row]);});
      try{const result=await invoke(config,'/api/run',payload);if(result.status==='rejected'||!Array.isArray(result.tasks)||result.tasks.length!==1||result.tasks[0].session_id!==row.id||(result.tasks[0].device_serial&&result.tasks[0].device_serial!==row.serial))throw Error('Task admission was not acknowledged');await updateJob(root,row.id,{phase:'SUBMITTED',observedStatus:JSON.stringify(result).slice(0,12000)});}
      catch{await updateJob(root,row.id,{phase:'OUTCOME_UNKNOWN',observedStatus:'Submission outcome is uncertain. Inspect this exact task ID; no automatic retry was made.'});}
      supervise(root,row);await snapshot(root);
     }
    }catch(error){if(panel===owned)owned.webview.postMessage({type:'service-error',message:error.message||'Service action failed'});}
   });
  }catch(error){vscode.window.showErrorMessage(error.message||'Cannot open connected capabilities');}
 }
 const api={
  async contextFor({root,query,remote,maxCharacters=3000,agent=false}){
   if(!root||disposed||maxCharacters===0)return '';const canonical=await scope(root),config=await stored(canonical,'openviking');
   if(!config?.enabled||!(remote?config.remoteRecall:config.localRecall)||(agent&&!config.agents))return '';
   const binding=JSON.stringify(config),hits=await client.find(config,query.slice(0,4000));await scope(root);
   if(JSON.stringify(await stored(canonical,'openviking'))!==binding)throw Error('Connected memory grant changed');return client.context(hits,maxCharacters);
  },open,
 };
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.connectedCapabilities',open),{dispose(){disposed=true;for(const control of watchers.values())control.cancel();for(const control of requests)control.abort();requests.clear();}});
 for(const folder of vscode.workspace.workspaceFolders||[]){if(folder.uri.scheme==='file')scope(folder.uri.fsPath).then(async root=>{for(const row of await jobs(root))if(!terminal.has(row.phase))await stop(root,row.id);}).catch(()=>{});}
 return api;
}
function render(){const nonce=crypto.randomBytes(20).toString('hex');return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';"><style nonce="${nonce}">
body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);max-width:1000px;margin:auto;padding:28px;line-height:1.5}section,article{border:1px solid var(--vscode-panel-border);border-radius:10px;padding:18px;margin:16px 0}h1{font-size:25px;font-weight:500}h2{font-size:19px}button,input,textarea{font:inherit;color:inherit;background:var(--vscode-input-background);border:1px solid var(--vscode-panel-border);border-radius:6px;padding:8px}button{cursor:pointer}button:focus-visible,input:focus-visible,textarea:focus-visible{outline:2px solid var(--vscode-focusBorder)}label{display:block;margin:10px 0}input[type=text],input[type=password],textarea{box-sizing:border-box;width:100%}textarea{min-height:100px}.row{display:flex;gap:8px;flex-wrap:wrap}.muted{color:var(--vscode-descriptionForeground)}pre{white-space:pre-wrap;overflow-wrap:anywhere}#status{white-space:pre-wrap}
</style></head><body><h1>Connected capabilities</h1><p>One workflow for agent roles, memory, model providers and phone automation. Each backend retains its grants and provenance. Connections and credentials are bound to this project and protected by OS SecretStorage.</p><p id="root" class="muted"></p><div class="row"><button data-open="roles">Agent teams & roles</button><button data-open="memory">Built-in project memory</button><button data-open="companions">Claude-Mem & computer companions</button><button data-open="models">Model providers</button><button data-open="access">App access</button><button id="refresh">Refresh</button></div><p id="status" role="status">Loading protected configuration…</p>
${['openviking','artemis'].map(kind=>`<section data-kind="${kind}"><h2>${kind==='openviking'?'OpenViking · memory, knowledge and skills':'Google Artemis · phone automation'}</h2><p class="muted">${kind==='openviking'?'Optional AGPL HTTP service. Built-in encrypted memory remains offline. No chat capture, session commit, skill execution or cloud sync is enabled. Recall sends the query to the memory server and may use its configured embedding provider.':'Optional Apache-2.0 host with a configured model and an authorized ADB-connected Android device. No installation, global rules or ADB pairing runs automatically. This adapter is human-reviewed; autonomous agent access awaits per-action scope qualification.'}</p><details><summary>Connection and permissions</summary><label>HTTPS address, or literal loopback HTTP<input type="text" data-field="address" maxlength="2048"></label><label>${kind==='openviking'?'User API key':'Host bearer token'}<input type="password" data-field="token" maxlength="8192" placeholder="Blank keeps the same-endpoint key"></label><label><input type="checkbox" data-field="enabled"> Enable connection</label>${kind==='openviking'?'<label>Canonical viking:// namespace<input type="text" data-field="namespace" value="viking://resources/meshlit" maxlength="1024"></label><label><input type="checkbox" data-field="localRecall"> Recall into local model chats</label><label><input type="checkbox" data-field="remoteRecall"> Recall into remote model chats</label><label><input type="checkbox" data-field="agents"> Allow agent recall</label>':''}<div class="row"><button data-op="save">Save configuration</button><button data-op="remove">Remove connection</button></div></details>${kind==='openviking'?'<label>Search memory and knowledge<input type="text" data-field="query" maxlength="4000"></label><button data-op="find">Find in namespace</button><div id="hits"></div><pre id="content"></pre>':'<div class="row"><button data-op="status">Check host</button><button data-op="devices">List actual devices</button></div><label>Exact device serial<input type="text" data-field="serial" maxlength="128"></label><label>App package constraint<input type="text" data-field="appPackage" maxlength="160" placeholder="com.meshlit"></label><label>Task<textarea data-field="goal" maxlength="4000"></textarea></label><button data-op="run">Review phone task</button><pre id="observation"></pre><div id="jobs"></div>'}</section>`).join('')}
<script nonce="${nonce}">const api=acquireVsCodeApi(),el=id=>document.getElementById(id),send=(op,fields={})=>api.postMessage({op,...fields});let loaded=false;function text(tag,value,parent){const n=document.createElement(tag);n.textContent=value;parent.append(n);return n;}function button(label,parent,fn){const n=text('button',label,parent);n.onclick=fn;}
document.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>send('open',{id:b.dataset.open}));el('refresh').onclick=()=>send('refresh');
document.querySelectorAll('section').forEach(section=>{const value=key=>section.querySelector('[data-field="'+key+'"]');section.querySelectorAll('[data-op]').forEach(b=>b.onclick=()=>{const kind=section.dataset.kind,op=b.dataset.op;if(op==='save'){const config={};for(const key of ['address','token','namespace','enabled','localRecall','remoteRecall','agents']){const input=value(key);config[key]=input?(input.type==='checkbox'?input.checked:input.value):(['enabled','localRecall','remoteRecall','agents'].includes(key)?false:'');}send(op,{kind,config});value('token').value='';}else if(op==='find')send(op,{kind,query:value('query').value});else if(op==='run')send(op,{kind,task:{goal:value('goal').value,serial:value('serial').value,appPackage:value('appPackage').value}});else send(op,{kind});});});
window.addEventListener('message',({data:d})=>{if(d.type==='services'){el('root').textContent=d.root;el('status').textContent=d.message||'Protected configuration loaded';if(!loaded){for(const [kind,c] of Object.entries(d.configs)){const section=document.querySelector('[data-kind="'+kind+'"]');for(const [k,v] of Object.entries(c)){if(k==='token')continue;const input=section.querySelector('[data-field="'+k+'"]');if(input){if(input.type==='checkbox')input.checked=v;else input.value=v;}}}loaded=true;}el('jobs').replaceChildren();[...d.jobs].reverse().forEach(job=>{const a=text('article','',el('jobs'));text('h3',job.phase+' · '+job.id,a);text('p',job.serial+' · '+job.appPackage+' · '+job.goal,a);text('pre',job.observedStatus,a);button('Refresh task',a,()=>send('task',{kind:'artemis',id:job.id}));button('Stop task',a,()=>send('stop',{kind:'artemis',id:job.id}));});}else if(d.type==='context-results'){el('hits').replaceChildren();d.hits.forEach(hit=>{const a=text('article','',el('hits'));text('h3',hit.kind+' · '+hit.uri,a);text('p',hit.text,a);[0,1,2].forEach(layer=>button('Read L'+layer,a,()=>send('read',{kind:'openviking',uri:hit.uri,layer})));});el('status').textContent=d.hits.length+' attributed matches';}else if(d.type==='context-read'){el('content').textContent=d.uri+' · L'+d.layer+'\\n'+d.text;}else if(d.type==='service-observation')el('observation').textContent=d.text;else if(d.type==='service-error')el('status').textContent=d.message;});send('ready');
</script></body></html>`;}
module.exports={register,render};
