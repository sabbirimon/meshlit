// Run with Code OSS --extensionTestsPath against an isolated empty test profile.
const assert=require('node:assert/strict');
const vscode=require('vscode');
const fs=require('node:fs');
exports.run=async()=>{
 const extension=vscode.extensions.getExtension('meshlit.meshlit-workbench');
 assert(extension,'Meshlit extension missing');
 await extension.activate();
 const commands=await vscode.commands.getCommands(true);
 assert(commands.includes('meshlit.deepseekHarness'),'Harness command missing');
 assert(commands.includes('workbench.action.browser.open'),'Native integrated browser missing');
 assert(extension.packageJSON.contributes.commands.some(c=>c.command==='meshlit.deepseekHarness'));
 // Do not answer a connection/approval dialog or start the model host in this test.
 fs.writeFileSync(process.env.MESHLIT_HARNESS_WORKBENCH_EVIDENCE,JSON.stringify({
  workbench:vscode.version,extension:extension.packageJSON.version,
  harnessCommandRegistered:true,nativeBrowserAvailable:true,modelRequests:0,
  renderedHarnessUi:false
 },null,2));
 await vscode.commands.executeCommand('workbench.action.closeAllEditors');
};
