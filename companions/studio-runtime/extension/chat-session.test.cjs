const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
function fixture(options={}){
 const commands=new Map(),panels=[],outbound=[],picks=[],routes=[],messages=[],edits=[];let event;
 const disposable={dispose(){}};
 const vscode={EventEmitter:class{fire(){}},TreeItem:class{},ThemeIcon:class{},ViewColumn:{Beside:2},StatusBarAlignment:{Right:1},Uri:{file:fsPath=>({fsPath}),parse:text=>text},WorkspaceEdit:class{replace(...args){this.args=args;}},Range:class{},
  workspace:{isTrusted:true,workspaceFolders:[{uri:{scheme:'file',fsPath:'/scratch'}}],registerTextDocumentContentProvider:()=>disposable,openTextDocument:async()=>({isDirty:false,version:1,getText:()=> 'original',positionAt:n=>n}),applyEdit:async edit=>{edits.push(edit);return true;}},
  commands:{registerCommand:(name,fn)=>{commands.set(name,fn);return disposable;},executeCommand:async(name,...args)=>commands.get(name)?.(...args)},
  window:{showQuickPick:async()=>picks.shift(),showInformationMessage:(m,...choices)=>{messages.push(m);return choices[0];},showWarningMessage:async(_m,_options,choice)=>choice,showErrorMessage:m=>messages.push(m),registerTreeDataProvider:()=>disposable,createStatusBarItem:()=>({show(){}}),tabGroups:{all:[{tabs:[{}]}]},createWebviewPanel:(_type,_title,column)=>{const p={viewColumn:column===2?3:column,sent:[],webview:{postMessage:m=>p.sent.push(m),onDidReceiveMessage:f=>{p.receive=f;}},onDidDispose:f=>{p.dispose=f;}};panels.push(p);return p;}}
 };
 const hostEvents={};const child={stdin:{write:text=>outbound.push(JSON.parse(text)),end(){}},stdout:{},stderr:{on(){}},on:(name,fn)=>hostEvents[name]=fn};
 const exports={},context={extensionPath:'/fixture',subscriptions:[]};
 const noop={register(){}};
 const imports={vscode,'./usage.cjs':{create:()=>({open(){},accept(){},failure(){},refresh(){}})},'node:fs':{readFileSync:()=>JSON.stringify({python:'/python',agentHost:'/agent'})},'node:child_process':{spawn:()=>child},'node:readline':{createInterface:()=>({on:(name,fn)=>{event=fn;}})},'./connections.cjs':{chooseConnection:async()=>routes.shift(),chooseMcp:async()=>options.connectors||[]},'./settings.cjs':noop,'./resource-sharing.cjs':noop,'./companions.cjs':noop,'./memory.cjs':{register:()=>options.memory||({contextFor:async()=>'',recordAction:async()=>{}})},'./voice.cjs':noop,'./navigation.cjs':noop,'./app-access.cjs':noop,'./protocols.cjs':noop,'./deepseek-harness.cjs':noop,'./gateways.cjs':noop};
 if(options.tools)imports['./workspace-tools.cjs']=options.tools;
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'extension.cjs'),'utf8'),{exports,require:n=>imports[n]||require(n.startsWith('.')?path.join(__dirname,n):n),process:{env:{}},Map,JSON});
 exports.activate(context);
 return {panels,outbound,picks,routes,messages,edits,vscode,hostEvents,event:e=>event(JSON.stringify(e)),open:()=>commands.get('meshlit.chat')(),unlock:()=>{event(JSON.stringify({type:'ready'}));event(JSON.stringify({type:'unlocked'}));event(JSON.stringify({type:'local_model',state:'ready',label:'Bundled fixture'}));},done:id=>event(JSON.stringify({type:'done',session:id}))};
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
test('unlock failures are visible and revoke stale unlocked state',async()=>{
 const f=fixture();await f.open();f.unlock();
 f.event({type:'error',operation:'unlock',message:'Cannot unlock usage'});
 assert(f.messages.includes('Cannot unlock usage'));
 assert.equal(f.panels[0].sent.at(-1).type,'notice');
 assert.equal(f.panels[0].sent.filter(e=>e.type==='runtime').at(-1).unlocked,false);
 await f.panels[0].receive({op:'send',prompt:'hello'});
 assert.equal(f.outbound.filter(e=>e.op==='send').length,0);
});
test('failed host startup clears readiness and permits a fresh host attempt',async()=>{
 const f=fixture();await f.open();f.unlock();f.hostEvents.error();
 assert.equal(f.panels[0].sent.filter(e=>e.type==='runtime').at(-1).ready,false);
 assert(f.messages.includes('Cannot start the Meshlit SDK host.'));
 await f.open();assert.equal(f.panels.length,2);
});

test('chat tools menu can open a tool-enabled mode before first send',async()=>{
 const f=fixture();await f.open();f.unlock();
 f.picks.push({op:'mode'},{id:'edit'});await f.panels[0].receive({op:'actions'});
 await f.panels[0].receive({op:'send',prompt:'Review the workspace'});
 assert.equal(f.outbound.at(-1).mode,'edit');
});

async function until(predicate){for(let i=0;i<100;i++){if(predicate())return;await new Promise(resolve=>setImmediate(resolve));}assert.fail('Expected asynchronous event did not arrive');}
test('memory context gets a bounded budget and follows the approved project and destination',async()=>{
 const queries=[],f=fixture({memory:{contextFor:async args=>{queries.push(args);assert.ok(args.maxCharacters<=3000);return args.maxCharacters?'Remembered project note':'';},recordAction:async()=>{}}});
 await f.open();f.unlock();f.vscode.workspace.workspaceFolders=[{uri:{scheme:'file',fsPath:'/new-project'}}];f.picks.push({id:'plan'});await f.panels[0].receive({op:'mode'});
 f.routes.push({kind:'compatible',label:'Local host',endpoint:'http://127.0.0.1:8000/v1'});await f.panels[0].receive({op:'connection'});
 await f.panels[0].receive({op:'send',prompt:'hello'});assert.equal(queries[0].root,'/new-project');assert.equal(queries[0].remote,true);assert.equal(queries[0].maxCharacters,3000);assert.match(f.outbound.at(-1).prompt,/Remembered project note\n\nhello/);
 f.done(f.outbound.at(-1).session);await f.panels[0].receive({op:'send',prompt:'a'.repeat(16000)});assert.equal(queries.at(-1).maxCharacters,0);assert.equal(f.outbound.at(-1).prompt.length,16000);
});
test('Stop during memory lookup prevents request dispatch',async()=>{
 let release,called=false;const pending=new Promise(resolve=>release=resolve),f=fixture({memory:{contextFor:async()=>{called=true;return pending;},recordAction:async()=>{}}});
 await f.open();f.unlock();const sending=f.panels[0].receive({op:'send',prompt:'hello'});await until(()=>called);await f.panels[0].receive({op:'stop'});release('untrusted memory');await sending;assert.equal(f.outbound.filter(e=>e.op==='send').length,0);
});
test('memory failure sends no model request and leaves an unstarted chat configurable',async()=>{
 const f=fixture({memory:{contextFor:async()=>{throw Error('Locked vault');},recordAction:async()=>{}}});await f.open();f.unlock();await f.panels[0].receive({op:'send',prompt:'hello'});
 assert.equal(f.outbound.filter(e=>e.op==='send').length,0);assert.match(f.panels[0].sent.at(-1).message,/No model request was sent/);f.picks.push({id:'plan'});await f.panels[0].receive({op:'mode'});assert.equal(f.panels.length,1);
});
test('Stop while approval history is saving cannot authorize a later MCP call',async()=>{
 let release,recording=false;const pending=new Promise(resolve=>release=resolve),records=[];
 const f=fixture({connectors:[{name:'fixture'}],memory:{contextFor:async()=>'',recordAction:async record=>{records.push(record);if(record.status==='approved'){recording=true;await pending;}}}});
 await f.open();f.unlock();f.picks.push({id:'edit'});await f.panels[0].receive({op:'mode'});await f.panels[0].receive({op:'send',prompt:'Use reviewed tools'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'mcp-review',session,name:'mcp_request',arguments:{tool:'fixture',arguments:{}}});await until(()=>recording);await f.panels[0].receive({op:'stop'});release();await until(()=>f.outbound.some(e=>e.id==='mcp-review'));
 assert.match(f.outbound.find(e=>e.id==='mcp-review').result,/denied/);assert.equal(records.at(-1).status,'cancelled');
});
test('Stop during final file revalidation prevents an editor mutation',async()=>{
 let reads=0,release,blocked=false;const pending=new Promise(resolve=>release=resolve),document={file:'/scratch/file.txt',hash:'same',text:'original'};
 const f=fixture({tools:{read:async()=>{reads++;if(reads===3){blocked=true;await pending;}return document;},replacement:()=> 'replacement'}});
 await f.open();f.unlock();f.picks.push({id:'edit'});await f.panels[0].receive({op:'mode'});await f.panels[0].receive({op:'send',prompt:'Review an edit'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'read',session,name:'read_file',arguments:{path:'file.txt'}});await until(()=>f.outbound.some(e=>e.id==='read'));
 f.event({type:'tool',id:'edit',session,name:'propose_edit',arguments:{path:'file.txt',old:'original',new:'replacement'}});await until(()=>blocked);await f.panels[0].receive({op:'stop'});release();await until(()=>f.outbound.some(e=>e.id==='edit'));
 assert.equal(f.edits.length,0);assert.match(f.outbound.find(e=>e.id==='edit').result,/Cancelled/);
});
