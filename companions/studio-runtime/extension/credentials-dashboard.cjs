const {randomBytes}=require('node:crypto');
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const tokenValid=value=>typeof value==='string'&&value.length>0&&value.length<=8192&&!/[\x00-\x1f\x7f-\x9f]/.test(value);
const routes=Object.freeze({models:'meshlit.mainModels',usage:'meshlit.unlockUsage',project:'meshlit.projectSecurity',apps:'meshlit.appAccess',ssh:'meshlit.sshDashboard'});
function render(profiles=[],{persistentSecrets=false,available=true}={}){
 const nonce=randomBytes(24).toString('hex');
 const storage=persistentSecrets?'Protected storage build':'Session-only storage';
 const storageDetail=persistentSecrets?'Keys use the editor’s SecretStorage. OS availability is checked only when you choose a credential action. This does not establish hardware-backed protection.':'Keys use volatile editor storage in this build and do not survive app restart. No plaintext disk fallback is used.';
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:980px;margin:auto;padding:40px 34px}h1{font-size:28px;font-weight:600;margin:0 0 8px;letter-spacing:-.6px}h2{font-size:18px;font-weight:500;margin:0 0 8px}h3{font-size:15px;font-weight:500;margin:0 0 5px}p{margin:0 0 12px;color:var(--vscode-descriptionForeground)}button{font:inherit;color:var(--vscode-button-secondaryForeground);background:var(--vscode-button-secondaryBackground);border:1px solid var(--vscode-panel-border);border-radius:7px;padding:8px 13px;cursor:pointer}button:hover{background:var(--vscode-button-secondaryHoverBackground)}button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}button:disabled{opacity:.5;cursor:default}.bar{display:flex;gap:9px;flex-wrap:wrap;margin:20px 0}.storage,.profile,.controls,.unavailable{padding:21px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin:16px 0}.storage{background:var(--vscode-editorWidget-background,var(--vscode-editor-background))}.profile-heading{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.status{font-size:11px;border:1px solid var(--vscode-panel-border);padding:4px 8px;border-radius:20px;white-space:nowrap}.address{font-size:12px;overflow-wrap:anywhere}.profile .bar{margin:16px 0 0}.hint{font-size:12px}.unavailable{border-style:dashed}.unavailable ul{padding-left:20px;color:var(--vscode-descriptionForeground);font-size:13px}#result{min-height:24px;white-space:pre-wrap;color:var(--vscode-descriptionForeground)}#result.error{color:var(--vscode-errorForeground)}.empty{padding:24px 0;color:var(--vscode-descriptionForeground)}@media(max-width:620px){main{padding:26px 20px}.profile-heading{flex-direction:column;gap:8px}.bar button{font-size:12px}}
 </style></head><body><main><h1>Credentials</h1><p>Manage saved keys for your exact model connections. Key values are never displayed here or stored in ordinary app preferences.</p><section class="storage"><h2>${storage}</h2><p>${storageDetail}</p><p class="hint">${available?'Opening this page does not read keys or trigger an OS keychain lookup. Use Check saved key when you want to inspect availability.':'Credential storage APIs are unavailable in this runtime. Saved connection metadata can be viewed, but key actions cannot run.'}</p></section><div class="bar"><button data-op="refresh">Refresh connections</button><button data-op="link" data-id="models">Models and connections</button></div><div id="result" role="status" aria-live="polite"></div>${profiles.length?profiles.map(profile=>`<article class="profile"><div class="profile-heading"><div><h3>${escape(profile.service)}</h3><p>${escape(profile.model)}</p><p class="address">${escape(profile.endpoint)}</p></div><span class="status">${profile.valid?escape(profile.presence||'Not checked'):'Endpoint needs review'}</span></div><div class="bar">${[['check','Check saved key'],['rotate','Replace stored key'],['remove','Remove stored key']].map(([op,label])=>`<button data-op="${op}" data-id="${escape(profile.id)}"${!profile.valid||!available?' disabled':''}>${label}</button>`).join('')}</div></article>`).join(''):'<p class="empty">No saved model connections. Add a provider or model server in Models first.</p>'}<p class="hint">Replacing or removing a stored key stops open chats using that connection. It does not rotate or revoke the key at the provider. Perform provider-side revocation through that provider’s account controls.</p><section class="controls"><h2>Related access controls</h2><p>These controls cover different data and actions.</p><div class="bar"><button data-op="link" data-id="usage">Protected Usage</button><button data-op="link" data-id="project">Project security</button><button data-op="link" data-id="apps">App access</button><button data-op="link" data-id="ssh">SSH & terminals</button></div><p class="hint">The Usage passphrase unlocks encrypted history and saved budgets. It does not lock the whole app or independently unlock provider credentials.</p></section><section class="unavailable"><h2>Additional protection not yet implemented</h2><ul><li>Full-app lock and biometric unlock.</li><li>Explicit TPM or Secure Enclave key management and hardware attestation.</li><li>A verified hardware-backed credential policy across desktop and mobile devices.</li></ul><p class="hint">OS secret-storage integration alone does not prove any of these features are active. There are no enabled switches for them in this build.</p></section></main><script nonce="${nonce}">
 const api=acquireVsCodeApi(),result=document.getElementById('result');let pending=false;
 document.querySelectorAll('[data-op]').forEach(button=>{button.dataset.originalDisabled=String(button.disabled);button.onclick=()=>{if(pending)return;pending=true;result.className='';result.textContent='';document.querySelectorAll('button').forEach(item=>item.disabled=true);api.postMessage({credentialsPage:true,op:button.dataset.op,id:button.dataset.id});};});
 window.addEventListener('message',({data})=>{if(data.type!=='credentialsResult')return;pending=false;result.className=data.error?'error':'';result.textContent=data.error||data.message||'';document.querySelectorAll('button').forEach(button=>button.disabled=button.dataset.originalDisabled==='true');});
 </script></body></html>`;
}
function register(context,vscode,{persistentSecrets=false}={}){
 let panel;const presence=new Map();
 const available=()=>['get','store','delete'].every(method=>typeof context.secrets?.[method]==='function');
 const raw=()=>{const value=context.globalState.get('meshlit.connections',[]);return Array.isArray(value)?value:[];};
 function inspect(profile){
  if(!profile||typeof profile.id!=='string'||!/^[-A-Za-z0-9_.]{1,128}$/.test(profile.id))throw Error('Invalid saved connection');
  const endpoint=require('./connections.cjs').endpoint(profile.endpoint);
  const binding=JSON.stringify([profile.id,profile.kind,endpoint,profile.model,profile.requiresKey===true]);
  return {id:profile.id,service:String(profile.service||'Model connection').slice(0,120),model:String(profile.model||'Unspecified model').slice(0,180),endpoint,binding,requiresKey:profile.requiresKey===true,valid:true};
 }
 function profile(id){const matches=raw().filter(item=>item?.id===id);if(matches.length!==1)throw Error('Connection changed');return inspect(matches[0]);}
 function unchanged(expected){try{return profile(expected.id).binding===expected.binding;}catch{return false;}}
 function rows(){return raw().filter(item=>item&&typeof item.id==='string').map(item=>{try{const info=inspect(item),checked=presence.get(info.id);return {...info,presence:checked?.binding===info.binding?checked.label:'Not checked'};}catch{return {id:item.id.slice(0,128),service:String(item.service||'Model connection').slice(0,120),model:'Review this connection in Models',endpoint:'Invalid endpoint — no credential action is available',valid:false};}});}
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.credentialsDashboard',()=>{
  if(panel){panel.webview.html=render(rows(),{persistentSecrets,available:available()});panel.reveal();return;}
  const current=require('./shell.cjs').createPanel(vscode,'meshlit.credentials','Credentials',vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});panel=current;context.subscriptions.push(current);let closed=false,busy=false;
  const refresh=()=>{if(!closed)current.webview.html=render(rows(),{persistentSecrets,available:available()});};refresh();
  current.onDidDispose(()=>{closed=true;panel=undefined;presence.clear();});
  current.webview.onDidReceiveMessage(async message=>{
   if(closed||busy||message?.credentialsPage!==true||!['refresh','link','check','rotate','remove'].includes(message.op))return;
   if(message.op==='link'&&!Object.hasOwn(routes,message.id))return;
   busy=true;let errorMessage='This credential action could not complete. Refresh the connection and check storage availability before retrying.';
   try{
    if(message.op==='refresh'){presence.clear();refresh();return;}
    if(message.op==='link'){await vscode.commands.executeCommand(routes[message.id]);if(!closed)await current.webview.postMessage({type:'credentialsResult'});return;}
    if(!available())throw Error('Storage unavailable');
    let target=profile(message.id);const secretKey='meshlit.model.'+target.id;
    const valid=()=>!closed&&unchanged(target);
    if(message.op==='check'){
     presence.delete(target.id);
     let value=await context.secrets.get(secretKey);if(!valid()){value=undefined;throw Error('Connection changed');}
     presence.set(target.id,{binding:target.binding,label:typeof value==='string'&&value.length>0?'Key available':'No key stored'});value=undefined;
     refresh();if(!closed)await current.webview.postMessage({type:'credentialsResult',message:'Saved-key availability checked. No model request was sent.'});return;
    }
    if(message.op==='remove'){
     const choice=await vscode.window.showWarningMessage(`Remove the stored key for ${target.service}?\n${target.endpoint}\n\nOpen chats using this connection will stop. The provider-issued key is not revoked by this action.`,{modal:true},'Remove stored key');
     if(choice!=='Remove stored key'||!valid()){if(choice==='Remove stored key'&&!closed)throw Error('Connection changed');if(!closed)await current.webview.postMessage({type:'credentialsResult'});return;}
     await vscode.commands.executeCommand('meshlit.revokeConnection',target.id);if(!valid())throw Error('Connection changed');
     presence.delete(target.id);await context.secrets.delete(secretKey);if(!valid())throw Error('Connection changed');
     presence.set(target.id,{binding:target.binding,label:'No key stored'});refresh();if(!closed)await current.webview.postMessage({type:'credentialsResult',message:'Stored key removed. Provider-side revocation is separate.'});return;
    }
    let token=await vscode.window.showInputBox({title:'Replace stored key · '+target.service,password:true,ignoreFocusOut:true,
     prompt:'Save a key for '+target.endpoint+'. It will replace only this connection’s stored key.',
     validateInput:value=>tokenValid(value)?undefined:'Enter a nonempty key of at most 8,192 characters without control characters.'});
    try{
     if(token===undefined||closed){if(!closed)await current.webview.postMessage({type:'credentialsResult'});return;}
     if(!tokenValid(token)||!valid())throw Error('Invalid or changed connection');
     await vscode.commands.executeCommand('meshlit.revokeConnection',target.id);if(!valid())throw Error('Connection changed');
     if(!valid())throw Error('Connection changed');presence.delete(target.id);
     await context.secrets.store(secretKey,token);
     if(!unchanged(target)){
      errorMessage='The connection changed while its key was being stored. Refresh and review that connection before using it.';
      try{await context.secrets.delete(secretKey);}catch{errorMessage='The connection changed and stored-key cleanup could not be verified. Remove its key before using that connection.';}
      throw Error('Connection changed during store');
     }
     presence.set(target.id,{binding:target.binding,label:'Key available'});refresh();if(!closed)await current.webview.postMessage({type:'credentialsResult',message:'Stored key replaced for this exact connection. Provider-side rotation and revocation are separate.'});
    }finally{token=undefined;}
   }catch{if(!closed){refresh();await current.webview.postMessage({type:'credentialsResult',error:errorMessage});}}
   finally{busy=false;}
  });
 }));
}
module.exports={register,render,tokenValid,routes};
