'use strict';
const crypto=require('node:crypto');
function dashboard(vscode, snapshot, act) {
    let panel, busy=false;
    async function refresh() {
        if(panel)await panel.webview.postMessage({type:'state',...snapshot(),busy});
    }
    function open() {
        if(panel){panel.reveal();return refresh();}
        panel=vscode.window.createWebviewPanel('meshlit.models','Meshlit · Model management',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[]});
        const nonce=crypto.randomBytes(20).toString('hex');
        panel.webview.html=html(nonce);
        panel.onDidDispose(()=>{panel=undefined;});
        panel.webview.onDidReceiveMessage(async m=>{
            if(m?.op==='ready')return refresh();
            if(busy||!['select','import','folder','discover','connect','remove','load','unload','endpoint','key','chat','back','pair'].includes(m?.op))return;
            busy=true;
            try {await refresh();await act(m.op,m.id);}
            catch(err){vscode.window.showErrorMessage(err.message);}
            finally {busy=false;await refresh();}
        });
        return panel;
    }
    return {open,refresh,dispose:()=>panel?.dispose()};
}
function html(n) {
    return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-${n}'; style-src 'nonce-${n}'"><style nonce="${n}">
body{max-width:1000px;margin:40px auto;padding:0 24px;font:14px/1.55 var(--vscode-font-family);color:var(--vscode-foreground)}h1{font-weight:600;font-size:28px}h2{font-size:18px;margin:0}header,.bar,.row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}header,.row{justify-content:space-between}section{border-top:1px solid var(--vscode-panel-border);padding:18px 0;margin-top:22px}.row{padding:10px 0}.detail,#server{color:var(--vscode-descriptionForeground);overflow-wrap:anywhere}button{font:inherit;padding:5px 11px;border:1px solid var(--vscode-panel-border);border-radius:4px;color:var(--vscode-button-secondaryForeground);background:var(--vscode-button-secondaryBackground);cursor:pointer}button:focus-visible{outline:2px solid var(--vscode-focusBorder)}button:disabled{opacity:.6;cursor:default}#chat{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}
</style></head><body><header><button id="back">← Back</button><h1>Model management</h1><button id="chat">General chat</button></header>
<section><div class="bar"><h2>Model library</h2><button id="discover">Discover models</button><button id="import">Import model file</button><button id="folder">Import model folder</button></div><div id="library"></div></section>
<section><div class="bar"><h2>Provider connections</h2><button id="connect">Connect provider</button><button id="pair">Pair device</button></div><div id="providers"></div></section>
<section><h2>Local API server</h2><p id="server" role="status"></p><div class="bar"><button id="load">Load selected model</button><button id="unload">Unload</button><button id="endpoint">Copy endpoint</button><button id="key">Copy API key</button></div></section>
<script nonce="${n}">const api=acquireVsCodeApi(),el=id=>document.getElementById(id);for(const button of document.querySelectorAll('button'))button.onclick=()=>api.postMessage({op:button.id});function rows(id,items,busy){const host=el(id);host.replaceChildren();if(!items.length){host.textContent='No saved connections';return;}for(const m of items){const row=document.createElement('div'),info=document.createElement('div'),name=document.createElement('strong'),detail=document.createElement('div');row.className='row';name.textContent=m.name;detail.textContent=m.detail;detail.className='detail';info.append(name,detail);row.append(info);const actions=document.createElement('div');actions.className='bar';for(const op of ['select','remove']){if((op==='remove'&&m.bundled)||(op==='select'&&m.canSelect===false))continue;const btn=document.createElement('button');btn.textContent=op==='remove'?'Forget':m.selected?'Selected':'Select';btn.disabled=busy||(op==='select'&&m.selected);btn.onclick=()=>api.postMessage({op,id:m.id});actions.append(btn);}row.append(actions);host.append(row);}}window.addEventListener('message',({data:s})=>{if(s.type!=='state')return;for(const b of document.querySelectorAll('button'))b.disabled=s.busy;rows('library',s.library,s.busy);rows('providers',s.providers,s.busy);el('server').textContent=s.server;el('load').disabled=s.busy||!s.isLocal||s.running;for(const id of ['unload','endpoint','key'])el(id).disabled=s.busy||!s.running;});api.postMessage({op:'ready'});</script></body></html>`;
}
module.exports={dashboard,html};
