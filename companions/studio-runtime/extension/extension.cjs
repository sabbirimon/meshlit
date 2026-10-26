const vscode = require('vscode');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const {spawn} = require('node:child_process');
const readline = require('node:readline');
const tools = require('./workspace-tools.cjs');
const connections = require('./connections.cjs');
const navigation = require('./navigation.cjs');
let host, unlocked = false, hostReady = false;
const changed = new vscode.EventEmitter();
const chats = new Map(), snapshots = new Map();
const send = message => {
  if (!host || host.killed) throw Error('Meshlit agent host is unavailable');
  host.stdin.write(JSON.stringify(message)+'\n');
};
function status(message) { vscode.window.showInformationMessage(message); }
function boot(context) {
  if (host) return;
  const configPath = path.join(context.extensionPath,'runtime.json');
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  const environment = Object.fromEntries(['HOME','PATH','TMPDIR','LANG','LC_ALL','SYSTEMROOT'].filter(k => process.env[k]).map(k=>[k,process.env[k]]));
  host = spawn(config.python, [config.agentHost, configPath], {shell:false, env:environment, stdio:['pipe','pipe','pipe']});
  // Do not surface raw SDK diagnostics, which may contain prompts or runtime URLs.
  host.stderr.on('data', () => {});
  readline.createInterface({input:host.stdout}).on('line', line => {
    let event; try { event=JSON.parse(line); } catch { return; }
    if(event.type==='ready') { hostReady=true; }
    else if(event.type==='unlocked') { unlocked=true; status('Usage unlocked. Model requests are recorded in Meshlit.'); }
    else if(event.type==='usage') status(`Requests: ${event.requests}; reported input: ${event.inputTokens}; output: ${event.outputTokens}; unknown usage: ${event.unknownUsageRequests}. Totals include existing history.`);
    else if(event.type==='tool') handleTool(event).catch(() => replyTool(event,'Denied or failed'));
    else if(chats.has(event.session)) {
      const chat=chats.get(event.session);
      if(['done','stopped','error'].includes(event.type)) chat.busy=false;
      changed.fire();
      chat.panel.webview.postMessage(event);
    } else if(event.type==='error') vscode.window.showErrorMessage(event.message);
  });
  host.on('error', () => vscode.window.showErrorMessage('Cannot start the Meshlit SDK host.'));
  host.on('exit', () => {
    host=undefined; unlocked=false;hostReady=false;
    for(const chat of chats.values()) {
      chat.busy=false;
      chat.panel.webview.postMessage({type:'error',message:'Agent host closed. Open a new chat after restarting the host.'});
    }
  });
  context.subscriptions.push({dispose(){ if(host) host.stdin.end(); }});
}
function replyTool(event,result) { if(host) send({op:'tool_result',id:event.id,session:event.session,result}); }
async function handleTool(event) {
  const chat=chats.get(event.session), args=event.arguments || {};
  if(!chat || !chat.busy || !chat.root || chat.mode==='chat') return replyTool(event,'Denied: no active workspace permission');
  if(event.name==='mcp_request') {
    if(!['edit','autopilot'].includes(chat.mode)||!chat.connectors.length)return replyTool(event,'denied');
    const detail=JSON.stringify(args,null,2);if(detail.length>8000)return replyTool(event,'denied: arguments too large to review');
    const choice=await vscode.window.showWarningMessage(`${chat.title}: allow MCP tool?\n${detail}`,{modal:true},'Allow this tool call');
    return replyTool(event,choice==='Allow this tool call'&&chat.busy?'approved':'denied');
  }
  const approved=await vscode.window.showInformationMessage(`${chat.title}: ${event.name} — ${String(args.path).slice(0,200)}`, 'Allow once', 'Deny');
  if(approved!=='Allow once' || !chat.busy || !chats.has(event.session)) return replyTool(event,'Denied by user');
  let result;
  if(event.name==='list_files') result=await tools.listing(chat.root,args.path);
  else if(event.name==='read_file') {
    const current=await tools.read(chat.root,args.path);
    chat.receipts.set(current.file,current.hash); result=current.text;
  } else if(event.name==='propose_edit') {
    if(!['edit','autopilot'].includes(chat.mode) || (chat.mode==='edit' && chat.writes>0)) throw Error('Write limit');
    const current=await tools.read(chat.root,args.path);
    if(chat.receipts.get(current.file)!==current.hash) throw Error('Read the current file before editing');
    const document=await vscode.workspace.openTextDocument(vscode.Uri.file(current.file));
    if(document.isDirty || document.getText()!==current.text) throw Error('Unsaved or changed document');
    const version=document.version, next=tools.replacement(current.text,args.old,args.new);
    const key=crypto.randomUUID(), before=vscode.Uri.parse(`meshlit-diff:/${key}/before`), after=vscode.Uri.parse(`meshlit-diff:/${key}/after`);
    snapshots.set(before.toString(),current.text); snapshots.set(after.toString(),next);
    try {
      await vscode.commands.executeCommand('vscode.diff',before,after,`Meshlit proposed edit: ${args.path}`);
      const decision=await vscode.window.showInformationMessage(`${chat.title}: apply the reviewed edit to ${args.path}?`, 'Apply edit', 'Reject');
      if(decision!=='Apply edit' || !chat.busy) return replyTool(event,'Edit rejected');
      const fresh=await tools.read(chat.root,args.path);
      if(fresh.hash!==current.hash || document.isDirty || document.version!==version) throw Error('File changed during review');
      const edit=new vscode.WorkspaceEdit();
      edit.replace(document.uri,new vscode.Range(document.positionAt(0),document.positionAt(document.getText().length)),next);
      if(!await vscode.workspace.applyEdit(edit)) throw Error('Editor rejected edit');
      chat.writes++;
      result='Applied to the editor as an UNSAVED change. The human must save it. Do not claim it is saved.';
    } finally { snapshots.delete(before.toString()); snapshots.delete(after.toString()); }
  } else throw Error('Unsupported tool');
  replyTool(event,result);
}
async function openChat(context,coding) {
  if(!vscode.workspace.isTrusted) throw Error('Trust the workspace first');
  boot(context);
  if(chats.size>=4) { status('Close a chat before opening more than four sessions.'); return; }
  let mode='chat', root, selectedConnectors=[];
  if(coding) {
    const folder=vscode.workspace.workspaceFolders?.[0];
    if(!folder || folder.uri.scheme!=='file') { status('Open a local folder first.'); return; }
    root=folder.uri.fsPath;
    mode=await vscode.window.showQuickPick(['plan','edit','autopilot'],{title:'Agent mode — all file actions require approval; edits open a diff'});
    if(!mode) return;
    if(['edit','autopilot'].includes(mode))selectedConnectors=await connections.chooseMcp(context);
  }
  const id=crypto.randomUUID(), title=coding ? `Agent · ${mode}` : `Chat ${chats.size+1}`;
  const panel=vscode.window.createWebviewPanel('meshlit.chat',title,vscode.ViewColumn.Beside,{enableScripts:true,retainContextWhenHidden:true,localResourceRoots:[]});
  const chat={panel,mode,root,title,busy:false,writes:0,receipts:new Map(),route:{kind:'local'},connectors:selectedConnectors,started:false}; chats.set(id,chat); changed.fire();
  panel.webview.html=html(title,mode);
  panel.webview.onDidReceiveMessage(async message => {
    try {
      if(message.op==='connection') {
        if(chat.started){status('Start a new chat to change the model without mixing histories.');return;}
        const route=await connections.chooseConnection(context);
        if(route && !chat.started){chat.route=route;panel.webview.postMessage({type:'connection',label:route.label});}
      }
      if(message.op==='stop') { send({op:'stop',session:id}); chat.busy=false; }
      if(message.op==='send' && !chat.busy) {
        if(!unlocked) { panel.webview.postMessage({type:'error',message:'Run Meshlit: Unlock Usage from the Command Palette first.'}); return; }
        if(typeof message.prompt!=='string' || !message.prompt.trim() || message.prompt.length>16000) return;
        chat.busy=true; chat.writes=0;chat.started=true;
        changed.fire();
        send({op:'send',session:id,prompt:message.prompt,mode,workspace:root,route:chat.route,connectors:chat.connectors});
      }
    } catch { panel.webview.postMessage({type:'error',message:'Agent host unavailable. Reopen this chat.'}); }
  });
  panel.onDidDispose(()=>{chats.delete(id); changed.fire(); if(host) send({op:'close',session:id});});
}
function html(title,mode) {
  const nonce=crypto.randomBytes(24).toString('hex');
  return `<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';"><style nonce="${nonce}">
  body{font:14px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-editor-background);margin:0;padding:24px;display:flex;flex-direction:column;height:calc(100vh - 48px)}
  header{font-size:20px;margin-bottom:8px}small{color:var(--vscode-descriptionForeground)}#history{flex:1;overflow:auto;margin:20px 0}.message{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6;padding:14px 0;border-bottom:1px solid var(--vscode-panel-border)}.user{color:var(--vscode-textLink-foreground)}
  textarea{box-sizing:border-box;width:100%;resize:vertical;min-height:90px;padding:14px;background:var(--vscode-input-background);color:var(--vscode-input-foreground);border:1px solid var(--vscode-input-border);border-radius:12px;font:inherit}button{margin:8px 8px 0 0;padding:9px 16px;border:0;border-radius:7px;background:var(--vscode-button-background);color:var(--vscode-button-foreground);cursor:pointer}#state{margin:8px 0;color:var(--vscode-descriptionForeground)}</style></head><body>
  <header>${title}</header><small id="model">Bundled local model · ${mode} · independent session</small><div id="history"></div><div id="state">Open another chat from the Command Palette while this session runs.</div><textarea id="prompt" maxlength="16000" placeholder="Ask Meshlit…"></textarea><div><button id="connection">Choose model</button><button id="send">Send</button><button id="stop">Stop this chat</button></div>
  <script nonce="${nonce}">const api=acquireVsCodeApi(), history=document.getElementById('history'), prompt=document.getElementById('prompt'), state=document.getElementById('state');let busy=false,answer;
  function row(text,kind){const el=document.createElement('div');el.className='message '+kind;el.textContent=text;history.append(el);return el;}
  document.getElementById('send').onclick=()=>{if(busy||!prompt.value.trim())return;row(prompt.value,'user');answer=row('','assistant');busy=true;state.textContent='Running · other chats remain available';api.postMessage({op:'send',prompt:prompt.value});prompt.value='';};
  document.getElementById('stop').onclick=()=>{api.postMessage({op:'stop'});state.textContent='Stopping…';};
  document.getElementById('connection').onclick=()=>api.postMessage({op:'connection'});
  window.addEventListener('message',({data:e})=>{if(e.type==='connection')document.getElementById('model').textContent=e.label;if(e.type==='delta'){if(!answer)answer=row('','assistant');answer.textContent+=e.text;history.scrollTop=history.scrollHeight;}if(['done','error','stopped'].includes(e.type)){busy=false;state.textContent=e.type==='error'?e.message:e.type==='stopped'?'Stopped. Open a new chat to continue. Applied edits remain unsaved.':'Complete';}});</script></body></html>`;
}
function activate(context) {
  navigation.register(context);
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.reveal',id=>chats.get(id)?.panel.reveal()),
    vscode.window.registerTreeDataProvider('meshlit.sessions', {
      onDidChangeTreeData:changed.event,
      getChildren:()=>[...chats.keys()],
      getTreeItem:id=>{const chat=chats.get(id),item=new vscode.TreeItem(chat.title);
        item.description=chat.busy?'Running':'Ready'; item.iconPath=new vscode.ThemeIcon(chat.mode==='chat'?'comment':'code');
        item.command={command:'meshlit.reveal',title:'Open chat',arguments:[id]};return item;}
    }));
  context.subscriptions.push(vscode.workspace.registerTextDocumentContentProvider('meshlit-diff',{provideTextDocumentContent:uri=>snapshots.get(uri.toString())||''}));
  context.subscriptions.push(vscode.commands.registerCommand('meshlit.chat',()=>openChat(context,false)),
    vscode.commands.registerCommand('meshlit.agent',()=>openChat(context,true)),
    vscode.commands.registerCommand('meshlit.addConnection',()=>connections.addConnection(context)),
    vscode.commands.registerCommand('meshlit.addMcp',()=>connections.addMcp(context)),
    vscode.commands.registerCommand('meshlit.unlockUsage',async()=>{
      boot(context);const password=await vscode.window.showInputBox({title:'Unlock Meshlit Usage',password:true,prompt:'Existing usage passphrase (12–256 characters). Close the older Studio if it has this vault unlocked.'});
      if(password && password.length>=12 && password.length<=256) send({op:'unlock',password});
    }),vscode.commands.registerCommand('meshlit.usage',()=>{boot(context);send({op:'usage'});}));
  const button=vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right,100);
  button.text='$(comment-discussion) Meshlit · New chat';button.command='meshlit.chat';button.show();context.subscriptions.push(button);
  if(!vscode.window.tabGroups.all.some(g=>g.tabs.length)) vscode.commands.executeCommand('meshlit.home');
  return {runtimeStatus:()=>({hostReady,panels:chats.size})};
}
exports.activate=activate;
exports.runtimeStatus=()=>({hostReady,panels:chats.size});
