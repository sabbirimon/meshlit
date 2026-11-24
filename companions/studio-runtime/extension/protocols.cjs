const vscode=require('vscode');
function register(context){
 context.subscriptions.push(vscode.commands.registerCommand('meshlit.protocols',async()=>{
  const choice=await vscode.window.showQuickPick([
   {label:'Connect a model source',detail:'This device, local servers, remote device, cluster or cloud.',id:'meshlit.addModelSource'},
   {label:'MCP tool connections',detail:'Installed stdio or Streamable HTTP servers; tools require per-call approval.',id:'meshlit.connectors'},
   {label:'DeepSeek Harness models',detail:'Inside Harness: Settings → Models → Add model provider → Custom model API.',id:'meshlit.deepseekHarness'},
   {label:'Android Agent Gateway',detail:'Settings → Agent Gateway: /mcp, /a2a, /v1/models, /v1/chat/completions.',id:'android-gateway'},
   {label:'Paired-device HTTPS control',detail:'Android Devices → Web/API devices and groups: enrollment, status, jobs, commands and cancel.',id:'device-api'}
  ],{title:'Meshlit APIs and protocols'});
  if(!choice)return;
  if(choice.id.startsWith('meshlit.'))return vscode.commands.executeCommand(choice.id);
  await vscode.window.showInformationMessage(choice.id==='android-gateway'
   ?'The phone gateway is off by default and requires a client bearer token. It binds to loopback. Remote clients need a verified private tunnel. Its model endpoint is separate from device control. The Studio gateway-host dashboard is not yet ported.'
   :'Enable the phone’s Web/API listener on HTTPS port 18792, create an invitation, and approve the enrolled client’s scopes on that phone. /api/v1/status, /commands, /jobs and /cancel require an approved bearer token. Verify the certificate. This is not native model-worker enrollment.',{modal:true});
 }));
}
module.exports={register};
