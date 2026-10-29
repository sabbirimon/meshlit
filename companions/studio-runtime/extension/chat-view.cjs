const crypto=require('node:crypto');
const modes=[
 {id:'chat',label:'Chat',description:'Conversation only · no workspace tools'},
 {id:'plan',label:'Plan',description:'Inspect approved files and plan · no edits'},
 {id:'edit',label:'Edit / Build',description:'Propose one reviewed file edit per turn · save it yourself'},
 {id:'autopilot',label:'Autopilot',description:'Multiple bounded steps · tool approvals and edit review stay on'}
];
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function render(title,mode){
 const nonce=crypto.randomBytes(24).toString('hex');
 return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';"><style nonce="${nonce}">
 *{box-sizing:border-box}body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:0;padding:18px;display:flex;flex-direction:column;height:100vh;min-width:220px}
 header{display:flex;justify-content:space-between;align-items:center;gap:8px}h1{font-size:18px;font-weight:500;margin:0}small,.muted{color:var(--vscode-descriptionForeground)}#route{overflow-wrap:anywhere;margin:8px 0;font-size:12px;line-height:1.5}
 #history{flex:1;min-height:0;overflow:auto;margin:12px 0}.message{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6;padding:14px 0;border-bottom:1px solid var(--vscode-panel-border)}.user{color:var(--vscode-textLink-foreground)}
 .composer{border:1px solid var(--vscode-panel-border);background:var(--vscode-input-background);border-radius:14px;padding:10px}.composer:focus-within{border-color:var(--vscode-focusBorder)}textarea{width:100%;resize:vertical;min-height:66px;max-height:35vh;padding:8px;background:transparent;color:var(--vscode-input-foreground);border:0;font:inherit;outline:none}
 button{font:12px var(--vscode-font-family);padding:5px 8px;border:1px solid transparent;border-radius:6px;background:transparent;color:var(--vscode-foreground);cursor:pointer}button:hover{background:var(--vscode-toolbar-hoverBackground)}button:focus-visible{outline:2px solid var(--vscode-focusBorder)}button:disabled{opacity:.45;cursor:default}.controls{display:flex;gap:4px;align-items:center;flex-wrap:wrap}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}#send,#stop{width:30px;height:30px;border-radius:50%;padding:0;font-size:19px}#connection{margin-left:auto}#send{margin-left:2px}#stop{font-size:12px}#connection{max-width:min(240px,55%);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}#mode{background:var(--vscode-badge-background);color:var(--vscode-badge-foreground)}#state{font-size:12px;margin:8px 0;line-height:1.5}#modeHelp{font-size:12px;line-height:1.5;margin:8px 2px}#keyboardHint{align-self:center;font-size:11px}footer{display:flex;gap:4px;flex-wrap:wrap;font-size:12px}
 </style></head><body><header><h1>${escape(title)}</h1><button id="newChat" aria-label="New independent chat">＋ New chat</button></header><div id="route" class="muted">Bundled local model · On this device</div><div id="history" role="log" aria-live="polite"></div><div id="state" role="status">Choose a model and mode below. Other chats can run independently.</div><div class="composer"><textarea id="prompt" aria-label="Message Meshlit" maxlength="16000" placeholder="Ask Meshlit…" aria-describedby="keyboardHint"></textarea><div class="controls"><button id="actions" title="Tools and settings" aria-label="Add tools or model connection">＋</button><button id="mode" aria-label="Choose chat mode">${escape(modes.find(m=>m.id===mode)?.label||'Chat')} ▾</button><button id="approvals" title="Tool approval policy">Review required</button><button id="connection" aria-label="Choose model or gateway">Choose model ▾</button><button class="primary" id="send" aria-label="Send message" title="Send · Enter">↑</button><button id="stop" hidden aria-label="Stop this chat" title="Stop this chat" disabled>■</button></div></div><div id="modeHelp" class="muted"></div><footer><small id="keyboardHint">Enter to send · Shift+Enter for a new line</small></footer>
 <script nonce="${nonce}">
 const api=acquireVsCodeApi(),el=id=>document.getElementById(id),history=el('history'),prompt=el('prompt'),state=el('state'),modes=${JSON.stringify(modes)};let busy=false,answer,closed=false,lastPrompt='';
 function row(text,kind){const e=document.createElement('div');e.className='message '+kind;e.textContent=text;history.append(e);return e;}
 function controls(){el('send').disabled=busy||closed;el('stop').disabled=!busy||closed;el('stop').hidden=!busy;el('send').hidden=busy;el('mode').disabled=busy||closed;el('connection').disabled=busy||closed;}
 for(const op of ['connection','mode','newChat','actions','approvals'])el(op).onclick=()=>api.postMessage({op});
 el('send').onclick=()=>{if(busy||closed||!prompt.value.trim())return;row(prompt.value,'user');answer=row('','assistant');busy=true;controls();state.textContent='Running · New chat stays available';lastPrompt=prompt.value;api.postMessage({op:'send',prompt:prompt.value});prompt.value='';};
 prompt.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey&&!e.altKey&&!e.ctrlKey&&!e.metaKey&&!e.isComposing&&e.keyCode!==229){e.preventDefault();el('send').onclick();}});
 el('stop').onclick=()=>{api.postMessage({op:'stop'});closed=true;busy=false;controls();state.textContent='Stopped. Open New chat to continue. Applied edits remain unsaved.';};
 window.addEventListener('message',({data:e})=>{
  if(e.type==='configuration'){el('route').textContent=e.label+' · '+e.endpoint;el('connection').textContent=(e.label==='Bundled local model'?'Local model':e.label)+' ▾';el('connection').title=e.endpoint;el('mode').textContent=(modes.find(m=>m.id===e.mode)?.label||'Chat')+' ▾';el('modeHelp').textContent=(modes.find(m=>m.id===e.mode)?.description||'')+(e.started?'. Changing model or mode opens a new chat.':'');}
  if(e.type==='delta'){if(!answer)answer=row('','assistant');answer.textContent+=e.text;history.scrollTop=history.scrollHeight;}
  if(['done','error','stopped'].includes(e.type)){if(e.type==='error'&&busy&&!prompt.value)prompt.value=lastPrompt;busy=false;if(e.type==='stopped')closed=true;controls();state.textContent=e.type==='error'?e.message:e.type==='stopped'?'Stopped. Open New chat to continue.':'Complete';}
 });api.postMessage({op:'ready'});
 </script></body></html>`;
}
module.exports={modes,render};
