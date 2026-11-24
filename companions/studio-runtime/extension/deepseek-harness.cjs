const vscode=require('vscode');
const {parseHarnessEndpoint}=require('./harness-endpoint.cjs');
function register(context){
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.deepseekHarness',async()=>{
  const choice=await vscode.window.showQuickPick([
   {label:'Open DeepSeek Harness',id:'open',description:'Real upstream workspace inside Meshlit'},
   {label:'Local, cluster and provider model setup',id:'models'},
   {label:'Setup and permission boundaries',id:'help'},
   {label:'Forget saved host address',id:'forget'}
  ],{title:'DeepSeek Harness · optional host runtime'});
  if(!choice)return;
  if(choice.id==='forget'){await context.globalState.update('meshlit.deepseekHarness.endpoint',undefined);return vscode.window.showInformationMessage('Host address forgotten. Close its browser tab to end that temporary browser session. Running host jobs are not stopped.');}
  if(choice.id==='models')return vscode.window.showInformationMessage('Inside Harness: Settings → Models → Add model provider → Custom model API. Enter your local, multi-model, remote device or cluster endpoint, choose OpenAI Chat Completions, and fetch/select model IDs. OmniRoute and FreeLLMAPI use their gateway URL and gateway key; Hugging Face uses its inference endpoint and scoped token. A file path is not an API. Loopback is relative to the Harness host, not the phone. No automatic paid fallback or account provisioning is performed by Meshlit.',{modal:true});
  if(choice.id==='help')return vscode.window.showInformationMessage('Start the reviewed dsh web host with a private DSH_HOME. Paste its launch URL below. Harness owns its model accounts, MCP tools, approvals and sessions; Meshlit budgets and Stop do not control these jobs. Android connects to the same host over HTTPS.',{modal:true});
  let value=await vscode.window.showInputBox({title:'Connect to DeepSeek Harness',password:true,ignoreFocusOut:true,
   value:context.globalState.get('meshlit.deepseekHarness.endpoint','http://127.0.0.1:3080/'),
   prompt:'Paste the host’s launch URL, including ?token=… for login. Only the clean host address is saved.',
   validateInput:v=>{try{parseHarnessEndpoint(v);return undefined;}catch{return 'Use HTTPS or literal loopback HTTP, a root ending in /, and an optional login token.';}}});
  if(!value)return;
  try{
   const address=parseHarnessEndpoint(value);value=undefined;
   const allow=await vscode.window.showWarningMessage(`Open ${address.endpoint}? Harness can act on its host under its own permissions. Its model charges are separate from Meshlit’s usage ledger. Closing this tab does not stop host jobs.`,{modal:true},'Open Harness');
   if(allow!=='Open Harness')return;
   const commands=await vscode.commands.getCommands(true);
   if(!commands.includes('workbench.action.browser.open'))throw Error('Integrated browser unavailable');
   const browser=vscode.workspace.getConfiguration('workbench.browser');
   if(browser.get('dataStorage')!=='ephemeral'){
    const answer=await vscode.window.showInformationMessage('Use isolated temporary browser sessions for Harness? This setting also applies to newly opened integrated browser tabs.','Use temporary sessions');
    if(!answer)return;
    await browser.update('dataStorage','ephemeral',vscode.ConfigurationTarget.Global);
   }
   await vscode.commands.executeCommand('workbench.action.browser.open',{url:address.launchUrl,openToSide:true});
   await context.globalState.update('meshlit.deepseekHarness.endpoint',address.endpoint);
  }catch{vscode.window.showErrorMessage('Could not open Harness. Verify the host address and integrated browser availability.');}
 }));
}
module.exports={register};
