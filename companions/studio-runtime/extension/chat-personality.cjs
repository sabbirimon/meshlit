'use strict';
const crypto=require('node:crypto');
const key='meshlit.chatPersonality';
const presets=Object.freeze({normal:'Balanced',direct:'Direct',friendly:'Friendly',teacher:'Teacher',concise:'Concise',creative:'Creative',professional:'Professional',playful:'Playful'});
const defaults=Object.freeze({personality:'normal',customInstructions:'',contentStyle:'default'});
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function validate(value){
 if(!value||typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(name=>!Object.hasOwn(defaults,name))||!Object.hasOwn(presets,value.personality)||!['default','direct'].includes(value.contentStyle)||typeof value.customInstructions!=='string'||value.customInstructions.length>1500||/[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]/.test(value.customInstructions))throw Error('Choose a listed personality and content style. Custom instructions must be at most 1,500 characters without control characters.');
 return {personality:value.personality,customInstructions:value.customInstructions,contentStyle:value.contentStyle};
}
function read(context){try{return validate(context.globalState.get(key,defaults));}catch{return {...defaults};}}
function render(value=defaults){
 const settings=validate(value),nonce=crypto.randomBytes(24).toString('hex');
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none';style-src 'nonce-${nonce}';script-src 'nonce-${nonce}'"><style nonce="${nonce}">
 *{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px/1.6 var(--vscode-font-family)}main{max-width:850px;margin:auto;padding:48px 36px}h1{font-size:28px;font-weight:600;margin:0 0 8px}p{color:var(--vscode-descriptionForeground);margin:8px 0 24px}section{padding:24px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin-top:20px}label{display:block;font-weight:500;margin:0 0 8px}select,textarea,button{font:inherit;color:inherit}select,textarea{width:100%;padding:10px;background:var(--vscode-input-background);border:1px solid var(--vscode-input-border,var(--vscode-panel-border));border-radius:7px}textarea{resize:vertical;min-height:160px}button{padding:9px 18px;border:0;border-radius:7px;background:var(--vscode-button-background);color:var(--vscode-button-foreground);cursor:pointer}button:disabled{opacity:.5;cursor:wait}button:focus-visible,select:focus-visible,textarea:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:2px}.toolbar{display:flex;gap:12px;margin-top:24px}.secondary{background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground)}#result{min-height:26px;margin-top:18px}.error{color:var(--vscode-errorForeground)}@media(max-width:600px){main{padding:28px 20px}section{padding:18px}}
 </style></head><body><main><h1>Chat personality</h1><p>Choose how Meshlit speaks. These app-wide preferences apply to your next Chat request, including bot conversations.</p><form id="preferences"><section><label for="personality">Personality</label><select id="personality">${Object.entries(presets).map(([id,label])=>`<option value="${id}"${settings.personality===id?' selected':''}>${label}</option>`).join('')}</select><p>Changes tone and explanation style. Model identity and factual accuracy remain unchanged.</p><label for="custom">Custom instructions</label><textarea id="custom" maxlength="1500" placeholder="For example: use plain language and give one practical example.">${escape(settings.customInstructions)}</textarea><p>Up to 1,500 characters. Saved as ordinary app preferences. Do not put passwords, API keys, or other secrets here.</p></section><section><label for="content-style">Local content style</label><select id="content-style"><option value="default"${settings.contentStyle==='default'?' selected':''}>Default</option><option value="direct"${settings.contentStyle==='direct'?' selected':''}>Candid discussion</option></select><p>Candid discussion requests direct, matter-of-fact language for mature topics on the bundled local route. It is a style preference, not a safety bypass, and cannot change learned model limits. This preference is omitted from external model requests.</p><p>Personality does not grant tools, unlock protected data, or change project and provider permissions.</p></section><div class="toolbar"><button id="save" type="submit">Save preferences</button><button class="secondary" type="button" id="reload">Reload saved values</button></div><div id="result" role="status" aria-live="polite"></div></form></main><script nonce="${nonce}">
 const api=acquireVsCodeApi();let pending=false;const result=document.getElementById('result');
 function busy(value){pending=value;document.querySelectorAll('button').forEach(button=>button.disabled=value);}
 document.getElementById('preferences').onsubmit=event=>{event.preventDefault();if(pending)return;busy(true);api.postMessage({personalityPage:true,op:'save',values:{personality:document.getElementById('personality').value,customInstructions:document.getElementById('custom').value,contentStyle:document.getElementById('content-style').value}});};
 document.getElementById('reload').onclick=()=>{if(pending)return;busy(true);api.postMessage({personalityPage:true,op:'reload'});};
 window.addEventListener('message',({data})=>{if(data.type!=='personalityResult')return;busy(false);result.className=data.error?'error':'';result.textContent=data.error||data.message||'';});
 </script></body></html>`;
}
function register(context,vscode){
 let panel;
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.chatPersonality',()=>{
  if(panel){panel.reveal();return;}
  const current=require('./shell.cjs').createPanel(vscode,'meshlit.personality','Chat personality',vscode.ViewColumn.One,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  panel=current;context.subscriptions.push(current);let closed=false,busy=false;
  current.webview.html=render(read(context));
  current.onDidDispose(()=>{closed=true;panel=undefined;});
  current.webview.onDidReceiveMessage(async message=>{
   if(closed||busy||!message||message.personalityPage!==true)return;
   if(message.op==='reload'){current.webview.html=render(read(context));return;}
   if(message.op!=='save')return;
   busy=true;
   try{const value=validate(message.values);await context.globalState.update(key,value);if(!closed)await current.webview.postMessage({type:'personalityResult',message:'Saved. Applies to your next Chat request.'});}
   catch(error){if(!closed)await current.webview.postMessage({type:'personalityResult',error:'Could not save preferences. '+(error instanceof Error&&error.message.startsWith('Choose a listed')?error.message:'Reload saved values and try again.')});}
   finally{busy=false;}
  });
 }));
}
module.exports={presets,defaults,validate,read,render,register};
