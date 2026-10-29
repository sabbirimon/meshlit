const vscode=require('vscode');
const crypto=require('node:crypto');
function create(context,request){
 let panel;
 const controller={
  open(){
   if(panel){panel.reveal();request({op:'usage'});return;}
   panel=vscode.window.createWebviewPanel('meshlit.usage','Usage & budgets',vscode.ViewColumn.Active,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
   context.subscriptions.push(panel);panel.onDidDispose(()=>{panel=undefined;});panel.webview.html=render();
   panel.webview.onDidReceiveMessage(async message=>{
    try{
     if(['ready','refresh'].includes(message.op))request({op:'usage'});
     else if(message.op==='unlock')await vscode.commands.executeCommand('meshlit.unlockUsage');
     else if(message.op==='budget'){
      if(typeof message.freeOnly!=='boolean'||typeof message.currency!=='string'||!/^[A-Z]{3}$/.test(message.currency)||typeof message.monthly!=='string'||typeof message.perRequest!=='string'||message.monthly.length>30||message.perRequest.length>30)throw Error('Invalid budget values');
      request({op:'budget',freeOnly:message.freeOnly,currency:message.currency,monthly:message.monthly,perRequest:message.perRequest});
     }
    }catch{controller.failure('Could not update Usage. Check the model host and unlock your usage ledger.');}
   });
  },
  accept(data){panel?.webview.postMessage({type:'usage',...data});},
  failure(message){panel?.webview.postMessage({type:'error',message});},
  refresh(){if(panel)request({op:'usage'});}
 };
 return controller;
}
function render(){const nonce=crypto.randomBytes(20).toString('hex');return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${nonce}'; style-src 'nonce-${nonce}';"><style nonce="${nonce}">
body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:auto;max-width:1100px;padding:28px}h1{font-size:24px;font-weight:500}h2{font-size:18px;font-weight:500}.muted{color:var(--vscode-descriptionForeground);line-height:1.5}.bar,.metrics{display:flex;gap:12px;flex-wrap:wrap;align-items:center}.metrics>div,section{padding:18px;border:1px solid var(--vscode-panel-border);border-radius:12px;margin:16px 0}.metrics>div{flex:1;min-width:140px}.metrics strong{display:block;font-size:24px;font-weight:500;margin-bottom:6px}button,input,select{font:inherit;border:1px solid var(--vscode-panel-border);border-radius:6px;padding:7px 10px;background:var(--vscode-input-background);color:var(--vscode-foreground)}button{cursor:pointer}button:hover{background:var(--vscode-toolbar-hoverBackground)}button:disabled{opacity:.4;cursor:default}button:focus-visible,input:focus-visible,select:focus-visible{outline:2px solid var(--vscode-focusBorder)}label{display:flex;gap:8px;align-items:center;margin:12px 0}label:has(input[type=text]){flex-direction:column;align-items:flex-start}table{width:100%;border-collapse:collapse;font-size:12px}td,th{text-align:left;padding:10px;border-bottom:1px solid var(--vscode-panel-border)}td{overflow-wrap:anywhere;max-width:300px}#status{white-space:pre-wrap}#historyWrap{overflow:auto}.primary{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}
</style></head><body><div class="bar"><h1>Usage & budgets</h1><button id="refresh">Refresh</button><button id="unlock">Unlock usage</button></div><p class="muted">Recorded on this device. Budgets are local admission estimates, not provider billing caps. Other apps and gateway dashboards can make requests outside this ledger.</p><p id="status" role="status">Loading encrypted usage history…</p><div class="metrics"><div><strong id="requests">—</strong>Recorded requests</div><div><strong id="input">—</strong>Reported input tokens</div><div><strong id="output">—</strong>Reported output tokens</div><div><strong id="unknown">—</strong>Requests with unknown token usage</div></div>
<section><h2>Request policy</h2><label><input type="checkbox" id="free" checked>Only routes with verified free pricing or owned local inference</label><p class="muted">Opaque gateways remain unpriced, even if their catalog calls a model free. Unknown costs stay unknown; unresolved requests can retain reservations.</p><div class="bar"><label>Currency<input type="text" id="currency" value="USD" maxlength="3" size="4"></label><label>Monthly limit<input type="text" id="monthly" inputmode="decimal" placeholder="No limit"></label><label>Per-request limit<input type="text" id="perRequest" inputmode="decimal" placeholder="No limit"></label><button id="save" class="primary" disabled>Apply policy</button></div><p class="muted">Enter both positive amounts, or leave both blank to remove only this currency's limit. Monthly accounting uses UTC calendar months. Other currency limits are retained.</p><div id="budgets"></div></section>
<section><h2>Recent requests</h2><div class="bar"><input id="search" aria-label="Search models" placeholder="Search model"><select id="filter" aria-label="Request status"><option value="">All statuses</option><option>complete</option><option>failed</option><option>cancelled</option><option>pending</option></select></div><p class="muted">Newest 200 records. These are observed requests, not provider balance statements. Prompts, replies and credentials are not stored in this ledger.</p><div id="historyWrap"><table><thead><tr><th>Time</th><th>Model / route</th><th>Status</th><th>Input</th><th>Output</th><th>Cost / currency</th></tr></thead><tbody id="history"></tbody></table></div></section>
<script nonce="${nonce}">
const api=acquireVsCodeApi(),el=id=>document.getElementById(id);let snapshot;
const send=op=>api.postMessage({op});el('refresh').onclick=()=>send('refresh');el('unlock').onclick=()=>send('unlock');
function amounts(){const b=snapshot?.budgets.find(b=>b.currency===el('currency').value.toUpperCase());el('monthly').value=b?.monthly||'';el('perRequest').value=b?.perRequest||'';}
el('currency').onchange=()=>{el('currency').value=el('currency').value.toUpperCase();amounts();};
el('save').onclick=()=>{el('save').disabled=true;el('status').textContent='Saving policy…';api.postMessage({op:'budget',freeOnly:el('free').checked,currency:el('currency').value.toUpperCase(),monthly:el('monthly').value,perRequest:el('perRequest').value});};
function draw(){el('history').replaceChildren();const rows=(snapshot?.history||[]).filter(r=>(!el('filter').value||el('filter').value===r.status)&&r.model.toLowerCase().includes(el('search').value.toLowerCase()));for(const r of rows){const tr=document.createElement('tr');for(const text of [new Date(r.startedAt).toLocaleString(),r.model+' · '+(r.local?'Local':r.gateway?'Gateway':'Provider'),r.status+(r.unresolved?' · unresolved':''),r.input??'Unknown',r.output??'Unknown',r.cost===null?'Unknown':r.cost+' '+r.currency]){const td=document.createElement('td');td.textContent=text;tr.append(td);}el('history').append(tr);}}
el('search').oninput=draw;el('filter').onchange=draw;
window.addEventListener('message',({data:d})=>{if(d.type==='error'){el('status').textContent=d.message;el('save').disabled=!snapshot;el('unlock').hidden=false;return;}if(d.type!=='usage')return;snapshot=d;el('status').textContent='History loaded · '+d.month+' UTC';el('unlock').hidden=true;el('save').disabled=false;for(const [id,key] of [['requests','requests'],['input','inputTokens'],['output','outputTokens'],['unknown','unknownUsageRequests']])el(id).textContent=d[key].toLocaleString();el('free').checked=d.freeOnly;amounts();el('budgets').replaceChildren();for(const b of d.budgets){const p=document.createElement('p');p.textContent=b.currency+': '+b.spent+' estimated spent this month · '+b.held+' reserved · '+b.monthly+' monthly / '+b.perRequest+' per request';el('budgets').append(p);}draw();});send('ready');
</script></body></html>`;}
module.exports={create,render};
