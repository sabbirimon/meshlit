const vscode = require('vscode');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');
const readline = require('node:readline');
const tools = require('./workspace-tools.cjs');
const connections = require('./connections.cjs');
const navigation = require('./navigation.cjs');
let host, unlocked = false, hostReady = false, localModel={state:'idle'}, usagePage, projectMemory;
const changed = new vscode.EventEmitter();
const chats = new Map(), snapshots = new Map();
const send = message => {
  if (!host || host.killed) throw Error('Meshlit agent host is unavailable');
  host.stdin.write(JSON.stringify(message)+'\n');
};
function readiness() { for(const chat of chats.values()) chat.panel.webview.postMessage({type:'runtime',ready:hostReady,unlocked,localModel}); }
function loadLocal(){if(!hostReady || ['loading','ready'].includes(localModel.state))return;localModel={state:'loading'};readiness();send({op:'load_local'});}
function status(message) { vscode.window.showInformationMessage(message); }
function boot(context) {
  if (host) return;
  const configPath = path.join(context.extensionPath,'runtime.json');
  const config = require('./runtime-config.cjs').resolveRuntime(context.extensionPath,JSON.parse(fs.readFileSync(configPath, 'utf8')));
  const environment = Object.fromEntries(['HOME','PATH','TMPDIR','LANG','LC_ALL','SYSTEMROOT'].filter(k => process.env[k]).map(k=>[k,process.env[k]]));
  host = spawn(config.python, [...(config.pythonArgs||[]),config.agentHost, configPath], {shell:false, env:environment, stdio:['pipe','pipe','pipe']});
  // Do not surface raw SDK diagnostics, which may contain prompts or runtime URLs.
  host.stderr.on('data', () => {});
  readline.createInterface({input:host.stdout}).on('line', line => {
    let event; try { event=JSON.parse(line); } catch { return; }
    if(event.type==='ready') { hostReady=true;readiness();if([...chats.values()].some(c=>c.route.kind==='local'))loadLocal(); }
    else if(event.type==='local_model') {localModel=event;readiness();}
    else if(event.type==='unlocked') { unlocked=true;readiness();usagePage?.refresh(); status('Usage unlocked. Model requests are recorded in Meshlit.'); }
    else if(event.type==='usage') usagePage?.accept(event);
    else if(event.type==='error' && !event.session) {
      if(event.operation==='unlock'){unlocked=false;readiness();}
      const message=typeof event.message==='string'?event.message.slice(0,250):'The model host could not complete this action.';
      vscode.window.showErrorMessage(message);
      usagePage?.failure(message);
      for(const chat of chats.values())chat.panel.webview.postMessage({type:'notice',message});
    }
    else if(event.type==='tool') handleTool(event).catch(async() => {try{await replyTool(event,'Denied or failed','failed');}catch{status('A tool result could not be delivered. No automatic retry was made.');}});
    else if(chats.has(event.session)) {
      const chat=chats.get(event.session);
      if(['done','stopped','error'].includes(event.type)) chat.busy=false;
      changed.fire();
      chat.panel.webview.postMessage(event);
    } else if(event.type==='error') vscode.window.showErrorMessage(event.message);
  });
  const child=host;
  const closed=() => {
    if(host!==child)return;
    host=undefined; unlocked=false;hostReady=false;localModel={state:'idle'};
    readiness();usagePage?.failure('Agent host closed. Refresh Usage and unlock your ledger again.');
    for(const chat of chats.values()) {
      chat.busy=false;chat.closed=true;
      chat.panel.webview.postMessage({type:'error',message:'Agent host closed. Open a new chat after restarting the host.'});
    }
  };
  child.on('error', () => {closed();vscode.window.showErrorMessage('Cannot start the Meshlit SDK host.');});
  child.on('exit', closed);
  context.subscriptions.push({dispose(){ if(host) host.stdin.end(); }});
}
async function replyTool(event,result,outcome='complete') {
  const chat=chats.get(event.session);
  const active=()=>chat&&chats.get(event.session)===chat&&chat.busy&&!chat.closed&&vscode.workspace.isTrusted;
  if(outcome==='approved'&&!active()){result='denied: session stopped or permission revoked';outcome='cancelled';}
  if(chat&&projectMemory){
    try{await projectMemory.recordAction({root:chat.root,tool:event.name,status:outcome,mode:chat.mode});}
    catch{chat.panel.webview.postMessage({type:'notice',message:'The tool outcome could not be saved to action history. Existing history was preserved.'});}
  }
  // Optional history persistence must not extend an approval past Stop or revocation.
  if(outcome==='approved'&&!active()){
    result='denied: session stopped or permission revoked';
    try{await projectMemory?.recordAction({root:chat.root,tool:event.name,status:'cancelled',mode:chat.mode});}catch{}
  }
  if(host) send({op:'tool_result',id:event.id,session:event.session,result});
}
async function handleTool(event) {
  const chat=chats.get(event.session), args=event.arguments || {};
  const active=()=>chat&&chats.get(event.session)===chat&&chat.busy&&!chat.closed&&vscode.workspace.isTrusted;
  if(event.name==='desktop_control') {
    if(!active() || !['edit','autopilot'].includes(chat.mode)) return replyTool(event,'Denied: use an active Edit or Autopilot agent','denied');
    return replyTool(event,await require('./app-access.cjs').execute(args,active));
  }
  if(!active() || !chat.root || chat.mode==='chat') return replyTool(event,'Denied: no active workspace permission','denied');
  if(event.name==='mcp_request') {
    if(!['edit','autopilot'].includes(chat.mode)||!chat.connectors.length)return replyTool(event,'denied','denied');
    const detail=JSON.stringify(args,null,2);if(detail.length>8000)return replyTool(event,'denied: arguments too large to review','denied');
    const choice=await vscode.window.showWarningMessage(`${chat.title}: allow MCP tool?\n${detail}`,{modal:true},'Allow this tool call');
    const approved=choice==='Allow this tool call'&&active();
    return replyTool(event,approved?'approved':'denied',approved?'approved':'denied');
  }
  const approved=await vscode.window.showInformationMessage(`${chat.title}: ${event.name} — ${String(args.path).slice(0,200)}`, 'Allow once', 'Deny');
  if(approved!=='Allow once' || !active()) return replyTool(event,'Denied by user','denied');
  let result;
  if(event.name==='list_files') {result=await tools.listing(chat.root,args.path);if(!active())return replyTool(event,'Cancelled before returning file listing','cancelled');}
  else if(event.name==='read_file') {
    const current=await tools.read(chat.root,args.path);
    if(!active())return replyTool(event,'Cancelled before returning file content','cancelled');
    chat.receipts.set(current.file,current.hash); result=current.text;
  } else if(event.name==='propose_edit') {
    if(!['edit','autopilot'].includes(chat.mode) || (chat.mode==='edit' && chat.writes>0)) throw Error('Write limit');
    const current=await tools.read(chat.root,args.path);
    if(!active())return replyTool(event,'Cancelled before preparing edit','cancelled');
    if(chat.receipts.get(current.file)!==current.hash) throw Error('Read the current file before editing');
    const document=await vscode.workspace.openTextDocument(vscode.Uri.file(current.file));
    if(!active())return replyTool(event,'Cancelled before preparing edit','cancelled');
    if(document.isDirty || document.getText()!==current.text) throw Error('Unsaved or changed document');
    const version=document.version, next=tools.replacement(current.text,args.old,args.new);
    const key=crypto.randomUUID(), before=vscode.Uri.parse(`meshlit-diff:/${key}/before`), after=vscode.Uri.parse(`meshlit-diff:/${key}/after`);
    snapshots.set(before.toString(),current.text); snapshots.set(after.toString(),next);
    try {
      await vscode.commands.executeCommand('vscode.diff',before,after,`Meshlit proposed edit: ${args.path}`);
      const decision=await vscode.window.showInformationMessage(`${chat.title}: apply the reviewed edit to ${args.path}?`, 'Apply edit', 'Reject');
      if(decision!=='Apply edit' || !active()) return replyTool(event,'Edit rejected','denied');
      const fresh=await tools.read(chat.root,args.path);
      if(!active())return replyTool(event,'Cancelled before applying edit','cancelled');
      if(fresh.hash!==current.hash || document.isDirty || document.version!==version) throw Error('File changed during review');
      const edit=new vscode.WorkspaceEdit();
      edit.replace(document.uri,new vscode.Range(document.positionAt(0),document.positionAt(document.getText().length)),next);
      if(!await vscode.workspace.applyEdit(edit)) throw Error('Editor rejected edit');
      chat.writes++;
      result='Applied to the editor as an UNSAVED change. The human must save it. Do not claim it is saved.';
    } finally { snapshots.delete(before.toString()); snapshots.delete(after.toString()); }
  } else throw Error('Unsupported tool');
  return replyTool(event,result);
}
async function openChat(context,coding,profileId,options={}) {
  if(!vscode.workspace.isTrusted) throw Error('Trust the workspace first');
  boot(context);
  if(chats.size>=4) { status('Close a chat before opening more than four sessions.'); return; }
  let mode=options.mode||'chat', root, selectedConnectors=[];
  if(coding || mode!=='chat') {
    const folder=vscode.workspace.workspaceFolders?.[0];
    if(!folder || folder.uri.scheme!=='file') { status('Open a local folder first.'); return; }
    root=folder.uri.fsPath;
    if(!options.mode)mode=await chooseMode();
    if(!mode) return;
    if(['edit','autopilot'].includes(mode))selectedConnectors=options.connectors||await connections.chooseMcp(context);
  }
  const initialRoute=options.route||(profileId?await connections.chooseConnection(context,profileId):{kind:'local',label:'Bundled local model'});
  if(!initialRoute)return;
  const id=crypto.randomUUID(), title=mode!=='chat' ? `Agent · ${mode}` : `Chat ${chats.size+1}`;
  const column=[...chats.values()].find(c=>c.panel.viewColumn)?.panel.viewColumn || vscode.ViewColumn.Beside;
  const panel=vscode.window.createWebviewPanel('meshlit.chat',title,column,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  context.subscriptions.push(panel);
  const memoryRoot=root||vscode.workspace.workspaceFolders?.find(f=>f.uri.scheme==='file')?.uri.fsPath;
  const chat={panel,mode,root,memoryRoot,title,busy:false,writes:0,receipts:new Map(),route:initialRoute,connectors:selectedConnectors,started:false,closed:false}; chats.set(id,chat); changed.fire();
  const update=()=>panel.webview.postMessage({type:'configuration',mode:chat.mode,label:chat.route.label||'Bundled local model',endpoint:chat.route.endpoint||'On this device',started:chat.started,local:chat.route.kind==='local'});
  panel.webview.html=require('./chat-view.cjs').render(title,mode);
  panel.webview.onDidReceiveMessage(async message => {
    try {
      if(message.op==='ready'){update();readiness();if(chat.route.kind==='local')loadLocal();return;}
      if(message.op==='loadLocal'){loadLocal();return;}
      if(message.op==='unlock'){await vscode.commands.executeCommand('meshlit.unlockUsage');return;}
      if(message.op==='newChat'){await openChat(context,false);return;}
      if(message.op==='approvals'){status('Workspace reads and tool calls require approval. Edits open a diff for review and remain unsaved. Autopilot keeps these controls; Full access is not implemented.');return;}
      if(message.op==='actions'){
        const action=await vscode.window.showQuickPick([{label:'Choose a tool-enabled mode',description:'Plan reads; Edit and Autopilot can use reviewed tools',op:'mode'},{label:'Select MCP tools for this chat',description:'Requires Edit or Autopilot and a local workspace',op:'tools'},{label:'Project memory and context',command:'meshlit.memory'},{label:'Meshlit settings',command:'meshlit.settings'},{label:'Connect a model or gateway',command:'meshlit.addModelSource'},{label:'Models and provider dashboards',command:'meshlit.models'},{label:'Manage MCP connectors',command:'meshlit.connectors'},{label:'Open workspace folder',command:'workbench.action.files.openFolder'},{label:'Unlock usage ledger',command:'meshlit.unlockUsage'}],{title:'Chat tools and settings'});
        if(!action)return;
        if(action.op)message={op:action.op};else{await vscode.commands.executeCommand(action.command);return;}
      }
      if(message.op==='gateways'){await vscode.commands.executeCommand('meshlit.models');return;}
      if(message.op==='setup'){await vscode.commands.executeCommand('meshlit.addModelSource');return;}
      if(chat.closed){panel.webview.postMessage({type:'error',message:'Session closed. Open a new chat.'});return;}
      if(['connection','mode','tools'].includes(message.op)) {
        if(chat.configuring)return;
        if(chat.busy){status('This chat is running. Open New chat to use another model or mode alongside it.');return;}
        chat.configuring=true;
        try {
          if(message.op==='tools'){
            if(!['edit','autopilot'].includes(chat.mode)){status('Choose Edit / Build or Autopilot from the mode button first. Chat has no tools; Plan is read-only.');return;}
            const connectors=await connections.chooseMcp(context,{editing:true});
            if(connectors===undefined)return;
            if(chat.closed||!chats.has(id))return;
            if(chat.started){await openChat(context,false,undefined,{mode:chat.mode,route:chat.route,connectors});return;}
            chat.connectors=connectors;update();status(`${connectors.length} MCP connectors selected for this chat. Each tool call still requires review.`);return;
          }
          const selected=message.op==='mode'?await chooseMode():await connections.chooseConnection(context);
          if(!selected || chat.closed || !chats.has(id))return;
          // SDK tools and provider history are fixed when a session starts.
          // A new visible conversation avoids silently reusing old permissions or sending old history.
          if(chat.started){
            await openChat(context,false,undefined,{mode:message.op==='mode'?selected:chat.mode,route:message.op==='connection'?selected:chat.route});return;
          }
          if(message.op==='mode'){
            const folder=vscode.workspace.workspaceFolders?.[0];
            if(selected!=='chat' && (!folder || folder.uri.scheme!=='file')){
              status('Open a local folder using Workspace → Open Folder, then select this mode.');return;
            }
            const connectors=['edit','autopilot'].includes(selected)?await connections.chooseMcp(context):[];
            if(chat.closed || !chats.has(id))return;
            chat.mode=selected;chat.root=selected==='chat'?undefined:folder.uri.fsPath;chat.connectors=connectors;
            chat.memoryRoot=chat.root||vscode.workspace.workspaceFolders?.find(f=>f.uri.scheme==='file')?.uri.fsPath;
          } else chat.route=selected;
          update();changed.fire();if(chat.route.kind==='local')loadLocal();
        } finally {chat.configuring=false;}
        return;
      }
      if(message.op==='stop') { send({op:'stop',session:id}); chat.busy=false; chat.closed=true; }
      if(message.op==='send' && !chat.busy) {
        if(chat.configuring){panel.webview.postMessage({type:'error',message:'Finish choosing the model or mode before sending.'});return;}
        if(!hostReady || !unlocked) { readiness();panel.webview.postMessage({type:'error',message:!hostReady?'The model host is still starting. Wait for it to become ready.':'Unlock the Meshlit usage ledger with its existing passphrase, then send again. This is not your Mac login or provider password.'}); return; }
        if(chat.route.kind==='local'&&localModel.state!=='ready'){loadLocal();panel.webview.postMessage({type:'error',message:'The bundled local model is not ready yet. Wait for Local model ready, then send again.'});return;}
        if(typeof message.prompt!=='string' || !message.prompt.trim() || message.prompt.length>16000) return;
        chat.busy=true; chat.writes=0;
        changed.fire();
        update();
        let recalled='';
        try{recalled=await projectMemory?.contextFor({root:chat.memoryRoot,query:message.prompt,remote:chat.route.kind!=='local',maxCharacters:Math.min(3000,Math.max(0,16000-message.prompt.length-2))})||'';}
        catch{chat.busy=false;changed.fire();panel.webview.postMessage({type:'error',message:'Project memory could not be read. No model request was sent. Open Project memory to check protected storage and project access.'});return;}
        if(chat.closed||!chat.busy||!chats.has(id)||!hostReady||!unlocked||!vscode.workspace.isTrusted){chat.busy=false;changed.fire();return;}
        chat.started=true;update();
        if(recalled)panel.webview.postMessage({type:'notice',message:`Including ${recalled.length} characters of ${path.basename(chat.memoryRoot)} project memory with this request. Token count is unavailable until reported by the model.`});
        send({op:'send',session:id,prompt:recalled?recalled+'\n\n'+message.prompt:message.prompt,mode:chat.mode,workspace:chat.root,route:chat.route,connectors:chat.connectors});
      }
    } catch { chat.busy=false;changed.fire();panel.webview.postMessage({type:'error',message:['connection','setup'].includes(message.op)?'Model setup did not finish. Check the API address and saved-key access; cancel any unwanted Keychain prompt and try again.':'Action could not complete. Check the selected workspace and runtime, then try again.'}); }
  });
  panel.onDidDispose(()=>{chats.delete(id); changed.fire(); if(host) send({op:'close',session:id});});
}
async function chooseMode() {
  const picked=await vscode.window.showQuickPick(require('./chat-view.cjs').modes,{title:'Conversation mode',placeHolder:'Choose how this chat can work'});
  return picked?.id;
}
function activate(context) {
  usagePage=require('./usage.cjs').create(context,message=>{boot(context);send(message);});
  navigation.register(context);
  require('./settings.cjs').register(context);
  require('./companions.cjs').register(context);
  projectMemory=require('./memory.cjs').register(context);
  require('./voice.cjs').register(context);
  require('./deepseek-harness.cjs').register(context);
  require('./protocols.cjs').register(context);
  require('./app-access.cjs').register(context);
  require('./gateways.cjs').register(context);
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.reveal',id=>chats.get(id)?.panel.reveal()),
    vscode.window.registerTreeDataProvider('meshlit.sessions', {
      onDidChangeTreeData:changed.event,
      getChildren:()=>[...chats.keys()],
      getTreeItem:id=>{const chat=chats.get(id),item=new vscode.TreeItem(chat.title);
        item.description=chat.busy?'Running':'Ready'; item.iconPath=new vscode.ThemeIcon(chat.mode==='chat'?'comment':'code');
        item.command={command:'meshlit.reveal',title:'Open chat',arguments:[id]};return item;}
    }));
  context.subscriptions.push(vscode.workspace.registerTextDocumentContentProvider('meshlit-diff',{provideTextDocumentContent:uri=>snapshots.get(uri.toString())||''}));
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.chat',profileId=>openChat(context,false,profileId)),
    vscode.commands.registerCommand('meshlit.revokeConnection',profileId=>{for(const [id,chat] of chats){if(chat.route.id===profileId)chat.panel.dispose();}}),
    vscode.commands.registerCommand('meshlit.revokeMcp',name=>{for(const chat of chats.values()){if(chat.connectors.some(c=>c.name===name))chat.panel.dispose();}}),
    vscode.commands.registerCommand('meshlit.agent',()=>openChat(context,true)),
    vscode.commands.registerCommand('meshlit.addConnection',()=>connections.addConnection(context)),
    vscode.commands.registerCommand('meshlit.addModelSource',async()=>{
      const source=await vscode.window.showQuickPick([
        {id:'bundled',label:'This device · bundled local model',detail:'Uses Meshlit’s existing local backend.'},
        ...require('./model-sources.cjs').sources
      ],{title:'Choose model source'});
      if(!source)return;
      if(source.id==='bundled')return openChat(context,false);
      return connections.addConnection(context,source.id==='cloud'?undefined:'Local/distributed server',source.id);
    }),
    vscode.commands.registerCommand('meshlit.checkConnection',()=>connections.checkSavedConnection(context)),
    vscode.commands.registerCommand('meshlit.addMcp',()=>connections.addMcp(context)),
    vscode.commands.registerCommand('meshlit.unlockUsage',async()=>{
      boot(context);const password=await vscode.window.showInputBox({title:'Unlock Meshlit Usage',password:true,prompt:'Existing usage passphrase (12–256 characters). Close the older Studio if it has this vault unlocked.'});
      if(password && password.length>=12 && password.length<=256) send({op:'unlock',password});
    }),vscode.commands.registerCommand('meshlit.usage',()=>{boot(context);usagePage.open();}));
  const button=vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right,100);
  button.text='$(comment-discussion) Meshlit · New chat';button.command='meshlit.chat';button.show();context.subscriptions.push(button);
  if(!vscode.window.tabGroups.all.some(g=>g.tabs.length)) vscode.commands.executeCommand('meshlit.home');
  return {runtimeStatus:()=>({hostReady,panels:chats.size})};
}
exports.activate=activate;
exports.runtimeStatus=()=>({hostReady,panels:chats.size});
