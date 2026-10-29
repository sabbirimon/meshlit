const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
function fixture(){
 const commands=new Map(),calls=[],panels=[],saved=new Map(),errors=[];let state={state:'stopped'};
 class GatewayRuntime{onChange(){return{dispose(){}};}status(){return state;}async start(name){calls.push(['start',name]);return state={state:'running',url:'http://127.0.0.1:20128/',api:'http://127.0.0.1:20128/v1'};}async stop(name){calls.push(['stop',name]);state={state:'stopped'};}}
 const vscode={ViewColumn:{One:1},workspace:{isTrusted:true,getConfiguration:()=>({get:()=> 'ephemeral'})},env:{clipboard:{writeText:async value=>calls.push(['copy',value])}},commands:{registerCommand:(name,f)=>commands.set(name,f),getCommands:async()=>['workbench.action.browser.open'],executeCommand:async(...args)=>calls.push(args)},window:{createWebviewPanel:()=>{const p={sent:[],webview:{postMessage:m=>p.sent.push(m),onDidReceiveMessage:f=>p.receive=f},onDidDispose(){},reveal(){}};panels.push(p);return p;}}};
 const module={exports:{}};const context={extensionPath:'/fixture',globalStorageUri:{fsPath:'/fixture'},subscriptions:[],globalState:{get:(key,fallback)=>saved.get(key)??fallback,update:async(key,v)=>saved.set(key,v)}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'gateways.cjs'),'utf8'),{module,URL,require:n=>n==='vscode'?vscode:n==='./gateway-runtime.cjs'?{GatewayRuntime}:n==='node:fs'?{readFileSync:()=> '{}'}:n.startsWith('./')?{}:require(n)});
 module.exports.register(context);commands.get('meshlit.gateway.OmniRoute')();return{vscode,panel:panels[0],calls,saved};
}
test('gateway actions start owned service, open integrated dashboard, copy URL and stop',async()=>{
 const f=fixture();await f.panel.receive({op:'start'});
 assert.equal(f.calls[0][0],'start');assert.equal(f.calls[1][0],'workbench.action.browser.open');assert.equal(f.calls[1][1].url,'http://127.0.0.1:20128/');
 await f.panel.receive({op:'copyApi'});assert.equal(f.calls.at(-1)[1],'http://127.0.0.1:20128/v1');
 await f.panel.receive({op:'stop'});assert.equal(f.calls.at(-1)[0],'stop');assert.equal(f.panel.sent.at(-1).runtime.state,'stopped');
});
test('untrusted workspace cannot start gateway runtime',async()=>{
 const f=fixture();f.vscode.workspace.isTrusted=false;await f.panel.receive({op:'start'});
 assert.equal(f.calls.length,0);assert.match(f.panel.sent.find(m=>m.type==='error').message,/Trust/);
});
