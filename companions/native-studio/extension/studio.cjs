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
    const pair = require('./pairing.cjs').register(vscode,context);
    const control = require('./control.cjs').register(vscode,context,root);
    const {imported, displayOutput} = require('./catalog.cjs');
    const saved = () => context.globalState.get('model-catalog', []);
    async function guardrails() {
        const enabled = context.globalState.get('filter-output',true);
        const choice = await vscode.window.showQuickPick([
            {label:'Filter replies',detail:'Hide email addresses and recognized API token patterns',enabled:true},
            {label:'Show original replies',detail:'Display the text returned by the model',enabled:false}
        ],{title:'Output guardrails · currently '+(enabled?'on':'off')});
        if(choice) {await context.globalState.update('filter-output',choice.enabled);history=[];panel?.webview.postMessage({type:'clear'});status();}
    }
    async function select() {
        if(active) throw Error("Stop the current reply before switching models");
        const selection = await vscode.window.showQuickPick([
            ...['Bundled offline model','Import local GGUF…','Ollama','LM Studio','Other OpenAI-compatible server','Remove saved model…'].map(label=>({label})),
            ...saved().map(route=>({label:route.model,description:route.base||route.file,route}))
        ], {title:'Models & providers'});
        const choice=selection?.label;
        if (!choice) return;
        if (choice==='Remove saved model…') {
            const item=await vscode.window.showQuickPick(saved().map(route=>({label:route.model,description:route.base||route.file,route})),{title:'Remove registration (model files stay on disk)'});
            if(!item)return;
            const retained=saved().filter(r=>r.file!==item.route.file || r.base!==item.route.base || r.model!==item.route.model);
            await context.globalState.update('model-catalog',retained);
            if(item.route.base&&!retained.some(r=>r.base===item.route.base))await context.secrets.delete('token:'+item.route.base);
            if(JSON.stringify(settings())===JSON.stringify(item.route))await context.globalState.update('route',{kind:'local',model:'meshlit-local'});
        } else if(selection.route) {
            await context.globalState.update('route',selection.route);
        } else if(choice==='Import local GGUF…') {
            const files=await vscode.window.showOpenDialog({canSelectMany:false,filters:{'GGUF models':['gguf']},openLabel:'Register local model'});
            if(!files?.length)return;
            const route=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Verifying GGUF'},()=>imported(files[0].fsPath));
            const others=saved().filter(r=>r.file!==route.file);
            if(others.length>=128)throw Error('Remove a saved model first');
            await context.globalState.update('model-catalog',[...others,route]);
            await context.globalState.update('route',route);
        } else if (choice === 'Bundled offline model') {
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
            const route={kind:choice,base,model};
            const others=saved().filter(r=>r.base!==base||r.model!==model);
            if(others.length>=128)throw Error('Remove a saved model first');
            await context.globalState.update('model-catalog',[...others,route]);
            await context.globalState.update('route',route);
        }
        active?.abort();
        local.stop();
        history = [];
        panel?.webview.postMessage({type:'clear'});
        status();
    }
    async function cpu() {
        if (active) throw Error('Stop the current reply before changing CPU threads');
        const max = Math.min(64, require('node:os').availableParallelism());
        const text = await vscode.window.showInputBox({title:'Local CPU threads',prompt:'0 selects automatic. Maximum on this host: '+max,
            value:String(context.globalState.get('threads',0)),validateInput:v=>/^\d+$/.test(v) && Number(v)<=max ? undefined : 'Enter an integer from 0 to '+max});
        if (text !== undefined) { await context.globalState.update('threads', Number(text)); local.stop(); }
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
            const route = ['local','import'].includes(selected.kind) ? await local.start(signal, context.globalState.get('threads',0), selected.kind==='import'?selected:undefined) : {...selected,token:await context.secrets.get('token:'+selected.base)};
            const messages=[...history,{role:'user',content:prompt}];
            const result=await request(route.base,'/chat/completions',route.token,{model:route.model,messages,max_tokens:128,temperature:0},signal);
            const answer=result.choices?.[0]?.message?.content;
            if (typeof answer !== 'string' || !answer.trim()) throw Error('Model returned an empty response');
            signal.throwIfAborted();
            const output=displayOutput(answer,context.globalState.get('filter-output',true));
            history=[...messages,{role:'assistant',content:output}].slice(-8);
            return output;
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
                if(msg.op==='pair') await pair();
                if(msg.op==='control') await control();
                if(msg.op==='guard' && !active) await guardrails();
                if(msg.op==='ide') await ide();
                if(msg.op==='cpu' && !active) await cpu();
                if(msg.op==='stop') active?.abort();
                if(msg.op==='send' && typeof msg.text==='string' && /^\/control(?:\s|$)/i.test(msg.text)){await control(msg.text.replace(/^\/control\s*/i,''));panel?.webview.postMessage({type:'notice',text:'App control review closed.'});return;}
                if(msg.op==='send' && typeof msg.text==='string' && /^\/pair(?:\s|$)/i.test(msg.text)){await pair(msg.text.replace(/^\/pair\s*/i,'')||undefined);panel?.webview.postMessage({type:'notice',text:'Pairing review closed.'});return;}
                if(msg.op==='send') { const answer=await generate(msg.text); panel?.webview.postMessage({type:'answer',text:answer}); }
            } catch(err) { panel?.webview.postMessage({type:'error',text:err.message}); }
        });
    }
    for(const [cmd,fn] of Object.entries({chat,models:select,ide,cpu,pair,guardrails,control})) {
        context.subscriptions.push(vscode.commands.registerCommand('meshlit.studio.'+cmd,async()=>{try {return await fn();} catch(err){vscode.window.showErrorMessage(err.message);}}));
    }
    context.subscriptions.push({dispose:()=>{active?.abort();local.stop();}});
    if (!vscode.workspace.workspaceFolders?.length) chat();
    return {generate, unload:()=>local.stop()};
}
function page(nonce) {
    return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}'">
<style nonce="${nonce}">body{max-width:760px;margin:8vh auto;padding:0 24px;color:var(--vscode-foreground);font:15px/1.5 var(--vscode-font-family)}header,nav,footer{display:flex;align-items:center;gap:10px;flex-wrap:wrap}header{justify-content:space-between}h1{font-size:28px;margin-bottom:0}#status{color:var(--vscode-descriptionForeground)}button,textarea{font:inherit;border-radius:5px}button{background:var(--vscode-button-secondaryBackground);color:var(--vscode-button-secondaryForeground);border:0;padding:6px 12px;cursor:pointer}button:focus-visible,textarea:focus{outline:2px solid var(--vscode-focusBorder)}#send{background:var(--vscode-button-background);color:var(--vscode-button-foreground)}textarea{display:block;width:100%;box-sizing:border-box;background:var(--vscode-input-background);color:var(--vscode-input-foreground);padding:14px;border:1px solid var(--vscode-panel-border);resize:vertical;margin-bottom:8px}article{white-space:pre-wrap;margin:16px 0}article b{display:block;font-size:12px;color:var(--vscode-descriptionForeground)}#log{margin:24px 0}#feedback{min-height:22px}</style></head><body><header><h1>General chat</h1><nav><button id="models">Models &amp; connections</button><button id="pair">Devices</button><button id="control">Control an app</button><button id="guard">Output guardrails</button><button id="cpu">CPU settings</button><button id="ide">Open coding IDE…</button></nav></header><div id="status"></div><main id="log" role="log" aria-live="polite"></main><textarea id="prompt" aria-label="Message" rows="3" maxlength="16000" placeholder="Message your model"></textarea><footer><button id="send">Send</button><button id="stop" disabled>Stop</button><span id="feedback" role="status"></span></footer>
<script nonce="${nonce}">const api=acquireVsCodeApi(),el=id=>document.getElementById(id);function row(label,text){const p=document.createElement('article'),b=document.createElement('b');b.textContent=label;p.append(b,document.createTextNode(text));el('log').append(p)}function busy(on){el('send').disabled=on;el('models').disabled=on;el('stop').disabled=!on}for(const id of ['models','cpu','ide','stop','pair','guard','control'])el(id).onclick=()=>api.postMessage({op:id});el('send').onclick=()=>{const text=el('prompt').value.trim();if(!text)return;row('You',text);el('prompt').value='';busy(true);el('feedback').textContent='Generating…';api.postMessage({op:'send',text})};el('prompt').onkeydown=e=>{if(e.key==='Enter'&&!e.shiftKey&&!el('send').disabled){e.preventDefault();el('send').click()}};window.addEventListener('message',e=>{const m=e.data;if(m.type==='status')el('status').textContent=m.text;if(m.type==='clear')el('log').replaceChildren();if(m.type==='answer')row('Model',m.text);if(['error','answer','notice'].includes(m.type)){busy(false);el('feedback').textContent=m.type==='answer'?'':m.text}});api.postMessage({op:'ready'});</script></body></html>`;
}
exports.activate=activate;
exports.deactivate=()=>{active?.abort();local?.stop();};
