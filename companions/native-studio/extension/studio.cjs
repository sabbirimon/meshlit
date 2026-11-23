'use strict';
const vscode = require('vscode');
const crypto = require('node:crypto');
const {page} = require('./welcome.cjs');
const {LocalServer, endpoint, request} = require('./runtime.cjs');
let local, active, panel;

async function activate(context) {
    const root = process.env.MESHLIT_SUITE_ROOT;
    if (!root) throw Error('Launch the extracted Meshlit Studio application');
    local = new LocalServer(root);
    const empty = !vscode.workspace.workspaceFolders?.length;
    const oldHome = require('node:path').join(require('node:os').homedir(),'.meshlit','native-studio','Meshlit Studio.code-workspace');
    const home = empty && (!vscode.workspace.workspaceFile || vscode.workspace.workspaceFile.fsPath===oldHome);
    await vscode.commands.executeCommand('setContext','meshlitStudioHome',home);
    let ready=false;
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
        if(choice) {await context.globalState.update('filter-output',choice.enabled);resetChats();status();}
    }
    async function select(forced) {
        if(active) throw Error("Stop the current reply before switching models");
        const selection = forced || await vscode.window.showQuickPick([
            ...['Bundled offline model','Import local GGUF…','Ollama','LM Studio','Other OpenAI-compatible server','Remove saved model…'].map(label=>({label})),
            ...saved().map(route=>({label:route.model,description:route.base||route.file,route}))
        ], {title:'Models & providers'});
        const choice=selection?.label;
        if (!choice) return;
        if (choice==='Remove saved model…') {
            const item=selection.route ? selection : await vscode.window.showQuickPick(saved().map(route=>({label:route.model,description:route.base||route.file,route})),{title:'Remove registration (model files stay on disk)'});
            if(!item)return;
            const retained=saved().filter(r=>r.file!==item.route.file || r.base!==item.route.base || r.model!==item.route.model);
            await context.globalState.update('model-catalog',retained);
            if(item.route.base&&!retained.some(r=>r.base===item.route.base))await context.secrets.delete('token:'+item.route.base);
            if(JSON.stringify(settings())===JSON.stringify(item.route))await context.globalState.update('route',{kind:'local',model:'meshlit-local'});
        } else if(selection.route) {
            await context.globalState.update('route',selection.route);
        } else if(choice==='Import local GGUF…'||choice==='Discover models') {
            let route;
            if(choice==='Discover models'){
                route=await require('./hub.cjs').discover(vscode,require('node:path').join(context.globalStorageUri.fsPath,'models'));
                if(!route)return;
            } else {
            const files=await vscode.window.showOpenDialog({canSelectMany:false,filters:{'GGUF models':['gguf']},openLabel:'Register local model'});
            if(!files?.length)return;
            route=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Verifying GGUF'},()=>imported(files[0].fsPath));
            }
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
            const route={kind:choice,base,model};
            const others=saved().filter(r=>r.base!==base||r.model!==model);
            if(others.length>=128)throw Error('Remove a saved model first');
            if (token) await context.secrets.store(key,token); else await context.secrets.delete(key);
            await context.globalState.update('model-catalog',[...others,route]);
            await context.globalState.update('route',route);
        }
        active?.abort();
        local.stop();
        resetChats();
        status();
    }
    async function cpu() {
        if (active||changing) throw Error('Finish the current model operation first');
        const max = Math.min(64, require('node:os').availableParallelism());
        const text = await vscode.window.showInputBox({title:'Local CPU threads',prompt:'0 selects automatic. Maximum on this host: '+max,
            value:String(context.globalState.get('threads',0)),validateInput:v=>/^\d+$/.test(v) && Number(v)<=max ? undefined : 'Enter an integer from 0 to '+max});
        if (text !== undefined) { await context.globalState.update('threads', Number(text)); local.stop(); management.refresh(); }
    }
    let history = [], changing=false, currentThread;
    const threads=new Map();
    function threadList(){
        const items=Array.from(threads,([id,t])=>({id,title:t.title})).reverse();
        panel?.webview.postMessage({type:'threads',items,current:currentThread});
    }
    function resetChats(){
        history=[];threads.clear();currentThread=undefined;
        panel?.webview.postMessage({type:'clear'});threadList();
    }
    function recordChat(messages){
        if(!currentThread)currentThread=crypto.randomUUID();
        const title=threads.get(currentThread)?.title||messages[0].content.slice(0,72);
        threads.set(currentThread,{title,messages});
        if(threads.size>12)threads.delete(threads.keys().next().value);
        threadList();
    }
    const packaged={kind:'local',model:'meshlit-local'};
    const manifest=JSON.parse(require('node:fs').readFileSync(require('node:path').join(root,'inference','model.json'),'utf8'));
    const modelId=r=>crypto.createHash('sha256').update(JSON.stringify([r.kind,r.base||'',r.file||'',r.model])).digest('hex');
    const routes=()=>[packaged,...saved()];
    function dashboardState(){
        const selected=settings(), rows=routes().map(r=>({
            id:modelId(r),name:r.kind==='local'?manifest.filename:r.model,bundled:r.kind==='local',
            selected:modelId(r)===modelId(selected),local:!r.base,
            detail:r.base?r.kind+' · '+r.base:r.kind==='local'?'Bundled GGUF':((r.bytes||0)/1024**3).toFixed(2)+' GiB · '+r.file
        }));
        return {library:rows.filter(r=>r.local),providers:rows.filter(r=>!r.local),isLocal:!selected.base,
            running:!!local.route,server:local.route?'Running · '+local.route.base:local.pending?'Loading local model…':'Stopped'};
    }
    async function action(op,id){
        if(op==='chat')return chat();
        if(op==='pair')return pair();
        if(active||changing)throw Error('Finish the current model operation first');
        changing=true;
        try{
            if(op==='connect'){
                const label=await vscode.window.showQuickPick(['Ollama','LM Studio','Other OpenAI-compatible server'],{title:'Connect a model server'});
                if(label)await select({label});
            }else if(op==='import'||op==='discover')await select({label:op==='import'?'Import local GGUF…':'Discover models'});
            else if(op==='select'||op==='remove'){
                const route=routes().find(r=>modelId(r)===id);
                if(!route)throw Error('Model registration has changed');
                if(op==='select')await select({label:route.model,route});
                else if(route.kind!=='local')await select({label:'Remove saved model…',route});
            }else if(op==='load'){
                const r=settings();if(r.base)throw Error('Select a local GGUF first');
                await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Loading '+(r.kind==='local'?manifest.filename:r.model)},()=>local.start(AbortSignal.timeout(180000),context.globalState.get('threads',0),r.kind==='import'?r:undefined));
            }else if(op==='unload')local.stop();
            else if(op==='endpoint'&&local.route)await vscode.env.clipboard.writeText(local.route.base);
            else if(op==='key'&&local.route&&await vscode.window.showInformationMessage('Copy the local server API key to the system clipboard?',{modal:true},'Copy key'))await vscode.env.clipboard.writeText(local.route.token);
        }finally{changing=false;}
    }
    const management=require('./management.cjs').dashboard(vscode,dashboardState,action);
    context.subscriptions.push({dispose:management.dispose});
    require('./legacy/ssh-dashboard.cjs').register(context,vscode);
    require('./legacy/memory.cjs').register(context);
    const dashboards=require('./operations.cjs').operations(vscode,context,root,{
        models:management.open,server:()=>dashboardState().server,cpu,guard:guardrails,pair,control,ide,chat
    });
    function status() { const current=settings(); panel?.webview.postMessage({type:'status',text:(current.kind==='local'?'On this device · Qwen2.5 1.5B':current.kind+' · '+current.model)+' · Reply filter '+(context.globalState.get('filter-output',true)?'on':'off')}); }
    async function generate(prompt) {
        if (typeof prompt !== 'string' || !prompt.trim() || prompt.length>16000) throw Error('Enter a prompt of up to 16,000 characters');
        if (active||changing) throw Error('A model operation is already running');
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
            recordChat(history);
            return output;
        } finally { if(active===controller) active=undefined; management.refresh(); }
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
        panel.onDidDispose(()=>{active?.abort();panel=undefined;ready=false;});
        panel.webview.onDidReceiveMessage(async msg=>{
            if(!msg || typeof msg!=='object') return;
            try {
                if(msg.op==='ready') {ready=true;status();if(history.length)panel?.webview.postMessage({type:'restore',messages:history});threadList();}
                if(msg.op==='new'&&!active){history=[];currentThread=undefined;panel?.webview.postMessage({type:'clear'});threadList();}
                if(msg.op==='thread'&&!active&&threads.has(msg.id)){currentThread=msg.id;history=threads.get(msg.id).messages;panel?.webview.postMessage({type:'restore',messages:history});threadList();}
                if(['tools','management','settings'].includes(msg.op))dashboards.open(msg.op);
                if(msg.op==='models') await management.open();
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
    for(const [cmd,fn] of Object.entries({chat,management:()=>dashboards.open('management'),tools:()=>dashboards.open('tools'),settings:()=>dashboards.open('settings'),models:management.open,ide,cpu,pair,guardrails,control})) {
        context.subscriptions.push(vscode.commands.registerCommand('meshlit.studio.'+cmd,async()=>{try {return await fn();} catch(err){vscode.window.showErrorMessage(err.message);}}));
    }
    context.subscriptions.push({dispose:()=>{active?.abort();local.stop();}});
    if (home) {
        // The dedicated workspace has no project contents. Its layout choices are window-scoped.
        for(const cmd of ['workbench.action.closePanel','workbench.action.closeAuxiliaryBar','workbench.action.closeSidebar']){
            await vscode.commands.executeCommand(cmd);
        }
        await vscode.commands.executeCommand('workbench.action.exitZenMode');
        await vscode.commands.executeCommand('workbench.action.toggleZenMode');
        chat();
    }
    return {generate, unload:()=>local.stop(), homeStatus:()=>({home,ready,visible:!!panel?.visible,folders:vscode.workspace.workspaceFolders?.length||0})};
}
exports.activate=activate;
exports.deactivate=()=>{active?.abort();local?.stop();};
