'use strict';
const vscode = require('vscode');
const crypto = require('node:crypto');
const {LocalServer, endpoint, request} = require('./runtime.cjs');
let local, active, panel;

async function activate(context) {
    const root = process.env.MESHLIT_SUITE_ROOT;
    if (!root) throw Error('Launch the extracted Meshlit Studio application');
    local = new LocalServer(root);
    const settings = () => context.globalState.get('route', {kind:'local', model:'meshlit-local'});
    async function select() {
        const choice = await vscode.window.showQuickPick(['Bundled offline model','Ollama','LM Studio','Other OpenAI-compatible server'], {title:'Model connection'});
        if (!choice) return;
        if (choice === 'Bundled offline model') {
            await context.globalState.update('route', {kind:'local', model:'meshlit-local'});
        } else {
            const input = await vscode.window.showInputBox({title:'Model server base URL',value:choice === 'Ollama'?'http://127.0.0.1:11434/v1':choice === 'LM Studio'?'http://127.0.0.1:1234/v1':'https://',ignoreFocusOut:true});
            if (!input) return;
            const base = endpoint(input);
            const token = await vscode.window.showInputBox({title:'API key (leave empty for a server without authentication)',password:true,ignoreFocusOut:true});
            if (token === undefined) return;
            const inventory = await request(base,'/models',token,null,AbortSignal.timeout(12000));
            const models = (inventory.data || []).map(m=>m.id).filter(id=>typeof id==='string' && id.length<=256);
            if (!models.length) throw Error('The server returned no models.');
            const model = await vscode.window.showQuickPick(models,{title:'Use model from ' + new URL(base).host});
            if (!model) return;
            const key = 'token:' + base;
            if (token) await context.secrets.store(key,token); else await context.secrets.delete(key);
            await context.globalState.update('route',{kind:choice,base,model});
            local.stop();
        }
        history = [];
        panel?.webview.postMessage({type:'clear'});
        status();
    }
    let history = [];
    function status() { const current=settings(); panel?.webview.postMessage({type:'status',text:current.kind==='local'?'On this device · Qwen2.5 1.5B':current.kind+' · '+current.model}); }
    async function generate(prompt) {
        if (typeof prompt !== 'string' || !prompt.trim() || prompt.length>16000) throw Error('Enter a prompt of up to 16,000 characters');
        if (active) throw Error('A reply is already running');
        const controller = new AbortController();
        active=controller;
        const signal=AbortSignal.any([controller.signal,AbortSignal.timeout(240000)]);
        try {
            const selected=settings();
            const route = selected.kind==='local' ? await local.start(signal) : {...selected,token:await context.secrets.get('token:'+selected.base)};
            const messages=[...history,{role:'user',content:prompt}];
            const result=await request(route.base,'/chat/completions',route.token,{model:route.model,messages,max_tokens:128,temperature:0},signal);
            const answer=result.choices?.[0]?.message?.content;
            if (typeof answer !== 'string' || !answer.trim()) throw Error('Model returned an empty response');
            history=[...messages,{role:'assistant',content:answer}].slice(-8);
            return answer;
        } finally { if(active===controller) active=undefined; }
    }
    async function ide() {
        const selected=await vscode.window.showOpenDialog({canSelectFolders:true,canSelectFiles:false,canSelectMany:false,openLabel:'Open coding workspace'});
        if (selected) await vscode.commands.executeCommand('vscode.openFolder',selected[0],true);
    }
    function chat() {
        if(panel) { panel.reveal(); return; }
        panel=vscode.window.createWebviewPanel('meshlit.chat','Meshlit · General Chat',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[],retainContextWhenHidden:true});
        const nonce=crypto.randomBytes(20).toString('hex');
        panel.webview.html=page(nonce);
        panel.onDidDispose(()=>{active?.abort();panel=undefined;history=[];});
        panel.webview.onDidReceiveMessage(async msg=>{
            if(!msg || typeof msg!=='object') return;
            try {
                if(msg.op==='ready') status();
                if(msg.op==='models' && !active) await select();
                if(msg.op==='ide') await ide();
                if(msg.op==='stop') active?.abort();
                if(msg.op==='send') { const answer=await generate(msg.text); panel?.webview.postMessage({type:'answer',text:answer}); }
            } catch(err) { panel?.webview.postMessage({type:'error',text:err.message}); }
        });
    }
    for(const [cmd,fn] of Object.entries({chat,models:select,ide})) {
        context.subscriptions.push(vscode.commands.registerCommand('meshlit.studio.'+cmd,async()=>{try {return await fn();} catch(err){vscode.window.showErrorMessage(err.message);}}));
    }
    context.subscriptions.push({dispose:()=>{active?.abort();local.stop();}});
    if (!vscode.workspace.workspaceFolders?.length) chat();
    return {generate, unload:()=>local.stop()};
}
function page(nonce) {
    return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}'">
<style nonce="${nonce}">body{max-width:760px;margin:8vh auto;padding:0 24px;color:var(--vscode-foreground);font:15px/1.5 var(--vscode-font-family)}header,nav,footer{display:flex;align-items:center;gap:10px;flex-wrap:wrap}header{justify-content:space-between}h1{font-size:28px;margin-bottom:0}#status{color:var(--vscode-descriptionForeground)}button,textarea{font:inherit;border-radius:5px}button{background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);border:0;padding:6px 12px;cursor:pointer}button:focus-visible,textarea:focus{outline:2px solid var(--vscode-focusBorder)}#send{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}textarea{display:block;width:100%;box-sizing:border-box;background:var(--vscode-input-background);color:var(--vscode-input-foreground);padding:14px;border:1px solid var(--vscode-panel-border);resize:vertical;margin-bottom:8px}article{white-space:pre-wrap;margin:16px 0}article b{display:block;font-size:12px;color:var(--vscode-descriptionForeground)}#log{margin:24px 0}#feedback{min-height:22px}</style></head><body><header><h1>General chat</h1><nav><button id="models">Models &amp; connections</button><button id="ide">Open coding IDE…</button></nav></header><div id="status"></div><main id="log" role="log" aria-live="polite"></main><textarea id="prompt" aria-label="Message" rows="3" maxlength="16000" placeholder="Message your model"></textarea><footer><button id="send">Send</button><button id="stop" disabled>Stop</button><span id="feedback" role="status"></span></footer>
<script nonce="${nonce}">const api=acquireVsCodeApi(),el=id=>document.getElementById(id);function row(label,text){const p=document.createElement('article'),b=document.createElement('b');b.textContent=label;p.append(b,document.createTextNode(text));el('log').append(p)}function busy(on){el('send').disabled=on;el('models').disabled=on;el('stop').disabled=!on}for(const id of ['models','ide','stop'])el(id).onclick=()=>api.postMessage({op:id});el('send').onclick=()=>{const text=el('prompt').value.trim();if(!text)return;row('You',text);el('prompt').value='';busy(true);el('feedback').textContent='Generating…';api.postMessage({op:'send',text})};el('prompt').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!el('send').disabled){e.preventDefault();el('send').click()}};window.addEventListener('message',e=>{const m=e.data;if(m.type==='status')el('status').textContent=m.text;if(m.type==='clear')el('log').replaceChildren();if(m.type==='answer')row('Model',m.text);if(m.type==='error'||m.type==='answer'){busy(false);el('feedback').textContent=m.type==='error'?m.text:''}});api.postMessage({op:'ready'});</script></body></html>`;
}
exports.activate=activate;
exports.deactivate=()=>{active?.abort();local?.stop();};
