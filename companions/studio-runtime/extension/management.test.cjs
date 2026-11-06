const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {render,runtimeSummary,groups,destinations}=require('./management.cjs');
function fixture(){
 const commands=new Map(),calls=[],panels=[];let runtime={},failRuntime=false,failCommand=false;
 const vscode={ViewColumn:{One:1},workspace:{workspaceFolders:[]},commands:{registerCommand:(id,handler)=>{commands.set(id,handler);return {dispose(){}};},executeCommand:async(...args)=>{if(failCommand)throw Error('private backend detail');calls.push(args);}}};
 const shell={createPanel:()=>{const panel={sent:[],webview:{postMessage:async message=>panel.sent.push(message),onDidReceiveMessage:handler=>panel.receive=handler},reveal(){panel.revealed=true;},onDidDispose:handler=>panel.close=handler};panels.push(panel);return panel;}};
 const loaded={exports:{}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'management.cjs'),'utf8'),{module:loaded,require:name=>name==='./shell.cjs'?shell:require(name)});
 loaded.exports.register({subscriptions:[]},vscode,{getRuntime:()=>{if(failRuntime)throw Error('private runtime diagnostic');return runtime;}});
 return {vscode,commands,calls,panels,setRuntime:value=>runtime=value,setRuntimeFailure:value=>failRuntime=value,setCommandFailure:value=>failCommand=value,open:async()=>{await commands.get('meshlit.management')();return panels.at(-1);}};
}
test('management groups expose implemented destinations and distinguish unbuilt coverage',()=>{
 const html=render();assert.equal(groups.length,5);assert.equal(Object.keys(destinations).length,18);
 for(const destination of Object.keys(destinations))assert(html.includes('data-destination="'+destination+'"'));
 assert(html.includes('Partial device management'));assert(html.includes('Coverage still to build and qualify'));assert(html.includes('thermal and power-saver signals are unavailable'));
 assert(!html.includes('data-destination="thermal"'));assert(!html.includes('data-destination="peerManager"'));
 assert(html.includes('Not reported'));assert(!html.includes('>100%<'));assert(!html.includes('All systems healthy'));
 const nonce=html.match(/<script nonce="([^"]+)"/)[1];assert(html.includes("script-src 'nonce-"+nonce+"'"));
});
test('runtime summaries include only actual supplied state and safely render the model label',()=>{
 const runtime={hostReady:true,localModel:{state:'ready',label:'<img src=x onerror=evil()>'},usageMode:'session',token:'do-not-leak',connectedDevices:99};
 const summary=runtimeSummary(runtime);assert.equal(summary.host,'Ready');assert.equal(summary.model,'Loaded');assert.equal(summary.usage,'Session-only local usage');
 const html=render(runtime);assert(html.includes('&lt;img src=x onerror=evil()&gt;'));assert(!html.includes('<img src=x'));assert(!html.includes('do-not-leak'));assert(!html.includes('99 devices'));
 assert.equal(runtimeSummary({hostReady:false,localModel:{state:'error'},usageMode:'locked'}).model,'Load failed');
 assert.equal(runtimeSummary({usageMode:'constructor',localModel:{state:'constructor'}}).usage,'Not reported');assert.equal(runtimeSummary({usageMode:'constructor',localModel:{state:'constructor'}}).model,'Not reported');
});
test('only allowlisted dashboard messages dispatch and project pages hand off explicitly',async()=>{
 const f=fixture(),panel=await f.open();
 await panel.receive({managementPage:true,op:'open',id:'models',command:'workbench.action.terminal.new'});assert.deepEqual(f.calls.at(-1),['meshlit.mainModels']);
 for(const id of ['constructor','__proto__','workbench.action.terminal.new','missing'])await panel.receive({managementPage:true,op:'open',id});
 await panel.receive({managementPage:false,op:'open',id:'monitor'});assert.equal(f.calls.length,1);
 await panel.receive({managementPage:true,op:'open',id:'memory'});assert.deepEqual(f.calls.at(-1),['meshlit.workspaces']);assert.match(panel.sent.at(-1).message,/Choose a workspace/);
 await panel.receive({managementPage:true,op:'open',id:'credentials'});assert.deepEqual(f.calls.at(-1),['meshlit.credentialsDashboard']);
 await panel.receive({managementPage:true,op:'open',id:'ssh'});assert.deepEqual(f.calls.at(-1),['meshlit.sshDashboard']);
 f.vscode.workspace.workspaceFolders=[{uri:{scheme:'file',fsPath:'/project'}}];await panel.receive({managementPage:true,op:'open',id:'changes'});assert.deepEqual(f.calls.at(-1),['meshlit.workspaceChanges']);
});
test('runtime refresh, failures and panel reuse do not synthesize health or leak diagnostics',async()=>{
 const f=fixture();f.setRuntime({hostReady:true,localModel:{state:'loading',label:'Qwen'},usageMode:'protected'});const panel=await f.open();
 assert.equal(panel.sent.at(-1).runtime.model,'Loading');assert.equal(panel.sent.at(-1).runtime.usage,'Protected history unlocked');
 f.setRuntime({hostReady:false,localModel:{state:'error'},usageMode:'locked'});await panel.receive({managementPage:true,op:'refresh'});assert.equal(panel.sent.at(-1).runtime.host,'Not ready');
 f.setRuntimeFailure(true);await panel.receive({managementPage:true,op:'refresh'});assert(panel.sent.at(-1).error);assert.equal(panel.sent.at(-1).runtime.model,'Not reported');assert(!JSON.stringify(panel.sent.at(-1)).includes('private runtime'));
 f.setCommandFailure(true);await panel.receive({managementPage:true,op:'open',id:'monitor'});assert(panel.sent.at(-1).error);assert(!JSON.stringify(panel.sent.at(-1)).includes('private backend'));
 await f.open();assert.equal(f.panels.length,1);assert(panel.revealed);
 panel.close();f.setCommandFailure(false);await panel.receive({managementPage:true,op:'open',id:'monitor'});assert.equal(f.calls.length,0);
});
