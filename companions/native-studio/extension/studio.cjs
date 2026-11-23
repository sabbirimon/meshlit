'use strict';
const vscode = require('vscode');
const crypto = require('node:crypto');
const adapters = require('./model-adapters.cjs');
const path = require('node:path');
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
            ...['Bundled offline model','Import model file…','Import model folder…','Ollama','LM Studio','Other OpenAI-compatible server','Remove saved model…'].map(label=>({label})),
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
            if(modelId(settings())===modelId(item.route))await context.globalState.update('route',{kind:'local',model:'meshlit-local'});
        } else if(selection.route) {
            let route=selection.route;
            if(route.kind==='asset'){
                const info=await adapters.inspect(route.file);
                if(!info.adapters.length)throw Error(info.label+' requires '+info.runtime+'. Connect a provider if the model is already served.');
                const adapter=info.adapters[0];
                const confirm=await vscode.window.showInformationMessage('Run this trusted model package with '+adapter+' on this device? The runtime opens files from '+route.file+'.',{modal:true},'Choose runtime');
                if(!confirm)return;
                const executables=await vscode.window.showOpenDialog({canSelectFiles:true,canSelectFolders:false,canSelectMany:false,openLabel:'Choose Python from the installed '+adapter+' environment'});
                if(!executables?.length)return;
                const python=executables[0].fsPath;
                await adapters.worker(python,adapter,route.file);
                route={...route,adapter,python};
            }
            await context.globalState.update('route',route);
        } else if(['Import model file…','Import model folder…','Discover models'].includes(choice)) {
            let route;
            if(choice==='Discover models'){
                route=await require('./hub.cjs').discover(vscode,require('node:path').join(context.globalStorageUri.fsPath,'models'));
                if(!route)return;
            } else {
                const folder=choice==='Import model folder…';
                const files=await vscode.window.showOpenDialog({canSelectMany:false,canSelectFolders:folder,canSelectFiles:!folder,openLabel:'Register model'});
                if(!files?.length)return;
                let info=await adapters.inspect(files[0].fsPath);
                const hints=adapters.binaryHints[info.format];
                if(hints){
                    const choices=[{label:'Keep unidentified'},...hints.map(id=>({label:adapters.formats[id].label,id}))];
                    const declared=await vscode.window.showQuickPick(choices,{title:'Choose the known exporter, or keep this file unidentified'});
                    if(!declared)return;
                    info=adapters.declareFormat(info,declared.id);
                }
                route=info.adapters.includes('llama.cpp')
                    ? await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Verifying GGUF'},()=>imported(info.file))
                    : {kind:'asset',model:path.basename(info.file),file:info.file,info};
            }
            const others=saved().filter(r=>r.file!==route.file);
            if(others.length>=128)throw Error('Remove a saved model first');
            await context.globalState.update('model-catalog',[...others,route]);
            if(route.kind==='asset'){await management.refresh();return;}
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
        pendingFile=undefined;sendUI('attachment',{name:''});history=[];threads.clear();currentThread=undefined;
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
            canSelect:r.kind!=='asset'||!!r.info?.adapters?.length,
            detail:r.kind==='asset'?[r.info.label,...r.info.quantization,r.info.evidence,r.info.adapters.length?'Requires local '+r.info.adapters.join(' or '):'External runtime required: '+r.info.runtime,r.file].join(' · '):r.base?r.kind+' · '+r.base:r.kind==='local'?'Bundled GGUF':((r.bytes||0)/1024**3).toFixed(2)+' GiB · '+r.file
        }));
        return {library:rows.filter(r=>r.local),providers:rows.filter(r=>!r.local),isLocal:['local','import'].includes(selected.kind),
            running:!!local.route,server:local.route?'Running · '+local.route.base:local.pending?'Loading local model…':'Stopped'};
    }
    async function action(op,id){
        if(op==='chat')return go('chat');
        if(op==='back')return back();
        if(op==='pair')return pair();
        if(active||changing)throw Error('Finish the current model operation first');
        changing=true;
        try{
            if(op==='connect'){
                const label=await vscode.window.showQuickPick(['Ollama','LM Studio','Other OpenAI-compatible server'],{title:'Connect a model server'});
                if(label)await select({label});
            }else if(['import','folder','discover'].includes(op))await select({label:{import:'Import model file…',folder:'Import model folder…',discover:'Discover models'}[op]});
            else if(op==='select'||op==='remove'){
                const route=routes().find(r=>modelId(r)===id);
                if(!route)throw Error('Model registration has changed');
                if(op==='select')await select({label:route.model,route});
                else if(route.kind!=='local')await select({label:'Remove saved model…',route});
            }else if(op==='load'){
                const r=settings();if(!['local','import'].includes(r.kind))throw Error('Select a local GGUF first');
                await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Loading '+(r.kind==='local'?manifest.filename:r.model)},()=>local.start(AbortSignal.timeout(180000),context.globalState.get('threads',0),r.kind==='import'?r:undefined));
            }else if(op==='unload')local.stop();
            else if(op==='endpoint'&&local.route)await vscode.env.clipboard.writeText(local.route.base);
            else if(op==='key'&&local.route&&await vscode.window.showInformationMessage('Copy the local server API key to the system clipboard?',{modal:true},'Copy key'))await vscode.env.clipboard.writeText(local.route.token);
        }finally{changing=false;}
    }
    const navigation=['chat'];
    function go(target,remember=true){
        if(remember&&navigation.at(-1)!==target){navigation.push(target);if(navigation.length>40)navigation.shift();}
        if(target==='chat'){const result=chat();sendUI('navigation',{back:navigation.length>1});return result;}
        if(target==='models')return management.open();
        return dashboards.open(target);
    }
    function back(){if(navigation.length>1)navigation.pop();return go(navigation.at(-1),false);}
    let pendingFile,speech;
    context.subscriptions.push({dispose:()=>speech?.abort()});
    const sendUI=(type,values)=>panel?.webview.postMessage({type,...values});
    const tools=require('./chat-tools.cjs');
    const permissionLabels={internet:'Internet search',microphone:'Voice input',files:'Read selected attachments',share:'Send attachments to providers',speech:'Read replies aloud'};
    const permissions=()=>context.globalState.get('chat-permissions',{});
    const allowed=id=>permissions()[id]!==false;
    function requirePermission(id){if(!allowed(id))throw Error(permissionLabels[id]+' is disabled in Studio Permissions.');}
    async function editPermission(id){
        const selected=await vscode.window.showQuickPick([{label:'Allow',value:true},{label:'Block',value:false}],{title:permissionLabels[id]+' · currently '+(allowed(id)?'allowed':'blocked')});
        if(!selected)return;
        await context.globalState.update('chat-permissions',{...permissions(),[id]:selected.value});
        if(!selected.value&&id==='files'){pendingFile=undefined;sendUI('attachment',{name:''});}
        if(!selected.value&&id==='speech')speech?.abort();
    }
    async function chooseAttachment(){
        requirePermission('files');
        const files=await vscode.window.showOpenDialog({canSelectMany:false,openLabel:'Attach to the next message'});
        if(!files?.length)return;
        pendingFile=await tools.attachment(files[0].fsPath);
        go('chat');sendUI('attachment',{name:pendingFile.name});
    }
    async function webSearch(){
        requirePermission('internet');
        const provider=await vscode.window.showQuickPick(['Wikipedia results in chat','Web search in browser'],{title:'Internet tools'});
        if(!provider)return;
        if(provider==='Web search in browser'){
            const q=await vscode.window.showInputBox({title:'Search the web with DuckDuckGo',prompt:'Open this query in your default browser',validateInput:v=>v.trim()&&v.length<=300?undefined:'Enter up to 300 characters'});
            if(q)await vscode.env.openExternal(vscode.Uri.parse('https://duckduckgo.com/?q='+encodeURIComponent(q)));
            return;
        }
        const query=await vscode.window.showInputBox({title:'Search Wikipedia',prompt:'Only this search query goes to en.wikipedia.org. Results are added to your draft.',validateInput:v=>v.trim()&&v.length<=300?undefined:'Enter up to 300 characters'});
        if(!query)return;
        const text=await tools.search(query.trim(),AbortSignal.timeout(12000));
        go('chat');sendUI('draft',{text:'Search results for '+query+String.fromCharCode(10)+text});
    }
    async function voice(){
        requirePermission('microphone');
        go('chat');
        if(process.platform!=='win32'){sendUI('focus',{});vscode.window.showInformationMessage('Use system dictation in the message box. On macOS use your configured Dictation shortcut.');return;}
        if(!await vscode.window.showInformationMessage('Use the Windows microphone for one dictation? Review the transcript before sending.',{modal:true},'Start dictation'))return;
        sendUI('notice',{text:'Listening on this device…'});
        try{sendUI('draft',{text:await tools.dictate()});}
        catch(e){sendUI('notice',{text:'Dictation unavailable. Focus the message box and press Windows + H. '+e.message.slice(0,180)});}
    }
    const management=require('./management.cjs').dashboard(vscode,dashboardState,action);
    context.subscriptions.push({dispose:management.dispose});
    require('./legacy/ssh-dashboard.cjs').register(context,vscode);
    require('./legacy/memory.cjs').register(context);
    const dashboards=require('./operations.cjs').operations(vscode,context,root,{
        models:()=>go('models'),go,back,webSearch,voice,permissionLabels,allowed,editPermission,attach:chooseAttachment,server:()=>dashboardState().server,cpu,guard:guardrails,pair,control,ide,chat
    });
    function status() { const current=settings(); panel?.webview.postMessage({type:'status',text:(current.kind==='local'?'On this device · Qwen2.5 1.5B':(current.adapter||current.kind)+' · '+current.model)+' · Reply filter '+(context.globalState.get('filter-output',true)?'on':'off')}); }
    async function generate(prompt,file) {
        if (typeof prompt !== 'string' || !prompt.trim() || prompt.length>16000) throw Error('Enter a prompt of up to 16,000 characters');
        if (active||changing) throw Error('A model operation is already running');
        const controller = new AbortController();
        active=controller;
        const signal=AbortSignal.any([controller.signal,AbortSignal.timeout(240000)]);
        try {
            const selected=settings();
            const messages=[...history,{role:'user',content:prompt}];
            const started=performance.now();
            const progress=text=>sendUI('progress',{text});
            let answer,count,rate;
            const last=messages.length-1;
            if(file){
                if(file.mime){
                    if(!selected.base)throw Error('Choose a vision-capable provider in Models & providers before sending an image.');
                    messages[last]={role:'user',content:[{type:'text',text:prompt},{type:'image_url',image_url:{url:file.url}}]};
                }else messages[last]={role:'user',content:prompt+'\n\nAttached file: '+file.name+'\n'+file.text};
            }
            if(selected.kind==='asset'){
                progress('Loading local runtime and generating…');
                answer=(await adapters.worker(selected.python,selected.adapter,selected.file,messages,signal)).text;
            }else{
                const route = ['local','import'].includes(selected.kind) ? await local.start(signal, context.globalState.get('threads',0), selected.kind==='import'?selected:undefined,progress) : {...selected,token:await context.secrets.get('token:'+selected.base)};
                progress('Waiting for '+(selected.base?new URL(route.base).host:'the local model')+' to reply…');
                const result=await request(route.base,'/chat/completions',route.token,{model:route.model,messages,max_tokens:128,temperature:0},signal);
                count=result.usage?.completion_tokens;rate=result.timings?.predicted_per_second;
                answer=result.choices?.[0]?.message?.content;
            }
            if (typeof answer !== 'string' || !answer.trim()) throw Error('Model returned an empty response');
            signal.throwIfAborted();
            const output=displayOutput(answer,context.globalState.get('filter-output',true));
            const seconds=(performance.now()-started)/1000;
            if(Number.isFinite(rate)&&rate>0)sendUI('metrics',{text:rate.toFixed(1)+' tokens/s · decode'});
            else if(Number.isSafeInteger(count)&&count>0)sendUI('metrics',{text:count+' tokens · '+(count/seconds).toFixed(1)+' tokens/s including startup'});
            else sendUI('metrics',{text:seconds.toFixed(1)+' s · runtime did not report token count'});
            history=[...history,{role:'user',content:prompt+(file?' [Attached: '+file.name+']':'')},{role:'assistant',content:output}].slice(-8);
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
                if(msg.op==='ready') {ready=true;sendUI('navigation',{back:navigation.length>1});status();if(history.length)panel?.webview.postMessage({type:'restore',messages:history});threadList();}
                if(msg.op==='new'&&!active){pendingFile=undefined;sendUI('attachment',{name:''});history=[];currentThread=undefined;panel?.webview.postMessage({type:'clear'});threadList();}
                if(msg.op==='thread'&&!active&&threads.has(msg.id)){pendingFile=undefined;sendUI('attachment',{name:''});currentThread=msg.id;history=threads.get(msg.id).messages;panel?.webview.postMessage({type:'restore',messages:history});threadList();}
                if(msg.op==='back')back();
                if(['tools','management','settings','permissions'].includes(msg.op))go(msg.op);
                if(msg.op==='web'&&!active)await webSearch();
                if(msg.op==='voice'&&!active)await voice();
                if(msg.op==='attach'&&!active)await chooseAttachment();
                if(msg.op==='dropAttachment'){pendingFile=undefined;sendUI('attachment',{name:''});}
                if(msg.op==='speak'&&typeof msg.text==='string'){
                    requirePermission('speech');speech?.abort();const controller=new AbortController();speech=controller;
                    try{await tools.readAloud(msg.text,controller.signal);}catch(e){if(!controller.signal.aborted)vscode.window.showErrorMessage('Read-aloud unavailable. '+e.message.slice(0,180));}
                    finally{if(speech===controller)speech=undefined;}
                }
                if(msg.op==='silence')speech?.abort();
                if(msg.op==='copy'&&typeof msg.text==='string')await vscode.env.clipboard.writeText(msg.text.slice(0,128000));
                if(msg.op==='export'&&typeof msg.text==='string'){
                    const dest=await vscode.window.showSaveDialog({saveLabel:'Save reply',filters:{Text:['txt']}});
                    if(dest)await vscode.workspace.fs.writeFile(dest,Buffer.from(msg.text.slice(0,128000)));
                }
                if(msg.op==='models')go('models');
                if(msg.op==='pair') await pair();
                if(msg.op==='control') await control();
                if(msg.op==='guard' && !active) await guardrails();
                if(msg.op==='ide') await ide();
                if(msg.op==='cpu' && !active) await cpu();
                if(msg.op==='stop') active?.abort();
                if(msg.op==='send' && typeof msg.text==='string' && /^\/control(?:\s|$)/i.test(msg.text)){await control(msg.text.replace(/^\/control\s*/i,''));panel?.webview.postMessage({type:'notice',text:'App control review closed.'});return;}
                if(msg.op==='send' && typeof msg.text==='string' && /^\/pair(?:\s|$)/i.test(msg.text)){await pair(msg.text.replace(/^\/pair\s*/i,'')||undefined);panel?.webview.postMessage({type:'notice',text:'Pairing review closed.'});return;}
                if(msg.op==='send') {
                    const file=pendingFile,route=settings();
                    if(file)requirePermission('files');
                    if(file&&route.base)requirePermission('share');
                    if(file&&route.base&&!await vscode.window.showInformationMessage('Send '+file.name+' to '+new URL(route.base).host+' with this message?',{modal:true},'Send attachment'))throw Error('Attachment was not sent.');
                    const answer=await generate(msg.text,file);
                    pendingFile=undefined;sendUI('attachment',{name:''});sendUI('answer',{text:answer});
                }
            } catch(err) { panel?.webview.postMessage({type:'error',text:err.name==='AbortError'?'Generation stopped.':err.name==='TimeoutError'?'The model timed out. Check Models & providers, then retry.':err.message}); }
        });
    }
    for(const [cmd,fn] of Object.entries({chat,management:()=>go('management'),tools:()=>go('tools'),settings:()=>go('settings'),models:()=>go('models'),ide,cpu,pair,guardrails,control})) {
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
