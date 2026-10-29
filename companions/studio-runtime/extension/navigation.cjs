const vscode=require('vscode');
const crypto=require('node:crypto');
const entries=[['Home','home','home'],['Chat','chat','comment-discussion'],['Workspace','workbench.action.files.openFolder','folder-opened'],['Coding agent','agent','code'],['Models','models','server'],['MCP directory','connectors','plug'],['Usage','usage','graph'],['Settings','workbench.action.openSettings','settings-gear']];
const command=id=>id.startsWith('workbench.')?id:'meshlit.'+id;
function register(context){
 let home;
 context.subscriptions.push(vscode.window.registerTreeDataProvider('meshlit.navigation',{
  getChildren:()=>entries,
  getTreeItem:([label,id,icon])=>{const item=new vscode.TreeItem(label);item.iconPath=new vscode.ThemeIcon(icon);item.command={command:command(id),title:label};return item;}
 }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.home',()=>{
  if(home){home.reveal(vscode.ViewColumn.One);return;}
  home=vscode.window.createWebviewPanel('meshlit.home','Meshlit',vscode.ViewColumn.One,{enableScripts:true,localResourceRoots:[]});
  home.webview.html=render();
  home.webview.onDidReceiveMessage(async({action})=>{
   const allowed={folder:'workbench.action.files.openFolder',clone:'git.clone',chat:'meshlit.chat',agent:'meshlit.agent',models:'meshlit.models',mcp:'meshlit.connectors',usage:'meshlit.usage',settings:'workbench.action.openSettings',unlock:'meshlit.unlockUsage'};
   if(allowed[action])await vscode.commands.executeCommand(allowed[action]);
  });
  home.onDidDispose(()=>home=undefined);
 }));
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.models',async()=>{
  const profiles=context.globalState.get('meshlit.connections',[]);
  const choice=await vscode.window.showQuickPick([{label:'$(add) Add model connection',id:'add'},{label:'$(pulse) Check API connection',id:'check'},{label:'$(chip) Bundled local model',detail:'Choose from any chat. No provider account needed.',id:'local'},...profiles.map(p=>({label:p.service+' · '+p.model,detail:p.endpoint,id:p.id}))],{title:'Meshlit · Models'});
  if(choice?.id==='add')return vscode.commands.executeCommand('meshlit.addConnection');
  if(choice?.id==='check')return vscode.commands.executeCommand('meshlit.checkConnection');
  if(choice)await vscode.commands.executeCommand('meshlit.chat');
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
</style></head><body><main><svg class="mark" viewBox="0 0 56 56" aria-label="Meshlit"><path d="M8 44V12L28 32L48 12V44" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/></svg><h1>Meshlit Studio</h1><p class="subtitle">Your workspace. Your models. Your agents.</p><button class="primary" data-action="folder">Open Folder</button><button class="secondary" data-action="clone">Clone Repository</button><h2>Start working</h2><div class="grid"><button class="card" data-action="chat">New chat<span>Keep conversations open while agents work.</span></button><button class="card" data-action="agent">Coding agent<span>Plan, review edits, and work in your project.</span></button><button class="card" data-action="models">Model connections<span>Local, distributed, and online providers.</span></button><button class="card" data-action="mcp">MCP directory<span>Choose the tools available to your agents.</span></button></div><footer><button data-action="usage">Usage</button><button data-action="unlock">Unlock usage</button><button data-action="settings">Settings</button></footer><p class="note">Development preview · The editor, Git, terminal and extensions use Code OSS. Older Studio device and VPN dashboards are not yet migrated into this workbench.</p></main><script nonce="${nonce}">const api=acquireVsCodeApi();document.querySelectorAll('[data-action]').forEach(button=>button.onclick=()=>api.postMessage({action:button.dataset.action}));</script></body></html>`;}
module.exports={register,render};
