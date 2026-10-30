const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
function fixture(){
 const sent=[],calls=[],commands=new Map();let receive,tick;
 const webview={postMessage:m=>sent.push(m),onDidReceiveMessage:f=>receive=f};
 const vscode={window:{createWebviewPanel:()=>({webview,onDidDispose(){}})},ViewColumn:{One:1},commands:{registerCommand:(id,f)=>{commands.set(id,f);return {dispose(){}};}}};
 class VoiceRuntime{constructor(){this.state='running';}stop(){this.state='stopped';}async request(route,body){calls.push({route,body});if(body?.liveModel==='reject')throw Error('OpenLive rejected the request (HTTP 400)');return route==='/api/providers'?[{kind:'ollama',name:'Local',apiKey:'must-not-forward'}]:{liveModel:'actual-model',liveProviderId:'ollama'};}}
 const module={exports:{}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'voice.cjs'),'utf8'),{module,require:n=>n==='vscode'?vscode:n==='./voice-runtime.cjs'?{VoiceRuntime,KEYS:['liveProviderId','liveModel'],ORIGIN:'http://127.0.0.1:47834'}:require(n),setInterval:f=>{tick=f;},clearInterval(){}});
 module.exports.register({subscriptions:[],globalState:{get:()=>'/installed/openlive'}});commands.get('meshlit.voice')();
 return {sent,calls,receive:m=>receive(m),tick:()=>tick()};
}
test('dashboard uses actual settings/provider kinds and never forwards provider secrets',async()=>{
 const f=fixture();await f.receive({op:'load'});const m=f.sent.at(-1);
 assert.equal(m.settings.liveModel,'actual-model');assert.equal(m.providers[0].id,'ollama');assert(!JSON.stringify(m).includes('must-not-forward'));
 await f.receive({op:'save',settings:{liveModel:'chosen'}});assert.equal(f.calls.at(-3).body.liveModel,'chosen');
});
test('polling preserves failed save message and never reports rejected settings as saved',async()=>{
 const f=fixture();await f.receive({op:'save',settings:{liveModel:'reject'}});assert.match(f.sent.at(-1).message,/rejected/);
 f.tick();assert.equal(Object.hasOwn(f.sent.at(-1),'message'),false);assert(!f.sent.some(m=>m.message?.startsWith('Saved')));
});
