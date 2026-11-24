const {randomUUID,randomBytes}=require('node:crypto');
const sources=require('./model-sources.cjs');
const localModels=require('./local-models.cjs');
const localBot=id=>id==='local'||id.startsWith('local:');
const localBotModel=id=>localModels.list().find(m=>m.id===(id==='local'?localModels.STARTER:id.slice(6)));
const localBotLabel=id=>localBotModel(id)?.label||'Bundled model missing';
const key='meshlit.bots';
const limit=24;
const local={id:'local',label:'Bundled local model'};
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const cleanText=(value,max,multiline=false)=>typeof value==='string'&&value.trim().length>0&&value.length<=max&&!(multiline?/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/:/[\x00-\x1f\x7f]/).test(value);
function validBot(bot){return !!bot&&cleanText(bot.id,128)&&cleanText(bot.name,60)&&cleanText(bot.instructions,2000,true)&&cleanText(bot.modelId,200);}
function render(bots=[],profiles=[]){
 const nonce=randomBytes(24).toString('hex');
 const button=(label,op,id,disabled=false)=>`<button data-op="${op}"${id===undefined?'':` data-id="${escape(id)}"`}${disabled?' disabled':''}>${escape(label)}</button>`;
 const cards=bots.map(bot=>{
  const profile=profiles.find(item=>item.id===bot.modelId),available=(localBot(bot.modelId)?!!localBotModel(bot.modelId)?.present:!!profile);
  const model=localBot(bot.modelId)?localBotLabel(bot.modelId):profile?`${sources.label(profile)||profile.service||'Model API'} · ${profile.model}`:'Model connection missing';
  return `<article class="bot"><div class="bot-heading"><div><span class="tag">CUSTOM BOT · CHAT</span><h2>${escape(bot.name)}</h2><p>${escape(model)}</p></div><span class="badge">${available?'Saved profile':'Needs model setup'}</span></div><details><summary>Instructions</summary><pre>${escape(bot.instructions)}</pre></details><p class="hint">Uses its saved model and instructions. No workspace or device tools are attached to this bot chat.</p><div class="actions">${button('Start chat','start',bot.id,!available)}${button('Details','details',bot.id)}${button('Separate window','detailsSeparate',bot.id)}${button('Edit','edit',bot.id)}${button('Remove','remove',bot.id)}</div></article>`;
 }).join('');
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:1040px;margin:auto;padding:44px 36px}h1{font-size:28px;font-weight:600;letter-spacing:-.6px;margin:0 0 8px}h2{font-size:18px;font-weight:500;margin:3px 0 6px}h3{font-size:15px;font-weight:500;margin:0 0 8px}p{color:var(--vscode-descriptionForeground);margin:0 0 14px;max-width:76ch}.toolbar,.actions{display:flex;gap:9px;flex-wrap:wrap;margin:22px 0}.actions{margin:16px 0 0}button{font:inherit;color:var(--vscode-button-secondaryForeground);background:var(--vscode-button-secondaryBackground);padding:8px 14px;border:1px solid var(--vscode-panel-border);border-radius:7px;cursor:pointer}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button:focus-visible,summary:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}button:disabled{opacity:.5;cursor:default}.toolbar button:first-child,.actions button:first-child{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}.bot{padding:22px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin:14px 0;background:var(--vscode-editorWidget-background,var(--vscode-editor-background))}.bot-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:18px}.tag{font-size:10px;letter-spacing:.1em;color:var(--vscode-descriptionForeground)}.badge{white-space:nowrap;font-size:11px;padding:3px 8px;border:1px solid var(--vscode-panel-border);border-radius:20px;color:var(--vscode-descriptionForeground)}summary{cursor:pointer;color:var(--vscode-textLink-foreground)}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.6 var(--vscode-font-family);padding:14px;background:var(--vscode-input-background);border-radius:8px}.hint{font-size:12px;margin:12px 0 0}.section-title{font-size:13px;color:var(--vscode-descriptionForeground);margin:30px 0 12px}.empty{padding:26px;border:1px dashed var(--vscode-panel-border);border-radius:12px;color:var(--vscode-descriptionForeground)}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.service{padding:20px;border:1px solid var(--vscode-panel-border);border-radius:12px}.service p{font-size:13px}.note{padding:18px 0;font-size:12px}#result{min-height:24px;color:var(--vscode-errorForeground)}@media(max-width:700px){main{padding:28px 20px}.grid{grid-template-columns:1fr}.bot-heading{flex-direction:column;gap:8px}}
 </style></head><body><main><h1>Bots</h1><p>Create a reusable assistant, connect another model, or open a workspace agent team. Each keeps its own model and permissions.</p><div class="toolbar">${button('Create bot','create')}${button('Add model / provider','connection')}${button('Refresh','refresh')}${button('General Chat','general')}</div><div id="result" role="status" aria-live="polite"></div><div class="section-title">Your bots · ${bots.length} / ${limit}</div>${cards||'<div class="empty">No saved bots yet. Create a bot with your own instructions and a local or connected model.</div>'}<div class="section-title">Agents and connected services</div><div class="grid"><section class="service"><h3>Workspace agent teams</h3><p>Assign project work to configured roles and models, with bounded execution, status, review and Stop. Teams have separate setup and permissions.</p>${button('Open agent teams','teams')}${button('Workspace agents','workspace')}</section><section class="service"><h3>Third-party bot companions</h3><p>Configure an owner-installed companion or model API. Provider accounts, service availability and supported tools are checked in their own setup. A saved custom bot is not an installed OpenClaw or AstrBot service.</p>${button('Companion services','companions')}${button('MCP connectors','connectors')}</section></div><p class="note">Current custom profiles reuse instructions; they do not train themselves, install new skills, or run continuously in the background. Autonomous self-improvement and always-on bot channels require additional implementation and qualification. Agent teams expose the bounded workflows available today.</p>${button('Setup and capability guide','manual')}</main><script nonce="${nonce}">
 const api=acquireVsCodeApi();let pending=false;const result=document.getElementById('result');
 document.querySelectorAll('[data-op]').forEach(button=>{button.dataset.originalDisabled=String(button.disabled);button.onclick=()=>{if(pending)return;pending=true;result.textContent='';document.querySelectorAll('button').forEach(item=>item.disabled=true);api.postMessage({botsPage:true,op:button.dataset.op,id:button.dataset.id});};});
 window.addEventListener('message',({data})=>{if(data.type!=='botsResult')return;pending=false;result.textContent=data.error||'';document.querySelectorAll('button').forEach(button=>button.disabled=button.dataset.originalDisabled==='true');});
 </script></body></html>`;
}
const MOVE_EDITOR='workbench.action.moveEditorToNewWindow';
function renderDetails(bot,profiles=[]){
 const nonce=randomBytes(24).toString('hex'),profile=bot&&profiles.find(item=>item.id===bot.modelId);
 const available=!!bot&&((localBot(bot.modelId)?!!localBotModel(bot.modelId)?.present:!!profile));
 const model=!bot?'Unavailable':localBot(bot.modelId)?localBotLabel(bot.modelId):profile?`${sources.label(profile)||profile.service||'Model API'} · ${profile.model}`:'Model connection missing';
 let endpoint=bot&&localBot(bot.modelId)?'On this device':'No saved endpoint';
 if(profile){try{const url=new URL(profile.endpoint);if(url.username||url.password||url.search||url.hash||!['https:','http:'].includes(url.protocol))throw Error('Invalid address');endpoint=url.toString();}catch{endpoint='Endpoint needs review in Models';}}
 const button=(label,op,disabled=false)=>`<button data-op="${op}"${disabled?' disabled':''}>${escape(label)}</button>`;
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:1000px;margin:auto;padding:36px}.eyebrow{font-size:10px;letter-spacing:.1em;color:var(--vscode-descriptionForeground);margin:26px 0 8px}h1{font-size:28px;font-weight:600;letter-spacing:-.6px;margin:0 0 8px;overflow-wrap:anywhere}h2{font-size:17px;font-weight:500;margin:0 0 12px}p{color:var(--vscode-descriptionForeground);margin:0 0 13px}button{font:inherit;color:var(--vscode-button-secondaryForeground);background:var(--vscode-button-secondaryBackground);padding:8px 13px;border:1px solid var(--vscode-panel-border);border-radius:7px;cursor:pointer}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button:disabled{opacity:.45;cursor:default}button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}.toolbar{display:flex;gap:9px;flex-wrap:wrap;margin:18px 0}.topbar{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap}.topbar div{display:flex;gap:8px;flex-wrap:wrap}.grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:24px}section{padding:22px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin:16px 0;background:var(--vscode-editorWidget-background,var(--vscode-editor-background))}.grid section{margin:0}.label{font-size:11px;color:var(--vscode-descriptionForeground);margin-bottom:4px}.value{overflow-wrap:anywhere;font-size:14px;margin-bottom:14px}.badge{display:inline-block;border:1px solid var(--vscode-panel-border);border-radius:20px;padding:3px 10px;font-size:11px;color:var(--vscode-descriptionForeground)}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:13px/1.65 var(--vscode-font-family);margin:0;padding:16px;border-radius:8px;background:var(--vscode-input-background)}.hint{font-size:12px;margin:12px 0 0}.capabilities{display:grid;grid-template-columns:1fr auto;gap:10px 18px;font-size:12px}.capabilities span:nth-child(2n){color:var(--vscode-descriptionForeground);text-align:right}.pending{border-style:dashed;background:transparent}#result{min-height:24px;color:var(--vscode-errorForeground)}@media(max-width:650px){main{padding:24px 18px}.grid{grid-template-columns:1fr}.capabilities{grid-template-columns:1fr}.capabilities span:nth-child(2n){text-align:left}}
 </style></head><body><main><nav class="topbar" aria-label="Bot navigation"><div>${button('All bots','dashboard')}${button('General Chat','general')}</div>${button('Open details in separate window','separate',!bot)}</nav><div class="eyebrow">${bot?'CUSTOM BOT PROFILE':'BOT UNAVAILABLE'}</div><h1>${escape(bot?.name||'This bot is no longer saved')}</h1><p>${bot?'Inspect this bot’s model, instructions and actual capabilities. Opening this page does not start a background agent.':'Return to Bots to choose a saved profile or create another one.'}</p><span class="badge">${bot?(available?'Chat-only profile':'Model setup required'):'Unavailable'}</span><div class="toolbar">${button('Start bot chat','start',!available)}${button('Edit profile','edit',!bot)}${button('Remove bot','remove',!bot)}${button('Refresh details','refresh')}</div><div id="result" role="status" aria-live="polite"></div>${bot?`<div class="grid"><section><h2>Selected model and source</h2><div class="label">MODEL</div><div class="value">${escape(model)}</div><div class="label">SOURCE</div><div class="value">${escape(localBot(bot.modelId)?'Bundled local inference':profile?sources.category(profile):'Missing connection')}</div><div class="label">DESTINATION</div><div class="value">${escape(endpoint)}</div><p class="hint">Saved configuration is not a live connection check. Starting a remote chat retains its exact-route review and usage controls.</p><div class="toolbar">${button('Models and providers','models')}${button('Check a saved connection…','check')}</div></section><section><h2>Capabilities in this bot chat</h2><div class="capabilities"><span>Conversation and saved instructions</span><span>Available</span><span>Workspace files, terminal and edits</span><span>Not attached</span><span>Computer and phone control</span><span>Not attached</span><span>MCP tool execution</span><span>Not attached</span><span>Scheduled / always-on operation</span><span>Not implemented</span><span>Self-training or new skill installation</span><span>Not implemented</span></div><p class="hint">These are capability boundaries, not switches. Model knowledge and response quality depend on the selected model.</p></section></div><section><h2>Saved instructions</h2><pre>${escape(bot.instructions)}</pre><p class="hint">Edit profile to update these instructions and the selected model. Changes apply to new bot chats; existing chats retain their launch configuration. Instructions are ordinary preferences, so keep credentials out of them.</p></section>`:''}<section><h2>Project work and connected tools</h2><p>For bounded autonomous project work, open a workspace agent or agent team. Companion services and connectors have their own setup and permission controls; opening them does not attach tools to this chat-only profile.</p><div class="toolbar">${button('Workspace agents','workspace')}${button('Agent teams','teams')}${button('MCP connectors','connectors')}${button('Companion services','companions')}${button('Tools and permissions','permissions')}</div></section><section class="pending"><h2>Background bots and self-improvement</h2><p>Always-on channels, learned skill updates, scheduling, and self-improvement remain separate implementation and qualification work. There is no enabled background-service or training toggle on this profile.</p>${button('Setup and capability guide','manual')}</section></main><script nonce="${nonce}">
 const api=acquireVsCodeApi(),result=document.getElementById('result');let busy=false;
 document.querySelectorAll('[data-op]').forEach(button=>{button.dataset.originalDisabled=String(button.disabled);button.onclick=()=>{if(busy)return;busy=true;result.textContent='';document.querySelectorAll('button').forEach(item=>item.disabled=true);api.postMessage({botDetailsPage:true,op:button.dataset.op});};});
 window.addEventListener('message',({data})=>{if(data.type!=='botDetailsResult')return;busy=false;result.textContent=data.error||'';document.querySelectorAll('button').forEach(button=>button.disabled=button.dataset.originalDisabled==='true');});
 </script></body></html>`;
}
function register(context,vscode,openChat) {
 const {window}=vscode;let panel;const details=new Map();
 const bots=()=>{
  const value=context.globalState.get(key,[]);
  if(!Array.isArray(value)||value.length>limit||value.some(bot=>!validBot(bot))||new Set(value.map(bot=>bot.id)).size!==value.length)throw Error('Saved bot profiles need review. Their data was not changed.');
  return value;
 };
 const connections=()=>{const value=context.globalState.get('meshlit.connections',[]);return Array.isArray(value)?value.filter(profile=>profile&&typeof profile.id==='string'&&typeof profile.model==='string'):[];};
 function same(original){return JSON.stringify(bots().find(bot=>bot.id===original.id))===JSON.stringify(original);}
 async function edit(original,active=()=>true) {
  const name=(await window.showInputBox({title:'Bot name',value:original?.name,
   validateInput:value=>cleanText(value.trim(),60)?undefined:'Enter a name of 1–60 characters.'}))?.trim();
  if(!active()||name===undefined)return;if(!cleanText(name,60))throw Error('Enter a name of 1–60 characters.');
  const instructions=(await window.showInputBox({title:'Bot instructions',value:original?.instructions,
   prompt:'Describe how this bot should answer. Use no credentials. Bots start without tools.',
   validateInput:value=>cleanText(value.trim(),2000,true)?undefined:'Enter 1–2,000 characters without control characters.'}))?.trim();
  if(!active()||instructions===undefined)return;if(!cleanText(instructions,2000,true))throw Error('Enter instructions of 1–2,000 characters.');
  const model=await window.showQuickPick([...localModels.list().filter(m=>m.present).map(m=>({id:m.id===localModels.STARTER?'local':'local:'+m.id,label:m.label})),...connections().map(p=>({id:p.id,label:`${sources.label(p)||p.service} · ${p.model}`,description:sources.category(p),detail:p.endpoint}))],
   {title:'Choose the bot model',placeHolder:original?'Choose the model for future chats':'Local or an existing model connection'});
  if(!active()||!model)return;
  if(!localBot(model.id)&&!connections().some(profile=>profile.id===model.id))throw Error('That model connection is no longer available.');
  const current=bots();
  if(original&&!same(original))throw Error('This bot changed while you were editing. Reopen it before saving.');
  if(!original&&current.length>=limit)throw Error(`Remove a bot before saving more than ${limit}.`);
  const bot={id:original?.id||randomUUID(),name,instructions,modelId:model.id};if(!validBot(bot))throw Error('Choose a valid bot name, instructions and model.');
  await context.globalState.update(key,[...current.filter(item=>item.id!==bot.id),bot]);refreshViews();
  // A nonmodal notification stays pending until dismissed; it must not hold the page busy.
  void Promise.resolve(window.showInformationMessage(`${bot.name} saved. Start a new bot chat to use these instructions.`)).catch(()=>{});
 }
 async function start(bot,options={}){
  const current=bots().find(item=>item.id===bot.id);if(!current)throw Error('This bot is no longer saved.');
  if(localBot(current.modelId)&&!localBotModel(current.modelId)?.present)throw Error('Bundled model missing');
  if(!localBot(current.modelId)&&!connections().some(profile=>profile.id===current.modelId))throw Error('This bot’s model connection is missing. Edit the bot to choose another model.');
  return openChat({...current},options);
 }
 async function remove(bot,active=()=>true){
  if(await window.showWarningMessage(`Remove ${bot.name} from saved bots?`,{modal:true},'Remove')!=='Remove'||!active())return;
  if(!same(bot))throw Error('This bot changed. Review it again before removal.');
  await context.globalState.update(key,bots().filter(item=>item.id!==bot.id));refreshViews();
 }
 function refreshViews(){
  if(panel)panel.webview.html=render(bots(),connections());
  for(const entry of details.values())entry.refresh();
 }
 const failure=error=>error?.code==='BOT_WINDOW_UNAVAILABLE'?'Separate bot windows are unavailable in this workbench build. Details remain available in this window.':'The bot action could not complete. Review the saved profile and model connection, then try again.';
 async function detachDetails(entry){
  const available=typeof vscode.commands.getCommands==='function'?await vscode.commands.getCommands(true):[];
  if(!available.includes(MOVE_EDITOR)){const error=Error('Window command unavailable');error.code='BOT_WINDOW_UNAVAILABLE';throw error;}
  if(entry.closed||!bots().some(bot=>bot.id===entry.id))throw Error('Bot details closed or removed');
  entry.panel.reveal();
  try{await vscode.commands.executeCommand(MOVE_EDITOR);}catch{const error=Error('Window command unavailable');error.code='BOT_WINDOW_UNAVAILABLE';throw error;}
 }
 async function openDetails(id,options={}){
  if(!cleanText(id,128)||!options||typeof options!=='object'||Array.isArray(options)||Object.keys(options).some(name=>name!=='separate')||(options.separate!==undefined&&typeof options.separate!=='boolean'))throw Error('Invalid bot details request');
  const bot=bots().find(item=>item.id===id);if(!bot)throw Error('Saved bot missing');
  let entry=details.get(id);
  if(!entry){
   const current=require('./shell.cjs').createPanel(vscode,'meshlit.botDetails',`Bot · ${bot.name}`,vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
   entry={id,panel:current,closed:false,busy:false,refresh:()=>{if(entry.closed)return;const latest=bots().find(item=>item.id===id);current.title=latest?`Bot · ${latest.name}`:'Bot unavailable';current.webview.html=renderDetails(latest,connections());}};
   details.set(id,entry);context.subscriptions.push(current);
   current.onDidDispose(()=>{entry.closed=true;if(details.get(id)===entry)details.delete(id);});
   current.webview.onDidReceiveMessage(async message=>{
    if(entry.closed||entry.busy||message?.botDetailsPage!==true||typeof message.op!=='string')return;
    const links={dashboard:'meshlit.botDashboard',general:'meshlit.generalChat',models:'meshlit.models',check:'meshlit.checkConnection',workspace:'meshlit.workspaceAgents',teams:'meshlit.agentTeams',connectors:'meshlit.connectors',companions:'meshlit.companions',permissions:'meshlit.permissions',manual:'meshlit.manual'};
    if(!['refresh','start','edit','remove','separate'].includes(message.op)&&!Object.hasOwn(links,message.op))return;
    entry.busy=true;
    try{
     if(message.op==='separate')await detachDetails(entry);
     else if(['start','edit','remove'].includes(message.op)){
      const latest=bots().find(item=>item.id===id);if(!latest)throw Error('Saved bot missing');
      if(message.op==='start'){entry.panel.reveal();await start(latest,{viewColumn:entry.panel.viewColumn??vscode.ViewColumn.Active});}else if(message.op==='edit')await edit(latest,()=>!entry.closed);else await remove(latest,()=>!entry.closed);
     }else if(Object.hasOwn(links,message.op))await vscode.commands.executeCommand(links[message.op]);
     entry.refresh();if(!entry.closed)await current.webview.postMessage({type:'botDetailsResult'});
    }catch(error){if(!entry.closed)await current.webview.postMessage({type:'botDetailsResult',error:failure(error)});}
    finally{entry.busy=false;}
   });
  }
  entry.refresh();entry.panel.reveal();
  if(options.separate)await detachDetails(entry);
  return entry.panel;
 }
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.openBotDetails',async(id,options)=>{try{return await openDetails(id,options);}catch(error){await window.showErrorMessage(failure(error));}}));
 async function manage(){try{
  const choice=await window.showQuickPick([{label:'$(add) Create bot',action:'create'},{label:'$(plug) Add a model connection',action:'connection'},{label:'$(comment-discussion) General Chat',action:'general'},...bots().map(bot=>({label:bot.name,description:localBot(bot.modelId)?localBotLabel(bot.modelId):connections().find(profile=>profile.id===bot.modelId)?.service||'Model connection missing',bot}))],{title:'Meshlit · Bots',placeHolder:'Choose a bot to chat with, edit, or remove'});
  if(!choice)return;if(choice.action==='create')return await edit();if(choice.action==='connection')return await vscode.commands.executeCommand('meshlit.addModelSource');if(choice.action==='general')return await vscode.commands.executeCommand('meshlit.generalChat');
  if(!choice.bot)return;
  const action=await window.showQuickPick(['Start bot chat','Open bot details','Open details in separate window','Edit bot','Remove bot'],{title:choice.bot.name}),bot=bots().find(item=>item.id===choice.bot.id);if(!bot)return;
  if(action==='Start bot chat')return await start(bot);if(action==='Open bot details')return await openDetails(bot.id);if(action==='Open details in separate window')return await openDetails(bot.id,{separate:true});if(action==='Edit bot')return await edit(bot);if(action==='Remove bot')return await remove(bot);
 }catch(error){await window.showErrorMessage(failure(error));}}
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.bots',manage),vscode.commands.registerCommand('meshlit.manageBots',manage));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.botDashboard',()=>{
  bots(); // Validate before creating a page, preserving damaged saved data for review.
  if(panel){panel.webview.html=render(bots(),connections());panel.reveal();return;}
  const current=require('./shell.cjs').createPanel(vscode,'meshlit.botDashboard','Bots',vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  panel=current;context.subscriptions.push(current);let closed=false,busy=false;
  const refresh=()=>{if(!closed)current.webview.html=render(bots(),connections());};refresh();
  current.onDidDispose(()=>{closed=true;panel=undefined;});
  current.webview.onDidReceiveMessage(async message=>{
   if(closed||busy||message?.botsPage!==true||typeof message.op!=='string')return;
   const links={general:'meshlit.generalChat',connection:'meshlit.addModelSource',teams:'meshlit.agentTeams',workspace:'meshlit.workspaceAgents',companions:'meshlit.companions',connectors:'meshlit.connectors',manual:'meshlit.manual'};
   if(!['refresh','create','start','edit','remove','details','detailsSeparate'].includes(message.op)&&!Object.hasOwn(links,message.op))return;
   busy=true;
   try{
    if(message.op==='create')await edit(undefined,()=>!closed);
    else if(message.op==='details'||message.op==='detailsSeparate')await openDetails(message.id,{separate:message.op==='detailsSeparate'});
    else if(['start','edit','remove'].includes(message.op)){
     const bot=bots().find(item=>item.id===message.id);if(!bot)throw Error('Saved bot missing');
     if(message.op==='start')await start(bot);else if(message.op==='edit')await edit(bot,()=>!closed);else await remove(bot,()=>!closed);
    }else if(Object.hasOwn(links,message.op))await vscode.commands.executeCommand(links[message.op]);
    refresh();if(!closed)await current.webview.postMessage({type:'botsResult'});
   }catch(error){if(!closed)await current.webview.postMessage({type:'botsResult',error:error?.code==='BOT_WINDOW_UNAVAILABLE'?'Separate bot windows are unavailable in this workbench build. Details remain available in this window.':'The bot action could not complete. Reload saved profiles and review the model connection before retrying.'});}
   finally{busy=false;}
  });
 }));
}
module.exports={register,render,renderDetails,validBot,MOVE_EDITOR};
