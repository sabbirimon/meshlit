'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm');
const {wrap}=require('./shell.cjs');
const page=`<!doctype html><html><head><meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-test123'; script-src 'nonce-test123';"><style nonce="test123">body{display:flex}body.general{padding:10px}.message-body{white-space:pre-wrap}.body-label{color:red}</style></head><body class="general"><textarea id="prompt"></textarea><script nonce="test123">const api=acquireVsCodeApi();api.postMessage({op:'ready'});</script></body></html>`;
function moduleFresh(){delete require.cache[require.resolve('./shell.cjs')];return require('./shell.cjs');}
function fixture(folder=false){
 const panels=[],calls=[],subscriptions=[];
 const vscode={workspace:{workspaceFolders:folder?[{name:'project'}]:undefined,getConfiguration:()=>({get:()=> 'chat'})},commands:{executeCommand:async(...args)=>calls.push(args)},window:{createWebviewPanel(type,title){
  const handlers=[],disposeHandlers=[],viewHandlers=[],messages=[];
  const panel={title,active:false,webview:{html:'',postMessage(message){messages.push(message);return Promise.resolve(true);},onDidReceiveMessage(handler){handlers.push(handler);return {dispose(){}};}},
   onDidDispose(handler){disposeHandlers.push(handler);return {dispose(){}};},onDidChangeViewState(handler){viewHandlers.push(handler);return {dispose(){}};},
   reveal(){this.revealed=true;this.active=true;for(const handler of viewHandlers)handler();},dispose(){this.disposed=true;for(const handler of disposeHandlers)handler();}};
  panels.push({panel,messages,handlers});return panel;
 }}};
 return {vscode,panels,calls,context:{subscriptions}};
}
test('shell retains CSP and safely escapes titles without inserting command URLs',()=>{
 const html=wrap(page,'<img src=x onerror=alert(1)>','meshlit.chat','"danger');
 assert.match(html,/data-meshlit-shell="true"/);assert.match(html,/class="meshlit-page general"/);
 assert.match(html,/\.message-body\{white-space:pre-wrap\}/);assert.match(html,/\.body-label\{color:red\}/);
 assert.match(html,/\.meshlit-page\{display:flex\}/);assert.match(html,/&lt;img src=x onerror=alert\(1\)&gt;/);
 assert.doesNotMatch(html,/<img src=x/);assert.match(html,/data-shell-id="&quot;danger"/);
 assert.match(html,/default-src 'none'; style-src 'nonce-test123'; script-src 'nonce-test123'/);
 for(const tag of html.matchAll(/<(style|script)\b([^>]*)>/g))assert.match(tag[2],/nonce="test123"/);
 assert.equal(wrap(html,'again','meshlit.chat'),html);
 assert.equal(wrap('<html><body>no nonce</body></html>','Title','meshlit.chat'),'<html><body>no nonce</body></html>');
});
test('shell and page acquire the webview API only once and preserve page ready event',()=>{
 const html=wrap(page,'Chat','meshlit.chat');let acquired=0;const sent=[];
 const api={postMessage:message=>sent.push(message)};
 const sandbox={window:{addEventListener(){}},document:{addEventListener(){}},acquireVsCodeApi(){acquired++;if(acquired>1)throw Error('Duplicate API acquisition');return api;}};
 sandbox.window.window=sandbox.window;vm.createContext(sandbox);
 for(const script of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g))vm.runInContext(script[1],sandbox);
 assert.equal(acquired,1);assert.equal(sent[0].op,'ready');
});
test('standalone callers without initialization keep the original panel',()=>{
 const shell=moduleFresh(),f=fixture();const panel=shell.createPanel(f.vscode,'meshlit.chat','Chat',1,{});assert.equal(panel,f.panels[0].panel);
 shell.initialize(f.context);delete f.vscode.workspace;assert.equal(shell.createPanel(f.vscode,'meshlit.chat','Chat',1,{}),f.panels[1].panel);
});
test('project IDE panels stay unwrapped',()=>{
 const shell=moduleFresh(),f=fixture(true);shell.initialize(f.context);
 const panel=shell.createPanel(f.vscode,'meshlit.chat','Project chat',1,{});panel.webview.html=page;
 assert.equal(panel,f.panels[0].panel);assert.equal(panel.webview.html,page);assert.equal(f.panels[0].handlers.length,0);
});
test('live panel navigation updates, reveals and closes actual panels with command allowlist',async()=>{
 const shell=moduleFresh(),f=fixture();shell.initialize(f.context);
 const first=shell.createPanel(f.vscode,'meshlit.chat','General chat',1,{});first.webview.html=page;
 const second=shell.createPanel(f.vscode,'meshlit.settings','Settings',1,{});second.webview.html=page;
 assert.match(first.webview.html,/data-meshlit-shell/);
 let state=f.panels[0].messages.at(-1);assert.deepEqual(state.panels.map(item=>item.title),['General chat','Settings']);
 second.title='Renamed settings';assert.equal(f.panels[0].messages.at(-1).panels[1].title,'Renamed settings');
 const listener=f.panels[0].handlers[0],secondId=state.panels[1].id;
 await listener({op:'meshlit.shell',action:'reveal',id:secondId});assert.equal(f.panels[1].panel.revealed,true);
 await listener({op:'meshlit.shell',action:'models'});assert.deepEqual(f.calls.at(-1),['meshlit.models']);
 await listener({op:'meshlit.shell',action:'settings'});assert.deepEqual(f.calls.at(-1),['meshlit.settings']);
 await listener({op:'meshlit.shell',action:'workspaces'});assert.deepEqual(f.calls.at(-1),['meshlit.workspaces']);
 const count=f.calls.length;await listener({op:'meshlit.shell',action:'workbench.action.terminal.new'});await listener({op:'meshlit.shell',action:'__proto__'});await listener({op:'send',action:'models'});assert.equal(f.calls.length,count);
 await listener({op:'meshlit.shell',action:'close',id:secondId});assert.equal(f.panels[1].panel.disposed,true);
 assert.equal(f.panels[0].messages.at(-1).panels.length,1);
 await listener({op:'meshlit.shell',action:'reveal',id:'made-up'});assert.equal(f.calls.length,count);
 await listener({op:'meshlit.shell',action:'close',id:f.panels[0].messages.at(-1).panels[0].id});assert.equal(f.panels[0].panel.disposed,undefined);
 first.dispose();
});
