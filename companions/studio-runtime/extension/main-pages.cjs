const crypto=require('node:crypto');
const localModels=require('./local-models.cjs');
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const defaults={temperature:0.4,maxOutputTokens:1024,historyTurns:5};
function validateSettings(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(key=>!Object.hasOwn(defaults,key)))throw Error('Choose valid chat settings.');
 if(typeof value.temperature!=='number'||!Number.isFinite(value.temperature)||value.temperature<0||value.temperature>2)throw Error('Temperature must be between 0 and 2.');
 if(!Number.isInteger(value.maxOutputTokens)||value.maxOutputTokens<1||value.maxOutputTokens>1024)throw Error('Reply length must be a whole number between 1 and 1,024.');
 if(!Number.isInteger(value.historyTurns)||value.historyTurns<0||value.historyTurns>10)throw Error('History must be a whole number between 0 and 10.');
 return {temperature:value.temperature,maxOutputTokens:value.maxOutputTokens,historyTurns:value.historyTurns};
}
const saved=(context,key)=>{const value=context.globalState.get(key,[]);return Array.isArray(value)?value:[];};
const profiles=context=>saved(context,'meshlit.connections').filter(p=>p&&typeof p.id==='string'&&typeof p.model==='string');
const workspaces=context=>saved(context,'meshlit.recentWorkspaces').filter(p=>p&&typeof p.path==='string'&&typeof p.name==='string');
function readSettings(vscode){const config=vscode.workspace.getConfiguration('meshlit.chat');return Object.fromEntries(Object.entries(defaults).map(([key,fallback])=>[key,config.get(key,fallback)]));}
function render(kind,data={}){
 const nonce=crypto.randomBytes(24).toString('hex');
 const button=(label,op,id)=>`<button data-op="${op}"${id===undefined?'':` data-id="${escape(id)}"`}>${escape(label)}</button>`;
 const runtime=data.runtime||{},local=runtime.localModel||{};
 const localState=({ready:'Ready',loading:'Loading',idle:'Not loaded',error:'Load failed'})[local.state]||'Status not checked';
 const modelLabel=typeof local.label==='string'&&local.label.trim()?local.label:undefined;
 const runtimeStatus=runtime.hostReady===false?'Model host not ready · '+localState:localState;
 const modeSection=`<h2>Chat and agent modes</h2><div class="mode-grid"><article class="mode-card"><h3>Chat</h3><p>General conversation. No workspace tools.</p>${button('Open general chat','generalChat')}</article><article class="mode-card"><h3>Plan</h3><p>Inspect approved project files and plan changes. No edits.</p>${button('Choose workspace mode','workspaceAgents')}</article><article class="mode-card"><h3>Edit / Build</h3><p>Propose a bounded edit in the selected project. Permissions and review stay active.</p>${button('Choose workspace mode','workspaceAgents')}</article><article class="mode-card"><h3>Autopilot</h3><p>Run multiple bounded project steps under the saved workspace action policy.</p>${button('Choose workspace mode','workspaceAgents')}</article></div>`;
 let title,description,body;
 if(kind==='models'){
  title='Models';description='Choose the model and where it runs. Each conversation keeps its own selection.';
  body=`<div class="toolbar">${button('Add model connection','add')}${button('Check a connection','check')}${button('Provider dashboards','manage')}${button('Refresh','refresh')}</div><h2>On this Mac</h2>${localModels.list().map(model=>`<article class="model"><div><span class="tag">BUNDLED · LOCAL · ${model.thinkingSupported?'REASONING':'QUICK CHAT'}</span><h3>${escape((local.modelId||localModels.STARTER)===model.id&&modelLabel?modelLabel:model.label)}</h3><p class="runtime-state" role="status">${escape((local.modelId||localModels.STARTER)===model.id?runtimeStatus:model.present?'Available · loads when selected':'Not packaged in this build')}</p><p>${model.thinkingSupported?'Thinking on/off · CPU · one local model loaded at a time. Longer reasoning uses more time and memory.':'Fast starter for everyday chat. Runs on this device.'} Refresh for the current loading status.</p></div>${model.present?button('Start local chat','bundled',model.id):''}</article>`).join('')}<h2>Your connections</h2><p class="hint">Online providers, other devices, and existing cluster coordinators appear here after you add them.</p>${data.profiles?.length?data.profiles.map(profile=>`<article class="model"><div><span class="tag">${escape(profile.sourceKind||'Model API')}</span><h3>${escape(profile.service||'Model connection')}</h3><p>${escape(profile.model)}</p></div>${button('Start chat','model',profile.id)}</article>`).join(''):'<div class="empty">No saved model connections yet. Add a provider API or an owner-approved model server to get started.</div>'}<h2>Provider dashboards</h2><p>Open the configured provider dashboard. Accounts, credentials and provider permissions remain separate.</p><div class="toolbar">${button('OmniRoute','gatewayOmni')}${button('FreeLLMAPI','gatewayFree')}${button('Hugging Face','gatewayHf')}</div>${modeSection}<h2>Reasoning and permissions</h2><p>Use Effort in each conversation for the levels supported by its selected model. The bundled reasoning model offers Thinking on/off; the starter uses Auto. Tools and project action permissions are configured separately.</p><div class="toolbar">${button('LLM settings','llmSettings')}${button('Tools & permissions','permissions')}</div>`;
 }else if(kind==='permissions'){
  title='Tools & permissions';description='Review what each workflow can access. General Chat has no workspace tools.';
  body=`<section><h2>General Chat</h2><p>Messages go to the model selected in the conversation. Chat cannot read project files, execute commands, control apps, or call workspace tools.</p><p class="hint">Changing a model does not approve a device, connector, or project action.</p>${button('Open general chat','generalChat')}</section><section><h2>Project actions</h2><p>Each project saves its own action preset: Ask each action, Auto workspace reads, or Autonomous workspace edits. Workspace edits remain bounded to the selected folder and available in the editor for review.</p><p class="hint">These presets do not grant shell, device, MCP, credential, or provider access. Review the current project in its coding window; from General Chat, choose a workspace first.</p>${button('Review project permissions','projectSecurity')}</section><section><h2>Connected tools</h2><p>App control, MCP connectors, and device connections keep independent permissions. Installing or configuring a connector does not authorize its tools.</p><div class="toolbar">${button('App access','appAccess')}${button('MCP connectors','connectors')}${button('Device connections','deviceConnections')}</div></section><section><h2>Chat access and protected data</h2><p>${runtime.usageMode==='protected'?'Protected Usage is unlocked in this window.':runtime.usageMode==='session'?'This window uses session-only usage.':runtime.usageMode==='locked'?'Protected Usage is locked in this window.':'Usage state has not been checked in this window.'} Protected history, saved budgets and provider credentials have separate access controls.</p>${button('Chat access settings','llmSettings')}${button('Usage & budgets','usage')}</section>`;
 }else if(kind==='workspaces'){
  title='Workspaces';description='Open a project in its own coding window, with files, changes, terminal, and project chat. This main window stays open.';
  body=`<div class="toolbar">${button('Open a folder…','browse')}${button('Refresh','refresh')}</div><h2>Recent projects</h2>${data.workspaces?.length?data.workspaces.map(folder=>`<article class="model"><div><h3>${escape(folder.name)}</h3><p class="path">${escape(folder.path)}</p></div>${button('Open IDE','workspace',folder.path)}</article>`).join(''):'<div class="empty">Your recent projects will appear here. Choose a folder to open the coding window.</div>'}`;
 }else{
  title='LLM settings';description='Tune the next chat request. Saved settings apply across Studio windows.';
  const values=data.settings||defaults;
  body=`<section><h2>Chat access</h2><label><input id="require-unlock" type="checkbox" ${data.requireUnlock?'checked':''}> Require a passphrase before local chat</label><p class="hint">Off by default. Local chat opens ready to use with session-only usage. Protected history, saved budgets, passwords and API keys remain separate. Changes apply when you reopen the window.</p></section><form id="chat-settings"><section><label for="temperature">Local sampling temperature</label><p class="hint">Lower values favor predictable replies. Hosted providers use their own defaults.</p><input id="temperature" name="temperature" type="number" min="0" max="2" step="0.1" required value="${escape(values.temperature)}"><label for="maxOutputTokens">Maximum reply tokens</label><p class="hint">The runtime may lower this limit to fit the selected model.</p><input id="maxOutputTokens" name="maxOutputTokens" type="number" min="1" max="1024" step="1" required value="${escape(values.maxOutputTokens)}"><label for="historyTurns">Completed turns in context</label><p class="hint">Zero sends only the current message. Long conversations may use fewer turns to fit.</p><input id="historyTurns" name="historyTurns" type="number" min="0" max="10" step="1" required value="${escape(values.historyTurns)}"></section><div class="toolbar"><button class="primary" type="submit" id="save">Save settings</button><button type="button" data-op="refresh">Reload saved values</button></div></form><section><h2>Reasoning effort</h2><p>Choose Effort in the conversation composer. Available levels depend on the selected connection and model. The bundled reasoning model has Thinking on/off in the composer. Sampling temperature does not enable reasoning.</p><div class="toolbar">${button('Models & providers','models')}${button('Tools & permissions','permissions')}</div></section>${modeSection}`;
 }
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:960px;margin:auto;padding:48px 40px}h1{font-size:28px;font-weight:600;letter-spacing:-.7px;margin:0 0 8px}h2{font-size:13px;font-weight:500;color:var(--vscode-descriptionForeground);margin:32px 0 12px}h3{font-size:16px;font-weight:500;margin:4px 0}p{margin:0;color:var(--vscode-descriptionForeground);max-width:68ch}.toolbar{display:flex;flex-wrap:wrap;gap:10px;margin:24px 0}.model{display:flex;align-items:center;justify-content:space-between;gap:24px;margin:10px 0;padding:22px;border:1px solid var(--vscode-panel-border);border-radius:12px;background:var(--vscode-editorWidget-background,var(--vscode-sideBar-background))}.model>div{min-width:0}.tag{font-size:10px;letter-spacing:.08em;color:var(--vscode-descriptionForeground)}.mode-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.mode-card{border:1px solid var(--vscode-panel-border);border-radius:12px;padding:20px}.mode-card button{margin-top:16px}.runtime-state{color:var(--vscode-foreground)}.path{overflow-wrap:anywhere}.empty{padding:32px 24px;border:1px dashed var(--vscode-panel-border);border-radius:12px;color:var(--vscode-descriptionForeground)}button,input{font:inherit;color:inherit}button{border:1px solid var(--vscode-panel-border);border-radius:7px;padding:8px 14px;background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);cursor:pointer;flex-shrink:0}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button:focus-visible,input:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}button:disabled{opacity:.5;cursor:wait}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}section{margin-top:30px;padding:24px;border:1px solid var(--vscode-panel-border);border-radius:12px}label{display:block;font-weight:500;margin:20px 0 5px}label:first-child{margin-top:0}.hint{font-size:13px;margin-bottom:10px}input[type=checkbox]{width:auto!important}input{width:170px;max-width:100%;padding:9px 12px;background:var(--vscode-input-background);border:1px solid var(--vscode-input-border,var(--vscode-panel-border));border-radius:6px}#result{min-height:24px;margin:16px 0}#result.error{color:var(--vscode-errorForeground)}@media(max-width:680px){.mode-grid{grid-template-columns:1fr}main{padding:28px 20px}.model{align-items:flex-start;flex-direction:column;gap:14px}}
 </style></head><body><main><h1>${title}</h1><p>${description}</p><div id="result" role="status" aria-live="polite"></div>${body}</main><script nonce="${nonce}">
 const api=acquireVsCodeApi();let pending=false;const result=document.getElementById('result');
 function busy(value){pending=value;document.querySelectorAll('main button').forEach(button=>button.disabled=value);}
 document.querySelectorAll('[data-op]').forEach(button=>button.onclick=()=>{if(pending)return;result.className='';result.textContent='';busy(true);api.postMessage({mainPage:true,op:button.dataset.op,id:button.dataset.id});});
 document.getElementById('require-unlock')?.addEventListener('change',event=>api.postMessage({mainPage:true,op:'chatLock',required:event.target.checked}));
 document.getElementById('chat-settings')?.addEventListener('submit',event=>{event.preventDefault();if(pending)return;const values={};for(const key of ['temperature','maxOutputTokens','historyTurns'])values[key]=Number(document.getElementById(key).value);busy(true);api.postMessage({mainPage:true,op:'save',values});});
 window.addEventListener('message',({data})=>{if(data.type==='mainPageIdle'){busy(false);return;}if(data.type!=='mainPageResult')return;busy(false);result.className=data.error?'error':'';result.textContent=data.error||data.message||'';if(data.settings)for(const [key,value]of Object.entries(data.settings)){const input=document.getElementById(key);if(input)input.value=value;}});
 </script></body></html>`;
}
function register(context,vscode,{getRuntime=()=>({})}={}){
 const panels=new Map();
 const commands={models:'meshlit.mainModels',workspaces:'meshlit.mainWorkspaces',settings:'meshlit.mainLlmSettings',permissions:'meshlit.permissions'};
 const data=kind=>{
  let runtime={};try{runtime=getRuntime()||{};}catch{}
  return kind==='models'?{profiles:profiles(context),runtime}:kind==='permissions'?{runtime}:kind==='workspaces'?{workspaces:workspaces(context)}:{settings:readSettings(vscode),requireUnlock:vscode.workspace.getConfiguration('meshlit.chat').get('requireUnlock',false)===true,runtime};
 };
 for(const [kind,command]of Object.entries(commands))context.subscriptions.push(vscode.commands.registerCommand(command,()=>{
  let panel=panels.get(kind);
  if(panel){panel.webview.html=render(kind,data(kind));panel.reveal();return;}
  panel=require('./shell.cjs').createPanel(vscode,'meshlit.main.'+kind,{models:'Models',workspaces:'Workspaces',settings:'LLM settings',permissions:'Tools & permissions'}[kind],vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  panels.set(kind,panel);context.subscriptions.push(panel);
  panel.webview.html=render(kind,data(kind));
  let busy=false,closed=false;
  panel.onDidDispose(()=>{closed=true;panels.delete(kind);});
  panel.webview.onDidReceiveMessage(async message=>{
   if(!message||message.mainPage!==true||typeof message.op!=='string'||busy||closed)return;
   busy=true;
   try{
    const {op}=message;
    if(op==='refresh'){panel.webview.html=render(kind,data(kind));return;}
    const navigation={generalChat:'meshlit.generalChat',workspaceAgents:'meshlit.workspaceAgents',models:'meshlit.mainModels',llmSettings:'meshlit.mainLlmSettings',permissions:'meshlit.permissions',projectSecurity:'meshlit.projectSecurity',appAccess:'meshlit.appAccess',connectors:'meshlit.connectors',deviceConnections:'meshlit.deviceConnections',usage:'meshlit.usage'};
    if(Object.hasOwn(navigation,op)){
     await vscode.commands.executeCommand(navigation[op]);if(!closed)await panel.webview.postMessage({type:'mainPageResult'});return;
    }
    if(kind==='models'){
     const targets={add:'meshlit.addModelSource',check:'meshlit.checkConnection',manage:'meshlit.modelPicker',local:'meshlit.chat',gatewayOmni:'meshlit.gateway.OmniRoute',gatewayFree:'meshlit.gateway.FreeLLMAPI',gatewayHf:'meshlit.gateway.Hugging Face'};
     if(Object.hasOwn(targets,op)){await vscode.commands.executeCommand(targets[op]);if(op==='add'&&!closed)panel.webview.html=render(kind,data(kind));}
     else if(op==='bundled'){
      const route=localModels.route('local:'+message.id);await vscode.commands.executeCommand('meshlit.chat','local:'+route.modelId);
     }else if(op==='model'){
      if(typeof message.id!=='string'||!profiles(context).some(profile=>profile.id===message.id))throw Error('That model connection is no longer available. Refresh the list.');
      await vscode.commands.executeCommand('meshlit.chat',message.id);
     }else return;
    }else if(kind==='workspaces'){
     if(op==='browse'){await vscode.commands.executeCommand('meshlit.chooseWorkspace');if(!closed)panel.webview.html=render(kind,data(kind));}
     else if(op==='workspace'){
      const folder=workspaces(context).find(item=>item.path===message.id);
      if(!folder)throw Error('That workspace is no longer in your recent projects. Refresh the list.');
      const uri=vscode.Uri.file(folder.path);
      try{if(!((await vscode.workspace.fs.stat(uri)).type&vscode.FileType.Directory))throw Error('Not a directory');}
      catch{throw Error('That folder is unavailable. Choose an existing folder to open.');}
      if(!await vscode.commands.executeCommand('meshlit.reviewProject',uri))return;
      await vscode.commands.executeCommand('vscode.openFolder',uri,{forceNewWindow:true});
     }else return;
    }else if(kind==='settings'&&op==='chatLock'){
     if(typeof message.required!=='boolean')throw Error('Choose a valid chat access setting.');
     await vscode.workspace.getConfiguration('meshlit.chat').update('requireUnlock',message.required,vscode.ConfigurationTarget.Global);
     if(!closed)await panel.webview.postMessage({type:'mainPageResult',message:'Chat access saved. Reopen this window to apply it.'});
     return;
    }else if(kind==='settings'&&op==='save'){
     const values=validateSettings(message.values),config=vscode.workspace.getConfiguration('meshlit.chat');
     for(const [key,value]of Object.entries(values))await config.update(key,value,vscode.ConfigurationTarget.Global);
     if(!closed)await panel.webview.postMessage({type:'mainPageResult',message:'Saved. These settings apply to your next chat request.',settings:readSettings(vscode)});
     return;
    }else return;
    if(!closed)await panel.webview.postMessage({type:'mainPageResult'});
   }catch(error){if(!closed)await panel.webview.postMessage({type:'mainPageResult',error:kind==='settings'?'Could not save all settings. '+String(error.message||'Reload saved values and try again.'):String(error.message||'This page could not complete the action.')});}
   finally{busy=false;if(!closed)try{await panel.webview.postMessage({type:'mainPageIdle'});}catch{}}
  });
 }));
}
module.exports={register,render,validateSettings,readSettings};
