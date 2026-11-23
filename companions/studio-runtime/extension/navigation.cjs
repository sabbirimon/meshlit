const vscode=require('vscode');
const entries=[['New chat','chat','add'],['General chat','generalChat','comment-discussion'],
 ['Workspaces','workspaces','folder-opened'],['Workspace agents','workspaceAgents','code'],
 ['Bots','bots','hubot'],['Models','models','server'],['LLM settings','llmSettings','settings-gear'],
 ['Studio dashboard','home','home'],['Open IDE','openIde','window'],
 ['Tools',undefined,'tools',[['Workspace changes','workspaceChanges','diff'],
  ['Connected capabilities','connectedCapabilities','plug'],['System resources','systemMonitor','dashboard'],
  ['Voice and vision','voice','mic'],['Project memory','memory','book'],
  ['Computer companions','companions','remote']]],
 ['Usage','usage','graph'],['Startup view','startupView','layout'],['Settings','settings','settings-gear']];
const command=id=>id.startsWith('workbench.')?id:'meshlit.'+id;
const workspaceKey='meshlit.recentWorkspaces';
const localFolder=()=>vscode.workspace.workspaceFolders?.find(folder=>folder.uri.scheme==='file');
function register(context){
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
   const views=[{id:'chat',label:'General Chat'},{id:'welcome',label:'Studio dashboard'},{id:'editor',label:'Editor'}];
   const choice=await vscode.window.showQuickPick(views.map(view=>({...view,description:view.id===current?'Current default':undefined})),
    {title:'Startup view for windows without a workspace'});
   if(!choice)return;
   await setting.update('startupView',choice.id,vscode.ConfigurationTarget.Global);
   await vscode.commands.executeCommand({chat:'meshlit.generalChat',welcome:'meshlit.home',editor:'workbench.view.explorer'}[choice.id]);
  }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.home',()=>vscode.commands.executeCommand('meshlit.management')));
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
module.exports={register,render:require('./management.cjs').render};
