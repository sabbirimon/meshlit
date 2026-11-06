const vscode=require('vscode');
const crypto=require('node:crypto');
const entries=[['New chat','chat','add'],['General chat','generalChat','comment-discussion'],
 ['Workspaces','workspaces','folder-opened'],['Workspace agents','workspaceAgents','code'],
 ['Bots','bots','hubot'],['Models','models','server'],['LLM settings','llmSettings','settings-gear'],
 ['Welcome','home','home'],['Open IDE','openIde','window'],
 ['Tools',undefined,'tools',[['Workspace changes','workspaceChanges','diff'],
  ['Connected capabilities','connectedCapabilities','plug'],['System resources','systemMonitor','dashboard'],
  ['Voice and vision','voice','mic'],['Project memory','memory','book'],
  ['Computer companions','companions','remote']]],
 ['Usage','usage','graph'],['Startup view','startupView','layout'],['Settings','settings','settings-gear']];
const command=id=>id.startsWith('workbench.')?id:'meshlit.'+id;
const workspaceKey='meshlit.recentWorkspaces';
const localFolder=()=>vscode.workspace.workspaceFolders?.find(folder=>folder.uri.scheme==='file');
function register(context){
 let home;
 context.subscriptions.push(vscode.window.registerTreeDataProvider('meshlit.navigation',{
  getChildren:item=>item?item[3]||[]:entries,
  getTreeItem:([label,id,icon,children])=>{
   const item=new vscode.TreeItem(label,children?vscode.TreeItemCollapsibleState.Collapsed:vscode.TreeItemCollapsibleState.None);
   item.iconPath=new vscode.ThemeIcon(icon);
   if(id)item.command={command:command(id),title:label};
   return item;
  }
 }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.openIde',()=>vscode.commands.executeCommand(localFolder()?'workbench.view.explorer':'meshlit.chooseWorkspace')),
  vscode.commands.registerCommand('meshlit.llmSettings',()=>vscode.commands.executeCommand('meshlit.mainLlmSettings')),
  vscode.commands.registerCommand('meshlit.workspaces',()=>vscode.commands.executeCommand('meshlit.mainWorkspaces')),
  vscode.commands.registerCommand('meshlit.chooseWorkspace',async()=>{
   const current=localFolder();
   const recent=context.globalState.get(workspaceKey,[]);
   const choice=await vscode.window.showQuickPick([
    {label:'$(folder-opened) Open folder…',action:'browse',description:'Open the project in a separate IDE window'},
    ...(current?[{label:`$(folder) ${current.name}`,description:'Current workspace',detail:current.uri.fsPath,action:'current'}]:[]),
    ...recent.filter(folder=>folder.path!==current?.uri.fsPath).map(folder=>({label:`$(folder) ${folder.name}`,detail:folder.path,folder})),
    ...(recent.length?[{label:'Clear recent workspaces',action:'clear'}]:[])
   ],{title:'Meshlit · Workspaces',matchOnDetail:true});
   if(!choice)return;
   if(choice.action==='current')return vscode.commands.executeCommand('meshlit.openIde');
   if(choice.action==='clear')return context.globalState.update(workspaceKey,[]);
   const uri=choice.action==='browse'?(await vscode.window.showOpenDialog({title:'Open a local workspace',
    canSelectFiles:false,canSelectFolders:true,canSelectMany:false,openLabel:'Open Workspace'}))?.[0]:vscode.Uri.file(choice.folder.path);
   if(uri?.scheme!=='file')return;
   try {if(!((await vscode.workspace.fs.stat(uri)).type&vscode.FileType.Directory))throw Error('Not a folder');}
   catch {await vscode.window.showWarningMessage('That workspace folder is unavailable.');return;}
   if(!await vscode.commands.executeCommand('meshlit.reviewProject',uri))return;
   const name=require('node:path').basename(uri.fsPath)||uri.fsPath;
   const latest=context.globalState.get(workspaceKey,[]);
   await context.globalState.update(workspaceKey,[{name,path:uri.fsPath},...latest.filter(folder=>folder.path!==uri.fsPath)].slice(0,12));
   await vscode.commands.executeCommand('vscode.openFolder',uri,{forceNewWindow:true});
  }),
  vscode.commands.registerCommand('meshlit.workspaceAgents',async()=>{
   const folder=localFolder();
   const choice=await vscode.window.showQuickPick([
    folder?{label:'$(add) New workspace agent',detail:folder.uri.fsPath,id:'agent'}:{label:'$(folder-opened) Open a workspace first',id:'workspaces'},
    {label:'$(organization) Agent teams & roles',id:'agentTeams'},
    ...(folder?[{label:'$(diff) Review workspace changes',id:'workspaceChanges'}]:[])
   ],{title:'Meshlit · Workspace agents',placeHolder:'Choose an agent workflow'});
   if(choice)await vscode.commands.executeCommand(command(choice.id));
  }),
  vscode.commands.registerCommand('meshlit.startupView',async()=>{
   const setting=vscode.workspace.getConfiguration('meshlit');
   const current=setting.get('startupView','chat');
   const views=[{id:'chat',label:'General Chat'},{id:'welcome',label:'Welcome'},{id:'editor',label:'Editor'}];
   const choice=await vscode.window.showQuickPick(views.map(view=>({...view,description:view.id===current?'Current default':undefined})),
    {title:'Startup view for windows without a workspace'});
   if(!choice)return;
   await setting.update('startupView',choice.id,vscode.ConfigurationTarget.Global);
   await vscode.commands.executeCommand({chat:'meshlit.generalChat',welcome:'meshlit.home',editor:'workbench.view.explorer'}[choice.id]);
  }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.home',()=>{
  if(home){home.reveal(vscode.ViewColumn.One);return;}
  home=require('./shell.cjs').createPanel(vscode,'meshlit.home','Welcome to Meshlit',vscode.ViewColumn.One,{enableScripts:true,localResourceRoots:[]});
  home.webview.html=render();
  home.webview.onDidReceiveMessage(async({action})=>{
   const allowed={folder:'meshlit.workspaces',chat:'meshlit.generalChat',agent:'meshlit.workspaceAgents',bots:'meshlit.bots',ide:'meshlit.openIde',models:'meshlit.models',usage:'meshlit.usage',startup:'meshlit.startupView',settings:'meshlit.settings',unlock:'meshlit.unlockUsage'};
   if(Object.hasOwn(allowed,action))await vscode.commands.executeCommand(allowed[action]);
  });
  home.onDidDispose(()=>home=undefined);
 }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.models',()=>vscode.commands.executeCommand('meshlit.mainModels')));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.modelPicker',async()=>{
  const profiles=context.globalState.get('meshlit.connections',[]);
  const choice=await vscode.window.showQuickPick([{label:'OmniRoute dashboard',id:'OmniRoute'},{label:'FreeLLMAPI dashboard',id:'FreeLLMAPI'},{label:'Hugging Face dashboard',id:'Hugging Face'},{label:'$(add) Add model connection',id:'add'},{label:'$(pulse) Check API connection',id:'check'},{label:'$(chip) Bundled local model',detail:'Choose from any chat. No provider account needed.',id:'local'},...profiles.map(p=>({label:p.service+' · '+p.model,detail:p.endpoint,id:p.id}))],{title:'Meshlit · Models'});
  if(['OmniRoute','FreeLLMAPI','Hugging Face'].includes(choice?.id))return vscode.commands.executeCommand('meshlit.gateway.'+choice.id);
  if(choice?.id==='add')return vscode.commands.executeCommand('meshlit.addModelSource');
  if(choice?.id==='check')return vscode.commands.executeCommand('meshlit.checkConnection');
  if(choice)await vscode.commands.executeCommand('meshlit.chat',choice.id==='local'?undefined:choice.id);
 }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.connectors',async()=>{
  const servers=context.globalState.get('meshlit.mcp',[]);
  const choice=await vscode.window.showQuickPick([{label:'$(add) Add MCP connector',id:'add'},...servers.map(s=>({label:s.name,description:s.type,detail:s.url||s.command,id:s.name}))],{title:'MCP directory · configured connectors; enabled per agent'});
  if(choice?.id==='add')return vscode.commands.executeCommand('meshlit.addMcp');
  if(choice)await vscode.window.showInformationMessage('This connector is available when starting an Edit or Autopilot agent. Tool calls require approval.');
 }));
}
function render(){const nonce=crypto.randomBytes(24).toString('hex');return `<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';"><style nonce="${nonce}">
*{box-sizing:border-box}body{margin:0;background:var(--vscode-editor-background);color:var(--vscode-foreground);font:14px var(--vscode-font-family)}main{max-width:640px;margin:auto;padding:clamp(40px,12vh,140px) 32px 30px}.mark{display:block;margin:0 auto 22px;width:56px;height:56px}h1{text-align:center;font-size:24px;font-weight:500;letter-spacing:-.6px;margin:0 0 8px}.subtitle{text-align:center;color:var(--vscode-descriptionForeground);margin-bottom:40px}button{font:inherit;cursor:pointer;color:inherit;background:transparent;border:0}button:focus-visible{outline:2px solid var(--vscode-focusBorder);outline-offset:3px}.primary,.secondary{display:block;width:100%;padding:13px;margin:10px 0;border-radius:5px;background:var(--vscode-button-background);color:var(--vscode-button-foreground)}.secondary{background:var(--vscode-input-background);color:var(--vscode-foreground)}h2{font-size:13px;font-weight:500;margin:30px 0 12px;color:var(--vscode-descriptionForeground)}.grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.card{text-align:left;padding:16px;border:1px solid var(--vscode-panel-border);border-radius:8px}.card:hover{background:var(--vscode-list-hoverBackground)}.card span{display:block;color:var(--vscode-descriptionForeground);font-size:12px;line-height:1.5;margin-top:6px}footer{display:flex;gap:20px;flex-wrap:wrap;margin-top:28px;color:var(--vscode-textLink-foreground)}.note{font-size:12px;line-height:1.6;color:var(--vscode-descriptionForeground);margin-top:28px}@media(max-width:450px){main{padding:32px 20px}.grid{grid-template-columns:1fr}}
</style></head><body><main><svg class="mark" viewBox="0 0 56 56" aria-label="Meshlit"><path d="M8 44V12L28 32L48 12V44" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/></svg><h1>Welcome to Meshlit</h1><p class="subtitle">Chat with a model, choose a bot, or open a project.</p><button class="primary" data-action="chat">Start general chat</button><h2>Explore Studio</h2><div class="grid"><button class="card" data-action="folder">Workspaces<span>Open a project folder or return to a recent project.</span></button><button class="card" data-action="agent">Workspace agents<span>Plan, review edits, and run agent teams in your project.</span></button><button class="card" data-action="bots">Bots<span>Create a reusable assistant with your own instructions and model.</span></button><button class="card" data-action="ide">Open IDE<span>Browse files and use the full editor and terminal.</span></button></div><footer><button data-action="startup">Startup view</button><button data-action="models">Models</button><button data-action="usage">Usage</button><button data-action="settings">Settings</button></footer></main><script nonce="${nonce}">const api=acquireVsCodeApi();document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>api.postMessage({action:button.dataset.action}));</script></body></html>`;}
module.exports={register,render};
