const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),fsp=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
async function fixture(t){
 const root=await fsp.mkdtemp(path.join(os.tmpdir(),'meshlit-memory-ui-'));t.after(()=>fsp.rm(root,{recursive:true,force:true}));
 const commands=new Map(),messages=[],values=new Map();let receive;
 const panel={webview:{onDidReceiveMessage:f=>receive=f,postMessage:async m=>messages.push(m)},onDidDispose(){},reveal(){}};
 const vscode={workspace:{isTrusted:true,workspaceFolders:[{name:'Project',uri:{scheme:'file',fsPath:root}}]},ViewColumn:{Active:1},commands:{registerCommand:(name,handler)=>{commands.set(name,handler);return {}; }},window:{createWebviewPanel:()=>panel,showErrorMessage:message=>messages.push({type:'error',message}),showWarningMessage:async(_message,_options,answer)=>answer}};
 const module={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'memory.cjs'),'utf8'),{module,require:name=>name==='vscode'?vscode:name.startsWith('./')?require(path.join(__dirname,name)):require(name)});
 const api=module.exports.register({subscriptions:[],globalStorageUri:{fsPath:path.join(root,'storage')},secrets:{get:async k=>values.get(k),store:async(k,v)=>values.set(k,v)}});
 await commands.get('meshlit.memory')();await receive({op:'ready'});return {api,root,commands,messages,panel,vscode,receive:message=>receive(message)};
}
test('memory controller saves, previews, forgets and gates recall on current project trust',async t=>{
 const f=await fixture(t);assert.equal(f.messages.at(-1).policy.remoteRecall,false);
 await f.receive({op:'save',title:'<script>decision</script>',text:'amber deployment uses a CPU'});assert.equal(f.messages.at(-1).notes.length,1);
 await f.receive({op:'policy',policy:{localRecall:true,remoteRecall:false,actionHistory:true}});
 await f.receive({op:'preview',query:'amber',remote:false});assert.match(f.messages.at(-1).text,/amber/);
 assert.equal(await f.api.contextFor({root:f.root,query:'amber',remote:true}),'');assert.match(await f.api.contextFor({root:f.root,query:'amber',remote:false}),/untrusted/);
 await f.api.recordAction({root:f.root,tool:'read_file',status:'complete',mode:'plan'});assert.equal(f.messages.at(-1).actions.length,1);
 const note=f.messages.at(-1).notes[0];await f.receive({op:'forget',id:note.id});assert.equal(f.messages.at(-1).notes.length,0);
 f.vscode.workspace.isTrusted=false;await assert.rejects(f.api.contextFor({root:f.root,query:'amber',remote:false}),/Trust/);
 f.vscode.workspace.isTrusted=true;f.vscode.workspace.workspaceFolders=[];await assert.rejects(f.api.recordAction({root:f.root,tool:'read_file',status:'complete',mode:'plan'}),/no longer open/);
});
test('memory UI is executable, uses safe text rendering, and labels character counts honestly',async t=>{
 const f=await fixture(t),html=f.panel.webview.html,script=html.match(/<script nonce="[^"]+">([\s\S]*?)<\/script>/)[1];new vm.Script(script);
 assert.match(html,/default-src 'none'/);assert.match(script,/textContent=n.text/);assert.doesNotMatch(script,/innerHTML/);assert.match(script,/Token count unavailable/);
 assert.ok(f.commands.has('meshlit.contextPreview'));assert.ok(f.commands.has('meshlit.actionHistory'));
 await f.receive({op:'preview',query:'hello',remote:'yes'});assert.equal(f.messages.at(-1).type,'error');
});
