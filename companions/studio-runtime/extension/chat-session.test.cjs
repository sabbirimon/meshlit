const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const fixtures=new Set();
// Stop requests create real bounded device-controller timers. Exercise that
// controller, then dispose extension subscriptions just as the host does.
test.afterEach(async()=>{
 for(const context of fixtures){for(const item of [...context.subscriptions].reverse())item?.dispose?.();}
 fixtures.clear();
 await new Promise(resolve=>setImmediate(resolve));
});
function fixture(options={}){
 const commands=new Map(),panels=[],outbound=[],picks=[],routes=[],messages=[],edits=[],inputs=[],clipboard=[];let event;
 const disposable={dispose(){}};
 const vscode={env:{clipboard:{writeText:async text=>{if(options.clipboardError)throw Error('Clipboard unavailable');clipboard.push(text);}}},EventEmitter:class{fire(){}},TreeItem:class{},ThemeIcon:class{},ViewColumn:{One:1},StatusBarAlignment:{Right:1},Uri:{file:fsPath=>({fsPath}),parse:text=>text},WorkspaceEdit:class{replace(...args){this.args=args;}},Range:class{},
  workspace:{isTrusted:true,workspaceFolders:options.empty?[]:[{uri:{scheme:'file',fsPath:'/scratch'}}],getConfiguration:()=>({get:()=>options.startup||'chat'}),registerTextDocumentContentProvider:()=>disposable,openTextDocument:async()=>({isDirty:false,version:1,getText:()=> 'original',positionAt:n=>n}),applyEdit:async edit=>{edits.push(edit);return true;}},
  commands:{registerCommand:(name,fn)=>{commands.set(name,fn);return disposable;},executeCommand:async(name,...args)=>commands.get(name)?.(...args)},
  window:{showQuickPick:async()=>picks.shift(),showInputBox:async()=>inputs.shift(),showInformationMessage:(m,...choices)=>{messages.push(m);return choices[0];},showWarningMessage:async(_m,_options,choice)=>choice,showErrorMessage:m=>messages.push(m),registerTreeDataProvider:()=>disposable,createStatusBarItem:()=>({show(){},hide(){}}),tabGroups:{all:[{tabs:[{}]}]},createWebviewPanel:(_type,title,column)=>{const disposeListeners=[];const p={title,viewColumn:column,sent:[],reveal(){p.revealed=true;},webview:{postMessage:m=>p.sent.push(m),onDidReceiveMessage:f=>{p.receive=f;}},onDidDispose:f=>{disposeListeners.push(f);return disposable;},dispose(){if(p.disposed)return;p.disposed=true;for(const listener of disposeListeners)listener();}};panels.push(p);return p;}}
 };
 const hostEvents={};const child={stdin:{write:text=>outbound.push(JSON.parse(text)),end(){}},stdout:{},stderr:{on(){}},on:(name,fn)=>hostEvents[name]=fn};
 const storage=new Map(),exports={},context={extensionPath:'/fixture',subscriptions:[],globalState:{get:(key,fallback)=>storage.get(key)??fallback,update:async(key,value)=>storage.set(key,value)}};
 const noop={register(){}};
 const imports={vscode,'./project-security.cjs':{create:()=>({ensure:async()=>true,review:async()=>true,status:()=>null})},'./system-monitor.cjs':{create:()=>({open(){},accept(){},failure(){}})},'./connected-services.cjs':{register:()=>({contextFor:async()=>''})},'./agent-teams.cjs':{create:()=>({open(){},accept(){},failure(){},revokeSource(){}})},'./usage.cjs':{create:()=>({open(){},accept(){},failure(){},refresh(){}})},'node:fs':{readFileSync:()=>JSON.stringify({python:'/python',agentHost:'/agent'}),lstatSync:()=>options.newVault?undefined:{}},'node:child_process':{spawn:()=>child},'node:readline':{createInterface:()=>({on:(name,fn)=>{event=fn;}})},'./connections.cjs':{chooseConnection:async()=>routes.shift(),chooseMcp:async()=>options.connectors||[]},'./settings.cjs':noop,'./resource-sharing.cjs':noop,'./companions.cjs':noop,'./memory.cjs':{register:()=>options.memory||({contextFor:async()=>'',recordAction:async()=>{}})},'./voice.cjs':noop,'./navigation.cjs':noop,'./app-access.cjs':noop,'./protocols.cjs':noop,'./deepseek-harness.cjs':noop,'./gateways.cjs':noop};
 if(options.tools)imports['./workspace-tools.cjs']=options.tools;
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'extension.cjs'),'utf8'),{exports,require:n=>imports[n]||require(n.startsWith('.')?path.join(__dirname,n):n),process:{env:{}},Map,JSON});
 fixtures.add(context);exports.activate(context);
 return {panels,clipboard,outbound,picks,routes,messages,edits,inputs,storage,commands,vscode,hostEvents,event:e=>event(JSON.stringify(e)),open:()=>commands.get('meshlit.chat')(),unlock:()=>{event(JSON.stringify({type:'ready'}));event(JSON.stringify({type:'unlocked'}));event(JSON.stringify({type:'local_model',state:'ready',label:'Bundled fixture'}));},done:id=>event(JSON.stringify({type:'done',session:id}))};
}
test('an empty window opens general chat in the main editor group and confirms first setup',async()=>{
 const f=fixture({empty:true,newVault:true});
 await until(()=>f.panels.length===1);
 const panel=f.panels[0];
 assert.equal(panel.title,'General chat');assert.equal(panel.viewColumn,1);
 await f.commands.get('meshlit.generalChat')();assert.equal(panel.revealed,true);
 f.event({type:'ready'});
 assert.equal(panel.sent.at(-1).needsSetup,true);
 f.inputs.push('fixture passphrase','different passphrase');
 await panel.receive({op:'unlock'});
 assert.equal(f.outbound.filter(message=>message.op==='unlock').length,0);
 f.inputs.push('fixture passphrase','fixture passphrase');
 await panel.receive({op:'unlock'});
 assert.equal(f.outbound.at(-1).op,'unlock');
 const editor=fixture({empty:true,startup:'editor'});
 await new Promise(resolve=>setImmediate(resolve));assert.equal(editor.panels.length,0);
});

test('saved bots use their own chat-only instructions and no workspace context',async()=>{
 const f=fixture({memory:{contextFor:async()=>assert.fail('Bots must not read project memory')}});
 f.picks.push({action:'create'},{id:'local'});
 f.inputs.push('Build notes','Summarize changes in plain language.');
 await f.commands.get('meshlit.bots')();
 const [bot]=f.storage.get('meshlit.bots');
 f.picks.push({bot},'Start bot chat');
 await f.commands.get('meshlit.bots')();
 const panel=f.panels[0];assert.equal(panel.title,'Bot · Build notes');
 f.unlock();
 await panel.receive({op:'mode'});
 await panel.receive({op:'send',prompt:'Summarize this.'});
 const request=f.outbound.at(-1);
 assert.equal(request.mode,'chat');assert.equal(request.workspace,undefined);
 assert.deepEqual(request.connectors,[]);assert.equal(request.botInstructions,bot.instructions);
});
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
test('effort changes stay in the same compatible chat and local unsupported choices preserve Auto',async()=>{
 const f=fixture();await f.open();f.unlock();const panel=f.panels[0];
 f.picks.push({id:'high'});await panel.receive({op:'effort'});
 assert.match(panel.sent.at(-1).message,/unavailable/);
 await panel.receive({op:'send',prompt:'local question'});assert.equal(f.outbound.at(-1).effort,'auto');f.done(f.outbound.at(-1).session);
 const route={kind:'compatible',endpoint:'https://example.test/v1',model:'reasoning-fixture',effortSupport:{endpoint:'https://example.test/v1',model:'reasoning-fixture',adapter:'reasoning_effort',values:['low','high']}};
 f.routes.push(route);await panel.receive({op:'connection'});const remote=f.panels[1];
 f.picks.push({id:'high'});await remote.receive({op:'effort'});await remote.receive({op:'send',prompt:'hard question'});
 const first=f.outbound.at(-1);assert.equal(first.effort,'high');f.done(first.session);
 f.picks.push({id:'low'});await remote.receive({op:'effort'});await remote.receive({op:'send',prompt:'short follow-up'});
 assert.equal(f.outbound.at(-1).effort,'low');assert.equal(f.outbound.at(-1).session,first.session);assert.equal(f.panels.length,2);
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
test('Auto workspace reads skip initial read prompts but an edit still needs diff Apply',async()=>{
 const document={file:'/scratch/file.txt',hash:'same',text:'original'},f=fixture({tools:{read:async()=>document,replacement:()=> 'replacement'}});
 await f.open();f.unlock();const panel=f.panels[0];f.picks.push({id:'edit'});await panel.receive({op:'mode'});
 f.picks.push({id:'read-only'});await panel.receive({op:'approvals'});await panel.receive({op:'send',prompt:'Review edit'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'read-auto',session,name:'read_file',arguments:{path:'file.txt'}});await until(()=>f.outbound.some(e=>e.id==='read-auto'));
 assert.equal(f.messages.some(m=>m.includes('read_file —')),false);
 f.event({type:'tool',id:'edit-auto',session,name:'propose_edit',arguments:{path:'file.txt',old:'original',new:'replacement'}});await until(()=>f.outbound.some(e=>e.id==='edit-auto'));
 assert.equal(f.messages.some(m=>m.includes('propose_edit —')),true);assert.equal(f.messages.some(m=>m.includes('apply the reviewed edit')),true);assert.equal(f.edits.length,1);
});
test('Selected workspace tools only skip selected initial prompts, and later policy changes open a fresh chat',async()=>{
 const document={file:'/scratch/file.txt',hash:'same',text:'original'},f=fixture({tools:{read:async()=>document,replacement:()=> 'replacement'}});
 await f.open();f.unlock();const panel=f.panels[0];f.picks.push({id:'edit'});await panel.receive({op:'mode'});
 f.picks.push({id:'selected'},[{id:'read_file'},{id:'propose_edit'}]);await panel.receive({op:'approvals'});await panel.receive({op:'send',prompt:'Review selected tools'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'read-selected',session,name:'read_file',arguments:{path:'file.txt'}});await until(()=>f.outbound.some(e=>e.id==='read-selected'));
 f.event({type:'tool',id:'edit-selected',session,name:'propose_edit',arguments:{path:'file.txt',old:'original',new:'replacement'}});await until(()=>f.outbound.some(e=>e.id==='edit-selected'));
 assert.equal(f.messages.some(m=>m.includes('read_file —')||m.includes('propose_edit —')),false);
 assert.equal(f.messages.some(m=>m.includes('apply the reviewed edit')),true);assert.equal(f.edits.length,1);
 f.done(session);f.picks.push({id:'ask'});await panel.receive({op:'approvals'});assert.equal(f.panels.length,2);
 await f.panels[1].receive({op:'ready'});assert.equal(f.panels[1].sent.find(e=>e.type==='configuration').approval,'ask');
 await panel.receive({op:'ready'});assert.equal(panel.sent.filter(e=>e.type==='configuration').at(-1).approval,'selected');
});
test('Stop while workspace read history is saving suppresses the file result',async()=>{
 let release,recording=false;const pending=new Promise(resolve=>release=resolve);
 const f=fixture({tools:{read:async()=>({file:'/scratch/file.txt',hash:'same',text:'private fixture content'})},memory:{contextFor:async()=>'',recordAction:async record=>{if(record.status==='complete'){recording=true;await pending;}}}});
 await f.open();f.unlock();const panel=f.panels[0];f.picks.push({id:'plan'});await panel.receive({op:'mode'});f.picks.push({id:'read-only'});await panel.receive({op:'approvals'});await panel.receive({op:'send',prompt:'Read selected file'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'delayed-read',session,name:'read_file',arguments:{path:'file.txt'}});await until(()=>recording);await panel.receive({op:'stop'});release();await until(()=>f.outbound.some(e=>e.id==='delayed-read'));
 const result=f.outbound.find(e=>e.id==='delayed-read').result;assert.match(result,/denied/);assert.doesNotMatch(result,/private fixture content/);
});

test('approved workspace autonomy applies bounded edits without another prompt and keeps review',async()=>{
 const document={file:'/scratch/file.txt',hash:'same',text:'original'},f=fixture({tools:{read:async()=>document,replacement:()=> 'replacement'}});
 await f.open();f.unlock();const panel=f.panels[0];f.picks.push({id:'autopilot'});await panel.receive({op:'mode'});
 f.picks.push({id:'workspace'});await panel.receive({op:'approvals'});await panel.receive({op:'send',prompt:'Apply the scoped change'});const session=f.outbound.at(-1).session;
 f.event({type:'tool',id:'auto-read',session,name:'read_file',arguments:{path:'file.txt'}});await until(()=>f.outbound.some(e=>e.id==='auto-read'));
 f.event({type:'tool',id:'auto-edit',session,name:'propose_edit',arguments:{path:'file.txt',old:'original',new:'replacement'}});await until(()=>f.outbound.some(e=>e.id==='auto-edit'));
 assert.equal(f.edits.length,1);assert.equal(f.messages.some(m=>m.includes('apply the reviewed edit')||m.includes('propose_edit —')||m.includes('read_file —')),false);
 assert.match(f.outbound.find(e=>e.id==='auto-edit').result,/UNSAVED/);
});

test('saved personality reaches a general chat request through the actual extension handler',async()=>{
 const f=fixture({empty:true});await until(()=>f.panels.length===1);f.unlock();
 f.storage.set('meshlit.chatPersonality',{personality:'teacher',customInstructions:'Use one practical example.',contentStyle:'direct'});
 await f.panels[0].receive({op:'send',prompt:'Explain fractions.'});
 const request=f.outbound.findLast(message=>message.op==='send');assert.equal(request.mode,'chat');
 assert.equal(request.chatSettings.personality,'teacher');assert.equal(request.chatSettings.customInstructions,'Use one practical example.');assert.equal(request.chatSettings.contentStyle,'direct');
 assert.equal(request.workspace,undefined);
});
test('a bot started from its detail window opens chat in that editor group',async()=>{
 const f=fixture();await f.open();
 f.storage.set('meshlit.bots',[{id:'window-bot',name:'Window helper',instructions:'Give short answers.',modelId:'local'}]);
 await f.commands.get('meshlit.openBotDetails')('window-bot');
 const detail=f.panels.at(-1);detail.viewColumn=7;
 await detail.receive({botDetailsPage:true,op:'start'});
 const chat=f.panels.at(-1);assert.notEqual(chat,detail);assert.equal(chat.title,'Bot · Window helper');assert.equal(chat.viewColumn,7);
});
test('copy message reaches the host clipboard and acknowledgement through the extension',async()=>{
 const f=fixture();await f.open();const panel=f.panels[0];
 await panel.receive({op:'copyMessage',text:'বাংলা <literal> & English'});
 assert.deepEqual(f.clipboard,['বাংলা <literal> & English']);assert.equal(panel.sent.at(-1).type,'messageAction');assert.equal(panel.sent.at(-1).message,'Message copied.');
 assert.equal(f.outbound.some(message=>message.op==='send'),false);
 const failed=fixture({clipboardError:true});await failed.open();await failed.panels[0].receive({op:'copyMessage',text:'Keep this draft'});
 assert.equal(failed.clipboard.length,0);assert.match(failed.panels[0].sent.at(-1).error,/did not complete/);
});
