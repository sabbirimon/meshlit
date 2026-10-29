const assert=require('node:assert/strict');
const vscode=require('vscode');
const fs=require('node:fs');
const http=require('node:http');
exports.run=async()=>{
 const extension=vscode.extensions.getExtension('meshlit.meshlit-workbench');
 assert(extension,'Bundled Meshlit extension not found');await extension.activate();
 const commands=await vscode.commands.getCommands(true);
 for(const command of ['meshlit.chat','meshlit.agent','meshlit.unlockUsage','workbench.action.terminal.new','workbench.action.findInFiles','meshlit.gateway.OmniRoute','meshlit.gateway.FreeLLMAPI','workbench.action.browser.open'])assert(commands.includes(command),command);
 await vscode.commands.executeCommand('meshlit.chat');
 await vscode.commands.executeCommand('meshlit.chat');
 for(let i=0;i<100&&!extension.exports.runtimeStatus().hostReady;i++)await new Promise(resolve=>setTimeout(resolve,100));
 assert(extension.exports.runtimeStatus().hostReady,'Actual SDK host did not start');
 const panels=vscode.window.tabGroups.all.flatMap(g=>g.tabs).filter(t=>t.input instanceof vscode.TabInputWebview);
 assert.equal(extension.exports.runtimeStatus().panels,2,'Both chat panels must remain open');
 const document=await vscode.workspace.openTextDocument({content:'Real Code OSS editor fixture',language:'javascript'});
 await vscode.window.showTextDocument(document);
 assert.equal(vscode.window.activeTextEditor.document.languageId,'javascript');
 await vscode.commands.executeCommand('workbench.action.revertAndCloseActiveEditor');
 const git=vscode.extensions.getExtension('vscode.git');assert(git,'Built-in Git extension missing');
 await git.activate();
 await vscode.commands.executeCommand('meshlit.gateway.OmniRoute');
 await vscode.commands.executeCommand('meshlit.gateway.FreeLLMAPI');
 assert(vscode.window.tabGroups.all.flatMap(g=>g.tabs).some(t=>t.label==='OmniRoute'));
 assert(vscode.window.tabGroups.all.flatMap(g=>g.tabs).some(t=>t.label==='FreeLLMAPI'));
 assert.equal(vscode.workspace.getConfiguration('workbench.browser').get('dataStorage'),'ephemeral');
 const hits=[];
 const server=http.createServer((req,res)=>{
  hits.push({url:req.url,cookie:req.headers.cookie||''});
  res.setHeader('X-Frame-Options','DENY');res.setHeader('Content-Security-Policy',"frame-ancestors 'none'");
  if(req.url==='/first')res.setHeader('Set-Cookie','meshlit_fixture=first; Path=/; HttpOnly; SameSite=Lax');
  res.end('<html><title>Meshlit dashboard fixture</title><body>Isolated dashboard fixture</body></html>');
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{
  const base='http://127.0.0.1:'+server.address().port;
  await vscode.commands.executeCommand('workbench.action.browser.open',{url:base+'/first'});
  for(let i=0;i<100&&!hits.some(h=>h.url==='/first');i++)await new Promise(r=>setTimeout(r,100));
  assert(hits.some(h=>h.url==='/first'),'First native browser did not load');
  await vscode.commands.executeCommand('workbench.action.browser.open',{url:base+'/second'});
  for(let i=0;i<100&&!hits.some(h=>h.url==='/second');i++)await new Promise(r=>setTimeout(r,100));
  assert(hits.some(h=>h.url==='/second'),'Second native browser did not load');
  assert(!hits.find(h=>h.url==='/second').cookie.includes('meshlit_fixture'),'Vendor sessions shared cookies');
 }finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
 fs.writeFileSync('/tmp/meshlit-workbench-ui-check.json',JSON.stringify({workbench:vscode.version,meshlitExtension:true,independentChats:2,separateGatewayPages:true,isolatedBrowserCookies:true,realEditor:true,gitExtension:true,terminalCommand:true,modelCalls:0},null,2));
 await vscode.commands.executeCommand('workbench.action.closeAllEditors');
};
