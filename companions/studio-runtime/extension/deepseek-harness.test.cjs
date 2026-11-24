const {test}=require('node:test');const assert=require('node:assert/strict');
const vm=require('node:vm');const fs=require('node:fs');const path=require('node:path');
function fixture({approval=true,browser=true}={}){
 const commands=new Map(),writes=[],opens=[],errors=[];
 const vscode={ConfigurationTarget:{Global:1},window:{
  showQuickPick:async()=>({id:'open'}),showInputBox:async options=>{assert.equal(options.password,true);return 'https://host.example/?token=abcdefghijklmnop';},
  showWarningMessage:async text=>{assert(!text.includes('token='));return approval?'Open Harness':undefined;},
  showInformationMessage:async()=>{},showErrorMessage:msg=>errors.push(msg)
 },workspace:{getConfiguration:()=>({get:()=> 'ephemeral'})},commands:{
  registerCommand:(id,fn)=>{commands.set(id,fn);return{dispose(){}};},
  getCommands:async()=>browser?['workbench.action.browser.open']:[],executeCommand:async(...args)=>opens.push(args)
 }};
 const context={subscriptions:[],globalState:{get:(_,fallback)=>fallback,update:async(...args)=>writes.push(args)}};
 const module={exports:{}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'deepseek-harness.cjs'),'utf8'),{module,require:id=>id==='vscode'?vscode:require(id)});
 module.exports.register(context);
 return {run:()=>commands.get('meshlit.deepseekHarness')(),writes,opens,errors};
}
test('opens the real integrated browser and saves no login secret',async()=>{
 const f=fixture();await f.run();assert.equal(f.opens.length,1);
 assert.equal(f.opens[0][0],'workbench.action.browser.open');
 assert.equal(f.opens[0][1].url,'https://host.example/?token=abcdefghijklmnop');
 assert.deepEqual(f.writes,[['meshlit.deepseekHarness.endpoint','https://host.example/']]);
});
test('cancelled permission opens nothing and saves nothing',async()=>{
 const f=fixture({approval:false});await f.run();assert.equal(f.opens.length,0);assert.equal(f.writes.length,0);
});
test('missing embedded browser fails without external fallback or token disclosure',async()=>{
 const f=fixture({browser:false});await f.run();assert.equal(f.opens.length,0);assert.equal(f.writes.length,0);
 assert.equal(f.errors.length,1);assert(!f.errors[0].includes('abcdefghijklmnop'));
});
