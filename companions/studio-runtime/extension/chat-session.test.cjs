const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
function fixture(){
 const commands=new Map(),panels=[],outbound=[],picks=[],routes=[],messages=[];let event;
 const disposable={dispose(){}};
 const vscode={EventEmitter:class{fire(){}},TreeItem:class{},ThemeIcon:class{},ViewColumn:{Beside:2},StatusBarAlignment:{Right:1},
  workspace:{isTrusted:true,workspaceFolders:[{uri:{scheme:'file',fsPath:'/scratch'}}],registerTextDocumentContentProvider:()=>disposable},
  commands:{registerCommand:(name,fn)=>{commands.set(name,fn);return disposable;},executeCommand:async(name,...args)=>commands.get(name)?.(...args)},
  window:{showQuickPick:async()=>picks.shift(),showInformationMessage:m=>messages.push(m),showErrorMessage:m=>messages.push(m),registerTreeDataProvider:()=>disposable,createStatusBarItem:()=>({show(){}}),tabGroups:{all:[{tabs:[{}]}]},createWebviewPanel:(_type,_title,column)=>{const p={viewColumn:column===2?3:column,sent:[],webview:{postMessage:m=>p.sent.push(m),onDidReceiveMessage:f=>{p.receive=f;}},onDidDispose:f=>{p.dispose=f;}};panels.push(p);return p;}}
 };
 const child={stdin:{write:text=>outbound.push(JSON.parse(text)),end(){}},stdout:{},stderr:{on(){}},on(){}};
 const exports={},context={extensionPath:'/fixture',subscriptions:[]};
 const noop={register(){}};
 const imports={vscode,'node:fs':{readFileSync:()=>JSON.stringify({python:'/python',agentHost:'/agent'})},'node:child_process':{spawn:()=>child},'node:readline':{createInterface:()=>({on:(name,fn)=>{event=fn;}})},'./connections.cjs':{chooseConnection:async()=>routes.shift(),chooseMcp:async()=>[]},'./navigation.cjs':noop,'./app-access.cjs':noop,'./protocols.cjs':noop,'./deepseek-harness.cjs':noop,'./gateways.cjs':noop};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'extension.cjs'),'utf8'),{exports,require:n=>imports[n]||require(n.startsWith('.')?path.join(__dirname,n):n),process:{env:{}},Map,JSON});
 exports.activate(context);
 return {panels,outbound,picks,routes,messages,open:()=>commands.get('meshlit.chat')(),unlock:()=>{event(JSON.stringify({type:'ready'}));event(JSON.stringify({type:'unlocked'}));event(JSON.stringify({type:'local_model',state:'ready',label:'Bundled fixture'}));},done:id=>event(JSON.stringify({type:'done',session:id}))};
}
test('composer changes reach the SDK; existing sessions fork without changing their permissions',async()=>{
 const f=fixture();await f.open();const p=f.panels[0];f.unlock();
 f.picks.push({id:'plan'});await p.receive({op:'mode'});
 f.routes.push({kind:'compatible',id:'gateway',label:'OmniRoute · example',endpoint:'http://127.0.0.1:20128/v1',model:'example'});await p.receive({op:'connection'});
 await p.receive({op:'send',prompt:'Plan the change'});
 const send=f.outbound.at(-1);assert.equal(send.mode,'plan');assert.equal(send.workspace,'/scratch');assert.equal(send.route.id,'gateway');
 f.done(send.session);f.picks.push({id:'autopilot'});await p.receive({op:'mode'});
 assert.equal(f.panels.length,2);assert.equal(f.panels[1].viewColumn,f.panels[0].viewColumn);assert.equal(f.outbound.filter(m=>m.op==='send').length,1);
 await f.panels[1].receive({op:'send',prompt:'Implement reviewed steps'});assert.equal(f.outbound.at(-1).mode,'autopilot');assert.notEqual(f.outbound.at(-1).session,send.session);
 await p.receive({op:'send',prompt:'Continue planning'});assert.equal(f.outbound.at(-1).mode,'plan');
});
test('cancelled route selection keeps the local model',async()=>{
 const f=fixture();await f.open();const p=f.panels[0];await p.receive({op:'connection'});await p.receive({op:'ready'});
 assert.equal(p.sent.find(e=>e.type==='configuration').label,'Bundled local model');f.unlock();await p.receive({op:'send',prompt:'hello'});assert.equal(f.outbound.at(-1).route.kind,'local');
});

test('locked host rejects send before creating a runtime request',async()=>{
 const f=fixture();await f.open();await f.panels[0].receive({op:'send',prompt:'hello'});
 assert.equal(f.outbound.filter(e=>e.op==='send').length,0);
 assert.equal(f.panels[0].sent.at(-1).type,'error');
});
