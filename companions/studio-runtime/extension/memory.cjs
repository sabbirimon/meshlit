'use strict';
const vscode=require('vscode'),fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {ProjectMemory,searchNotes}=require('./memory-store.cjs');
function register(context){
 const stores=new Map();let panel,selected,disposed=false;
 async function scope(root){
  if(!vscode.workspace.isTrusted)throw Error('Trust the workspace before opening project memory');
  if(typeof root!=='string'||!path.isAbsolute(root))throw Error('Open a local project folder first');
  const canonical=await fs.realpath(root),folders=vscode.workspace.workspaceFolders||[];
  for(const folder of folders){if(folder.uri.scheme==='file'&&await fs.realpath(folder.uri.fsPath).catch(()=>null)===canonical)return canonical;}
  throw Error('This project is no longer open in this workspace');
 }
 async function storeFor(root){
  const canonical=await scope(root);let store=stores.get(canonical);
  if(!store){store=new ProjectMemory(path.join(context.globalStorageUri.fsPath,'project-memory'),context.secrets,canonical);stores.set(canonical,store);}return store;
 }
 async function choose(root){
  if(root)return scope(root);
  const folders=(vscode.workspace.workspaceFolders||[]).filter(f=>f.uri.scheme==='file');if(!folders.length)throw Error('Open a local project folder first');
  const folder=folders.length===1?folders[0]:(await vscode.window.showQuickPick(folders.map(f=>({label:f.name,description:f.uri.fsPath,folder:f})),{title:'Choose project memory'}))?.folder;
  return folder?scope(folder.uri.fsPath):undefined;
 }
 async function update(message=''){
  if(!panel||!selected)return;const root=selected,target=panel,store=await storeFor(root),value=await store.snapshot();await scope(root);
  if(panel===target&&selected===root)await target.webview.postMessage({type:'memory',root,policy:value.policy,notes:searchNotes(value.notes,''),actions:[...value.actions].reverse(),message});
 }
 function failure(error){panel?.webview.postMessage({type:'error',message:error.message||'Project memory could not be opened. Existing data was preserved.'});}
 async function open(root,tab='notes'){
  try{
   const chosen=await choose(root);if(!chosen)return;selected=chosen;
   if(panel){panel.reveal();await update();panel.webview.postMessage({type:'tab',tab});return;}
   panel=vscode.window.createWebviewPanel('meshlit.memory','Project memory',vscode.ViewColumn.Active,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
   const owned=panel;context.subscriptions.push(owned);owned.webview.html=render(tab);owned.onDidDispose(()=>{if(panel===owned){panel=undefined;selected=undefined;}});
   owned.webview.onDidReceiveMessage(async message=>{
    try{
     if(panel!==owned||!selected||!message||typeof message.op!=='string')return;
     if(message.op==='project'){const root=await choose();if(root){selected=root;await update();}return;}
     const root=selected,store=await storeFor(root);
     if(message.op==='ready'||message.op==='refresh'){await update();return;}
     if(message.op==='save'){
      await store.add(message.title,message.text);if(selected===root)owned.webview.postMessage({type:'saved'});await update('Note saved locally in protected storage.');
     }else if(message.op==='policy'){
      await store.policy(message.policy);await update('Permissions saved for this project. Turning off recall does not delete notes or erase context already sent.');
     }else if(message.op==='forget'){
      const note=(await store.snapshot()).notes.find(n=>n.id===message.id);if(!note)return;
      const answer=await vscode.window.showWarningMessage('Forget “'+note.title+'” from this project memory?',{modal:true},'Forget note');
      if(answer==='Forget note'){await scope(root);await store.forget(note.id);await update('Note forgotten. Copies already sent to a model are not removed.');}
     }else if(message.op==='clearActions'){
      const answer=await vscode.window.showWarningMessage('Clear this project’s local action history?',{modal:true},'Clear history');
      if(answer==='Clear history'){await scope(root);await store.clearActions();await update('Local action history cleared.');}
     }else if(message.op==='preview'){
      if(typeof message.query!=='string'||message.query.length>12000||typeof message.remote!=='boolean')throw Error('Choose a destination and enter a query up to 12,000 characters');
      const text=await store.context(message.query,message.remote);await scope(root);
      if(selected===root&&panel===owned)owned.webview.postMessage({type:'preview',text,remote:message.remote});
     }
    }catch(error){failure(error);}
   });
  }catch(error){vscode.window.showErrorMessage(error.message||'Cannot open project memory');}
 }
 const api={
  async contextFor({root,query,remote,maxCharacters=3000}){
   if(disposed||!root)return '';if(typeof query!=='string')throw Error('Memory recall requires a text query');const store=await storeFor(root),text=await store.context(query.slice(0,12000),remote,maxCharacters);await scope(root);return text;
  },
  async recordAction({root,tool,status,mode}){
   if(disposed||!root)return false;const canonical=await scope(root),store=await storeFor(canonical),saved=await store.record({tool,status,mode});if(saved&&selected===canonical)await update();return saved;
  },
  open,
 };
 context.subscriptions.push({dispose(){disposed=true;stores.clear();}},
  vscode.commands.registerCommand('meshlit.memory',()=>open()),
  vscode.commands.registerCommand('meshlit.actionHistory',()=>open(undefined,'actions')),
  vscode.commands.registerCommand('meshlit.contextPreview',()=>open(undefined,'preview')));
 return api;
}
function render(tab='notes'){
 const nonce=crypto.randomBytes(20).toString('hex');return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}'"><style nonce="${nonce}">
body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);max-width:960px;padding:28px;margin:auto}h1{font-size:26px;font-weight:550;margin:0}h2{font-size:18px;font-weight:550;color:var(--vscode-textLink-foreground);margin-top:28px}h3{font-size:15px;margin:0}p{line-height:1.6}.muted,small{color:var(--vscode-descriptionForeground)}.bar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.header{justify-content:space-between}button,input,textarea,select{font:inherit;color:var(--vscode-input-foreground);background:var(--vscode-input-background);border:1px solid var(--vscode-panel-border);border-radius:5px;padding:9px}button{cursor:pointer}button:disabled{cursor:default;opacity:.5}button:hover{background:var(--vscode-toolbar-hoverBackground)}button:focus-visible,input:focus-visible,textarea:focus-visible,select:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}label{display:block;margin:12px 0 6px}label.check{display:flex;gap:9px;align-items:flex-start}input[type=text],textarea{box-sizing:border-box;width:100%}textarea{min-height:110px;resize:vertical}nav{display:flex;gap:10px;margin:24px 0;border-bottom:1px solid var(--vscode-panel-border);padding-bottom:12px;flex-wrap:wrap}nav button[aria-pressed=true]{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}section{padding:4px 0 20px}article{border-bottom:1px solid var(--vscode-panel-border);padding:18px 0}.note,pre{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6}pre{font:inherit;background:var(--vscode-textCodeBlock-background);padding:16px}#root{overflow-wrap:anywhere}#status{min-height:24px}details{border-top:1px solid var(--vscode-panel-border);padding:18px 0}summary{cursor:pointer;font-weight:550}table{width:100%;border-collapse:collapse}td,th{text-align:left;padding:9px 4px;border-bottom:1px solid var(--vscode-panel-border)}.table{overflow:auto}small{display:block;margin:6px 0}
</style></head><body><header class="bar header"><h1>Project memory</h1><div class="bar"><button id="project">Choose project</button><button id="refresh">Refresh</button></div></header><p id="root" class="muted"></p><p>Save useful decisions and facts for this project. Notes are encrypted locally; the key uses the editor’s OS secret storage. Nothing is captured automatically.</p><p id="status" role="status">Opening project memory…</p><nav aria-label="Project memory sections"><button data-tab="notes" aria-pressed="true">Notes</button><button data-tab="preview" aria-pressed="false">Context preview</button><button data-tab="actions" aria-pressed="false">Action history</button></nav>
<section id="notes"><h2>Save a project note</h2><label for="title">Title</label><input type="text" id="title" maxlength="120" placeholder="A decision, preference or useful fact"><label for="text">Note</label><textarea id="text" maxlength="4000" placeholder="Write only information you want to retain."></textarea><small>Up to 100 notes, 4,000 characters each and 200,000 characters per project. These are reference notes, not secret storage for passwords or API keys.</small><button id="save" class="primary">Save note</button><h2>Saved notes</h2><label for="search">Find a note</label><input type="text" id="search" maxlength="4000" placeholder="Search titles and note text"><p id="noteCount" class="muted"></p><div id="noteList"></div></section>
<section id="preview" hidden><h2>See what memory can be included</h2><p class="muted">Preview uses bounded word matching across this project’s notes. It does not contact a model or estimate tokens. At most three excerpts are included.</p><label for="query">Your question</label><textarea id="query" maxlength="12000" placeholder="Enter the question you intend to ask."></textarea><label for="destination">Model destination</label><select id="destination"><option value="local">Bundled model on this device</option><option value="remote">Online or local-host API</option></select><button id="previewButton">Preview context</button><p id="previewStatus" role="status"></p><pre id="previewText"></pre></section>
<section id="actions" hidden><div class="bar"><h2>Reviewed action history</h2><button id="clearActions">Clear history</button></div><p class="muted">Newest 200 recorded action events for this project. Tool name, mode, outcome and time only; prompts, tool arguments, file paths and credentials are excluded. This history does not replay actions or reverse completed changes.</p><p id="historyStatus"></p><div class="table"><table><thead><tr><th>Time</th><th>Action</th><th>Mode</th><th>Outcome</th></tr></thead><tbody id="actionList"></tbody></table></div></section>
<details open><summary>Project permissions</summary><p class="muted">All permissions start off. Saving notes is manual. Recall sends matching excerpts with future requests to the selected destination. Turning recall off does not remove excerpts already sent in a conversation; open a new chat to start without that earlier context.</p><label class="check"><input type="checkbox" id="localRecall">Include matching notes with the bundled model on this device</label><label class="check"><input type="checkbox" id="remoteRecall">Include matching notes with online and local-host APIs</label><label class="check"><input type="checkbox" id="actionHistory">Keep reviewed action metadata for this project</label><button id="policy" disabled>Save permissions</button></details>
<script nonce="${nonce}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id);let snapshot;
function send(op,extra={}){api.postMessage({op,...extra});}
function tab(name){for(const key of ['notes','preview','actions'])el(key).hidden=key!==name;document.querySelectorAll('[data-tab]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.tab===name)));}
document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>tab(button.dataset.tab));
for(const op of ['project','refresh','clearActions'])el(op).onclick=()=>send(op);
el('save').onclick=()=>{el('save').disabled=true;send('save',{title:el('title').value,text:el('text').value});};
el('policy').onclick=()=>{el('policy').disabled=true;send('policy',{policy:{localRecall:el('localRecall').checked,remoteRecall:el('remoteRecall').checked,actionHistory:el('actionHistory').checked}});};
el('previewButton').onclick=()=>send('preview',{query:el('query').value,remote:el('destination').value==='remote'});
function notes(){el('noteList').replaceChildren();const query=el('search').value.toLocaleLowerCase().trim(),rows=(snapshot?.notes||[]).filter(n=>!query||(n.title+' '+n.text).toLocaleLowerCase().includes(query));el('noteCount').textContent=rows.length+' of '+(snapshot?.notes.length||0)+' saved notes · newest first';for(const n of rows){const article=document.createElement('article'),heading=document.createElement('h3'),time=document.createElement('small'),text=document.createElement('p'),button=document.createElement('button');heading.textContent=n.title;time.textContent=new Date(n.createdAt).toLocaleString()+' · Manually saved';text.className='note';text.textContent=n.text;button.textContent='Forget note';button.onclick=()=>send('forget',{id:n.id});article.append(heading,time,text,button);el('noteList').append(article);}if(!rows.length){const p=document.createElement('p');p.textContent=query?'No matching notes.':'No notes saved for this project.';el('noteList').append(p);}}
el('search').oninput=notes;
window.addEventListener('message',({data:d})=>{
 if(d.type==='error'){el('status').textContent=d.message;el('save').disabled=false;el('policy').disabled=!snapshot;return;}
 if(d.type==='tab'){tab(d.tab);return;}if(d.type==='saved'){el('title').value='';el('text').value='';return;}
 if(d.type==='preview'){el('previewText').textContent=d.text;el('previewStatus').textContent=d.text?d.text.length+' characters · Token count unavailable · Destination: '+(d.remote?'online or local-host API':'bundled local model'):'No context will be included: recall is disabled, the query is empty, or no note matches.';return;}
 if(d.type!=='memory')return;const changed=snapshot&&snapshot.root!==d.root;snapshot=d;el('root').textContent=d.root;el('status').textContent=d.message||'Project memory ready · Local protected storage';el('save').disabled=false;el('policy').disabled=false;for(const key of ['localRecall','remoteRecall','actionHistory'])el(key).checked=d.policy[key];el('previewText').textContent='';el('previewStatus').textContent='';if(changed){el('title').value='';el('text').value='';el('search').value='';el('query').value='';}notes();el('historyStatus').textContent=d.policy.actionHistory?'Recording reviewed action metadata.':'History recording is off. Earlier records remain until cleared.';el('actionList').replaceChildren();for(const a of d.actions){const row=document.createElement('tr');for(const value of [new Date(a.at).toLocaleString(),a.tool.replaceAll('_',' '),a.mode,a.status]){const cell=document.createElement('td');cell.textContent=value;row.append(cell);}el('actionList').append(row);}
});tab(${JSON.stringify(['notes','preview','actions'].includes(tab)?tab:'notes')});send('ready');
</script></body></html>`;
}
module.exports={register,render};
