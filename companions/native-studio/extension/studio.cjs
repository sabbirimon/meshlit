'use strict';
const vscode = require('vscode');
const crypto = require('node:crypto');
const adapters = require('./model-adapters.cjs');
const path = require('node:path');
const {page} = require('./welcome.cjs');
const {LocalServer, endpoint, request} = require('./runtime.cjs');
let local, active, panel;

async function activate(context) {
    const brand = require('./legacy/brand.cjs');
    const root = process.env.MESHLIT_SUITE_ROOT;
    if (!root) throw Error('Launch the extracted Meshlit Studio application');
    local = new LocalServer(root, status, ()=>context.globalState.get('local-server-port',0));
    const empty = !vscode.workspace.workspaceFolders?.length;
    const oldHome = require('node:path').join(require('node:os').homedir(),'.meshlit','native-studio','Meshlit Studio.code-workspace');
    const home = empty && (!vscode.workspace.workspaceFile || vscode.workspace.workspaceFile.fsPath===oldHome);
    await vscode.commands.executeCommand('setContext','meshlitStudioHome',home);
    let ready=false;
    const settings = () => context.globalState.get('route', {kind:'local', model:'meshlit-local'});
    const serverEvents=[];
    function serverLog(message){serverEvents.push(new Date().toLocaleTimeString()+' · '+message);if(serverEvents.length>50)serverEvents.shift();management.refresh();}
    const pair=require('./pairing.cjs').register(vscode,context,{
        root,threads:()=>context.globalState.get('threads',0),log:serverLog,
        models:()=>routes().filter(r=>['local','import'].includes(r.kind)).map(route=>({route,id:modelId(route),name:route.kind==='local'?manifest.filename:route.model})),
        select:async route=>{
            if(active||changing)throw Error('Finish the current model operation first');
            const others=saved().filter(r=>modelId(r)!==modelId(route));
            if(others.length>=128)throw Error('Remove a saved model first');
            await context.globalState.update('model-catalog',[...others,route]);
            await select({label:route.model,route});go('chat');
        }
    });
    const control = require('./control.cjs').register(vscode,context,root);
    const {imported, displayOutput} = require('./catalog.cjs');
    const saved = () => context.globalState.get('model-catalog', []);
    const firstRun=()=>!context.globalState.get('welcome-complete',false)&&!context.globalState.get('route')&&!saved().length;
    const discovery = require('./discovery.cjs');
    let suggestion;
    function updateWelcome(visible) {
        const capacity = discovery.budget();
        suggestion = visible ? discovery.recommended(starters,capacity) : undefined;
        const host = require('node:os').availableParallelism()+' logical CPUs · estimated CPU weight budget '+discovery.size(capacity);
        sendUI('setup',{visible,host,recommendation:suggestion ?
            {name:suggestion.label||suggestion.filename,detail:discovery.size(suggestion.sizeBytes)+' · '+(suggestion.filename===manifest.filename?'Included for offline chat':'Download from '+suggestion.publisher)} :
            {name:'Connect a provider',detail:'Free memory is low for the starter models.'}});
    }
    function advancedSetup(){sendUI('advancedSetup',{login:context.globalState.get('start-at-login',false)});}
    async function finishWelcome(){
        await context.globalState.update('welcome-complete',true);updateWelcome(false);
    }
    async function guardrails() {
        const enabled = context.globalState.get('filter-output',true);
        const choice = await vscode.window.showQuickPick([
            {label:'Filter replies',detail:'Hide email addresses and recognized API token patterns',enabled:true},
            {label:'Show original replies',detail:'Display the text returned by the model',enabled:false}
        ],{title:'Output guardrails · currently '+(enabled?'on':'off')});
        if(choice) {await context.globalState.update('filter-output',choice.enabled);resetChats();status();}
    }
    const hfAccount=require('./hugging-face.cjs').account(vscode,context);
    async function browseModels(){
        const hub=require('./hub.cjs');
        const state=dashboardState();
        const inventory=[...state.library,...state.providers].map(row=>({...row,local:true,summary:row.detail}));
        const lmRoute=[settings(),...routes()].find(r=>r.kind==='LM Studio' && r.base);
        const lmOptions=lmRoute?{baseUrl:lmRoute.base.replace(/^http/,'ws').replace(/\/v1\/?$/,''),apiToken:await context.secrets.get('token:'+lmRoute.base)}:{};
        const item=await discovery.chooseFile(vscode,(url,signal,page)=>hub.metadata(url,signal,hfAccount.request,page),hub.files,brand,inventory,hfAccount,lmOptions);
        if(!item)return;
        if(item.action==='lm-download'){try{return await require('./lm-catalog.cjs').install(vscode,item);}finally{await item.dispose();}}
        if(item.action==='more')return select();
        if(item.action==='vendor')return select({label:'Import from LM Studio or Bionic…'});
        if(item.action==='local'){
            const route=routes().find(r=>modelId(r)===item.id);
            if(!route)throw Error('This registration changed. Reopen the model picker.');
            if(modelId(route)===modelId(settings()))return true;
            return select({label:route.model,route});
        }
        return select({label:'Import from Hugging Face…',item});
    }
    async function select(forced) {
        if(active) throw Error("Stop the current reply before switching models");
        const selection = forced || await vscode.window.showQuickPick([
            ...['Bundled offline model','Import from Hugging Face…','Import from LM Studio or Bionic…','Import model file…','Import model folder…','Ollama','LM Studio','Bionic','Other OpenAI-compatible server','Remove saved model…'].map(label=>({label})),
            ...saved().map(route=>({label:route.model,description:route.base||route.file,route}))
        ], {title:'Models & providers'});
        const choice=selection?.label;
        if (!choice) return;
        if (choice==='Remove saved model…') {
            const item=selection.route ? selection : await vscode.window.showQuickPick(saved().map(route=>({label:route.model,description:route.base||route.file,route})),{title:'Remove registration (model files stay on disk)'});
            if(!item)return;
            const retained=saved().filter(r=>modelId(r)!==modelId(item.route));
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
        } else if(['Import model file…','Import model folder…','Import from LM Studio or Bionic…','Import from Hugging Face…'].includes(choice)) {
            let route;
            if(choice==='Import from Hugging Face…'){
                const hub=require('./hub.cjs'),dir=path.join(context.globalStorageUri.fsPath,'models');
                if(!forced?.item)return browseModels();
                route=await hub.install(vscode,dir,forced.item,hfAccount.request);
                if(!route)return;
            } else if(choice==='Import from LM Studio or Bionic…'){
                const file=await require('./catalog.cjs').pickLibraryFile(vscode);
                if(!file)return;
                route=await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Verifying local library model'},()=>imported(file));
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
            const input = await vscode.window.showInputBox({title:'Model server base URL',value:choice === 'Ollama'?'http://127.0.0.1:11434/v1':choice === 'LM Studio'?'http://127.0.0.1:1234/v1':choice==='Bionic'?'http://127.0.0.1:':'https://',ignoreFocusOut:true});
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
        return true;
    }
    async function cpu() {
        if (active||changing) throw Error('Finish the current model operation first');
        const max = Math.min(64, require('node:os').availableParallelism());
        const text = await vscode.window.showInputBox({title:'Local CPU threads',prompt:'0 selects automatic. Maximum on this host: '+max,
            value:String(context.globalState.get('threads',0)),validateInput:v=>/^\d+$/.test(v) && Number(v)<=max ? undefined : 'Enter an integer from 0 to '+max});
        if (text !== undefined) { await context.globalState.update('threads', Number(text)); local.stop(); management.refresh(); }
    }
    let history = [], changing=false, currentThread,idleTimer,automaticReads=false;
    function scheduleUnload(){
        clearTimeout(idleTimer);
        const minutes=context.globalState.get('idle-minutes',0);
        if(minutes>0&&local.route)idleTimer=setTimeout(()=>{if(!active&&!changing){local.stop();serverLog('Unloaded after '+minutes+' idle minutes');}else scheduleUnload();},minutes*60000);
    }
    async function serverSettings(settingName){
        const key=settingName||await vscode.window.showQuickPick(['On-demand loading','Idle unload','Start at login'],{title:'Local server settings'});
        if(key==='Server port'){
            const port=await vscode.window.showInputBox({title:'Local server port',prompt:'Use 0 for an automatic port. Takes effect at next load.',value:String(context.globalState.get('local-server-port',0)),validateInput:x=>/^\d{1,5}$/.test(x)&&(Number(x)===0||Number(x)>=1024&&Number(x)<=65535)?undefined:'Use 0 or a port from 1024 through 65535'});
            if(port!==undefined)await context.globalState.update('local-server-port',Number(port));
        }else if(key==='Idle unload'){
            const input=await vscode.window.showInputBox({title:'Minutes before unloading (0 keeps the model loaded)',value:String(context.globalState.get('idle-minutes',0)),validateInput:v=>/^\d+$/.test(v)&&Number(v)<=1440?undefined:'Enter 0 to 1440 minutes'});
            if(input!==undefined){await context.globalState.update('idle-minutes',Number(input));scheduleUnload();}
        }else if(key){
            const setting=key==='Start at login'?'start-at-login':'on-demand';
            const enabled=await vscode.window.showQuickPick(['Enable','Disable'],{title:key+' · currently '+(context.globalState.get(setting,key==='On-demand loading')?'on':'off')});
            if(enabled){if(key==='Start at login')await require('./login-service.cjs').setLogin(root,enabled==='Enable');await context.globalState.update(setting,enabled==='Enable');}
        }
    }
    const threads=new Map();
    let diskHistory;
    const archive=()=>diskHistory ||= new (require('./chat-history.cjs').ChatHistory)(context.globalStorageUri.fsPath,context.secrets);
    context.subscriptions.push({dispose:()=>diskHistory?.close()});
    const historyScope=()=>modelId(settings())+':'+Number(context.globalState.get('filter-output',true));
    async function loadRecents(){
        const scope=historyScope(),saved=await archive().recent(scope);
        if(scope!==historyScope())return;
        for(const [id,row] of saved)if(!threads.has(id))threads.set(id,row);
        threadList();
    }
    async function exportConversation(){
        if(active)throw Error('Finish the current reply before exporting.');
        const choices=require('./chat-export.cjs').exportOptions(history);
        if(!choices.length){vscode.window.showInformationMessage('Open a conversation with messages to export.');return;}
        const picked=await vscode.window.showQuickPick(choices,{title:'Export current chat',placeHolder:'Choose what to save in a readable file'});
        if(!picked)return;
        const dest=await vscode.window.showSaveDialog({saveLabel:'Save chat export',filters:{[picked.label]:[picked.extension]}});
        if(dest)await vscode.workspace.fs.writeFile(dest,Buffer.from(picked.text,'utf8'));
    }
    async function historyOptions(){
        if(active)throw Error('Finish the current reply before changing chat history.');
        const store=archive();
        const selection=await vscode.window.showQuickPick([
            store.enabled()?'Pause saving on this device':'Save future chats on this device',
            ...(threads.size?['Find a recent chat']:[]),
            ...(history.length?['Branch from a reply','Export current chat']:[]),
            ...(currentThread&&threads.get(currentThread)?.version?['Delete this saved chat']:[])
        ],{title:'Local chat history',placeHolder:'Message text is encrypted on this device. Each model has its own history.'});
        if(!selection)return;
        if(selection==='Export current chat')return exportConversation();
        if(selection==='Find a recent chat'){
            const picked=await vscode.window.showQuickPick([...threads].reverse().map(([id,row])=>({id,label:row.title,detail:row.messages.at(-1)?.content.slice(0,160)})),{title:'Find a recent chat',matchOnDetail:true});
            if(picked&&!active&&threads.has(picked.id)){currentThread=picked.id;history=threads.get(picked.id).messages;showThread();}
            return;
        }
        if(selection==='Branch from a reply'){
            const parent=currentThread,scope=historyScope();
            const pick=await vscode.window.showQuickPick(history.map((m,index)=>({label:m.content.slice(0,120),index,role:m.role})).filter(m=>m.role==='assistant'),{title:'Start a new chat after this reply'});
            if(!pick||active||parent!==currentThread||scope!==historyScope())return;
            history=history.slice(0,pick.index+1);currentThread=crypto.randomUUID();
            threads.set(currentThread,{title:'Branch · '+(threads.get(parent)?.title||'Chat'),messages:history});
            await recordChat(history);showThread();return;
        }
        if(selection.startsWith('Delete')){
            const id=currentThread;
            if(!await vscode.window.showWarningMessage('Delete this saved chat from this device?',{modal:true},'Delete'))return;
            await store.forget(id);threads.delete(id);currentThread=undefined;history=[];sendUI('clear',{});threadList();return;
        }
        store.enable(selection.startsWith('Save'));
        if(store.enabled())await loadRecents();else resetChats();
        vscode.window.showInformationMessage(store.enabled()?'Future chats will be saved on this device.':'Saving paused. Resume saving to reopen previous chats.');
    }
    const replyIds=new Set();
    const savedVotes=()=>context.globalState.get('reply-votes',{});
    async function rateReply(id,value){
        if(!replyIds.has(id)||!['up','down'].includes(value))return;
        const votes=savedVotes(),next={...votes};
        delete next[id];
        if(votes[id]!==value)next[id]=value;
        const retained=Object.fromEntries(Object.entries(next).slice(-256));
        await context.globalState.update('reply-votes',retained);
        sendUI('rated',{id,vote:retained[id]||''});
    }
    async function replyPreference(){
        const value=await vscode.window.showInputBox({title:'Local reply preference',prompt:'Describe how you like replies, for example “use short steps”. Saved on this device for local chats. Clear the field to forget it.',value:context.globalState.get('local-reply-preference',''),validateInput:text=>text.length>400?'Use up to 400 characters':undefined});
        if(value!==undefined)await context.globalState.update('local-reply-preference',value.trim()||undefined);
    }
    function restoredHistory(){
        const votes=savedVotes();
        return history.map(message=>message.id?{...message,vote:votes[message.id]}:message);
    }
    function showThread(){automaticReads=false;status();
        pendingFile=undefined;sendUI('attachment',{name:''});
        for(const message of history)if(message.id)replyIds.add(message.id);
        sendUI('restore',{messages:restoredHistory()});threadList();
    }
    function threadList(){
        const items=Array.from(threads,([id,t])=>({id,title:t.title})).reverse();
        panel?.webview.postMessage({type:'threads',items,current:currentThread});
    }
    function resetChats(){
        automaticReads=false;pendingFile=undefined;sendUI('attachment',{name:''});history=[];threads.clear();currentThread=undefined;
        panel?.webview.postMessage({type:'clear'});threadList();void loadRecents().catch(e=>vscode.window.showWarningMessage(e.message));
    }
    async function recordChat(messages){
        if(!currentThread)currentThread=crypto.randomUUID();
        const title=threads.get(currentThread)?.title||messages[0].content.slice(0,72);
        const scope=historyScope();
        let version=threads.get(currentThread)?.version||0;
        try{
            const saved=await archive().save(scope,currentThread,version,{title,messages});
            if(saved){currentThread=saved.id;version=saved.version;}
        }catch(err){vscode.window.showWarningMessage('This chat is available in this window. Saving failed: '+err.message);}
        threads.set(currentThread,{title,messages,version});
        if(threads.size>50)threads.delete(threads.keys().next().value);
        threadList();
    }
    const packaged={kind:'local',model:'meshlit-local'};
    const manifest=JSON.parse(require('node:fs').readFileSync(require('node:path').join(root,'inference','model.json'),'utf8'));
    const catalogPath=path.join(root,'inference','studio-models.json');
    const starters=require('node:fs').existsSync(catalogPath)?JSON.parse(require('node:fs').readFileSync(catalogPath,'utf8')).models:[manifest];
    const modelId=r=>crypto.createHash('sha256').update(JSON.stringify([r.kind,r.device||r.base||'',r.remoteModel||r.file||'',r.model])).digest('hex');
    function generationSettings(route=settings()){
        return context.globalState.get('generation:'+modelId(route),{maxTokens:128,temperature:0});
    }
    async function tuneModel(){
        const route=settings(),current=generationSettings(route);
        const raw=await vscode.window.showInputBox({title:'Maximum new tokens · '+route.model,value:String(current.maxTokens),validateInput:v=>/^\d+$/.test(v)&&Number(v)>=1&&Number(v)<=128?undefined:'Choose 1 to 128 tokens'});
        if(raw===undefined)return;
        let temperature=0;
        if(route.kind!=='asset'){
            const value=await vscode.window.showInputBox({title:'Temperature · '+route.model,value:String(current.temperature),validateInput:v=>v.trim()!==''&&Number.isFinite(Number(v))&&Number(v)>=0&&Number(v)<=2?undefined:'Choose a number between 0 and 2'});
            if(value===undefined)return;temperature=Number(value);
        }
        await context.globalState.update('generation:'+modelId(route),{maxTokens:Number(raw),temperature});
    }
    const routes=()=>[packaged,...saved()];
    function dashboardState(){
        const selected=settings(), rows=routes().map(r=>({
            id:modelId(r),name:r.kind==='local'?manifest.filename:r.model,bundled:r.kind==='local',
            selected:modelId(r)===modelId(selected),local:!r.base&&r.kind!=='peer',
            canSelect:r.kind!=='asset'||!!r.info?.adapters?.length,
            detail:r.kind==='peer'?'Shared by '+r.hostName:r.kind==='asset'?[r.info.label,...r.info.quantization,r.info.evidence,r.info.adapters.length?'Requires local '+r.info.adapters.join(' or '):'External runtime required: '+r.info.runtime,r.file].join(' · '):r.base?r.kind+' · '+r.base:r.kind==='local'?'Bundled GGUF':((r.bytes||0)/1024**3).toFixed(2)+' GiB weights · '+(require('node:os').freemem()/1024**3).toFixed(1)+' GiB RAM free now · '+r.file
        }));
        return {selectedName:modelName(selected),port:local.route?new URL(local.route.base).port:context.globalState.get('local-server-port',0),onDemand:context.globalState.get('on-demand',true),idleMinutes:context.globalState.get('idle-minutes',0),events:serverEvents.slice(-12),isLmStudio:selected.kind==='LM Studio',library:rows.filter(r=>r.local),providers:rows.filter(r=>!r.local),isLocal:['local','import'].includes(selected.kind),
            running:!!local.route,server:local.route?'Running · '+local.route.base:local.pending?'Loading local model…':'Stopped'};
    }
    async function action(op,id){
        if(op==='chat')return go('chat');
        if(op==='back')return back();
        if(op==='pair')return pair();
        if(active||changing)throw Error('Finish the current model operation first');
        changing=true;
        try{
            if(op==='serverSettings')await serverSettings();
            else if(['port','onDemand','idle'].includes(op))await serverSettings({port:'Server port',onDemand:'On-demand loading',idle:'Idle unload'}[op]);
            else if(op==='mcp')await vscode.commands.executeCommand('workbench.mcp.listServer');
            else if(op==='tune')await tuneModel();
            else if(op==='lmManage'){
                const route=settings();
                if(route.kind!=='LM Studio')throw Error('Select an LM Studio connection first');
                await require('./lm-studio.cjs').manage(vscode,route,await context.secrets.get('token:'+route.base));
            }
            else if(op==='connect'){
                const label=await vscode.window.showQuickPick(['Ollama','LM Studio','Bionic','Other OpenAI-compatible server'],{title:'Connect a model server'});
                if(label)await select({label});
            }else if(['import','folder','discover','lmLibrary'].includes(op))await select({label:{import:'Import model file…',folder:'Import model folder…',discover:'Import from Hugging Face…',lmLibrary:'Import from LM Studio or Bionic…'}[op]});
            else if(op==='select'||op==='remove'){
                const route=routes().find(r=>modelId(r)===id);
                if(!route)throw Error('Model registration has changed');
                if(op==='select')await select({label:route.model,route});
                else if(route.kind!=='local')await select({label:'Remove saved model…',route});
            }else if(op==='load'){
                const r=settings();if(!['local','import'].includes(r.kind))throw Error('Select a local GGUF first');
                await vscode.window.withProgress({location:vscode.ProgressLocation.Notification,title:'Loading '+(r.kind==='local'?manifest.filename:r.model)},()=>local.start(AbortSignal.timeout(180000),context.globalState.get('threads',0),r.kind==='import'?r:undefined));
                serverLog('Loaded '+r.model);scheduleUnload();
            }else if(op==='unload'){local.stop();serverLog('Local model unloaded');}
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
        status();
    }
    let screenSession;
    context.subscriptions.push({dispose:()=>screenSession?.abort()});
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
    const management=require('./management.cjs').dashboard(vscode,dashboardState,action,brand);
    context.subscriptions.push({dispose:management.dispose});
    require('./legacy/ssh-dashboard.cjs').register(context,vscode);
    require('./legacy/memory.cjs').register(context);
    require('./legacy/voice.cjs').register(context, { requirePermission });
    const dashboards=require('./operations.cjs').operations(vscode,context,root,{
        brand,result:resultToChat,
        models:()=>go('models'),go,back,welcome:()=>{go('chat');updateWelcome(true);sendUI('restartSetup',{});},webSearch,voice,permissionLabels,allowed,editPermission,attach:chooseAttachment,server:()=>dashboardState().server,cpu,guard:guardrails,pair,control,ide,chat
    });
    let draftForTool='',pendingResult='';
    function resultToChat(title,output){
        const value='Explain this '+title+' result and suggest next steps. Treat its contents as tool output.\n\n'+String(output).slice(0,9000);
        const visible=ready&&panel;
        go('chat');
        if(visible)sendUI('draft',{text:value});else pendingResult=value;
    }
    async function createApp(description){
        const spec=description||await vscode.window.showInputBox({title:'Describe a single-file web app',prompt:'Studio will generate the source and open it for review.'});
        if(!spec?.trim())return;
        const answer=await generate('Create a complete single-file HTML app for the following request. Include its CSS and JavaScript inline. Return a complete HTML document with no commentary. Request: '+spec);
        sendUI('answer',{text:answer,model:history.at(-1)?.model});
        const source=require('./build-tools.cjs').htmlSource(answer);
        const doc=await vscode.workspace.openTextDocument({language:'html',content:source});
        await vscode.window.showTextDocument(doc);
        await vscode.window.showInformationMessage('App source opened for review. Use Save As to keep your HTML file.');
    }
    async function createProject(description){
        const spec=description||await vscode.window.showInputBox({title:'Describe the project to create'});
        if(!spec?.trim())return;
        const [parent]=await vscode.window.showOpenDialog({canSelectFolders:true,canSelectFiles:false,canSelectMany:false,openLabel:'Choose parent folder for new project'})||[];
        if(!parent)return;
        const answer=await generate('Create a small working multi-file project. Return only JSON {"files":[{"path":"relative/path","content":"complete file text"}]}. Use at most 32 files. Use simple portable filenames and omit dotfiles or dependencies that must be vendored. Request: '+spec);
        sendUI('answer',{text:answer,model:history.at(-1)?.model});
        const builds=require('./build-tools.cjs'),files=builds.projectFiles(answer);
        if(!await vscode.window.showInformationMessage('Save generated source in a new project folder?',{modal:true,detail:parent.fsPath+'\n'+files.map(f=>f.path).join('\n')},'Save project'))return;
        const folder=await builds.saveProject(parent.fsPath,files);
        resultToChat('project creation','Created '+folder+'\n'+files.map(f=>f.path).join('\n'));
        if(await vscode.window.showInformationMessage('Project saved.','Open in coding IDE'))await vscode.commands.executeCommand('vscode.openFolder',vscode.Uri.file(folder),true);
    }
    const composerTools=require('./composer-tools.cjs');
    const composer=composerTools.composer(vscode,context,{refresh:status,result:resultToChat,arguments:()=>{if(!draftForTool.trim()||draftForTool.startsWith('/tool '))return '{}';try{const parsed=JSON.parse(draftForTool);if(parsed&&!Array.isArray(parsed)&&typeof parsed==='object')return draftForTool;}catch{}return JSON.stringify({text:draftForTool.slice(0,3000)});},
        destinations:dashboards.composerDestinations,dashboard:dashboards.composerAction,agentChat:async()=>{
            if(!(await vscode.commands.getCommands(true)).includes('workbench.action.chat.open'))throw Error('Enable a chat agent in the coding IDE to open agent chat.');
            return vscode.commands.executeCommand('workbench.action.chat.open',draftForTool?{query:draftForTool,isPartialQuery:true}:{});
        },activate:async target=>{
        if(target==='project')return createProject(draftForTool.startsWith('/tool ')?'':draftForTool);
        if(target==='build')return require('./build-tools.cjs').localTask(vscode,'build',resultToChat);
        if(target==='ide')return ide();
        if(target==='app')return createApp(draftForTool.startsWith('/tool ')?'':draftForTool);
        if(target==='ssh')return require('./build-tools.cjs').remoteBuild(vscode,context,{action:'build'},resultToChat);
        if(target==='devices')return pair();
        if(target==='models')return go('models');
        if(target==='files')return chooseAttachment();
        if(target==='internet')return editPermission('internet');
        if(target==='mcp')return vscode.commands.executeCommand('workbench.mcp.listServer');
        if(target==='shirudo')return go('tools');
        if(target==='autopilot'){
            if(automaticReads)automaticReads=false;
            else if(await vscode.window.showWarningMessage('Enable automatic web searches for this chat?',{modal:true,detail:'Prompts starting with “search the web for” can send the query to Wikipedia and include the results in the model request. Other tools keep their own approvals.'},'Enable approved searches'))automaticReads=true;
            status();
        }
    }});
    function modelName(route) {
        return route.kind==='local' ? manifest.id : route.model;
    }
    function status() {
        if(!panel)return;
        const route=settings();
        const native=['local','import'].includes(route.kind);
        const state=native ? local.route?'Loaded · '+local.route.contextSize+' configured context':local.pending?'Loading':'Selected · unloaded'
            :route.kind==='asset'?'Local runtime · loads per request':'Remote · '+(route.hostName || new URL(route.base).host);
        sendUI('status',{text:modelName(route)+' · '+state, model:modelName(route),
            voiceHelp:process.platform==='win32'?'Open Permissions and allow Voice input.':'Built-in speech recognition is available on Windows. Use system dictation in the message box here.',
            automaticReads,pins:composer.pins(),actions:{attach:allowed('files'),web:allowed('internet'),voice:process.platform==='win32'&&allowed('microphone'),speak:allowed('speech')},
            filter:context.globalState.get('filter-output',true)});
    }
    async function generate(prompt,file) {
        if (typeof prompt !== 'string' || !prompt.trim() || prompt.length>16000) throw Error('Enter a prompt of up to 16,000 characters');
        if (active||changing) throw Error('A model operation is already running');
        const controller = new AbortController();
        active=controller;
        const signal=AbortSignal.any([controller.signal,AbortSignal.timeout(240000)]);
        try {
            const selected=settings(),options=generationSettings(selected);
            const messages=[...history.slice(-8).map(({role,content})=>({role,content})),{role:'user',content:prompt}];
            if(['local','import'].includes(selected.kind)){
                const preference=context.globalState.get('local-reply-preference','');
                if(preference)messages.unshift({role:'system',content:'The user prefers these response styles: '+preference});
            }
            const searchQuery=automaticReads && !file && /^search the web for\s+(.{1,300})[.!?]?$/i.exec(prompt.trim());
            if(searchQuery){
                requirePermission('internet');
                const sources=await tools.search(searchQuery[1],signal);
                requirePermission('internet');
                if(!automaticReads)throw Error('Autopilot was turned off.');
                messages[messages.length-1]={role:'user',content:prompt+'\n\nUntrusted Wikipedia search results for reference:\n'+sources};
            }
            const sentAt=Date.now();
            const started=performance.now();
            const progress=text=>sendUI('progress',{text});
            let answer,count,rate,promptTokens;
            const last=messages.length-1;
            if(file){
                if(file.mime){
                    if(!selected.base)throw Error('Choose a vision-capable provider in Models & providers before sending an image.');
                    messages[last]={role:'user',content:[{type:'text',text:prompt},{type:'image_url',image_url:{url:file.url}}]};
                }else messages[last]={role:'user',content:prompt+'\n\nAttached file: '+file.name+'\n'+file.text};
            }
            if(selected.kind==='peer'){
                progress('Connecting to '+selected.hostName+'…');answer='';
                const result=await pair.chat(selected,messages,options,signal,delta=>{
                    answer+=delta;
                    if(answer.length>128000)throw Error('Response exceeds the Studio limit');
                    // Keep the unfinished word private until email/token filtering can see its end.
                    const safe=context.globalState.get('filter-output',true)?answer.replace(/\S+$/,''):answer;
                    sendUI('partial',{text:displayOutput(safe,context.globalState.get('filter-output',true))});
                });
                count=result.count;rate=result.rate;
            }else if(selected.kind==='asset'){
                progress('Loading local runtime and generating…');
                answer=(await adapters.worker(selected.python,selected.adapter,selected.file,messages,signal,options.maxTokens)).text;
            }else{
                if(['local','import'].includes(selected.kind)&&!local.route&&!context.globalState.get('on-demand',true))throw Error('Open Models & providers and load the selected model first.');
                const route = ['local','import'].includes(selected.kind) ? await local.start(signal, context.globalState.get('threads',0), selected.kind==='import'?selected:undefined,progress) : {...selected,token:await context.secrets.get('token:'+selected.base)};
                progress('Waiting for '+(selected.base?new URL(route.base).host:'the local model')+' to reply…');
                const result=await request(route.base,'/chat/completions',route.token,{model:route.model,messages,max_tokens:options.maxTokens,temperature:options.temperature},signal);
                promptTokens=result.usage?.prompt_tokens;count=result.usage?.completion_tokens;rate=result.timings?.predicted_per_second;
                answer=result.choices?.[0]?.message?.content;
            }
            if (typeof answer !== 'string' || !answer.trim()) throw Error('Model returned an empty response');
            signal.throwIfAborted();
            const output=displayOutput(answer,context.globalState.get('filter-output',true));
            const seconds=(performance.now()-started)/1000;
            const usage=[seconds.toFixed(1)+' s elapsed'];
            if(Number.isSafeInteger(promptTokens)&&promptTokens>=0)usage.push(promptTokens+' input tokens');
            if(Number.isSafeInteger(count)&&count>=0)usage.push(count+' output tokens');
            if(Number.isFinite(rate)&&rate>0)usage.push(rate.toFixed(1)+' tokens/s decode');
            else if(Number.isSafeInteger(count)&&count>0&&seconds>0)usage.push((count/seconds).toFixed(1)+' tokens/s overall');
            else usage.push('Token speed unavailable');
            if(['local','import'].includes(selected.kind)&&local.route?.contextSize)usage.push(local.route.contextSize+' configured context');
            const metrics=usage.join(' · ');
            const id=crypto.randomUUID();replyIds.add(id);if(replyIds.size>512)replyIds.delete(replyIds.values().next().value);
            history=[...history,{role:'user',createdAtMs:sentAt,content:prompt+(file?' [Attached: '+file.name+']':'')},{role:'assistant',id,createdAtMs:Date.now(),content:output,model:modelName(selected),metrics}].slice(-128);
            await recordChat(history);
            return output;
        } finally { if(active===controller) active=undefined;scheduleUnload();management.refresh(); }
    }
    async function ide() {
        const selected=await vscode.window.showOpenDialog({canSelectFolders:true,canSelectFiles:false,canSelectMany:false,openLabel:'Open coding workspace'});
        if (selected) await vscode.commands.executeCommand('vscode.openFolder',selected[0],true);
    }
    function chat() {
        if(panel) { panel.reveal(); return; }
        panel=vscode.window.createWebviewPanel('meshlit.chat','Meshlit · General Chat',vscode.ViewColumn.Active,{enableScripts:true,localResourceRoots:[],retainContextWhenHidden:true});
        const nonce=crypto.randomBytes(20).toString('hex');
        panel.iconPath=vscode.Uri.file(brand.file);
        panel.webview.html=page(nonce,firstRun(),brand.markup);
        panel.onDidDispose(()=>{screenSession?.abort();active?.abort();panel=undefined;ready=false;});
        panel.webview.onDidReceiveMessage(async msg=>{
            if(!msg || typeof msg!=='object') return;
            try {
                if(msg.op==='rateReply')await rateReply(msg.id,msg.vote);
                if(msg.op==='replyPreference')await replyPreference();
                if(msg.op==='sidebar'&&typeof msg.compact==='boolean')await context.globalState.update('compact-sidebar',msg.compact);
                if(msg.op==='exportChat')return await exportConversation();
                if(msg.op==='historyOptions')return await historyOptions();
                if(msg.op==='ready') {ready=true;if(pendingResult){sendUI('draft',{text:pendingResult});pendingResult='';}const compact=context.globalState.get('compact-sidebar');if(typeof compact==='boolean')sendUI('sidebar',{compact});updateWelcome(firstRun());sendUI('navigation',{back:navigation.length>1});status();if(history.length)panel?.webview.postMessage({type:'restore',messages:restoredHistory()});threadList();await loadRecents();}
                if(msg.op==='new'&&!active){automaticReads=false;screenSession?.abort();await finishWelcome();pendingFile=undefined;sendUI('attachment',{name:''});history=[];currentThread=undefined;panel?.webview.postMessage({type:'clear'});threadList();}
                if(msg.op==='thread'&&!active&&threads.has(msg.id)){currentThread=msg.id;history=threads.get(msg.id).messages;showThread();}
                if(msg.op==='useSuggestion'){
                    if(active||changing)return;
                    const model=suggestion;
                    if(!model){await action('connect');if(settings().base)await finishWelcome();return;}
                    changing=true;
                    try{
                        const done=model.filename===manifest.filename?await select({label:'Bundled offline model'}):
                            await select({label:'Import from Hugging Face…',item:{name:model.filename,sha:model.sha256,bytes:model.sizeBytes,url:model.url,repo:model.repository}});
                        if(done)advancedSetup();
                    }finally{changing=false;}
                    return;
                }
                if(msg.op==='skipSetup'||msg.op==='startLocal'){if(msg.op==='startLocal'&&settings().kind!=='local')await select({label:'Bundled offline model'});advancedSetup();return;}
                if(msg.op==='finishSetup'){
                    if(typeof msg.login!=='boolean'||typeof msg.server!=='boolean')return;
                    if(msg.login!==context.globalState.get('start-at-login',false)){
                        await require('./login-service.cjs').setLogin(root,msg.login);
                        await context.globalState.update('start-at-login',msg.login);
                    }
                    await finishWelcome();if(msg.server)go('models');return;
                }
                if(msg.op==='startLibrary'){await finishWelcome();go('models');return;}
                if(msg.op==='startProvider'){
                    await action('connect');
                    if(settings().base)await finishWelcome();
                    return;
                }
                if(msg.op==='back')back();
                if(['tools','management','settings','permissions','cloud'].includes(msg.op))go(msg.op);
                if(msg.op==='web'&&!active)await webSearch();
                if(msg.op==='voice'&&!active)await voice();
                if(msg.op==='attach'&&!active)await chooseAttachment();
                if(msg.op==='screenFrame'&&!active){
                    requirePermission('files');
                    screenSession?.abort();
                    const session=new AbortController();screenSession=session;
                    const thread=currentThread, ownerPanel=panel;
                    const frame=await require('./screen-share.cjs').chooseScreenFrame(
                        url=>vscode.env.openExternal(vscode.Uri.parse(url)),AbortSignal.any([session.signal,AbortSignal.timeout(120000)]));
                    if(frame && panel===ownerPanel && thread===currentThread && !active && !session.signal.aborted){
                        requirePermission('files');pendingFile=frame;sendUI('attachment',{name:frame.name});
                    }
                }
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
                if(msg.op==='pickModel'&&!active&&!changing){changing=true;try{await browseModels();}finally{changing=false;}}
                if(msg.op==='tuneChat'&&!active)await tuneModel();
                if(msg.op==='pair') await pair();
                if(msg.op==='wallet')await pair.wallet();
                if(msg.op==='control') await control();
                if(msg.op==='guard' && !active) await guardrails();
                if(msg.op==='ide') await ide();
                if(msg.op==='cpu' && !active) await cpu();
                if(msg.op==='stop') active?.abort();
                if(['composerBuiltin','composerPin','moreTools'].includes(msg.op))draftForTool=typeof msg.draft==='string'?msg.draft.slice(0,12000):'';
                if(msg.op==='pasteResult'&&!active){const text=await vscode.env.clipboard.readText();if(text.trim())resultToChat('pasted tool',text);return;}
                if(msg.op==='copyDraft'&&typeof msg.draft==='string'){await vscode.env.clipboard.writeText(msg.draft.slice(0,16000));return;}
                if(msg.op==='composerBuiltin'&&!active&&['devices','models','agents','internet','autopilot','files'].includes(msg.target)){await composer.activate(msg.target);return;}
                if(msg.op==='moreTools'&&!active){await composer.menu();return;}
                if(msg.op==='composerPin'&&!active&&typeof msg.command==='string'){await composer.shortcut(msg.command);return;}
                if(msg.op==='send'&&!active&&typeof msg.text==='string'){
                    draftForTool=msg.text;
                    const remote=require('./build-tools.cjs').sshIntent(msg.text);
                    if(remote){await require('./build-tools.cjs').remoteBuild(vscode,context,remote,resultToChat);sendUI('notice',{text:'SSH task review closed.'});return;}
                    if(['/build','/test'].includes(msg.text.trim())){await require('./build-tools.cjs').localTask(vscode,msg.text.trim().slice(1),resultToChat);sendUI('notice',{text:'Project task review closed.'});return;}
                    const project=/^\/project\s+([\s\S]+)$/i.exec(msg.text);
                    if(project){await createProject(project[1]);sendUI('notice',{text:'Project review closed.'});return;}
                    const app=/^\/app\s+([\s\S]+)$/i.exec(msg.text);
                    if(app){await createApp(app[1]);sendUI('notice',{text:'Source review opened.'});return;}
                    const intent=composerTools.shieldIntent(msg.text);
                    if(intent){sendUI('notice',{text:await dashboards.shieldChat(intent)});return;}
                    const match=/^\/tool ([a-z][a-z0-9-]{0,31})$/i.exec(msg.text.trim());
                    if(match){await composer.shortcut(match[1].toLowerCase());sendUI('notice',{text:'Shortcut review closed.'});return;}
                }
                if(msg.op==='send' && typeof msg.text==='string' && /^\/control(?:\s|$)/i.test(msg.text)){await control(msg.text.replace(/^\/control\s*/i,''));panel?.webview.postMessage({type:'notice',text:'App control review closed.'});return;}
                if(msg.op==='send' && typeof msg.text==='string' && /^\/pair(?:\s|$)/i.test(msg.text)){await pair(msg.text.replace(/^\/pair\s*/i,'')||undefined);panel?.webview.postMessage({type:'notice',text:'Pairing review closed.'});return;}
                if(msg.op==='send') {
                    const file=pendingFile,route=settings();
                    if(file)requirePermission('files');
                    if(file&&(route.base||route.device))requirePermission('share');
                    if(file&&(route.base||route.device)&&!await vscode.window.showInformationMessage('Send '+file.name+' to '+(route.hostName||new URL(route.base).host)+' with this message?',{modal:true},'Send attachment'))throw Error('Attachment was not sent.');
                    const answer=await generate(msg.text,file);
                    pendingFile=undefined;sendUI('attachment',{name:''});sendUI('answer',{text:answer,model:history.at(-1)?.model,id:history.at(-1)?.id,metrics:history.at(-1)?.metrics,createdAtMs:history.at(-1)?.createdAtMs});
                }
            } catch(err) { panel?.webview.postMessage({type:'error',text:err.name==='AbortError'?'Generation stopped.':err.name==='TimeoutError'?'The model timed out. Check Models & providers, then retry.':err.message}); }
        });
    }
    for(const [cmd,fn] of Object.entries({chat,exportChat:exportConversation,management:()=>go('management'),tools:()=>go('tools'),settings:()=>go('settings'),models:()=>go('models'),ide,cpu,pair,guardrails,control})) {
        context.subscriptions.push(vscode.commands.registerCommand('meshlit.studio.'+cmd,async()=>{try {return await fn();} catch(err){vscode.window.showErrorMessage(err.message);}}));
    }
    context.subscriptions.push({dispose:()=>{active?.abort();local.stop();}});
    if (!home) {
        const links = [
            ['General chat', 'chat', 'comment-discussion'], ['Models', 'models', 'library'],
            ['Devices', 'pair', 'device-desktop'], ['Settings', 'settings', 'settings-gear']
        ];
        context.subscriptions.push(vscode.window.registerTreeDataProvider('meshlit.studio.navigation', {
            getChildren: item => item ? [] : links,
            getTreeItem: ([label, command, icon]) => {
                const item = new vscode.TreeItem(label);
                item.command = { command: 'meshlit.studio.' + command, title: label };
                item.iconPath = new vscode.ThemeIcon(icon);
                return item;
            }
        }));
    }
    if (home) {
        if(context.globalState.get('start-at-login',false)&&['local','import'].includes(settings().kind)){
            local.start(AbortSignal.timeout(180000),context.globalState.get('threads',0),settings().kind==='import'?settings():undefined).then(()=>{serverLog('Loaded local model at startup');scheduleUnload();}).catch(e=>serverLog('Startup load failed · '+e.message));
        }
        pair.restore().catch(e=>vscode.window.showWarningMessage('Device networking unavailable: '+e.message));
        // The dedicated workspace has no project contents. Its layout choices are window-scoped.
        for(const cmd of ['workbench.action.closePanel','workbench.action.closeAuxiliaryBar','workbench.action.closeSidebar']){
            await vscode.commands.executeCommand(cmd);
        }
        await vscode.commands.executeCommand('workbench.action.exitZenMode');
        await vscode.commands.executeCommand('workbench.action.toggleZenMode');
        chat();
    }
    return {generate, lastReplyMetrics:()=>history.at(-1)?.metrics, unload:()=>local.stop(), homeStatus:()=>({home,ready,visible:!!panel?.visible,folders:vscode.workspace.workspaceFolders?.length||0})};
}
exports.activate=activate;
exports.deactivate=()=>{active?.abort();local?.stop();};
