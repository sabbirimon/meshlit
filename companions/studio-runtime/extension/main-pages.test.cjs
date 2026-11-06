const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const {render,validateSettings}=require('./main-pages.cjs');
function fixture(getRuntime){
 const commands=new Map(),calls=[],panels=[],updates=[],settings=new Map(),saved=new Map();let directory=true,updateFailure=false,reviewApproved=true;
 const api={ViewColumn:{One:1},ConfigurationTarget:{Global:1},FileType:{Directory:2},Uri:{file:value=>({scheme:'file',fsPath:value})},
  workspace:{fs:{stat:async()=>({type:directory?2:1})},getConfiguration:()=>({get:(key,fallback)=>settings.has(key)?settings.get(key):fallback,update:async(key,value,target)=>{if(updateFailure)throw Error('Write failed');updates.push([key,value,target]);settings.set(key,value);}})},
  commands:{registerCommand:(id,handler)=>{commands.set(id,handler);return {dispose(){}};},executeCommand:async(...args)=>args[0]==='meshlit.reviewProject'?reviewApproved:calls.push(args)}};
 const shell={createPanel:()=>{const panel={sent:[],title:'test',reveal(){this.revealed=true;},onDidDispose:handler=>panel.close=handler,webview:{postMessage:async message=>panel.sent.push(message),onDidReceiveMessage:handler=>panel.receive=handler}};panels.push(panel);return panel;}};
 const loaded={exports:{}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'main-pages.cjs'),'utf8'),{module:loaded,require:name=>name==='./shell.cjs'?shell:require(name)});
 loaded.exports.register({subscriptions:[],globalState:{get:(key,fallback)=>saved.get(key)||fallback}},api,{getRuntime});
 return {commands,calls,panels,updates,settings,saved,setReviewApproved:value=>reviewApproved=value,setDirectory:value=>directory=value,setFailure:value=>updateFailure=value,open:async kind=>{await commands.get('meshlit.main'+kind)();return panels.at(-1);}};
}
test('main pages keep untrusted names inert and model readiness unclaimed',()=>{
 const html=render('models',{profiles:[{id:'<x>',service:'<script>evil()</script>',model:'"Q"'}]});
 assert(!html.includes('<script>evil()'));
 assert(html.includes('&lt;script&gt;evil()&lt;/script&gt;'));
 assert(html.includes('data-id="&lt;x&gt;"'));
 assert(html.includes('current loading status'));
 const scripts=[...html.matchAll(/<script nonce="([^"]+)"/g)];assert.equal(scripts.length,1);
 assert(html.includes("script-src 'nonce-"+scripts[0][1]+"'"));
 assert(render('workspaces',{workspaces:[]}).includes('Your recent projects will appear here'));
});
test('LLM settings reject nonnumeric, partial, unknown and out-of-range fields',()=>{
 const valid={temperature:0.4,maxOutputTokens:1024,historyTurns:5};
 assert.deepEqual(validateSettings(valid),valid);
 for(const invalid of [{...valid,temperature:'1'},{...valid,temperature:Infinity},{...valid,maxOutputTokens:2048},{...valid,historyTurns:2.5},{temperature:0.4},{...valid,token:'secret'}])assert.throws(()=>validateSettings(invalid));
});
test('workspace page opens only current saved directories in a separate native window',async()=>{
 const f=fixture();f.saved.set('meshlit.recentWorkspaces',[{path:'/test/project',name:'Project'}]);
 const panel=await f.open('Workspaces');
 await panel.receive({mainPage:true,op:'workspace',id:'/arbitrary/path'});
 assert.equal(f.calls.length,0);assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error,/no longer/);
 f.setDirectory(false);await panel.receive({mainPage:true,op:'workspace',id:'/test/project'});
 assert.equal(f.calls.length,0);assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error,/unavailable/);
 f.setDirectory(true);await panel.receive({mainPage:true,op:'workspace',id:'/test/project'});
 assert.equal(f.calls[0][0],'vscode.openFolder');assert.equal(f.calls[0][1].fsPath,'/test/project');assert.equal(f.calls[0][2].forceNewWindow,true);
 await panel.receive({mainPage:true,op:'browse'});assert.equal(f.calls.at(-1)[0],'meshlit.chooseWorkspace');
});
test('models page revalidates saved connections before dispatch and uses picker for management',async()=>{
 const f=fixture();f.saved.set('meshlit.connections',[{id:'saved',service:'Server',model:'Qwen'}]);
 const panel=await f.open('Models');
 await panel.receive({mainPage:true,op:'model',id:'saved'});assert.deepEqual(f.calls.at(-1),['meshlit.chat','saved']);
 f.saved.set('meshlit.connections',[]);await panel.receive({mainPage:true,op:'model',id:'saved'});assert.equal(f.calls.length,1);assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error,/no longer/);
 await panel.receive({mainPage:true,op:'manage'});assert.equal(f.calls.at(-1)[0],'meshlit.modelPicker');
 await panel.receive({mainPage:true,op:'workbench.action.terminal.new'});assert.equal(f.calls.length,2);
});
test('saved chat settings persist, reload and validate before writing',async()=>{
 const f=fixture();const panel=await f.open('LlmSettings');
 await panel.receive({mainPage:true,op:'save',values:{temperature:0.7,maxOutputTokens:512,historyTurns:3}});
 assert.equal(f.updates.length,3);assert(f.updates.every(row=>row[2]===1));assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).message,/next chat request/);
 assert.equal(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).settings.temperature,0.7);
 await panel.receive({mainPage:true,op:'save',values:{temperature:1,maxOutputTokens:9999,historyTurns:3}});
 assert.equal(f.updates.length,3);assert(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error);
 await panel.receive({mainPage:true,op:'refresh'});assert(panel.webview.html.includes('value="0.7"'));
 await f.commands.get('meshlit.mainLlmSettings')();assert.equal(f.panels.length,1);assert(panel.revealed);
 f.setFailure(true);await panel.receive({mainPage:true,op:'save',values:{temperature:1,maxOutputTokens:128,historyTurns:1}});
 assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error,/Could not save all settings/);
 panel.close();await panel.receive({mainPage:true,op:'save',values:{temperature:1,maxOutputTokens:128,historyTurns:1}});assert.equal(f.updates.length,3);
});

test('model dashboard reports observed runtime state and refreshes without assuming readiness',async()=>{
 let runtime={hostReady:true,localModel:{state:'loading',label:'Test model'}};
 const f=fixture(()=>runtime),panel=await f.open('Models');
 assert.match(panel.webview.html,/Test model/);assert.match(panel.webview.html,/role="status">Loading/);assert.doesNotMatch(panel.webview.html,/role="status">Ready/);
 runtime={hostReady:true,localModel:{state:'ready',label:'<b>Actual model</b>'}};await panel.receive({mainPage:true,op:'refresh'});
 assert.match(panel.webview.html,/&lt;b&gt;Actual model&lt;\/b&gt;/);assert.match(panel.webview.html,/role="status">Ready/);
 assert.match(render('models'),/Status not checked/);assert.doesNotMatch(render('models'),/role="status">Ready/);
 assert.match(render('models',{runtime:{hostReady:false,localModel:{state:'idle'}}}),/Model host not ready · Not loaded/);
});
test('model mode cards and provider dashboards dispatch only explicit commands',async()=>{
 const f=fixture(),panel=await f.open('Models');
 for(const [op,target]of [['generalChat','meshlit.generalChat'],['workspaceAgents','meshlit.workspaceAgents'],['gatewayOmni','meshlit.gateway.OmniRoute'],['gatewayFree','meshlit.gateway.FreeLLMAPI'],['gatewayHf','meshlit.gateway.Hugging Face'],['permissions','meshlit.permissions']]){
  await panel.receive({mainPage:true,op});assert.equal(f.calls.at(-1)[0],target);
 }
 const count=f.calls.length;for(const op of ['__proto__','constructor','meshlit.gateway.Evil'])await panel.receive({mainPage:true,op});assert.equal(f.calls.length,count);
 assert.match(panel.webview.html,/Chat and agent modes/);assert.match(panel.webview.html,/bundled reasoning model offers Thinking on\/off; the starter uses Auto/);
});
test('permissions page exposes independent scopes and cannot write chat settings',async()=>{
 const f=fixture(()=>({usageMode:'session'}));await f.commands.get('meshlit.permissions')();const panel=f.panels.at(-1);
 assert.match(panel.webview.html,/General Chat has no workspace tools/);assert.match(panel.webview.html,/session-only usage/);assert.match(panel.webview.html,/do not grant shell, device, MCP, credential, or provider access/);
 for(const [op,target]of [['projectSecurity','meshlit.projectSecurity'],['appAccess','meshlit.appAccess'],['connectors','meshlit.connectors'],['deviceConnections','meshlit.deviceConnections']]){await panel.receive({mainPage:true,op});assert.equal(f.calls.at(-1)[0],target);}
 const count=f.calls.length;await panel.receive({mainPage:true,op:'executeCommand',id:'workbench.action.terminal.new'});assert.equal(f.calls.length,count);
 await panel.receive({mainPage:true,op:'chatLock',required:false});await panel.receive({mainPage:true,op:'save',values:{temperature:1,maxOutputTokens:128,historyTurns:1}});assert.equal(f.updates.length,0);assert.equal(panel.sent.filter(message=>message.type==='mainPageResult'&&message.error).length,0);assert.equal(panel.sent.at(-1).type,'mainPageIdle');
});
test('chat access checkbox retains persisted setting and rejects nonboolean requests',async()=>{
 const f=fixture(),panel=await f.open('LlmSettings');
 await panel.receive({mainPage:true,op:'chatLock',required:true});assert.deepEqual(f.updates.at(-1),['requireUnlock',true,1]);
 await panel.receive({mainPage:true,op:'refresh'});assert.match(panel.webview.html,/id="require-unlock" type="checkbox" checked/);
 await panel.receive({mainPage:true,op:'chatLock',required:'false'});assert.equal(f.updates.length,1);assert.match(panel.sent.filter(message=>message.type==='mainPageResult').at(-1).error,/valid chat access/);
});

test('cancelling project security returns the page to idle without opening a workspace',async()=>{
 const f=fixture();f.saved.set('meshlit.recentWorkspaces',[{path:'/test/project',name:'Project'}]);f.setReviewApproved(false);
 const panel=await f.open('Workspaces');await panel.receive({mainPage:true,op:'workspace',id:'/test/project'});
 assert.equal(f.calls.length,0);assert.deepEqual(JSON.parse(JSON.stringify(panel.sent)),[{type:'mainPageIdle'}]);
 f.setReviewApproved(true);await panel.receive({mainPage:true,op:'workspace',id:'/test/project'});assert.equal(f.calls.at(-1)[0],'vscode.openFolder');
 assert.match(panel.webview.html,/data.type==='mainPageIdle'/);
});

test('observed runtime identity is used only on its matching bundled model card',()=>{
 const models=require('./local-models.cjs'),previous=models.list();
 try{
  models.accept([{id:models.STARTER,label:'Starter catalog label',present:true,thinkingSupported:false},{id:'qwen3-4b',label:'Reasoning catalog label',present:true,thinkingSupported:true}]);
  const html=render('models',{runtime:{hostReady:true,localModel:{modelId:'qwen3-4b',label:'Observed reasoning model',state:'loading'}}});
  const cards=html.match(/<article class="model">[\s\S]*?<\/article>/g);
  assert.match(cards[0],/Starter catalog label/);assert.doesNotMatch(cards[0],/Observed reasoning model|role="status">Loading/);
  assert.match(cards[1],/Observed reasoning model/);assert.match(cards[1],/role="status">Loading/);
 }finally{models.accept(previous);}
});
